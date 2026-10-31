package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Live Top-Bar Headline: Asynchronously fetches Weather, Cairo Prayer Times, and Al-Ahly SC Fixtures.
 */
public final class LiveHeadline {
    private static final String TAG = "LiveHeadline";
    private static final long FAST_TICK_MS = 60 * 1000L; // 1 minute ticker for prayer countdown
    private static final long WEATHER_INTERVAL_MS = 30 * 60 * 1000L; // 30 minutes
    private static final long SPORTS_INTERVAL_MS = 5 * 60 * 1000L; // local provider read; API cadence is owned by addon
    private static final long PRAYER_API_INTERVAL_MS = 2 * 60 * 60 * 1000L; // 2 hours

    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextView tvWeather;
    private final TextView tvPrayer;
    private final TextView tvAlAhly;
    private final Context context;

    private static String cachedWeather = "Cairo · 28°C · Partly Cloudy";
    private static String cachedPrayer = "Prayer Times";
    private static String cachedAlAhly = "Al Ahly · Loading matches";
    private static String[] cachedTimings = null;
    private static long lastWeatherFetch = 0;
    private static long lastPrayerFetch = 0;
    private static long lastSportsFetch = 0;

    private final Runnable tickTask = new Runnable() {
        @Override
        public void run() {
            onTick();
            main.postDelayed(this, FAST_TICK_MS);
        }
    };

    public LiveHeadline(Context context, TextView weather, TextView prayer, TextView alAhly) {
        this.context = context.getApplicationContext();
        this.tvWeather = weather;
        this.tvPrayer = prayer;
        this.tvAlAhly = alAhly;
        applyCached();
    }

    public void start() {
        applyCached();
        main.removeCallbacks(tickTask);
        onTick(); // immediate run
        main.postDelayed(tickTask, FAST_TICK_MS);
    }

    public void stop() {
        main.removeCallbacks(tickTask);
    }

    private void applyCached() {
        if (tvWeather != null) tvWeather.setText(cachedWeather);
        if (tvPrayer != null) tvPrayer.setText(cachedPrayer);
        if (tvAlAhly != null) tvAlAhly.setText(cachedAlAhly);
    }

    private void onTick() {
        long now = System.currentTimeMillis();

        // 1. Minute-by-minute local countdown update from cached prayer timings
        updatePrayerCountdown();

        // 2. Refresh weather if expired
        if (now - lastWeatherFetch > WEATHER_INTERVAL_MS) {
            new Thread(this::updateWeather).start();
        }

        // 3. Refresh prayer API if expired or not fetched yet
        if (now - lastPrayerFetch > PRAYER_API_INTERVAL_MS || cachedTimings == null) {
            new Thread(this::updatePrayerApi).start();
        }

        // 4. Refresh sports schedule if expired
        if (now - lastSportsFetch > SPORTS_INTERVAL_MS) {
            new Thread(this::updateAlAhly).start();
        }
    }

    private void updateWeather() {
        try {
            // The TV's coordinates; see config.xml's latitude and longitude.
            String url = "https://api.open-meteo.com/v1/forecast?latitude=0.0&longitude=0.0&current=temperature_2m,weather_code&timezone=auto";
            String jsonStr = fetch(url);
            JSONObject root = new JSONObject(jsonStr);
            JSONObject cur = root.getJSONObject("current");
            int temp = (int) Math.round(cur.getDouble("temperature_2m"));
            int code = cur.optInt("weather_code", 0);
            String desc = weatherDesc(code);
            String result = "Cairo · " + temp + "°C · " + desc;
            cachedWeather = result;
            lastWeatherFetch = System.currentTimeMillis();
            main.post(() -> {
                if (tvWeather != null) tvWeather.setText(result);
            });
        } catch (Exception e) {
            Log.w(TAG, "Weather fetch error: " + e.getMessage());
        }
    }

    private String weatherDesc(int code) {
        if (code == 0) return "Clear";
        if (code == 1 || code == 2) return "Partly Cloudy";
        if (code == 3) return "Overcast";
        if (code >= 45 && code <= 48) return "Fog";
        if (code >= 51 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 95) return "Thunderstorm";
        return "Fair";
    }

    private void updatePrayerApi() {
        try {
            // Egyptian General Authority of Survey (Method 5)
            String url = "https://api.aladhan.com/v1/timings?latitude=0.0&longitude=0.0&method=5";
            String jsonStr = fetch(url);
            JSONObject root = new JSONObject(jsonStr);
            JSONObject timings = root.getJSONObject("data").getJSONObject("timings");

            String[] names = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
            String[] times = new String[names.length];
            for (int i = 0; i < names.length; i++) {
                String raw = timings.getString(names[i]).trim();
                int sp = raw.indexOf(' ');
                if (sp > 0) raw = raw.substring(0, sp);
                times[i] = raw;
            }
            cachedTimings = times;
            lastPrayerFetch = System.currentTimeMillis();
            main.post(this::updatePrayerCountdown);
        } catch (Exception e) {
            Log.w(TAG, "Prayer API fetch error: " + e.getMessage());
        }
    }

