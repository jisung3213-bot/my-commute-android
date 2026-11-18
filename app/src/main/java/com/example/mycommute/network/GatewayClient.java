package com.example.mycommute.network;

import com.example.mycommute.BuildConfig;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Authenticated HTTPS gateway. Do not put provider keys in the mobile build. */
public final class GatewayClient {
    private GatewayClient(){}

    public static boolean enabled(){
        String base=BuildConfig.API_BASE_URL.trim();
        return base.startsWith("https://") && !base.contains("YOUR_SERVER_URL")
                && !base.contains("?") && !base.contains("#") && !base.contains("@");
    }

    /** Worker thread only. Returns native provider JSON for the existing parsers. */
    public static String request(String method,String path,String json) throws Exception {
        if(!enabled())throw new IllegalStateException("서버 연결 미설정");
        if(!path.startsWith("/v1/")||path.contains("://"))
            throw new IllegalArgumentException("허용되지 않은 경로");
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
        if(user==null)throw new IllegalStateException("로그인 필요");
        String token=Tasks.await(user.getIdToken(false),12,TimeUnit.SECONDS).getToken();
        if(token==null||token.isEmpty())throw new IllegalStateException("로그인 유효기간 만료");
        String origin=BuildConfig.API_BASE_URL.trim().replaceAll("/+$","");
        HttpURLConnection connection=(HttpURLConnection)new URL(origin+path).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(25000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Authorization","Bearer "+token);
            connection.setRequestProperty("Accept","application/json");
            if(json!=null){
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
                if(bytes.length>10000)throw new IllegalArgumentException("요청 크기 초과");
                try(OutputStream output=connection.getOutputStream()){output.write(bytes);}
            }
            int code=connection.getResponseCode();
            if(code!=200)throw new IllegalStateException(
                    code==401?"로그인 유효기간 만료":
                    code==429?"잠시 후 다시 이용 가능":
                    code==504?"연결 시간 초과":
                    code==503?"서비스 일시 중단":
                    code>=500?"서버 일시 오류":"요청 오류");
            try(InputStream stream=connection.getInputStream();
                BufferedReader reader=new BufferedReader(
                        new InputStreamReader(stream,StandardCharsets.UTF_8))){
                StringBuilder result=new StringBuilder();
                char[] chars=new char[4096];
                int n;
                while((n=reader.read(chars))!=-1){
                    if(result.length()+n>2_000_000)
                        throw new IllegalStateException("응답 크기 초과");
                    result.append(chars,0,n);
                }
                return result.toString();
            }
        }finally {connection.disconnect();}
    }
}
