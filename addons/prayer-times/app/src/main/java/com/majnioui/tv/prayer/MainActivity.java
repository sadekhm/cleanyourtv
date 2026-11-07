package com.majnioui.tv.prayer;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.ColorStateList;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

/**
 * Today's prayer times in Arabic for one city in Egypt (Egyptian General Authority of Survey).
 * The next prayer is highlighted with an Arabic countdown.
 */
public class MainActivity extends Activity {
    private static final String TAG = "PrayerTimes";
    private static final String[] NAMES = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final int[] NAME_RES = {
            R.string.prayer_fajr,
            R.string.prayer_sunrise,
            R.string.prayer_dhuhr,
            R.string.prayer_asr,
            R.string.prayer_maghrib,
            R.string.prayer_isha
    };
    private static final String[] ARABIC = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final int[] ICONS = {R.drawable.ic_fajr, R.drawable.ic_sunrise, R.drawable.ic_dhuhr,
            R.drawable.ic_asr, R.drawable.ic_maghrib, R.drawable.ic_isha};
    private static final long MINUTE_MS = 60_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Typeface font;
    private int accent;
    private TextView dates, next, status, place;
    private LinearLayout rows;
    /** Today's times as minutes after midnight, in NAMES order; null until loaded. */
    private int[] minutes;
    private String[] times;
    /** The day the times are for (yyyy-MM-dd), to reload after midnight. */
    private String loadedFor;
    private boolean loading;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!today().equals(loadedFor)) load();
            else render();
            main.postDelayed(this, MINUTE_MS);
        }
    };

    private String placeName = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            font = getResources().getFont(R.font.outfit);
        } catch (Exception e) {
            font = Typeface.DEFAULT;
        }
        accent = getColor(R.color.accent);
        try {
            placeName = getString(R.string.place);
        } catch (Exception ignored) {}

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {(accent & 0x00FFFFFF) | 0x55000000, 0xFF050607, 0xFF050607}));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        col.setPadding(dp(80), dp(48), dp(80), dp(40));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        place = text(20, 500, 0xB3FFFFFF);
        place.setText(placeName);
        col.addView(place);

        next = text(52, 600, 0xFFFFFFFF);
        next.setPadding(0, dp(10), 0, dp(4));
        col.addView(next);

        dates = text(20, 400, 0xB3FFFFFF);
        col.addView(dates);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.HORIZONTAL);
        rows.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        rlp.setMargins(0, dp(36), 0, 0);
        col.addView(rows, rlp);

        status = text(16, 400, 0x80FFFFFF);
        status.setPadding(0, dp(18), 0, 0);
        status.setText("توقيت الصلاة حسب الهيئة المصرية العامة للمساحة");
        col.addView(status);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
        main.postDelayed(tick, MINUTE_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    private void load() {
        if (loading) return;
        loading = true;
        final String day = today();
        new Thread(() -> {
            String lat = getString(R.string.latitude);
            String lon = getString(R.string.longitude);
            try {
                String ipJson = fetch("http://ip-api.com/json");
                JSONObject ipObj = new JSONObject(ipJson);
                if ("success".equals(ipObj.optString("status"))) {
                    lat = String.valueOf(ipObj.getDouble("lat"));
                    lon = String.valueOf(ipObj.getDouble("lon"));
                }
            } catch (Exception ignored) {}

            String url = "https://api.aladhan.com/v1/timings/"
                    + new SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).format(new Date())
                    + "?latitude=" + lat + "&longitude=" + lon
                    + "&method=" + getString(R.string.method)
                    + "&school=" + getString(R.string.school);
            JSONObject json = null;
            try {
                json = new JSONObject(fetch(url));
            } catch (Exception e) {
                Log.w(TAG, "fetch: " + e);
            }
            final JSONObject result = json;
            main.post(() -> {
                loading = false;
                if (place != null) {
                    place.setText(placeName);
                }
                if (result == null) {
                    status.setText("توقيت الصلاة حسب الهيئة المصرية العامة للمساحة (تعذر الاتصال)");
                    return;
                }
                try {
                    parse(result.getJSONObject("data"));
                    loadedFor = day;
                    if (getResources().getBoolean(R.bool.reminders)) {
                        PrayerScheduler.saveTimingsAndSchedule(MainActivity.this, day, NAMES, times, minutes, getString(R.string.place));
                    } else {
                        PrayerScheduler.cancel(MainActivity.this);
                    }
                    render();
                } catch (Exception e) {
                    Log.w(TAG, "parse: " + e);
                    status.setText("توقيت الصلاة حسب الهيئة المصرية العامة للمساحة");
                }
            });
        }).start();
    }

    private void parse(JSONObject data) throws Exception {
        JSONObject t = data.getJSONObject("timings");
        int[] m = new int[NAMES.length];
        String[] s = new String[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            String v = t.getString(NAMES[i]).trim();
            int sp = v.indexOf(' ');
            if (sp > 0) v = v.substring(0, sp);   // "05:12 (+01)" style suffixes
            s[i] = v;
            m[i] = Integer.parseInt(v.substring(0, 2)) * 60 + Integer.parseInt(v.substring(3, 5));
        }
        minutes = m;
        times = s;

        JSONObject date = data.getJSONObject("date");
        JSONObject hijri = date.getJSONObject("hijri");
        String hijriDay = hijri.optString("day", "");
        String hijriYear = hijri.optString("year", "");
        JSONObject monthObj = hijri.optJSONObject("month");
        String hijriMonth = monthObj != null ? monthObj.optString("ar", "") : "";
        if (hijriMonth.isEmpty() && monthObj != null) {
            hijriMonth = monthObj.optString("en", "");
        }

        SimpleDateFormat gregFmt = new SimpleDateFormat("EEEE d MMMM", new Locale("ar"));
        String gregDate = gregFmt.format(new Date());

        String hijriText = hijriDay + " " + hijriMonth + " " + hijriYear + " هـ";
        dates.setText(gregDate + "  ·  " + hijriText);
    }

    private void render() {
        if (minutes == null) return;
        Calendar c = Calendar.getInstance();
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int nextIdx = -1;
        for (int i = 0; i < NAMES.length; i++) {
            if (i == 1) continue;   // sunrise is shown but is not a prayer
            if (minutes[i] > now) { nextIdx = i; break; }
        }
        if (nextIdx >= 0) {
            int left = minutes[nextIdx] - now;
            String prayer = ARABIC[nextIdx];
            String duration;
            if (left < 60) {
                duration = "بعد " + left + " دقيقة";
            } else {
                int h = left / 60;
                int m = left % 60;
                String hStr;
                if (h == 1) {
                    hStr = "ساعة";
                } else if (h == 2) {
                    hStr = "ساعتين";
                } else if (h <= 10) {
                    hStr = h + " ساعات";
                } else {
                    hStr = h + " ساعة";
                }

                if (m == 0) {
                    duration = "بعد " + hStr;
                } else {
                    String mStr = (m <= 10 && m >= 3) ? (m + " دقائق") : (m + " دقيقة");
                    duration = "بعد " + hStr + " و " + mStr;
                }
            }
            next.setText("أذان " + prayer + " " + duration);
        } else {
            next.setText("صلاة الفجر غداً");
        }

        rows.removeAllViews();
        for (int i = 0; i < NAMES.length; i++) {
            boolean hl = i == nextIdx;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(6), dp(24), dp(6), dp(24));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(22));
            bg.setColor(hl ? accent : 0x14FFFFFF);
            card.setBackground(bg);
            int dim = i == 1 ? 0x80FFFFFF : 0xFFFFFFFF;
            int fg = hl ? 0xFFFFFFFF : dim;

            ImageView icon = new ImageView(this);
            icon.setImageResource(ICONS[i]);
            icon.setImageTintList(ColorStateList.valueOf(hl ? 0xFFFFFFFF : (i == 1 ? 0x80FFFFFF : accent)));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
            ilp.bottomMargin = dp(20);
            card.addView(icon, ilp);

            TextView ar = centered(text(28, 600, fg));
            ar.setText(ARABIC[i]);
            ar.setPadding(0, 0, 0, dp(18));
            card.addView(ar);
            TextView time = centered(text(40, hl ? 600 : 300, fg));
            time.setText(times[i]);
            time.setMaxLines(1);
            time.setAutoSizeTextTypeUniformWithConfiguration(20, 40, 1, TypedValue.COMPLEX_UNIT_SP);
            card.addView(time, new LinearLayout.LayoutParams(-1, dp(52)));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            lp.setMargins(i == 0 ? 0 : dp(8), 0, i == NAMES.length - 1 ? 0 : dp(8), 0);
            rows.addView(card, lp);
        }
        String statusStr = "توقيت الصلاة حسب الهيئة المصرية العامة للمساحة";
        if (getResources().getBoolean(R.bool.reminders)) {
            statusStr += " · " + getString(R.string.reminders_active);
        }
        status.setText(statusStr);
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "CleanYourTV/1.0");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** Centred on its own line, whatever the script direction. */
    private static TextView centered(TextView t) {
        t.setGravity(Gravity.CENTER);
        t.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        return t;
    }

    private TextView text(float sp, int weight, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        t.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, weight, false) : font);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
