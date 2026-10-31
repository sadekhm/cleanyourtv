package com.clubfixtures;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, account-free reader for the club's server-rendered homepage and first-team page. */
public final class ClubMatchFeed {
    public static final String HOME_URL = "https://www.alahlyegypt.com/en";
    public static final String RESULTS_URL = "https://www.alahlyegypt.com/en/football/first-team";
    public static final TimeZone CAIRO = TimeZone.getTimeZone("Africa/Cairo");
    private static final Pattern UPCOMING_CARD = Pattern.compile("class=\"w-\\[250px\\] h-\\[170px\\][^\"]*\"");
    private static final Pattern RESULT_CARD = Pattern.compile("class=\"bg-\\[#181818\\] rounded-lg[^\"]*\"");
    private static final Pattern ALT = Pattern.compile("<img[^>]+alt=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),?\\s+([A-Za-z]+)\\s+(\\d{1,2})");
    private static final Pattern TIME = Pattern.compile(">(\\d{1,2}:\\d{2})</p>\\s*<p[^>]*>(AM|PM)</p>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_DATE = Pattern.compile(">(20\\d{2}-\\d{2}-\\d{2})<");
    private static final Pattern SCORE = Pattern.compile(">(\\d{1,2})\\s*-\\s*(\\d{1,2})<");

    public static final class Match {
        public final Date when;
        public final String homeTeam, awayTeam, competition, round, venue, homeScore, awayScore, status;
        public Match(Date when, String homeTeam, String awayTeam, String competition, String homeScore, String awayScore) {
            this(when, homeTeam, awayTeam, competition, "", "", homeScore, awayScore);
        }
        public Match(Date when, String homeTeam, String awayTeam, String competition, String round, String venue, String homeScore, String awayScore) {
            this(when, homeTeam, awayTeam, competition, round, venue, homeScore, awayScore,
                    homeScore.isEmpty() || awayScore.isEmpty() ? "NS" : "FT");
        }
        public Match(Date when, String homeTeam, String awayTeam, String competition, String round, String venue,
                     String homeScore, String awayScore, String status) {
            this.when = when;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.competition = competition;
            this.round = round;
            this.venue = venue;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.status = status;
        }
        public boolean isResult() { return "FT".equals(status) || "AET".equals(status)
                || "PEN".equals(status) || "AWD".equals(status) || "WO".equals(status); }
    }

    public static final class Feed {
        public final List<Match> upcoming, results;
        Feed(List<Match> upcoming, List<Match> results) {
            this.upcoming = upcoming;
            this.results = results;
        }
    }

    private ClubMatchFeed() {}

    public static Feed parse(String homeHtml, String resultsHtml, long nowMs) {
        List<Match> upcoming = new ArrayList<>();
        List<Match> results = new ArrayList<>();
        for (String card : cards(homeHtml, UPCOMING_CARD)) {
            Matcher date = DATE.matcher(card);
            Matcher time = TIME.matcher(card);
            List<String> teams = imageAlts(card);
            if (!date.find() || !time.find() || teams.size() < 2 || !hasAhly(teams)) continue;
            Date when = upcomingDate(date.group(1), date.group(2), time.group(1), time.group(2), nowMs);
            if (when != null && when.getTime() >= nowMs - 3 * 60 * 60 * 1000L) {
                upcoming.add(new Match(when, teams.get(0), teams.get(1), "", "", ""));
            }
        }
        for (String card : cards(resultsHtml, RESULT_CARD)) {
            Matcher date = ISO_DATE.matcher(card);
            Matcher score = SCORE.matcher(card);
            List<String> teams = imageAlts(card);
            if (!date.find() || !score.find() || teams.size() < 2 || !hasAhly(teams)) continue;
            Date when = parseDate(date.group(1), "yyyy-MM-dd");
            if (when != null && when.getTime() <= nowMs) {
                results.add(new Match(when, teams.get(0), teams.get(1), "", score.group(1), score.group(2)));
            }
        }
        Collections.sort(upcoming, (a, b) -> a.when.compareTo(b.when));
        Collections.sort(results, (a, b) -> b.when.compareTo(a.when));
        return new Feed(upcoming, results);
    }

    private static List<String> cards(String html, Pattern marker) {
        List<String> out = new ArrayList<>();
        if (html == null) return out;
        Matcher m = marker.matcher(html);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) starts.add(m.start());
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : Math.min(html.length(), starts.get(i) + 12000);
            out.add(html.substring(starts.get(i), end));
        }
        return out;
    }

    private static List<String> imageAlts(String html) {
        List<String> names = new ArrayList<>();
        Matcher m = ALT.matcher(html);
        while (m.find() && names.size() < 2) names.add(unescape(m.group(1)));
        return names;
    }

    private static boolean hasAhly(List<String> teams) {
        for (String team : teams) if (team.toLowerCase(Locale.ROOT).contains("ahly")) return true;
        return false;
    }

    private static Date upcomingDate(String month, String day, String time, String amPm, long nowMs) {
        SimpleDateFormat year = new SimpleDateFormat("yyyy", Locale.US);
        year.setTimeZone(CAIRO);
        int currentYear = Integer.parseInt(year.format(new Date(nowMs)));
        for (int y = currentYear; y <= currentYear + 1; y++) {
            Date d = parseDate(month + " " + day + " " + y + " " + time + " " + amPm, "MMMM d yyyy h:mm a");
            if (d != null && d.getTime() >= nowMs - 3 * 60 * 60 * 1000L
                    && d.getTime() - nowMs < 180L * 24 * 60 * 60 * 1000) return d;
        }
        return null;
    }

    private static Date parseDate(String value, String pattern) {
        try {
            SimpleDateFormat fmt = new SimpleDateFormat(pattern, Locale.US);
            fmt.setLenient(false);
            fmt.setTimeZone(CAIRO);
            return fmt.parse(value);
        } catch (ParseException e) {
            return null;
        }
    }

    public static String format(Date date, String pattern, TimeZone zone) {
        SimpleDateFormat fmt = new SimpleDateFormat(pattern, Locale.US);
        fmt.setTimeZone(zone);
        return fmt.format(date);
    }

    private static String unescape(String value) {
        return value.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"");
    }

    public static String fetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; AlAhlyTV/1.0)");
        try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (connection.getResponseCode() != 200) throw new java.io.IOException("HTTP " + connection.getResponseCode());
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > 2_000_000) throw new java.io.IOException("Club page too large");
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            connection.disconnect();
        }
    }
}
