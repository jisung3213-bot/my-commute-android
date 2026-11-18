package com.example.mycommute.alarms;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.app.PendingIntent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.example.mycommute.R;
import com.example.mycommute.ui.TrackingActivity;

public final class NotificationHelper {
    public static final String REMINDER="commute_reminders";
    public static final String TRACKING="commute_tracking";
    private NotificationHelper() { }
    public static void ensureChannels(Context context) {
        if(Build.VERSION.SDK_INT>=26) {
            NotificationManager manager=context.getSystemService(NotificationManager.class);
            if(manager==null)return;
            manager.createNotificationChannel(new NotificationChannel(REMINDER,"출퇴근 계획 알림",NotificationManager.IMPORTANCE_HIGH));
            manager.createNotificationChannel(new NotificationChannel(TRACKING,"위치 이동 추적",NotificationManager.IMPORTANCE_LOW));
        }
    }
    public static PendingIntent trackingIntent(Context context) {
        Intent i=new Intent(context,TrackingActivity.class);
        return PendingIntent.getActivity(context,99,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static NotificationCompat.Builder trackingBuilder(Context context) {
        ensureChannels(context);
        return new NotificationCompat.Builder(context,TRACKING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("나만의 출퇴근 · 이동 추적 중")
                .setContentText("위치 추적 중 · 탭하여 이동 화면 열기")
                .setContentIntent(trackingIntent(context)).setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
    }
    public static boolean show(Context context,String title,String message,int uniqueId,String routineId) {
        ensureChannels(context);
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)
                !=PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if(!NotificationManagerCompat.from(context).areNotificationsEnabled())return false;
        if(manager!=null&&manager.getNotificationChannel(REMINDER)!=null&&manager.getNotificationChannel(REMINDER).getImportance()==NotificationManager.IMPORTANCE_NONE)return false;
        Intent open=new Intent(context,TrackingActivity.class);open.putExtra("routineId",routineId);
        PendingIntent destination=PendingIntent.getActivity(context,routineId.hashCode(),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try {
            NotificationCompat.Builder n=new NotificationCompat.Builder(context,REMINDER)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(destination).setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
            NotificationManagerCompat.from(context).notify(uniqueId,n.build());return true;
        }catch(SecurityException e){return false;}
    }
}
