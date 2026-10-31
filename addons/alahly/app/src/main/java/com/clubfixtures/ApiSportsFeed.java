package com.clubfixtures;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** API-Sports football v3 reader. One season request contains all known team competitions. */
public final class ApiSportsFeed {
    private static final String BASE = "https://v3.football.api-sports.io/";

    public static final class Response {
        public final String body;
        public final int remaining;
        Response(String body, int remaining) { this.body = body; this.remaining = remaining; }
    }

    private ApiSportsFeed() {}

    public static int currentSeason(long now) {
        Calendar cal = Calendar.getInstance(ClubMatchFeed.CAIRO);
        cal.setTimeInMillis(now);
        return cal.get(Calendar.MONTH) < Calendar.JULY ? cal.get(Calendar.YEAR) - 1 : cal.get(Calendar.YEAR);
    }

    public static Response fetch(String path, String key) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("x-apisports-key", key);
        try {
            int status = conn.getResponseCode();
            if (status != 200) throw new IOException("API-Sports HTTP " + status);
            int remaining = -1;
            try { remaining = Integer.parseInt(conn.getHeaderField("x-ratelimit-requests-remaining")); }
            catch (Exception ignored) {}
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (out.size() + n > 2_000_000) throw new IOException("API-Sports response too large");
                    out.write(buf, 0, n);
                }
                return new Response(out.toString("UTF-8"), remaining);
            }
        } finally { conn.disconnect(); }
    }

    public static int findEgyptianAlAhly(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        ensureNoErrors(root);
        JSONArray teams = root.optJSONArray("response");
        if (teams != null) for (int i = 0; i < teams.length(); i++) {
            JSONObject team = teams.getJSONObject(i).optJSONObject("team");
            if (team != null && "Egypt".equalsIgnoreCase(team.optString("country"))
                    && team.optString("name").toLowerCase(Locale.ROOT).contains("ahly")
                    && team.optInt("id", 0) > 0) {
                return team.optInt("id", 0);
            }
        }
        throw new IOException("API-Sports Al Ahly team not found");
    }

    public static ClubMatchFeed.Feed parseFixtures(String json, long now) throws Exception {
        JSONObject root = new JSONObject(json);
        ensureNoErrors(root);
        List<ClubMatchFeed.Match> upcoming = new ArrayList<>();
        List<ClubMatchFeed.Match> results = new ArrayList<>();
        JSONArray items = root.optJSONArray("response");
        if (items == null) throw new IOException("API-Sports fixtures missing");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            JSONObject fixture = item.optJSONObject("fixture");
            JSONObject teams = item.optJSONObject("teams");
            if (fixture == null || teams == null) continue;
            JSONObject home = teams.optJSONObject("home"), away = teams.optJSONObject("away");
            if (home == null || away == null) continue;
            long stamp = fixture.optLong("timestamp", 0) * 1000L;
            if (stamp <= 0) continue;
            JSONObject statusObj = fixture.optJSONObject("status");
            String status = statusObj == null ? "" : statusObj.optString("short");
            JSONObject league = item.optJSONObject("league");
            JSONObject venue = fixture.optJSONObject("venue");
            String competition = league == null ? "Match" : league.optString("name", "Match");
            String round = league == null ? "" : league.optString("round", "");
            String venueName = venue == null ? "" : venue.optString("name", "");
            String homeName = home.optString("name", "");
            String awayName = away.optString("name", "");
            if (homeName.isEmpty() || awayName.isEmpty()) continue;
            JSONObject goals = item.optJSONObject("goals");
            if (isFinal(status) && goals != null && !goals.isNull("home") && !goals.isNull("away")) {
                results.add(new ClubMatchFeed.Match(new Date(stamp), homeName, awayName, competition,
                        round, venueName, String.valueOf(goals.optInt("home")), String.valueOf(goals.optInt("away")), status));
            } else if (("NS".equals(status) || isInPlay(status)) && stamp >= now - 12 * 60 * 60 * 1000L) {
                String homeScore = goals == null || goals.isNull("home") ? "" : String.valueOf(goals.optInt("home"));
                String awayScore = goals == null || goals.isNull("away") ? "" : String.valueOf(goals.optInt("away"));
                upcoming.add(new ClubMatchFeed.Match(new Date(stamp), homeName, awayName, competition,
                        round, venueName, homeScore, awayScore, status));
            }
        }
        Collections.sort(upcoming, (a, b) -> {
            if (isInPlay(a.status) != isInPlay(b.status)) return isInPlay(a.status) ? -1 : 1;
            return a.when.compareTo(b.when);
        });
        Collections.sort(results, (a, b) -> b.when.compareTo(a.when));
        return new ClubMatchFeed.Feed(upcoming, results);
    }

    private static boolean isFinal(String status) {
        return "FT".equals(status) || "AET".equals(status) || "PEN".equals(status)
                || "AWD".equals(status) || "WO".equals(status);
    }

    public static boolean isInPlay(String status) {
        return "1H".equals(status) || "HT".equals(status) || "2H".equals(status)
                || "ET".equals(status) || "BT".equals(status) || "P".equals(status)
                || "LIVE".equals(status);
    }

    private static void ensureNoErrors(JSONObject root) throws IOException {
        Object errors = root.opt("errors");
        if (errors instanceof JSONObject && ((JSONObject) errors).length() > 0
                || errors instanceof JSONArray && ((JSONArray) errors).length() > 0) {
            throw new IOException("API-Sports returned an error");
        }
    }
}
