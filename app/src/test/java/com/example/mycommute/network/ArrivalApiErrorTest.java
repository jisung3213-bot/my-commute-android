package com.example.mycommute.network;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArrivalApiErrorTest {
    @Test public void showsProviderAnd403WithoutLeakingRawResponse() {
        String html="<html>key=SUPER_SECRET_TOKEN <returnReasonCode>30</returnReasonCode></html>";
        String message=ArrivalApiError.describe("TAGO 정류소정보",403,html);
        assertTrue(message.contains("TAGO 정류소정보 HTTP 403"));
        assertTrue(message.contains("오류코드 30"));
        assertFalse(message.contains("SUPER_SECRET_TOKEN"));
    }
    @Test public void differentiatesMissingApproval() {
        String json="{\"response\":{\"header\":{\"resultCode\":\"20\"}}}";
        String message=ArrivalApiError.describe("TAGO 버스도착정보",403,json);
        assertTrue(message.contains("활용승인"));
        assertTrue(message.contains("TAGO 버스도착정보"));
    }
    @Test public void handlesMissingErrorBody() {
        assertTrue(ArrivalApiError.describe("TAGO 정류소정보",403,"")
                .contains("인증키·개별 서비스 승인"));
    }
}
