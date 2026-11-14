package com.example.seoulmobilitytester;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private EditText bikeKey, pmUrl, pmKey;
    private TextView status, result;
    private volatile String lastDisplay = "";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_test);
        bikeKey = findViewById(R.id.bikeKey);
        pmUrl = findViewById(R.id.pmUrl);
        pmKey = findViewById(R.id.pmKey);
        status = findViewById(R.id.status);
        result = findViewById(R.id.result);
        findViewById(R.id.openTmap).setOnClickListener(v -> startActivity(new Intent(this, TmapTestActivity.class)));
        findViewById(R.id.bikeSample).setOnClickListener(v -> fetchBike(true));
        findViewById(R.id.bikeLive).setOnClickListener(v -> fetchBike(false));
        findViewById(R.id.pmRequest).setOnClickListener(v -> fetchPm());
        findViewById(R.id.copyResult).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("API 응답", lastDisplay));
            Toast.makeText(this, "결과를 복사했습니다.", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchBike(boolean sample) {
        String key = sample ? "sample" : bikeKey.getText().toString().trim();
        if (key.isEmpty()) { status.setText("서울시 인증키를 입력하세요."); return; }
        if (!key.matches("[a-zA-Z0-9]+")) { status.setText("API 키에는 영문·숫자만 입력하세요."); return; }
        String url = "http://openapi.seoul.go.kr:8088/" + key + "/json/bikeList/1/" + (sample ? 5 : 20) + "/";
        request(url, true, key);
    }

    private void fetchPm() {
        String template = pmUrl.getText().toString().trim();
        String key = pmKey.getText().toString().trim();
        if (template.isEmpty()) { status.setText("공식 명세의 GET 요청 URL을 입력하세요."); return; }
        if (template.contains("{KEY}") && key.isEmpty()) { status.setText("TAGO 인증키를 입력하세요."); return; }
        String encoded = "";
        try { encoded = URLEncoder.encode(key, "UTF-8"); }
        catch (Exception e) { status.setText(e.getMessage()); return; }
        String url = template.replace("{KEY}", encoded);
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            status.setText("http:// 또는 https:// 주소만 지원합니다."); return;
        }
        request(url, false, key);
    }

    private void request(String requestUrl, boolean isBike, String secret) {
        status.setText("요청 중... (최대 12초)");
        result.setText("네트워크 응답을 확인하고 있습니다.");
        new Thread(() -> {
            HttpURLConnection connection = null;
            String display;
            String headline;
            try {
                connection = (HttpURLConnection) new URL(requestUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.setRequestProperty("Accept", "application/json, application/xml, text/xml, */*");
                int httpCode = connection.getResponseCode();
                InputStream stream = httpCode >= 200 && httpCode < 300
                    ? connection.getInputStream() : connection.getErrorStream();
                String raw = readAll(stream);
                String parsed = isBike ? summarizeBike(raw) : summarizePm(raw);
                headline = "HTTP " + httpCode + (httpCode >= 200 && httpCode < 300 ? " (서버 응답 수신)" : " (요청 오류)");
                // Mask keys in any visible server diagnostic to prevent accidentally sharing credentials.
                String maskedUrl = mask(requestUrl, secret);
                String maskedRaw = mask(raw, secret);
                display = "요청: " + maskedUrl + "\n" + headline + "\n\n"
                    + parsed + "\n\n--- 원본 응답 (최대 6000자) ---\n"
                    + maskedRaw.substring(0, Math.min(6000, maskedRaw.length()));
            } catch (Exception ex) {
                headline = "연결 실패: " + ex.getClass().getSimpleName();
                display = headline + "\n" + ex.getMessage()
                    + "\n확인: 인터넷 연결, 키 권한, URL, HTTP/HTTPS 정책";
            } finally { if (connection != null) connection.disconnect(); }
            String finalHeadline = headline;
            String finalDisplay = display;
            runOnUiThread(() -> {
                status.setText(finalHeadline);
                result.setText(finalDisplay);
                lastDisplay = finalDisplay;
            });
        }).start();
    }

    private static String summarizeBike(String raw) {
        try {
            JSONObject top = new JSONObject(raw);
            JSONObject data = top.optJSONObject("rentBikeStatus");
            if (data == null) return "따릉이 데이터 없음 / API 오류 가능성:\n" + top.toString(2);
            JSONObject info = data.optJSONObject("RESULT");
            JSONArray rows = data.optJSONArray("row");
            if (rows == null) return "row 필드가 없습니다. 상태: " + (info == null ? "미상" : info.toString());
            StringBuilder sb = new StringBuilder("따릉이 응답: " + rows.length() + "개 대여소\n");
            for (int i = 0; i < Math.min(rows.length(), 20); i++) {
                JSONObject item = rows.getJSONObject(i);
                sb.append("\n").append(i + 1).append(". ").append(item.optString("stationName", "이름 없음"));
                sb.append(" | 대여 가능 ").append(item.optString("parkingBikeTotCnt", "?"));
                sb.append("대 | 좌표 ").append(item.optString("stationLatitude", "?"));
                sb.append(", ").append(item.optString("stationLongitude", "?"));
            }
            return sb.toString();
        } catch (Exception ex) { return "JSON 분석 실패: " + ex.getMessage(); }
    }

    private static String summarizePm(String raw) {
        try {
            String trimmed = raw.trim();
            if (trimmed.startsWith("{")) return "JSON 응답 수신 (전체 구조 아래 표시):\n"
                + new JSONObject(trimmed).toString(2).substring(0, Math.min(1800, new JSONObject(trimmed).toString(2).length()));
            if (trimmed.startsWith("[")) return "JSON 배열 응답 수신 (전체 구조 아래 표시)";
            if (trimmed.startsWith("<")) return "XML 또는 HTML 응답 수신. 아래 원본과 HTTP 코드를 확인하세요.";
            return "응답 타입을 식별하지 못했습니다. 아래 원본을 확인하세요.";
        } catch (Exception ex) { return "응답 해석 실패: " + ex.getMessage(); }
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "(응답 본문 없음)";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null && out.length() < 250000) out.append(line).append('\n');
            return out.toString();
        }
    }
    private static String mask(String s, String secret) {
        if (secret == null || secret.isEmpty()) return s;
        String masked = s.replace(secret, "***KEY***");
        try { masked = masked.replace(URLEncoder.encode(secret, "UTF-8"), "***KEY***"); }
        catch (Exception ignored) { }
        return masked;
    }
}
