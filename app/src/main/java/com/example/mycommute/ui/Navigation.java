package com.example.mycommute.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import androidx.core.view.ViewCompat;
import com.example.mycommute.R;
import com.google.android.material.button.MaterialButton;

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
        if(view==null)return;
        boolean selected=a.getClass()==destination;
        view.setSelected(selected);
        if(selected&&view instanceof Button){
            Button button=(Button)view;
            button.setBackgroundTintList(ColorStateList.valueOf(a.getColor(R.color.primary_light)));
            button.setTypeface(null,Typeface.BOLD);
            ViewCompat.setStateDescription(button,"현재 화면");
        }
        view.setOnClickListener(v -> {
            if(a.getClass()==destination) return;
            Intent intent=new Intent(a,destination);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            a.startActivity(intent);
        });
    }

    static void direction(Activity activity,int id,boolean selected){
        MaterialButton tab=activity.findViewById(id);
        tab.setSelected(selected);
        tab.setBackgroundTintList(ColorStateList.valueOf(
                activity.getColor(selected?R.color.primary:R.color.surface)));
        tab.setTextColor(activity.getColor(selected?R.color.surface:R.color.primary_dark));
        tab.setStrokeColor(ColorStateList.valueOf(
                activity.getColor(selected?R.color.primary:R.color.border)));
        tab.setStrokeWidth(Math.round(activity.getResources().getDisplayMetrics().density));
        tab.setTypeface(null,selected?Typeface.BOLD:Typeface.NORMAL);
        ViewCompat.setStateDescription(tab,selected?"선택됨":null);
    }
}
