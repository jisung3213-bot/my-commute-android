package com.example.mycommute.network;

import com.example.mycommute.BuildConfig;
import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.TransitRoute;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Seoul-only bus arrivals using Seoul BIS rather than TAGO's region-dependent catalog.
 * Uses an independent, explicitly approved SEOUL_BUS_SERVICE_KEY.
 * Called on LiveArrivalClient's IO executor (never the main thread).
 * Does not persist stop IDs or provider ETA beyond LiveArrivalClient's 45s cache.
 */
public final class SeoulBusClient {
    private static final String BASE="http://ws.bus.go.kr/api/rest/";
    private SeoulBusClient(){}

    public static boolean enabled(){
        String key=BuildConfig.SEOUL_BUS_SERVICE_KEY;
        return key!=null&&!key.trim().isEmpty()
                &&!"YOUR_SEOUL_BUS_SERVICE_KEY_HERE".equals(key.trim());
    }

    /** Conservative Seoul city envelope; a nearby Gyeonggi stop may require TAGO instead. */
    public static boolean likelySeoul(double lat,double lon){
        return Double.isFinite(lat)&&Double.isFinite(lon)
                &&lat>=37.42&&lat<=37.70&&lon>=126.75&&lon<=127.20;
    }

    private static String key()throws Exception{
        String raw=BuildConfig.SEOUL_BUS_SERVICE_KEY.trim();
        if(raw.matches(".*%[0-9a-fA-F]{2}.*"))raw=URLDecoder.decode(raw,"UTF-8");
        return URLEncoder.encode(raw,"UTF-8");
    }

