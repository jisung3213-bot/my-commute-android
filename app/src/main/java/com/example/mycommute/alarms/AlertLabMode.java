package com.example.mycommute.alarms;

import android.content.Context;
import com.example.mycommute.BuildConfig;

/** Release builds ALWAYS run production; debug-only, device-local preference. */
public final class AlertLabMode {
    private static final String PREF="alert_lab_private";
    private AlertLabMode(){}
    public static boolean enabled(Context c){
        return BuildConfig.DEBUG && c.getSharedPreferences(PREF,Context.MODE_PRIVATE)
                .getBoolean("enabled",false);
    }
    public static void setEnabled(Context c,boolean enabled){
        if(!BuildConfig.DEBUG)return;
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putBoolean("enabled",enabled).apply();
    }
}
