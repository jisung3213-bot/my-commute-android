package com.example.mycommute.alarms;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.example.mycommute.data.Session;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String action=intent.getAction();
        if(action==null||!Session.signedIn(context))return;
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)){
            AutoCommute.restoreAfterBoot(context);
            ReminderScheduler.scheduleCurrent(context);
        }else if(Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action))ReminderScheduler.scheduleCurrent(context);
        else if(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            if(Build.VERSION.SDK_INT<31 || context.getSystemService(AlarmManager.class).canScheduleExactAlarms())
                ReminderScheduler.scheduleCurrent(context);
        }
    }
}
