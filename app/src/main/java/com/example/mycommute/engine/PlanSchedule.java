package com.example.mycommute.engine;
import java.util.Calendar;
import java.util.Locale;
/** Weekdays refer to the arrival date, including overnight departures. */
public final class PlanSchedule {
    public static boolean validTime(String s){return s!=null&&s.matches("(?:[01]\\d|2[0-3]):[0-5]\\d");}
    public static Calendar arrivalOn(Calendar day,String time){
        if(!validTime(time))throw new IllegalArgumentException("Enter arrival HH:mm");
        Calendar c=(Calendar)day.clone();String[] hm=time.split(":");
        c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(hm[0]));c.set(Calendar.MINUTE,Integer.parseInt(hm[1]));c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c;
    }
    public static Calendar departureOn(Calendar day,String arrival,int duration,int margin){
        if(duration<1||margin<0)throw new IllegalArgumentException("Invalid duration or margin");
        Calendar c=arrivalOn(day,arrival);c.add(Calendar.MINUTE,-duration-margin);return c;
    }
    public static String time(Calendar c){return String.format(Locale.KOREA,"%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE));}
    public static boolean runsOn(String days,int weekday){for(String d:days.split(","))if(d.trim().equals(String.valueOf(weekday)))return true;return false;}
    public static boolean active(Calendar now,String arrival,int duration,int margin,String days){
        if(!validTime(arrival))return false;
        for(int offset=-1;offset<=1;offset++){
            Calendar day=(Calendar)now.clone();day.add(Calendar.DAY_OF_YEAR,offset);
            if(!runsOn(days,day.get(Calendar.DAY_OF_WEEK)))continue;
            long start=departureOn(day,arrival,duration,margin).getTimeInMillis(),end=arrivalOn(day,arrival).getTimeInMillis();
            if(now.getTimeInMillis()>=start&&now.getTimeInMillis()<=end)return true;
        }return false;
    }
    public static long nextAlert(Calendar now,String arrival,int duration,int margin,int lead,String days){
        if(!validTime(arrival))return -1;
        for(int offset=0;offset<=8;offset++){
            Calendar day=(Calendar)now.clone();day.add(Calendar.DAY_OF_YEAR,offset);
            if(!runsOn(days,day.get(Calendar.DAY_OF_WEEK)))continue;
            Calendar depart=departureOn(day,arrival,duration,margin);depart.add(Calendar.MINUTE,-lead);
            if(depart.after(now))return depart.getTimeInMillis();
        }return -1;
    }
    private PlanSchedule(){}
}
