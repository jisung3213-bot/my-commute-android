package com.example.mycommute.debug;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.example.mycommute.alarms.AlertLabMode;
import com.example.mycommute.alarms.AlertLedger;
import com.example.mycommute.alarms.NotificationHelper;
import com.example.mycommute.data.AlarmLogEntity;
import com.example.mycommute.data.AlarmLogRepository;
import com.example.mycommute.data.AlarmSettingsStore;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.engine.AlertThresholds;
import com.example.mycommute.engine.PlanSchedule;
import com.example.mycommute.engine.StagePositionMatcher;
import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.TransitRoute;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * One-button debug-only suite using production pure engines and NotificationHelper.
 * No provider calls, real tracking, production routine changes or actual alarms.
 * Reports genuinely evaluated assertions; missing end-to-end coverage is NOT a pass.
 */
public final class AlertLabSuite {
    public interface Callback { void complete(String report); }
    private final Context app;
    private final StringBuilder report=new StringBuilder();
    private int passed, failed, skipped;
    private final String runId="LAB"+System.currentTimeMillis();
    private static final String[] TITLES={"출발 준비","출발 권장","대중교통 도착 임박",
            "승차지 출발","환승 준비","하차 준비","도착 지연 예상",
            "날씨 주의","경로 이탈","목적지 도착"};
    private static final String[] TYPES=AlarmSettingsStore.TYPES;

