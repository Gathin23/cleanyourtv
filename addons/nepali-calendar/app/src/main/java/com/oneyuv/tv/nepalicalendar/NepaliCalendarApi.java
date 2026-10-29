package com.oneyuv.tv.nepalicalendar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Reads the current Bikram Sambat month's tithi from the Nepali Calendar Data API. */
final class NepaliCalendarApi {
    private static final String BASE_URL = "https://api-nepalicalendar.leapcell.app/calendar/";
    private static final int MAX_RESPONSE_BYTES = 128 * 1024;

    private NepaliCalendarApi() {}

    static String tithi(String payload, int nepaliDay) throws JSONException {
        JSONArray days = new JSONObject(payload).optJSONArray("days");
        if (days == null) throw new JSONException("Calendar response has no days");
        String nepaliDate = NepaliDate.digits(nepaliDay);
        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.optJSONObject(i);
            if (day == null || !nepaliDate.equals(day.optString("n", "").trim())) continue;
            String tithi = day.optString("t", "").trim();
            if (!tithi.isEmpty()) return tithi;
            break;
        }
        throw new JSONException("Calendar response has no tithi for BS day " + nepaliDay);
    }

    static String fetch(int year, int month, int nepaliDay) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                BASE_URL + year + "/" + month).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(8_000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Calendar API returned HTTP " + status);
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_RESPONSE_BYTES) throw new IOException("Calendar response exceeds 128 KB");
                    output.write(buffer, 0, count);
                }
                return tithi(new String(output.toByteArray(), StandardCharsets.UTF_8), nepaliDay);
            }
        } finally {
            connection.disconnect();
        }
    }
}
