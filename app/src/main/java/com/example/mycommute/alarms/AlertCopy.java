package com.example.mycommute.alarms;

/** Single source of notification copy used by production and debug fixtures. */
public final class AlertCopy {
    public static final class Message {
        public final String title,body;
        private Message(String title,String body){this.title=title;this.body=body;}
    }
    private AlertCopy(){}
    private static Message of(String title,String body){return new Message(title,body);}
    private static String name(String s,String fallback){
        return s==null||s.trim().isEmpty()?fallback:s.trim();
    }
    public static Message prepare(int lead,String departure,String routine,String weather){
        String body=departure+" 출발 예정 · "+name(routine,"등록한 이동 경로");
        if(weather!=null&&!weather.trim().isEmpty())body+=" · "+weather.trim();
        return of("출발 "+lead+"분 전",body);
    }
    public static Message departure(String arrival,String routine){
        return of("출발 시각",arrival+" 도착 목표 · "+name(routine,"등록한 이동 경로"));
    }
    public static Message vehicle(String station,int seconds){
        return of("차량 도착 임박",name(station,"승차지")+" · 약 "+((seconds+59)/60)+"분 후 도착");
    }
    public static Message walk(String station,int slackSeconds){
        return of("승차지 이동 필요",name(station,"승차지")+" · "+
                (slackSeconds>=0?"탑승 여유 약 "+((slackSeconds+59)/60)+"분":"탑승 여유 부족"));
    }
    public static Message transfer(String station,String line){
        return of("환승 지점 접근",name(station,"환승지")+" · "+name(line,"다음 노선")+" 환승 준비");
    }
    public static Message exit(String stop){
        return of("하차 준비",name(stop,"하차지")+" · 다음 정류장 하차");
    }
    public static Message delay(long minutes){
        return of("도착 지연 예상","목표 시각보다 약 "+minutes+"분 지연 예상");
    }
    public static Message weather(String forecast){
        return of("출발 전 기상 안내",name(forecast,"날씨 확인 필요"));
    }
    public static Message deviation(){
        return of("경로 이탈 확인","등록 경로 이탈 감지");
    }
    public static Message finish(String destination){
        return of("목적지 인근 도착",name(destination,"목적지")+" · 이동 안내 종료");
    }
}