    private static Document request(String path)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(BASE+path).openConnection();
        try{
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(14000);
            connection.setRequestProperty("Accept","application/xml");
            int status=connection.getResponseCode();
            if(status!=200)throw new IllegalStateException(
                    "서울 버스 HTTP "+status+" (정류소 API 이용승인·키 확인 필요)");
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            try(InputStream input=new java.io.BufferedInputStream(connection.getInputStream())){
                Document xml=factory.newDocumentBuilder().parse(
                        new java.io.FilterInputStream(input){
                            private long count;
                            @Override public int read()throws java.io.IOException{
                                int b=super.read();
                                if(b>=0&&++count>900000)throw new java.io.IOException("응답이 너무 커");
                                return b;
                            }
                            @Override public int read(byte[] data,int offset,int len)throws java.io.IOException{
                                int n=super.read(data,offset,Math.min(len,8192));
                                if(n>0&&(count+=n)>900000)throw new java.io.IOException("응답이 너무 커");
                                return n;
                            }
                        });
                NodeList headers=xml.getElementsByTagName("headerCd");
                if(headers.getLength()>0){
                    String code=headers.item(0).getTextContent().trim();
                    if(!"0".equals(code)&&!"00".equals(code))
                        throw new IllegalStateException("서울 버스 제공기관 오류코드 "+code.replaceAll("[^0-9A-Za-z_-]",""));
                }
                return xml;
            }
        }finally{connection.disconnect();}
    }

    private static String get(Element e,String tag){
        if(e==null)return "";
        NodeList nodes=e.getElementsByTagName(tag);
        return nodes.getLength()>0?nodes.item(0).getTextContent().trim():"";
    }
    private static double numeric(Element e,String tag){
        try{return Double.parseDouble(get(e,tag));}catch(Exception ignored){return Double.NaN;}
    }
    private static int integer(Element e,String tag){
        try{return Integer.parseInt(get(e,tag));}catch(Exception ignored){return -1;}
    }
    private static String normalize(String s){
        if(s==null)return "";
        return s.replaceAll("\\([^)]*\\)","").replaceAll("[^가-힣A-Za-z0-9]","")
                .replaceAll("역$","");
    }
    private static double meters(double lat1,double lon1,double lat2,double lon2){
        double a=Math.pow(Math.sin(Math.toRadians((lat2-lat1)/2)),2)
                +Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                *Math.pow(Math.sin(Math.toRadians((lon2-lon1)/2)),2);
        return 6371000*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
    }

    public static TransitArrival fetch(TransitRoute.Leg leg)throws Exception{
        long now=System.currentTimeMillis();
        if(!enabled())return TransitArrival.unavailable(
                "서울 버스 API 키가 없어. local.properties의 SEOUL_BUS_SERVICE_KEY를 설정해야 해",now);
        if(!Double.isFinite(leg.startLat)||!Double.isFinite(leg.startLon))
            return TransitArrival.unavailable("서울 버스 승차 정류소 좌표 없음",now);
        Document stops=request("stationinfo/getStationByPos?serviceKey="+key()
                +"&tmX="+leg.startLon+"&tmY="+leg.startLat+"&radius=100");
        NodeList list=stops.getElementsByTagName("itemList");
        String expected=normalize(leg.startName);
        String stopId="",stopName="";
        double best=Double.MAX_VALUE,second=Double.MAX_VALUE;
        for(int i=0;i<list.getLength();i++){
            if(!(list.item(i) instanceof Element))continue;
            Element row=(Element)list.item(i);
            String name=get(row,"stNm");
            String id=get(row,"stId");
            double lat=numeric(row,"gpsY"),lon=numeric(row,"gpsX");
            if(!Double.isFinite(lat)||!Double.isFinite(lon)){
                lat=numeric(row,"tmY");lon=numeric(row,"tmX");
            }
            if(id.isEmpty()||expected.isEmpty()||!normalize(name).equals(expected)
                    ||!Double.isFinite(lat)||!Double.isFinite(lon))continue;
            double dist=meters(leg.startLat,leg.startLon,lat,lon);
            if(dist>70)continue;
            if(dist<best){second=best;best=dist;stopId=id;stopName=name;}
            else if(dist<second)second=dist;
        }
        if(stopId.isEmpty()||second-best<20)return TransitArrival.unavailable(
                "서울 버스 정류소 이름·위치가 불일치하거나 방향별 정류소가 겹쳐 확정 불가",now);
        Document arrivals=request("arrive/getArrInfoByStId?serviceKey="+key()
                +"&stId="+URLEncoder.encode(stopId,"UTF-8"));
        NodeList arrivalsList=arrivals.getElementsByTagName("itemList");
        String route=ArrivalMatcher.transitLine(leg.routeName);
        if(route.isEmpty())return TransitArrival.unavailable("버스 노선명이 없어",now);
        TreeSet<Integer> seconds=new TreeSet<>();
        int stopsLeft=-1;
        for(int i=0;i<arrivalsList.getLength();i++){
            if(!(arrivalsList.item(i) instanceof Element))continue;
            Element row=(Element)arrivalsList.item(i);
            if(!route.equals(ArrivalMatcher.transitLine(get(row,"rtNm"))))continue;
            for(int n=1;n<=2;n++){
                // Both exps and traTime are specified in seconds in the Seoul BIS guide.
                int eta=integer(row,"exps"+n);
                if(eta<=0){
                    int fallbackSeconds=integer(row,"traTime"+n);
                    eta=fallbackSeconds>0?fallbackSeconds:-1;
                }
                if(eta>0&&eta<=7200){
                    seconds.add(eta);
                    if(stopsLeft<0)stopsLeft=integer(row,"sectOrd"+n);
                }
            }
        }
        if(seconds.isEmpty())return TransitArrival.unavailable(
                stopName+" · "+route+"번 버스의 실시간 도착정보 없음 (서울시 버스 API)",now);
        int first=seconds.first();
        Integer next=seconds.higher(first);
        return TransitArrival.found(stopName,route+"번 버스",
                "서울시 BIS 기준 · 진행 방향은 정류소/노선으로만 확인",first,-1,
                next==null?-1:next,System.currentTimeMillis());
    }
}