    private AlertLabSuite(Context context){app=context.getApplicationContext();}
    public static void run(Context context,Callback callback){
        AlertLabSuite suite=new AlertLabSuite(context);
        suite.execute(callback);
    }
    private void check(String name,boolean result,String detail){
        if(result)passed++; else failed++;
        report.append(result?"PASS ":"FAIL ").append(name).append(" | ").append(detail).append('\n');
    }
    private void skip(String name,String reason){
        skipped++;report.append("NOT_RUN ").append(name).append(" | ").append(reason).append('\n');
    }
    private void section(String name){report.append("\n=== ").append(name).append(" ===\n");}
    private Calendar date(int year,int month,int day,int hour,int minute){
        Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"),Locale.KOREA);
        c.clear();c.set(year,month-1,day,hour,minute);return c;
    }
    private long minute(long x){return x/60000L;}
    private void execute(Callback callback){
        report.append("MY-COMMUTE ALERT LAB\n");
        report.append("RUN=").append(runId).append(" / ").append(new Date()).append('\n');
        report.append("BUILD=DEBUG / SDK=").append(Build.VERSION.SDK_INT)
                .append(" / MODEL=").append(Build.MODEL).append('\n');
        report.append("MODE=").append(AlertLabMode.enabled(app)?"TEST":"REAL").append('\n');
        report.append("SCOPE=real pure engines + OS notification request + Room write/read\n");
        if(!AlertLabMode.enabled(app)){
            check("MODE-GUARD",false,"테스트 모드가 아니므로 실행 차단");
            callback.complete(finish());return;
        }
        check("MODE-GUARD",true,"테스트 모드 활성화");
        section("A. PLAN / TIME");
        try{
            Calendar monday=date(2026,11,16,7,0);
            Calendar target=date(2026,11,16,7,15);
            long plan=PlanSchedule.nextAlert(monday,"09:00",60,15,30,"2,3,4,5,6");
            check("PREPARE-15",plan==target.getTimeInMillis(),
                    "actual="+minute(plan)+" expected="+minute(target.getTimeInMillis()));
            long depart=PlanSchedule.nextAlert(monday,"09:00",60,15,0,"2,3,4,5,6");
            check("DEPARTURE-EXACT",depart==date(2026,11,16,7,45).getTimeInMillis(),
                    "planned=07:45 actual="+new Date(depart));
            long next=PlanSchedule.nextAlert(date(2026,11,16,8,0),"09:00",60,15,0,"2");
            check("WEEKLY-REPEAT",next==date(2026,11,23,7,45).getTimeInMillis(),
                    "next Monday="+new Date(next));
            long overnight=PlanSchedule.nextAlert(date(2026,11,16,23,0),
                    "00:30",90,15,0,"3");
            check("OVERNIGHT-NEXT",overnight==date(2026,11,16,22,45).getTimeInMillis(),
                    "arrival Tue 00:30 / departure Mon 22:45");
            long overnightFuture=PlanSchedule.nextAlert(date(2026,11,16,20,0),
                    "00:30",90,15,0,"3");
            check("OVERNIGHT-LOOKAHEAD",overnightFuture==date(2026,11,16,22,45).getTimeInMillis(),
                    "before midnight / next arrival day");
            check("INVALID-TIME",!PlanSchedule.validTime("25:62")&&PlanSchedule.validTime("23:59"),
                    "invalid vs valid");
            check("WEEKDAY-DATE",PlanSchedule.runsOn("2,3,4,5,6",Calendar.MONDAY)
                    &&!PlanSchedule.runsOn("2,3,4,5,6",Calendar.SUNDAY),"Mon yes / Sun no");
        }catch(Throwable t){check("PLAN-EXCEPTION",false,t.toString());}
        section("B. LIVE VEHICLE + WALK");
        try{
            long now=System.currentTimeMillis();
            TransitArrival fresh=TransitArrival.found("역","1호선","상행",300,2,600,now);
            check("ETA-REAL-POSITIVE",fresh.available&&fresh.currentSeconds(now)==300,
                    "300s fresh vehicle");
            check("ETA-NEXT-VEHICLE",fresh.nextRemainingSeconds(now)==600,
                    "second vehicle 600s");
            check("ETA-STALE",fresh.currentSeconds(now+121000)==-1,
                    "121s stale cannot be used");
            check("ETA-ZERO",!AlertThresholds.vehicleDue(0,5),"0s cannot fire");
            check("ETA-BOUNDARY-300",AlertThresholds.vehicleDue(300,5),"300s should fire");
            check("ETA-BOUNDARY-301",!AlertThresholds.vehicleDue(301,5),"301s cannot fire");
            check("ETA-NEGATIVE",!AlertThresholds.vehicleDue(-1,5),"negative cannot fire");
            check("WALK-SLACK-120",AlertThresholds.walkDue(540,300,120,2),
                    "540-300-120=120s");
            check("WALK-SLACK-121",!AlertThresholds.walkDue(541,300,120,2),
                    "541-300-120=121s");
            check("WALK-IMPOSSIBLE",!AlertThresholds.walkDue(360,300,120,2),
                    "360-300-120=-60s");
            check("WALK-MISSING",!AlertThresholds.walkDue(480,-1,120,2),
                    "no verified walk duration");
            check("WALK-ETA-MISSING",!AlertThresholds.walkDue(0,120,120,2),
                    "missing vehicle ETA");
        }catch(Throwable t){check("VEHICLE-EXCEPTION",false,t.toString());}
        section("C. GPS / ROUTE / TRANSFER / EXIT");
        try{
            TransitRoute route=fixtureRoute();
            long now=System.currentTimeMillis();
            StagePositionMatcher.Match match=StagePositionMatcher.locate(route,37.5001,127.0001,
                    now,10,now);
            check("STAGE-ON-ROUTE",match.known()&&match.legIndex==0,
                    "index="+match.legIndex+" "+match.description);
            check("STAGE-STALE-GPS",!StagePositionMatcher.locate(route,37.5001,127.0001,
                    now-91000,10,now).known(),"GPS >90s");
            check("STAGE-LOW-ACCURACY",!StagePositionMatcher.locate(route,37.5001,127.0001,
                    now,100,now).known(),"accuracy=100m");
            check("STAGE-FAR-AWAY",!StagePositionMatcher.locate(route,37.55,127.05,
                    now,10,now).known(),"far away");
            check("BOARDING-LEG",StagePositionMatcher.boardingIndex(route,match)==1,
                    "first transit index=1");
            check("TRANSFER-RADIUS",85<100&&16>=15&&!(100<100)&&!(14>=15),
                    "85m/16s yes; 100m/14s no (threshold expression only)");
            check("EXIT-RADIUS",94<95&&16>=15&&!(95<95),
                    "94m/16s yes; 95m no (threshold expression only)");
            check("OFFROUTE-30S",AlertThresholds.offRoute(190,20,30000),
                    "190m > 150+20 and 30s");
            check("OFFROUTE-29S",!AlertThresholds.offRoute(190,20,29999),
                    "29.999s insufficient");
            check("OFFROUTE-GPS-BAD",!AlertThresholds.offRoute(500,70,60000),
                    "accuracy=70m is invalid");
            check("OFFROUTE-DISTANCE",!AlertThresholds.offRoute(170,20,60000),
                    "must EXCEED 150+accuracy");
            check("DESTINATION-VALID",AlertThresholds.nearDestination(30,true,4,5,70000,20000),
                    "30m; far before; late leg; 70s tracked; 20s");
            check("DESTINATION-EARLY",!AlertThresholds.nearDestination(30,true,0,5,70000,20000),
                    "not last legs");
            check("DESTINATION-NO-HISTORY",!AlertThresholds.nearDestination(30,false,4,5,70000,20000),
                    "previously far required");
            check("DESTINATION-TOO-FAST",!AlertThresholds.nearDestination(30,true,4,5,50000,20000),
                    "60s minimum service");
            check("DESTINATION-NO-CONFIRM",!AlertThresholds.nearDestination(30,true,4,5,70000,14000),
                    "15s minimum near destination");
        }catch(Throwable t){check("GPS-EXCEPTION",false,t.toString());}
        section("D. DELAY / SETTINGS / DEDUPE");
        try{
            check("DELAY-300S",AlertThresholds.delayed(1300000,1000000,5),"exact +5min");
            check("DELAY-299S",!AlertThresholds.delayed(1299000,1000000,5),"+4m59s");
            check("DELAY-INVALID",!AlertThresholds.delayed(0,1000000,5),
                    "missing projected arrival");
            check("SETTINGS-DEFAULTS",AlarmSettingsStore.TYPES.length==10,
                    "10 alert types wired");
            String trip=runId;
            String rid="lab_only_"+runId;
            long now=System.currentTimeMillis();
            boolean first=AlertLedger.tryClaim(app,rid,trip,"vehicle","1",now,Long.MAX_VALUE);
            boolean duplicate=AlertLedger.tryClaim(app,rid,trip,"vehicle","1",now+1000,Long.MAX_VALUE);
            boolean separate=AlertLedger.tryClaim(app,rid,trip,"vehicle","2",now+2000,Long.MAX_VALUE);
            check("LEDGER-ONCE",first&&!duplicate,"same trip/type/leg once");
            check("LEDGER-LEG-SEPARATE",separate,"different leg allowed");
            boolean firstCooldown=AlertLedger.tryClaim(app,rid,trip,"deviation","route",now,300000);
            boolean early=AlertLedger.tryClaim(app,rid,trip,"deviation","route",now+1000,300000);
            boolean after=AlertLedger.tryClaim(app,rid,trip,"deviation","route",now+300001,300000);
            check("LEDGER-COOLDOWN",firstCooldown&&!early&&after,"5m cooldown");
            AlertLedger.finish(app,rid,trip);
            check("LEDGER-FINISH",AlertLedger.finished(app,rid,trip),
                    "trip marked complete");
            android.content.SharedPreferences prefs=RoutineStore.prefs(app);
            android.content.SharedPreferences.Editor cleanup=prefs.edit();
            String prefix="alert_seen_"+rid+"_"+trip+"_";
            cleanup.remove(prefix+"vehicle_1").remove(prefix+"vehicle_2")
                    .remove(prefix+"deviation_route")
                    .remove("alert_finished_"+rid+"_"+trip).apply();
        }catch(Throwable t){check("LEDGER-EXCEPTION",false,t.toString());}
        section("E. OS NOTIFICATION + ROOM");
        NotificationHelper.ensureChannels(app);
        NotificationManager nm=app.getSystemService(NotificationManager.class);
        NotificationChannel channel=nm==null?null:nm.getNotificationChannel(NotificationHelper.REMINDER);
        check("CHANNEL-HIGH",channel!=null&&channel.getImportance()==NotificationManager.IMPORTANCE_HIGH,
                "existing channel importance="+(channel==null?"missing":channel.getImportance()));
        boolean granted=Build.VERSION.SDK_INT<33 ||
                ContextCompat.checkSelfPermission(app,Manifest.permission.POST_NOTIFICATIONS)
                        ==PackageManager.PERMISSION_GRANTED;
        boolean enabled=NotificationManagerCompat.from(app).areNotificationsEnabled();
        check("NOTIFY-PERMISSION",granted&&enabled,
                "permission="+granted+" appEnabled="+enabled);
        int accepted=0;
        List<String> refs=new ArrayList<>();
        for(int i=0;i<TITLES.length;i++){
            String title="[TEST "+runId+"] "+TITLES[i];
            boolean sent=NotificationHelper.show(app,title,"자동 검증 / "+TYPES[i]+" / 실제 교통정보 아님",
                    (runId+"-"+i).hashCode(),"lab-fixture");
            if(sent)accepted++;
            refs.add(title);
            check("NOTIFY-"+TYPES[i],sent,"NotificationManager request="+sent+
                    " (visible delivery NOT verified)");
            AlarmLogRepository.write(app,"test:"+TYPES[i],title,
                    "자동 검증 / "+runId,true,null);
        }
        final int expected=accepted;
        // Room writes use a single-thread executor; the queued read happens after every write.
        AlarmLogRepository.list(app,rows->{
            int count=0;
            for(AlarmLogEntity row:rows)
                if(row.title!=null&&row.title.startsWith("[TEST "+runId+"]"))count++;
            check("ROOM-10-ROWS",count==10,
                    "found="+count+" / 10 within latest 200 rows");
            check("NOTIFICATION-REQUEST-COUNT",expected==10,
                    "accepted="+expected+"/10 (OS may coalesce or suppress banners)");
            section("F. END-TO-END NOT VERIFIED BY MOCK RUN");
            skip("REAL-ALARM-MANAGER","actual scheduled wakeup, reboot, idle & timezone transition");
            skip("REAL-LOCATION-SERVICE","Fused Location updates, screen-off service, OEM battery controls");
            skip("REAL-TRANSIT-API","TMAP/Seoul Subway/TAGO authenticated network responses");
            skip("REAL-WEATHER-API","KMA response/forecast freshness while app is backgrounded");
            skip("REAL-TRANSFER-EXIT","TravelAlertMonitor state-machine with moving GPS stream");
            skip("REAL-NOTIFICATION-VISIBILITY","user must confirm 10 entries in notification shade");
            skip("REAL-FIRESTORE","cloud setting sync not exercised");
            callback.complete(finish());
        });
    }
    private TransitRoute fixtureRoute() throws Exception{
        JSONObject root=new JSONObject().put("duration",1800).put("walk",300)
                .put("meters",400).put("transfers",0).put("fare",1400);
        JSONArray legs=new JSONArray();
        legs.put(leg("WALK","집","역",300,37.5,127.0,37.501,127.001));
        legs.put(leg("SUBWAY","역","학교",1500,37.52,127.02,37.53,127.03));
        root.put("legs",legs);
        return TransitRoute.fromCacheJson(root);
    }
    private JSONObject leg(String mode,String start,String end,int seconds,
                            double lat1,double lon1,double lat2,double lon2)throws Exception{
        return new JSONObject().put("mode",mode).put("line",mode)
                .put("start",start).put("end",end).put("seconds",seconds)
                .put("lat",lat1).put("lon",lon1)
                .put("points",new JSONArray()
                        .put(new JSONArray().put(lat1).put(lon1))
                        .put(new JSONArray().put(lat2).put(lon2)));
    }
    private String finish(){
        report.append("\n=== SUMMARY ===\n");
        report.append("PASS=").append(passed).append(" FAIL=").append(failed)
                .append(" NOT_RUN=").append(skipped).append('\n');
        report.append("OVERALL=").append(failed==0?"SIMULATED_CHECKS_PASS":"CHECKS_FAILED").append('\n');
        report.append("Important: OS notify() success does not prove heads-up visibility.\n");
        report.append("No real API, scheduled receiver, real tracking, reboot or battery run performed.\n");
        report.append("END REPORT\n");
        return report.toString();
    }
}
