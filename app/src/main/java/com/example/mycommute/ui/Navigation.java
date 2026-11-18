package com.example.mycommute.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import com.example.mycommute.R;

/** Simple uniform bottom navigation for the six PDF-defined screens. */
public final class Navigation {
    private Navigation() { }
    public static void bind(Activity a) {
        go(a,R.id.navHome,MainActivity.class);
        go(a,R.id.navCustom,CustomActivity.class);
        go(a,R.id.navMap,MapActivity.class);
        go(a,R.id.navTracking,TrackingActivity.class);
        go(a,R.id.navSettings,SettingsActivity.class);
    }
    private static void go(Activity a,int id,Class<? extends Activity> destination) {
        View view=a.findViewById(id);
        if(view!=null) view.setOnClickListener(v -> {
            if(a.getClass()==destination) return;
            Intent intent=new Intent(a,destination);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            a.startActivity(intent);
        });
    }
}
