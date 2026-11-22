package com.example.mycommute.data;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import com.example.mycommute.alarms.AutoCommute;
import com.example.mycommute.alarms.ReminderScheduler;
import com.example.mycommute.engine.CommuteWindow;
import com.example.mycommute.model.Routine;
import com.example.mycommute.network.KmaWeatherClient;
import java.util.HashSet;
import java.util.Set;

/** Looks up a default routine's departure forecast before its alarm or location window. */
public final class WeatherDepartureJob extends JobService {
    private static final long MINUTE = 60_000L;
    private static final long LOOKUP_BEFORE_CUTOFF = 45 * MINUTE;
    private static final long RETRY_DELAY = 15 * MINUTE;
    private final Set<JobParameters> running = new HashSet<>();

    public static void schedule(Context context) {
        for (String direction : RoutineStore.IDS) scheduleDirection(context, direction, 0, 0);
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        for (String direction : RoutineStore.IDS) scheduler.cancel(jobId(direction));
    }

    private static int jobId(String direction) {
        return RoutineStore.RETURN.equals(direction) ? 662 : 661;
    }

    private static Routine eligibleRoutine(Context context, String direction) {
        if (!Session.signedIn(context) || !KmaWeatherClient.enabled()) return null;
        Routine routine = RoutineStore.read(context, RoutineStore.defaultId(context, direction));
        return routine.weatherEnabled && routine.isConfigured() && routine.validCoordinates() ? routine : null;
    }

    private static int cutoffLead(Context context) {
        return Math.max(AlarmSettingsStore.prepareMinutes(context), CommuteWindow.TRACKING_LEAD_MINUTES)
                + DepartureSchedule.MAX_WEATHER_MINUTES;
    }

    private static void scheduleDirection(Context context, String direction, long retryTarget, long retryAt) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        int id = jobId(direction);
        Routine routine = eligibleRoutine(context, direction);
        int duration = routine == null ? -1 : ReminderScheduler.durationMinutes(context, routine);
        if (duration < 1) {
            scheduler.cancel(id);
            return;
        }
        int lead = cutoffLead(context);
        long now = System.currentTimeMillis();
        long cutoff = CommuteWindow.nextDeparture(routine, duration, lead, now);
        if (cutoff <= 0) return;
        long target = cutoff + (duration + (long) routine.marginMinutes + lead) * MINUTE;
        if (DepartureSchedule.hasWeather(context, routine, target)
                || (target == retryTarget && retryAt >= cutoff)) {
            cutoff = CommuteWindow.nextDeparture(routine, duration, lead, cutoff);
            if (cutoff <= 0) return;
            target = cutoff + (duration + (long) routine.marginMinutes + lead) * MINUTE;
        }
        String key = DepartureSchedule.weatherKey(routine, target);
        String account = Session.accountId(context);
        JobInfo pending = scheduler.getPendingJob(id);
        if (retryAt == 0 && pending != null && pending.getExtras().getLong("cutoff") == cutoff
                && key.equals(pending.getExtras().getString("key"))
                && account.equals(pending.getExtras().getString("account"))) return;

        long lookupAt = cutoff - LOOKUP_BEFORE_CUTOFF;
        if (target == retryTarget) lookupAt = Math.max(lookupAt, retryAt);
        PersistableBundle extras = new PersistableBundle();
        extras.putString("account", account);
        extras.putString("direction", direction);
        extras.putString("key", key);
        extras.putLong("target", target);
        extras.putLong("cutoff", cutoff);
        scheduler.schedule(new JobInfo.Builder(id, new ComponentName(context, WeatherDepartureJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(Math.max(0, lookupAt - now))
                .setExtras(extras)
                .build());
    }

    @Override public boolean onStartJob(JobParameters params) {
        PersistableBundle extras = params.getExtras();
        String direction = extras.getString("direction");
        long target = extras.getLong("target");
        long cutoff = extras.getLong("cutoff");
        Routine routine = currentRoutine(params);
        if (routine == null || DepartureSchedule.hasWeather(this, routine, target)) {
            // Post after the current job has returned so scheduling the next one can't replace it.
            new android.os.Handler(getMainLooper()).post(() -> scheduleDirection(this, direction, 0, 0));
            return false;
        }

        running.add(params);
        long departure = cutoff + cutoffLead(this) * MINUTE;
        KmaWeatherClient.forecastForDeparture(routine, departure, new KmaWeatherClient.Callback() {
            @Override public void success(KmaWeatherClient.Report report) {
                if (!running.remove(params)) return;
                Routine latest = currentRoutine(params);
                if (latest != null) {
                    DepartureSchedule.saveWeather(WeatherDepartureJob.this, latest, target, report);
                    ReminderScheduler.schedule(WeatherDepartureJob.this, latest);
                    AutoCommute.schedule(WeatherDepartureJob.this);
                }
                jobFinished(params, false);
                scheduleDirection(WeatherDepartureJob.this, direction, 0, 0);
            }

            @Override public void error(String reason) {
                if (!running.remove(params)) return;
                jobFinished(params, false);
                scheduleDirection(WeatherDepartureJob.this, direction, target,
                        System.currentTimeMillis() + RETRY_DELAY);
            }
        });
        return true;
    }

    private Routine currentRoutine(JobParameters params) {
        PersistableBundle extras = params.getExtras();
        long target = extras.getLong("target");
        long cutoff = extras.getLong("cutoff");
        if (!Session.accountId(this).equals(extras.getString("account"))
                || System.currentTimeMillis() >= cutoff) return null;
        Routine current = eligibleRoutine(this, extras.getString("direction"));
        if (current == null || !DepartureSchedule.weatherKey(current, target).equals(extras.getString("key"))) return null;
        int duration = ReminderScheduler.durationMinutes(this, current);
        DepartureSchedule.Plan plan = DepartureSchedule.forTarget(this, current, duration, target);
        return plan != null && CommuteWindow.plannedDeparture(current, duration, target)
                - cutoffLead(this) * MINUTE == cutoff ? current : null;
    }

    @Override public boolean onStopJob(JobParameters params) {
        running.remove(params);
        return true;
    }
}