    private void updatePrayerCountdown() {
        String[] times = cachedTimings;
        if (times == null || times.length < 5) {
            // Default Cairo timings fallback if API not fetched yet
            times = new String[]{"05:37", "12:41", "15:52", "18:17", "19:34"};
        }

        String[] names = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Africa/Cairo"));
        int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        int nextIdx = -1;
        int nextMinutes = -1;
        String nextTimeStr = "";

        for (int i = 0; i < names.length; i++) {
            String raw = times[i];
            int m = Integer.parseInt(raw.substring(0, 2)) * 60 + Integer.parseInt(raw.substring(3, 5));
            if (m > currentMinutes && nextIdx == -1) {
                nextIdx = i;
                nextMinutes = m;
                nextTimeStr = raw;
            }
        }

        final String result;
        if (nextIdx >= 0) {
            int left = nextMinutes - currentMinutes;
            String leftStr = left >= 60 ? (left / 60) + "h " + (left % 60) + "m" : left + "m";
            result = names[nextIdx] + " in " + leftStr + " · " + nextTimeStr;
        } else {
            result = "Fajr tomorrow · " + times[0];
        }

        cachedPrayer = result;
        main.post(() -> {
            if (tvPrayer != null) tvPrayer.setText(result);
        });
    }

    private void updateAlAhly() {
        lastSportsFetch = System.currentTimeMillis();
        String line = "Al Ahly · Addon unavailable";
        try (Cursor cursor = context.getContentResolver().query(
                Uri.parse("content://com.alahly.tv.fixtures.matches/v1/summary"), null, null, null, null)) {
            if (cursor != null) line = matchLine(cursor);
        } catch (Exception e) {
            Log.w(TAG, "Match provider unavailable: " + e.getClass().getSimpleName());
        }
        cachedAlAhly = line;
        final String displayLine = line;
        main.post(() -> { if (tvAlAhly != null) tvAlAhly.setText(displayLine); });
    }

    private String matchLine(Cursor cursor) {
        if (!cursor.moveToFirst()) return "Al Ahly · Schedule unavailable";
        long updated = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        if (updated == 0) return "Al Ahly · Schedule unavailable";
        boolean stale = System.currentTimeMillis() - updated > 24L * 60 * 60 * 1000;
        String prefix = stale ? "Al Ahly · Last checked "
                + cairoFormat(updated, "d MMM") + " · " : "";
        String kind = cursor.getString(cursor.getColumnIndexOrThrow("kind"));
        if ("upcoming".equals(kind)) {
            String home = cursor.getString(cursor.getColumnIndexOrThrow("home"));
            String away = cursor.getString(cursor.getColumnIndexOrThrow("away"));
            String status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
            long kickoff = cursor.getLong(cursor.getColumnIndexOrThrow("kickoff_ms"));
            if (System.currentTimeMillis() - updated <= 60 * 60 * 1000L && isLive(status)) {
                String hs = cursor.getString(cursor.getColumnIndexOrThrow("home_score"));
                String as = cursor.getString(cursor.getColumnIndexOrThrow("away_score"));
                return "LIVE · " + home + " " + hs + "–" + as + " " + away;
            }
            String when = cairoFormat(kickoff, "EEE d MMM · h:mm a");
            return prefix + home + " vs " + away + " · " + when;
        }
        if (cursor.moveToNext() || "result".equals(kind)) {
            return prefix + "Last: " + cursor.getString(cursor.getColumnIndexOrThrow("home")) + " "
                    + cursor.getString(cursor.getColumnIndexOrThrow("home_score")) + "–"
                    + cursor.getString(cursor.getColumnIndexOrThrow("away_score")) + " "
                    + cursor.getString(cursor.getColumnIndexOrThrow("away"));
        }
        return "Al Ahly · Schedule unavailable";
    }

    private static boolean isLive(String status) {
        return "1H".equals(status) || "HT".equals(status) || "2H".equals(status)
                || "ET".equals(status) || "BT".equals(status) || "P".equals(status) || "LIVE".equals(status);
    }

    private static String cairoFormat(long stamp, String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Africa/Cairo"));
        return format.format(new Date(stamp));
    }

    private static String fetch(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
