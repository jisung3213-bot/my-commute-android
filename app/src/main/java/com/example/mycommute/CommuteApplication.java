package com.example.mycommute;

import android.app.Application;
import org.osmdroid.config.Configuration;
import com.example.mycommute.alarms.NotificationHelper;

public class CommuteApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        NotificationHelper.ensureChannels(this);
    }
}
