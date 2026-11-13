package com.example.seoulmobilitytester;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Only for developer-run tests. Never embed a production API key in an APK. */
public final class TmapApiClient {
    private TmapApiClient() { }
    public static class Response {
        public final int code;
        public final String body;
        Response(int code, String body) { this.code=code; this.body=body; }
    }
    public static Response call(String url, String key, JSONObject postBody) throws Exception {
        if (!url.startsWith("https://apis.openapi.sk.com/")) throw new IllegalArgumentException("허용되지 않은 요청 주소");
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(postBody == null ? "GET" : "POST");
            c.setConnectTimeout(8000); c.setReadTimeout(12000);
            c.setRequestProperty("appKey", key);
            c.setRequestProperty("Accept", "application/json");
            if (postBody != null) {
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                c.setDoOutput(true);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(postBody.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = c.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
            if (stream == null) return new Response(status, "(응답 본문 없음)");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder text = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null && text.length() < 300000) text.append(line).append('\n');
                return new Response(status, text.toString());
            }
        } finally { if (c != null) c.disconnect(); }
    }
}
