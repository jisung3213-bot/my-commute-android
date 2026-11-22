package com.example.mycommute.data;

import android.content.Context;
import com.example.mycommute.engine.CommuteWindow;
import com.example.mycommute.engine.PlanSchedule;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Display format only. Stored routine times stay in HH:mm. */
public final class ClockPreference {
    private ClockPreference() {}

    public static boolean amPm(Context context) {
        return RoutineStore.prefs(context).getBoolean("clock_am_pm",false);
    }

    public static void setAmPm(Context context,boolean enabled) {
        RoutineStore.prefs(context).edit().putBoolean("clock_am_pm",enabled).apply();
    }

    public static String time(Context context,String time) {
        if(!PlanSchedule.validTime(time)||!amPm(context))return time;
        return time(context,PlanSchedule.arrivalOn(CommuteWindow.seoulCalendar(),time)
                .getTimeInMillis());
    }

    public static String time(Context context,long timestamp) {
        return format(context,timestamp,"");
    }

    public static String dateTime(Context context,long timestamp) {
        return format(context,timestamp,"yyyy.MM.dd E ");
    }

    public static String monthDayTime(Context context,long timestamp) {
        return format(context,timestamp,"MM/dd ");
    }

    private static String format(Context context,long timestamp,String prefix) {
        SimpleDateFormat formatter=new SimpleDateFormat(prefix+(amPm(context)?"a h:mm":"HH:mm"),
                Locale.KOREA);
        formatter.setTimeZone(CommuteWindow.seoulCalendar().getTimeZone());
        return formatter.format(new Date(timestamp));
    }
}
