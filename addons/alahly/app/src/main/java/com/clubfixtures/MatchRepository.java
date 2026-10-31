package com.clubfixtures;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** The addon's single persisted feed and UTC request budget. Provider queries serialize here. */
public final class MatchRepository {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final int APP_DAILY_LIMIT = 25;
    private static final String TAG = "AlAhlyMatchFeed";

    public static final class Snapshot {
        public final ClubMatchFeed.Feed feed;
        public final long updated;
        public final String source;
        Snapshot(ClubMatchFeed.Feed feed, long updated, String source) {
            this.feed = feed; this.updated = updated; this.source = source;
        }
    }

    private MatchRepository() {}

    public static synchronized Snapshot load(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences("club_matches_v2", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        String apiJson = prefs.getString("api_json", "");
        long apiUpdated = prefs.getLong("api_updated", 0);
        ClubMatchFeed.Feed apiFeed = parseApi(apiJson, now);
        long kickoff = apiFeed.upcoming.isEmpty() ? Long.MAX_VALUE : apiFeed.upcoming.get(0).when.getTime();
        String status = apiFeed.upcoming.isEmpty() ? "" : apiFeed.upcoming.get(0).status;
        long due = RefreshPolicy.nextDue(now, prefs.getLong("api_attempt", 0), apiUpdated, kickoff, status);
        if (key != null && !key.isEmpty() && now >= due
                && now >= prefs.getLong("api_pause_until", 0) && callsToday(prefs, now) < APP_DAILY_LIMIT) {
            // Commit before HTTP so a process restart cannot immediately repeat the request.
            prefs.edit().putLong("api_attempt", now).commit();
            try {
                int teamId = prefs.getInt("team_id", 0);
                if (teamId == 0) {
                    ApiSportsFeed.Response team = countedFetch(prefs, now, "teams?search=Al%20Ahly", key);
                    teamId = ApiSportsFeed.findEgyptianAlAhly(team.body);
                    prefs.edit().putInt("team_id", teamId).commit();
                    if (isPaused(prefs, now)) throw new IllegalStateException("Provider quota nearly exhausted");
                }
                int season = ApiSportsFeed.currentSeason(now);
                ApiSportsFeed.Response fixtures = countedFetch(prefs, now,
                        "fixtures?team=" + teamId + "&season=" + season + "&timezone=Africa%2FCairo", key);
                ClubMatchFeed.Feed fresh = ApiSportsFeed.parseFixtures(fixtures.body, now);
                if (!hasMatches(fresh)) throw new IllegalStateException("No season fixtures");
                prefs.edit().putString("api_json", fixtures.body).putLong("api_updated", now).commit();
                apiFeed = fresh;
                apiUpdated = now;
            } catch (Exception e) {
                Log.w(TAG, "Provider unavailable: " + e.getClass().getSimpleName());
            }
        }

        if (hasBoth(apiFeed) && now - apiUpdated <= 72 * 60 * 60 * 1000L)
            return new Snapshot(apiFeed, apiUpdated, "API-Sports");

        String home = prefs.getString("club_home", "");
        String results = prefs.getString("club_results", "");
        long clubUpdated = prefs.getLong("club_updated", 0);
        ClubMatchFeed.Feed clubFeed = ClubMatchFeed.parse(home, results, now);
        if (now - clubUpdated >= DAY && now - prefs.getLong("club_attempt", 0) >= DAY) {
            prefs.edit().putLong("club_attempt", now).commit();
            try {
                String freshHome = ClubMatchFeed.fetch(ClubMatchFeed.HOME_URL);
                String freshResults = ClubMatchFeed.fetch(ClubMatchFeed.RESULTS_URL);
                ClubMatchFeed.Feed fresh = ClubMatchFeed.parse(freshHome, freshResults, now);
                if (hasMatches(fresh)) {
                    prefs.edit().putString("club_home", freshHome).putString("club_results", freshResults)
                            .putLong("club_updated", now).apply();
                    clubFeed = fresh;
                    clubUpdated = now;
                }
            } catch (Exception e) {
                Log.w(TAG, "Club site unavailable: " + e.getClass().getSimpleName());
            }
        }
        return select(apiFeed, apiUpdated, clubFeed, clubUpdated);
    }

    private static ApiSportsFeed.Response countedFetch(SharedPreferences prefs, long now, String path, String key) throws Exception {
        String today = dayKey(now);
        int count = today.equals(prefs.getString("api_day", "")) ? prefs.getInt("api_calls", 0) : 0;
        if (count >= APP_DAILY_LIMIT) throw new IllegalStateException("Local request budget reached");
        prefs.edit().putString("api_day", today).putInt("api_calls", count + 1).commit();
        try {
            ApiSportsFeed.Response response = ApiSportsFeed.fetch(path, key);
            if (response.remaining >= 0 && response.remaining <= 5)
                prefs.edit().putLong("api_pause_until", nextUtcMidnight(now)).commit();
            return response;
        } catch (java.io.IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("429"))
                prefs.edit().putLong("api_pause_until", nextUtcMidnight(now)).commit();
            throw e;
        }
    }

    private static String dayKey(long now) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(now));
    }

    private static long nextUtcMidnight(long now) { return (Math.floorDiv(now, DAY) + 1) * DAY; }

    private static boolean isPaused(SharedPreferences prefs, long now) {
        return now < prefs.getLong("api_pause_until", 0);
    }

    private static int callsToday(SharedPreferences prefs, long now) {
        return dayKey(now).equals(prefs.getString("api_day", "")) ? prefs.getInt("api_calls", 0) : 0;
    }

    private static ClubMatchFeed.Feed parseApi(String json, long now) {
        try { return ApiSportsFeed.parseFixtures(json, now); }
        catch (Exception ignored) { return ClubMatchFeed.parse("", "", now); }
    }

    private static boolean hasMatches(ClubMatchFeed.Feed feed) {
        return !feed.upcoming.isEmpty() || !feed.results.isEmpty();
    }

    private static boolean hasBoth(ClubMatchFeed.Feed feed) {
        return !feed.upcoming.isEmpty() && !feed.results.isEmpty();
    }

    static Snapshot select(ClubMatchFeed.Feed api, long apiAt, ClubMatchFeed.Feed club, long clubAt) {
        ClubMatchFeed.Feed first = apiAt >= clubAt ? api : club;
        ClubMatchFeed.Feed second = apiAt >= clubAt ? club : api;
        java.util.List<ClubMatchFeed.Match> upcoming = first.upcoming.isEmpty() ? second.upcoming : first.upcoming;
        java.util.List<ClubMatchFeed.Match> results = first.results.isEmpty() ? second.results : first.results;
        boolean usesApi = !upcoming.isEmpty() && upcoming == api.upcoming
                || !results.isEmpty() && results == api.results;
        boolean usesClub = !upcoming.isEmpty() && upcoming == club.upcoming
                || !results.isEmpty() && results == club.results;
        long updated = usesApi && usesClub ? Math.min(apiAt, clubAt) : usesApi ? apiAt : clubAt;
        String source = usesApi && usesClub ? "API-Sports + Al Ahly club" : usesApi ? "API-Sports" : "Al Ahly club";
        return new Snapshot(new ClubMatchFeed.Feed(upcoming, results), updated, source);
    }
}
