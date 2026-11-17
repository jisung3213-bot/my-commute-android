package com.example.mycommute.network;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Safe provider diagnostics. Never expose raw error bodies or request URLs. */
public final class ArrivalApiError {
    private static final Pattern XML=Pattern.compile(
            "<(?:returnReasonCode|resultCode|errorCode|code)>\\s*([A-Za-z0-9_\\-]{1,64})\\s*</",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON=Pattern.compile(
            "\"(?:returnReasonCode|resultCode|errorCode|code)\"\\s*:\\s*\"?([A-Za-z0-9_\\-]{1,64})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTH_MSG=Pattern.compile(
            "(SERVICE_ACCESS_DENIED_ERROR|SERVICE_KEY_IS_NOT_REGISTERED_ERROR|"
                    +"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR|"
                    +"LIMITED_NUMBER_OF_SERVICE_REQUESTS_PER_SECOND_EXCEEDS_ERROR|"
                    +"DEADLINE_HAS_EXPIRED_ERROR|PERMISSION_DENIED)",
            Pattern.CASE_INSENSITIVE);
    private ArrivalApiError(){}

    public static String describe(String provider,int status,String body){
        String code=extract(XML,body);
        if(code.isEmpty())code=extract(JSON,body);
        String message=extract(AUTH_MSG,body);
        String reason;
        if("20".equals(code)||"SERVICE_ACCESS_DENIED_ERROR".equalsIgnoreCase(message)||
                "PERMISSION_DENIED".equalsIgnoreCase(message))
            reason="해당 API 서비스 활용승인·접근 권한 확인 필요";
        else if("30".equals(code)||"SERVICE_KEY_IS_NOT_REGISTERED_ERROR".equalsIgnoreCase(message))
            reason="해당 서비스에 등록된 인증키 확인 필요";
        else if("22".equals(code)||"23".equals(code)||status==429||
                message.contains("LIMITED_NUMBER"))
            reason="API 호출 한도 초과 가능성";
        else if("31".equals(code)||"DEADLINE_HAS_EXPIRED_ERROR".equalsIgnoreCase(message))
            reason="인증키 이용 기한 확인 필요";
        else if(status==401||status==403)
            reason="인증키·개별 서비스 승인·호출 한도 확인 필요";
        else reason="제공기관 응답 오류";
        return provider+" HTTP "+status+(code.isEmpty()?"":" · 오류코드 "+code)+" · "+reason;
    }

    private static String extract(Pattern pattern,String body){
        if(body==null||body.isEmpty())return "";
        Matcher m=pattern.matcher(body.substring(0,Math.min(4096,body.length())));
        return m.find()?m.group(1):"";
    }
}
