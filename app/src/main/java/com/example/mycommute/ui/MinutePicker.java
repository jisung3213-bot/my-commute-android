package com.example.mycommute.ui;

import android.app.Activity;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;

/**
 * Read-only minute field backed by a native wheel picker.
 * Keeps the selected number in a view tag until the existing Save action commits it.
 */
public final class MinutePicker {
    private MinutePicker() {}

    public static void attach(Activity activity,int viewId,String title,
                              int minimum,int maximum,int initial) {
        MaterialButton button=activity.findViewById(viewId);
        set(button,initial,minimum,maximum);
        button.setTag(com.example.mycommute.R.id.minute_picker_accessibility_title,title);
        button.setContentDescription(title+" · "+value(button)+"분 · 변경");
        button.setOnClickListener(v->{
            NumberPicker wheel=new NumberPicker(activity);
            wheel.setMinValue(minimum);
            wheel.setMaxValue(maximum);
            wheel.setWrapSelectorWheel(false);
            wheel.setValue(value(button));
            wheel.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            int pad=(int)(activity.getResources().getDisplayMetrics().density*20+0.5f);
            android.widget.FrameLayout frame=new android.widget.FrameLayout(activity);
            frame.setPadding(pad,pad,pad,pad);
            frame.addView(wheel,new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setView(frame)
                    .setNegativeButton("취소",null)
                    .setPositiveButton("확인",(dialog,which)->{
                        wheel.clearFocus();
                        set(button,wheel.getValue(),minimum,maximum);
                        button.setContentDescription(title+" · "+value(button)+"분 · 변경");
                    })
                    .show();
        });
    }

    public static void set(View field,int minutes,int minimum,int maximum){
        if(minutes<minimum||minutes>maximum)
            throw new IllegalArgumentException("Minutes out of range");
        field.setTag(minutes);
        ((TextView)field).setText(minutes+"분");
        Object title=field.getTag(com.example.mycommute.R.id.minute_picker_accessibility_title);
        if(title instanceof String)
            field.setContentDescription(title+" · "+minutes+"분 · 변경");
    }

    public static int value(View field){
        Object stored=field.getTag();
        if(!(stored instanceof Integer))
            throw new IllegalStateException("Minute picker not initialized");
        return (Integer)stored;
    }
}
