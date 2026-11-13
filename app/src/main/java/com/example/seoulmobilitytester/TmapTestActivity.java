package com.example.seoulmobilitytester;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class TmapTestActivity extends Activity {
    private EditText keyInput, keywordInput, startLonInput, startLatInput, endLonInput, endLatInput;
    private TextView status, result;
    private String lastDisplay = "";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tmap_test);
        keyInput = findViewById(R.id.tmapKey);
        keywordInput = findViewById(R.id.tmapKeyword);
        startLonInput = findViewById(R.id.startLon);
        startLatInput = findViewById(R.id.startLat);
        endLonInput = findViewById(R.id.endLon);
        endLatInput = findViewById(R.id.endLat);
        status = findViewById(R.id.tmapStatus);
        result = findViewById(R.id.tmapResult);
        findViewById(R.id.tmapPoi).setOnClickListener(v -> execute("poi"));
        findViewById(R.id.tmapWalk).setOnClickListener(v -> execute("walk"));
        findViewById(R.id.tmapTransit).setOnClickListener(v -> execute("transit"));
        findViewById(R.id.tmapCopy).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("TMAP 결과", lastDisplay));
            Toast.makeText(this, "키를 제외한 결과 복사 완료", Toast.LENGTH_SHORT).show();
        });
    }

    private void execute(String kind) {
        String key = keyInput.getText().toString().trim();
        if (key.isEmpty()) { status.setText("TMAP App Key를 입력해."); return; }
        final String endpoint;
        final JSONObject body;
        try {
            if (kind.equals("poi")) {
                String keyword = keywordInput.getText().toString().trim();
                if (keyword.isEmpty()) { status.setText("검색어를 입력해."); return; }
                endpoint = "https://apis.openapi.sk.com/tmap/pois?version=1&count=5&searchType=all&searchKeyword="
                    + URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
                body = null;
            } else {
                double sx = coord(startLonInput, 124, 132, "출발 경도");
                double sy = coord(startLatInput, 33, 39, "출발 위도");
                double ex = coord(endLonInput, 124, 132, "도착 경도");
                double ey = coord(endLatInput, 33, 39, "도착 위도");
                body = new JSONObject();
                body.put("startX", sx); body.put("startY", sy);
                body.put("endX", ex); body.put("endY", ey);
                if (kind.equals("walk")) {
                    endpoint = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";
                    body.put("startName", URLEncoder.encode("출발", "UTF-8"));
                    body.put("endName", URLEncoder.encode("도착", "UTF-8"));
                    body.put("reqCoordType", "WGS84GEO");
                    body.put("resCoordType", "WGS84GEO");
                    body.put("searchOption", "0");
                } else {
                    endpoint = "https://apis.openapi.sk.com/transit/routes";
                    body.put("startX", String.valueOf(sx));
                    body.put("startY", String.valueOf(sy));
                    body.put("endX", String.valueOf(ex));
                    body.put("endY", String.valueOf(ey));
                    body.put("count", 1);
                    body.put("lang", 0);
                    body.put("format", "json");
                }
            }
        } catch (Exception e) {
            status.setText("입력 확인: " + e.getMessage()); return;
        }
        status.setText("TMAP 요청 중...");
        result.setText("응답을 기다리고 있습니다.");
        new Thread(() -> {
            String headline, text;
            try {
                TmapApiClient.Response r = TmapApiClient.call(endpoint, key, body);
                headline = "HTTP " + r.code + (r.code >= 200 && r.code < 300 ? " (수신 완료)" : " (오류)");
                String summary = summarize(kind, r.body);
                String raw = r.body.length() > 7000 ? r.body.substring(0, 7000) + "\n...(생략)" : r.body;
                // Never print the API key, including unexpected error reflections.
                summary = summary.replace(key, "***KEY***");
                raw = raw.replace(key, "***KEY***");
                text = "요청: " + (body == null ? "GET " : "POST ") + endpoint + "\n" + headline
                    + "\n\n" + summary + "\n\n--- 원본 응답 앞부분 ---\n" + raw;
            } catch (Exception e) {
                headline = "연결 실패";
                text = "네트워크/키/상품 권한 확인: " + String.valueOf(e.getMessage()).replace(key, "***KEY***");
            }
            String finalHeadline = headline, finalText = text;
            runOnUiThread(() -> { status.setText(finalHeadline); result.setText(finalText); lastDisplay = finalText; });
        }).start();
    }

    private static double coord(EditText edit, double min, double max, String label) {
        double v;
        try { v = Double.parseDouble(edit.getText().toString().trim()); }
        catch (Exception e) { throw new IllegalArgumentException(label + "는 숫자여야 해."); }
        if (!Double.isFinite(v) || v < min || v > max) throw new IllegalArgumentException(label + " 범위를 확인해.");
        return v;
    }

    private static String summarize(String kind, String raw) {
        try {
            JSONObject json = new JSONObject(raw);
            if (kind.equals("poi")) {
                JSONObject search = json.optJSONObject("searchPoiInfo");
                JSONObject pois = search == null ? null : search.optJSONObject("pois");
                JSONArray rows = pois == null ? null : pois.optJSONArray("poi");
                if (rows == null) return "POI 목록이 없거나 응답 구조가 달라. 원본 응답을 확인해.";
                StringBuilder sb = new StringBuilder("검색 결과 " + rows.length() + "건\n");
                for (int i = 0; i < Math.min(rows.length(), 5); i++) {
                    JSONObject p = rows.optJSONObject(i);
                    if (p == null) continue;
                    sb.append(i + 1).append(". ").append(p.optString("name", "이름 없음"))
                      .append("\n  좌표 ").append(p.optString("frontLat", p.optString("noorLat", "?")))
                      .append(", ").append(p.optString("frontLon", p.optString("noorLon", "?")))
                      .append("\n");
                }
                return sb.toString();
            }
            if (kind.equals("walk")) {
                JSONArray features = json.optJSONArray("features");
                if (features == null) return "GeoJSON features 없음. 원본 확인.";
                StringBuilder sb = new StringBuilder("경로 Feature " + features.length() + "개\n");
                for (int i = 0; i < features.length(); i++) {
                    JSONObject f = features.optJSONObject(i);
                    JSONObject prop = f == null ? null : f.optJSONObject("properties");
                    if (prop != null && (prop.has("totalDistance") || prop.has("totalTime"))) {
                        sb.append("총 거리: ").append(prop.optString("totalDistance", "?"))
                          .append("m / 소요시간: ").append(prop.optString("totalTime", "?"))
                          .append("초\n");
                        break;
                    }
                }
                return sb.toString();
            }
            JSONObject meta = json.optJSONObject("metaData");
            JSONObject plan = meta == null ? null : meta.optJSONObject("plan");
            JSONArray routes = plan == null ? null : plan.optJSONArray("itineraries");
            if (routes == null) return "대중교통 경로 배열 없음. API 권한 또는 원본 응답 확인.";
            StringBuilder sb = new StringBuilder("대중교통 경로 " + routes.length() + "건\n");
            for (int i = 0; i < Math.min(routes.length(), 3); i++) {
                JSONObject route = routes.optJSONObject(i);
                if (route == null) continue;
                sb.append(i + 1).append(". 총 ").append(route.optString("totalTime", "?"))
                  .append("초 / 도보 ").append(route.optString("totalWalkTime", "?"))
                  .append("초 / 환승 ").append(route.optString("transferCount", "?"))
                  .append("회\n");
            }
            return sb.toString();
        } catch (Exception e) { return "JSON 분석 실패 또는 비JSON 오류 응답: " + e.getMessage(); }
    }
}
