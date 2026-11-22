package com.example.mycommute.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.mycommute.engine.CommuteWindow;
import com.example.mycommute.engine.PlanSchedule;
import com.example.mycommute.model.Routine;
import com.example.mycommute.network.KmaWeatherClient;
import java.util.Calendar;

/** A commute occurrence is identified by its scheduled arrival in Seoul time. */
public final class DepartureSchedule {
    public static final int MAX_WEATHER_MINUTES = 15;
    private static final long MINUTE = 60_000L;
    private static final String WEATHER_PREFIX = "departure_weather_";

    public static final class Plan {
        public final long target, departure;
        public final int durationMinutes, weatherMinutes;
        public final String weatherNotice;

        private Plan(Routine routine, int duration, long target, int extra, String notice) {
            this.target = target;
            durationMinutes = duration + extra;
            weatherMinutes = extra;
            weatherNotice = notice;
            departure = CommuteWindow.plannedDeparture(routine, durationMinutes, target);
        }

        public long eventAt(int leadMinutes) {
            return departure - leadMinutes * MINUTE;
        }

        public String weatherLabel() {
            return weatherMinutes > 0 ? " · 기상으로 " + weatherMinutes + "분 앞당김" : "";
        }
    }

    private DepartureSchedule() {}

    public static int extraMinutes(int precipitationType, int probability, double temperature) {
        int rainOrSnow = precipitationType == 2 || precipitationType == 3 ? 10
                : precipitationType == 1 || precipitationType == 4 || probability >= 60 ? 5 : 0;
        int cold = Double.isFinite(temperature) && temperature <= 0 ? 5 : 0;
        return Math.min(MAX_WEATHER_MINUTES, rainOrSnow + cold);
    }

    static String weatherKey(Routine routine, long target) {
        return WEATHER_PREFIX + routine.id + ":" + target + ":" + routine.startLat + "," + routine.startLon
                + ":" + routine.marginMinutes + ":" + routine.estimatedMinutes + ":" + routine.selectedRouteSignature;
    }

    public static boolean isWeatherPreference(String key) {
        return key != null && key.startsWith(WEATHER_PREFIX);
    }

    static boolean hasWeather(Context context, Routine routine, long target) {
        return RoutineStore.prefs(context).contains(weatherKey(routine, target));
    }

    static void saveWeather(Context context, Routine routine, long target, KmaWeatherClient.Report report) {
        int extra = extraMinutes(report.precipitationType, report.rainProbability, report.temperatureC);
        String key = weatherKey(routine, target);
        SharedPreferences preferences = RoutineStore.prefs(context);
        SharedPreferences.Editor edit = preferences.edit().putInt(key, extra);
        if (extra > 0) {
            edit.putString(key + ":notice", report.conditionText() + " · " + report.temperatureText());
        }
        // Keep the current and next occurrence, even when an overnight commute crosses a date.
        String prefix = WEATHER_PREFIX + routine.id + ":";
        long oldest = target - 2 * 24 * 60 * MINUTE;
        for (String saved : preferences.getAll().keySet()) {
            if (saved.startsWith(prefix)) {
                int end = saved.indexOf(':', prefix.length());
                if (end > prefix.length() && Long.parseLong(saved.substring(prefix.length(), end)) < oldest) {
                    edit.remove(saved);
                }
            }
        }
        edit.apply();
    }

    public static Plan forTarget(Context context, Routine routine, int duration, long target) {
        if (duration < 1 || !routine.isConfigured() || target <= 0 || target == Long.MAX_VALUE) return null;
        Calendar day = CommuteWindow.seoulCalendar();
        day.setTimeInMillis(target);
        if (!routine.runsOn(day.get(Calendar.DAY_OF_WEEK))
                || PlanSchedule.arrivalOn(day, routine.arrivalTime).getTimeInMillis() != target) return null;
        return plan(context, routine, duration, target);
    }

    public static Plan next(Context context, Routine routine, int duration, int lead, long now) {
        if (duration < 1 || !routine.isConfigured()) return null;
        Calendar day = CommuteWindow.seoulCalendar();
        day.setTimeInMillis(now);
        for (int offset = 0; offset <= 8; offset++, day.add(Calendar.DAY_OF_YEAR, 1)) {
            if (!routine.runsOn(day.get(Calendar.DAY_OF_WEEK))) continue;
            Plan plan = plan(context, routine, duration, PlanSchedule.arrivalOn(day, routine.arrivalTime).getTimeInMillis());
            if (plan.eventAt(lead) > now) return plan;
        }
        return null;
    }

    public static Plan currentTracking(Context context, Routine routine, int duration, long now) {
        if (duration < 1 || !routine.isConfigured()) return null;
        Calendar day = CommuteWindow.seoulCalendar();
        day.setTimeInMillis(now);
        day.add(Calendar.DAY_OF_YEAR, -1);
        for (int offset = -1; offset <= 1; offset++, day.add(Calendar.DAY_OF_YEAR, 1)) {
            if (!routine.runsOn(day.get(Calendar.DAY_OF_WEEK))) continue;
            Plan plan = plan(context, routine, duration, PlanSchedule.arrivalOn(day, routine.arrivalTime).getTimeInMillis());
            if (now >= plan.eventAt(CommuteWindow.TRACKING_LEAD_MINUTES)
                    && now < plan.target + CommuteWindow.TRACKING_GRACE_MS) return plan;
        }
        return null;
    }

    private static Plan plan(Context context, Routine routine, int duration, long target) {
        if (!routine.weatherEnabled) return new Plan(routine, duration, target, 0, "");
        SharedPreferences preferences = RoutineStore.prefs(context);
        String key = weatherKey(routine, target);
        int extra = preferences.getInt(key, 0);
        return new Plan(routine, duration, target, extra, preferences.getString(key + ":notice", ""));
    }
}
