package com.alahly.tv.fixtures;

import android.app.Activity;
import com.clubfixtures.ClubMatchFeed;
import com.clubfixtures.MatchDayLabel;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity {
    private static final String TAG = "AlAhlyFixtures";

    // Al Ahly palette
    private static final int RED = 0xFFE30613;
    private static final int RED_DEEP = 0xFF9A050D;
    private static final int GOLD = 0xFFFFD700;
    private static final int GOLD_SOFT = 0xFFC9A227;
    private static final int CARD_GLASS_TOP = 0xFF1E1F26;
    private static final int CARD_GLASS_BOTTOM = 0xFF121318;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xB3FFFFFF;
    private static final int TEXT_MUTED = 0x80FFFFFF;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final TimeZone cairoTz = TimeZone.getTimeZone("Africa/Cairo");
    private Typeface font;
    private Bitmap crestBitmap;
    private ScrollView scroll;
    private LinearLayout contentCol;
    private LinearLayout upcomingContainer;
    private LinearLayout resultsContainer;
    private TextView statusText;
    private boolean firstRender = true;
    private long renderedUpdate = Long.MIN_VALUE;
    private String renderedCairoDay = "";
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() { load(); main.postDelayed(this, 5 * 60 * 1000L); }
    };

    private static class Fixture {
        final boolean isResult;
        Date matchDate;
        String tournament;
        String round;
        String homeTeam;
        String awayTeam;
        String venue;
        boolean isHome;
        String homeScore;
        String awayScore;
        String resultLabel; // WIN / DRAW / LOSS / FT
        String status;

        Fixture(boolean isResult) { this.isResult = isResult; }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            font = getResources().getFont(R.font.outfit);
        } catch (Exception e) {
            font = Typeface.DEFAULT;
        }
        crestBitmap = loadTrimmedCrest();
        buildUi();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable rootBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF390C13, 0xFF13151B, 0xFF080A0E});
        root.setBackground(rootBg);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        contentCol = new LinearLayout(this);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        contentCol.setClipChildren(false);
        contentCol.setClipToPadding(false);
        // Android TV safe margins for 10-foot UI
        contentCol.setPadding(dp(56), dp(26), dp(56), dp(40));
        contentCol.setFocusable(false);
        contentCol.setFocusableInTouchMode(false);
        scroll.addView(contentCol, new FrameLayout.LayoutParams(-1, -2));

        contentCol.addView(buildHeader());

        statusText = text(14, 500, TEXT_SECONDARY);
        statusText.setPadding(0, 0, 0, dp(16));
        statusText.setText("Loading Al Ahly matches…");
        contentCol.addView(statusText);

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setGravity(Gravity.TOP);
        columns.setClipChildren(false);
        contentCol.addView(columns, new LinearLayout.LayoutParams(-1, -2));

        upcomingContainer = new LinearLayout(this);
        upcomingContainer.setOrientation(LinearLayout.VERTICAL);
        upcomingContainer.setClipChildren(false);
        LinearLayout.LayoutParams upcomingLp = new LinearLayout.LayoutParams(0, -2, 1.35f);
        upcomingLp.rightMargin = dp(22);
        columns.addView(upcomingContainer, upcomingLp);

        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.setClipChildren(false);
        columns.addView(resultsContainer, new LinearLayout.LayoutParams(0, -2, 1f));

        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView crest = new ImageView(this);
        crest.setImageDrawable(new BitmapDrawable(getResources(), crestBitmap));
        int crestSize = dp(58);
        crest.setLayoutParams(new LinearLayout.LayoutParams(crestSize, crestSize));
        row.addView(crest);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(20), 0, 0, 0);

        TextView title = text(30, 700, WHITE);
        title.setLetterSpacing(0.11f);
        title.setText("AL AHLY SC  ·  النادي الأهلي");
        titles.addView(title);

        TextView sub = text(15, 400, TEXT_SECONDARY);
        sub.setText("MATCH CENTRE  /  FIRST TEAM");
        titles.addView(sub);

        row.addView(titles);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView sectionTitle(String label) {
        TextView t = text(16, 700, GOLD);
        t.setLetterSpacing(0.14f);
        t.setText(label);
        t.setPadding(0, dp(10), 0, dp(12));
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.removeCallbacks(refreshTask);
        refreshTask.run();
    }

    @Override protected void onPause() {
        main.removeCallbacks(refreshTask);
        super.onPause();
    }

    private void load() {
        new Thread(() -> {
            List<Fixture> upcoming = new ArrayList<>();
            List<Fixture> results = new ArrayList<>();
            long updated = 0;
            String source = "";
            try (Cursor cursor = getContentResolver().query(MatchesProvider.MATCHES, null, null, null, null)) {
                if (cursor != null) while (cursor.moveToNext()) {
                    updated = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
                    source = cursor.getString(cursor.getColumnIndexOrThrow("source"));
                    String kind = cursor.getString(cursor.getColumnIndexOrThrow("kind"));
                    if ("empty".equals(kind)) continue;
                    String matchStatus = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                    if (System.currentTimeMillis() - updated > 60 * 60 * 1000L && isLive(matchStatus))
                        matchStatus = "STALE";
                    ClubMatchFeed.Match match = new ClubMatchFeed.Match(
                            new Date(cursor.getLong(cursor.getColumnIndexOrThrow("kickoff_ms"))),
                            cursor.getString(cursor.getColumnIndexOrThrow("home")),
                            cursor.getString(cursor.getColumnIndexOrThrow("away")),
                            cursor.getString(cursor.getColumnIndexOrThrow("competition")),
                            cursor.getString(cursor.getColumnIndexOrThrow("round")),
                            cursor.getString(cursor.getColumnIndexOrThrow("venue")),
                            cursor.getString(cursor.getColumnIndexOrThrow("home_score")),
                            cursor.getString(cursor.getColumnIndexOrThrow("away_score")),
                            matchStatus);
                    ("result".equals(kind) ? results : upcoming).add(toFixture(match));
                }
            } catch (Exception e) {
                Log.w(TAG, "Match provider unavailable: " + e.getClass().getSimpleName());
            }
            showFeed(upcoming, results, updated, source);
        }).start();
    }

    private void showFeed(List<Fixture> upcoming, List<Fixture> results, long updated, String source) {
        main.post(() -> {
            String cairoDay = ClubMatchFeed.format(new Date(), "yyyyMMdd", cairoTz);
            if (updated != renderedUpdate || !cairoDay.equals(renderedCairoDay)) {
                render(upcoming, results);
                renderedUpdate = updated;
                renderedCairoDay = cairoDay;
            }
            if (updated == 0) {
                statusText.setText("Match data unavailable · Check connection");
                return;
            }
            String time = ClubMatchFeed.format(new Date(updated), "d MMM · h:mm a", cairoTz);
            String freshness = System.currentTimeMillis() - updated > 24L * 60 * 60 * 1000 ? "Last checked " : "Updated ";
            statusText.setText(source + " · Cairo time · " + freshness + time);
        });
    }

    private Fixture toFixture(ClubMatchFeed.Match match) {
        Fixture f = new Fixture(match.isResult());
        f.matchDate = match.when;
        f.homeTeam = match.homeTeam;
        f.awayTeam = match.awayTeam;
        f.homeScore = match.homeScore;
        f.awayScore = match.awayScore;
        f.status = match.status;
        f.isHome = isAhly(f.homeTeam);
        f.tournament = match.competition.isEmpty() ? (match.isResult() ? "Recent result" : "Next match") : match.competition;
        f.round = match.round;
        f.venue = match.venue;
        f.resultLabel = match.isResult() ? deriveResultLabel(f) : "";
        return f;
    }

    private boolean isAhly(String team) {
        if (team == null) return false;
        String t = team.toLowerCase(Locale.ROOT);
        return t.contains("ahly") || t.contains("al-ahly") || t.contains("الأهلي");
    }

    private String deriveResultLabel(Fixture f) {
        try {
            int hs = Integer.parseInt(f.homeScore);
            int aws = Integer.parseInt(f.awayScore);
            int ahlyScore = f.isHome ? hs : aws;
            int oppScore = f.isHome ? aws : hs;
            if (ahlyScore > oppScore) return "WIN";
            if (ahlyScore < oppScore) return "LOSS";
            return "DRAW";
        } catch (Exception e) {
            return "FT";
        }
    }

    private void render(List<Fixture> upcoming, List<Fixture> results) {
        upcomingContainer.removeAllViews();
        resultsContainer.removeAllViews();

        // UPCOMING FIXTURES
        upcomingContainer.addView(sectionTitle("NEXT MATCH  /  UPCOMING"));
        if (upcoming.isEmpty()) {
            upcomingContainer.addView(emptyNote("No upcoming fixtures found."));
        } else {
            Fixture hero = upcoming.get(0);
            View heroCard = buildHero(hero);
            upcomingContainer.addView(heroCard);
            for (int i = 1; i < upcoming.size(); i++) {
                upcomingContainer.addView(buildUpcomingCard(upcoming.get(i)));
            }
            if (firstRender) {
                firstRender = false;
                main.postDelayed(() -> {
                    heroCard.requestFocus();
                    scroll.scrollTo(0, 0);
                }, 250);
            }
        }

        // RECENT RESULTS
        resultsContainer.addView(sectionTitle("RECENT RESULTS"));
        if (results.isEmpty()) {
            resultsContainer.addView(emptyNote("No recent results found."));
        } else {
            for (Fixture f : results) {
                resultsContainer.addView(buildCompactResultCard(f));
            }
        }
    }

    private static final int HERO_ID = 0x7F099901;

    private View buildHero(Fixture f) {
        LinearLayout card = new LinearLayout(this);
        card.setId(HERO_ID);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setPadding(dp(28), dp(24), dp(28), dp(24));

        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xFFD40A17, RED_DEEP, 0xFF321217});
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(2), 0x55FFD700);
        card.setBackground(bg);
        applyFocus(card, true);

        // Countdown pill: "TOMORROW · 7:00 PM"
        TextView countdown = badge(isLive(f.status) ? "LIVE · " + f.status : formatCountdown(f.matchDate),
                isLive(f.status) ? GOLD_SOFT : 0x22FFFFFF, WHITE);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.bottomMargin = dp(14);
        countdown.setLayoutParams(clp);
        card.addView(countdown);

        // Tournament + round/stage
        TextView comp = text(15, 600, WHITE);
        comp.setAlpha(0.95f);
        comp.setText(f.tournament.toUpperCase(Locale.ROOT) + (!f.round.isEmpty() ? " · " + roundName(f.round) : ""));
        card.addView(comp);

        // Big team presentation
        card.addView(buildTeamsRow(f, true));

        // Date / time / home-away
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(0, dp(10), 0, 0);

        SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, d MMM yyyy", Locale.US);
        dateFmt.setTimeZone(cairoTz);
        SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.US);
        timeFmt.setTimeZone(cairoTz);

        TextView dateTime = text(15, 500, WHITE);
        dateTime.setText(dateFmt.format(f.matchDate) + "  ·  " + timeFmt.format(f.matchDate) + " CAIRO");
        meta.addView(dateTime);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, dp(1), 1f));
        meta.addView(spacer);

        meta.addView(badge(f.isHome ? "HOME" : "AWAY", 0x22FFFFFF, WHITE));
        card.addView(meta);

        // Venue
        if (f.venue != null && !f.venue.isEmpty()) {
            TextView venue = text(14, 400, TEXT_SECONDARY);
            venue.setText(f.venue);
            venue.setPadding(0, dp(6), 0, 0);
            card.addView(venue);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private View buildTeamsRow(Fixture f, boolean big) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(big ? 12 : 8), 0, 0);

        LinearLayout homeSide = teamSide(f.homeTeam, f.isHome, big, Gravity.START);
        row.addView(homeSide, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView vs = text(big ? 18 : 14, 300, TEXT_MUTED);
        vs.setText(isLive(f.status) && !f.homeScore.isEmpty() && !f.awayScore.isEmpty()
                ? f.homeScore + " – " + f.awayScore : "VS");
        vs.setPadding(dp(12), 0, dp(12), 0);
        row.addView(vs);

        LinearLayout awaySide = teamSide(f.awayTeam, !f.isHome, big, Gravity.END);
        row.addView(awaySide, new LinearLayout.LayoutParams(0, -2, 1f));

        return row;
    }

    private LinearLayout teamSide(String team, boolean isAhlySide, boolean big, int gravity) {
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.HORIZONTAL);
        side.setGravity(Gravity.CENTER_VERTICAL | gravity);

        int size = big ? dp(60) : dp(42);
        if (isAhlySide) {
            ImageView crest = new ImageView(this);
            crest.setImageDrawable(new BitmapDrawable(getResources(), crestBitmap));
            crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
            crest.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            side.addView(crest);
        } else {
            TextView placeholder = text(big ? 20 : 15, 700, WHITE);
            placeholder.setText(initials(team));
            placeholder.setGravity(Gravity.CENTER);
            placeholder.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(0xFF2E303A);
            circle.setStroke(dp(1), 0x44FFFFFF);
            placeholder.setBackground(circle);
            side.addView(placeholder);
        }

        TextView name = text(big ? 24 : 17, 600, WHITE);
        name.setText(team);
        name.setPadding(dp(16), 0, 0, 0);
        side.addView(name);

        return side;
    }

    private String initials(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        int max = Math.min(2, parts.length);
        for (int i = 0; i < max; i++) {
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.toString();
    }

    private View buildUpcomingCard(Fixture f) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setPadding(dp(24), dp(14), dp(24), dp(14));
        card.setBackground(glassCard(false));
        applyFocus(card, false);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView comp = text(13, 600, GOLD_SOFT);
        comp.setText(f.tournament.toUpperCase(Locale.ROOT) + (!f.round.isEmpty() ? " · " + roundName(f.round) : ""));
        top.addView(comp);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, dp(1), 1f));
        top.addView(spacer);

        top.addView(badge(f.isHome ? "HOME" : "AWAY", 0x22FFFFFF, TEXT_SECONDARY));
        card.addView(top);

        card.addView(buildTeamsRow(f, false));

        SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, d MMM", Locale.US);
        dateFmt.setTimeZone(cairoTz);
        SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.US);
        timeFmt.setTimeZone(cairoTz);

        TextView details = text(14, 400, TEXT_SECONDARY);
        details.setText(dateFmt.format(f.matchDate) + " · " + timeFmt.format(f.matchDate)
                + (f.venue == null || f.venue.isEmpty() ? "" : " · " + f.venue));
        details.setPadding(0, dp(8), 0, 0);
        card.addView(details);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        return card;
    }

    private View buildCompactResultCard(Fixture f) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        card.setBackground(glassCard(false));
        applyFocus(card, false);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView comp = text(12, 600, GOLD_SOFT);
        comp.setSingleLine(true);
        comp.setEllipsize(TextUtils.TruncateAt.END);
        comp.setText(f.tournament.toUpperCase(Locale.ROOT));
        top.addView(comp, new LinearLayout.LayoutParams(0, -2, 1f));

        top.addView(badge(f.resultLabel, resultBadgeColor(f.resultLabel), WHITE));
        card.addView(top);

        LinearLayout scoreRow = new LinearLayout(this);
        scoreRow.setOrientation(LinearLayout.HORIZONTAL);
        scoreRow.setGravity(Gravity.CENTER);
        scoreRow.setPadding(0, dp(9), 0, 0);

        TextView home = text(16, 600, f.isHome ? WHITE : TEXT_SECONDARY);
        home.setText(f.homeTeam);
        home.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        home.setSingleLine(true);
        home.setEllipsize(TextUtils.TruncateAt.END);
        scoreRow.addView(home, new LinearLayout.LayoutParams(0, dp(38), 1f));

        TextView score = text(24, 700, WHITE);
        score.setText(f.homeScore + "–" + f.awayScore);
        score.setPadding(dp(12), 0, dp(12), 0);
        scoreRow.addView(score);

        TextView away = text(16, 600, f.isHome ? TEXT_SECONDARY : WHITE);
        away.setText(f.awayTeam);
        away.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        away.setSingleLine(true);
        away.setEllipsize(TextUtils.TruncateAt.END);
        scoreRow.addView(away, new LinearLayout.LayoutParams(0, dp(38), 1f));
        card.addView(scoreRow);

        SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, d MMM yyyy", Locale.US);
        dateFmt.setTimeZone(cairoTz);

        TextView date = text(12, 400, TEXT_MUTED);
        date.setGravity(Gravity.START);
        date.setText(dateFmt.format(f.matchDate) + "  ·  FULL TIME");
        date.setPadding(0, dp(4), 0, 0);
        card.addView(date);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        return card;
    }

    private int resultBadgeColor(String label) {
        switch (label) {
            case "WIN": return GOLD_SOFT;
            case "DRAW": return 0xFF6B6D78;
            case "LOSS": return RED;
            default: return 0xFF3A3C45;
        }
    }

    private String roundName(String round) {
        if (round == null || round.isEmpty()) return "";
        try {
            int r = Integer.parseInt(round);
            return "Matchweek " + r;
        } catch (NumberFormatException e) {
            return round;
        }
    }

    private String formatCountdown(Date matchDate) {
        SimpleDateFormat tf = new SimpleDateFormat("h:mm a", Locale.US);
        tf.setTimeZone(cairoTz);
        return MatchDayLabel.day(System.currentTimeMillis(), matchDate.getTime())
                + " · " + tf.format(matchDate);
    }

    private boolean isLive(String status) {
        return "1H".equals(status) || "HT".equals(status) || "2H".equals(status)
                || "ET".equals(status) || "BT".equals(status) || "P".equals(status)
                || "LIVE".equals(status);
    }

    private TextView badge(String label, int bgColor, int textColor) {
        TextView t = text(12, 700, textColor);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(bgColor);
        t.setBackground(bg);
        return t;
    }

    private GradientDrawable glassCard(boolean focused) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {CARD_GLASS_TOP, CARD_GLASS_BOTTOM});
        g.setCornerRadius(dp(18));
        g.setStroke(focused ? dp(3) : dp(1), focused ? WHITE : 0x22FFFFFF);
        return g;
    }

    private void applyFocus(View card, boolean isHero) {
        card.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.025f : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(160).start();
            if (hasFocus) ensureVisible(v);
            if (isHero) {
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[] {0xFFD40A17, RED_DEEP, 0xFF321217});
                g.setCornerRadius(dp(24));
                g.setStroke(hasFocus ? dp(4) : dp(2), hasFocus ? WHITE : 0x55FFD700);
                v.setBackground(g);
            } else {
                v.setBackground(glassCard(hasFocus));
            }
        });
    }

    private int topInContent(View v) {
        int top = 0;
        View current = v;
        while (current != null && current != contentCol) {
            top += current.getTop();
            ViewParent parent = current.getParent();
            current = (parent instanceof View) ? (View) parent : null;
        }
        return top;
    }

    private void ensureVisible(View v) {
        if (scroll == null || contentCol == null) return;
        if (v != null && v.getId() == HERO_ID) {
            scroll.smoothScrollTo(0, 0);
            return;
        }
        int target = topInContent(v) - dp(80);
        scroll.smoothScrollTo(0, Math.max(0, target));
    }

    private TextView emptyNote(String msg) {
        TextView t = text(14, 400, TEXT_MUTED);
        t.setText(msg);
        t.setPadding(0, dp(12), 0, dp(12));
        return t;
    }

    private TextView text(float sp, int weight, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (Build.VERSION.SDK_INT >= 28) {
            t.setTypeface(Typeface.create(font, weight, false));
        } else {
            t.setTypeface(font);
        }
        return t;
    }

    private Bitmap loadTrimmedCrest() {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ahly_crest);
        int left = bitmap.getWidth(), top = bitmap.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if ((bitmap.getPixel(x, y) >>> 24) < 24) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        if (right < left || bottom < top) return bitmap;
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
