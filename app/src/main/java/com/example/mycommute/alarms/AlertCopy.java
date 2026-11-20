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
        return of("지금 출발할 시간이에요",arrival+" 도착 목표 · "+name(routine,"등록한 이동 경로"));
    }
    public static Message vehicle(String station,int seconds){
        return of("탑승할 차량이 곧 도착해요",name(station,"승차지")+" · 약 "+((seconds+59)/60)+"분 후 도착");
    }
    public static Message walk(String station,int slackSeconds){
        return of("지금 승차지로 이동하세요",name(station,"승차지")+" · "+
                (slackSeconds>=0?"탑승 여유 약 "+((slackSeconds+59)/60)+"분":"탑승 시간이 부족할 수 있어요"));
    }
    public static Message transfer(String station,String line){
        return of("환승할 곳에 가까워졌어요",name(station,"환승지")+" · "+name(line,"다음 노선")+" 환승 준비");
    }
    public static Message exit(String stop){
        return of("곧 내릴 곳이에요",name(stop,"하차지")+" · 다음 정류장에서 하차하세요");
    }
    public static Message delay(long minutes){
        return of("도착이 늦어질 수 있어요","목표 시각보다 약 "+minutes+"분 지연 예상");
    }
    public static Message weather(String forecast){
        return of("출발 전 날씨를 확인하세요",name(forecast,"날씨 확인 필요"));
    }
    public static Message deviation(){
        return of("이동 경로를 확인하세요","등록한 경로에서 벗어난 것으로 보여요");
    }
    public static Message finish(String destination){
        return of("목적지 근처에 도착했어요",name(destination,"목적지")+" · 이동 안내를 종료해요");
    }
}
