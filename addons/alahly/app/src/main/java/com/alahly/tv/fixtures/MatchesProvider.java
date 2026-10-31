package com.alahly.tv.fixtures;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import com.clubfixtures.ClubMatchFeed;
import com.clubfixtures.MatchRepository;

/** Read-only cross-app view of the addon's one cache. Never returns credentials or raw API data. */
public final class MatchesProvider extends ContentProvider {
    public static final String AUTHORITY = "com.alahly.tv.fixtures.matches";
    public static final Uri SUMMARY = Uri.parse("content://" + AUTHORITY + "/v1/summary");
    public static final Uri MATCHES = Uri.parse("content://" + AUTHORITY + "/v1/matches");
    private static final String[] COLUMNS = {"kind", "kickoff_ms", "home", "away", "competition",
            "round", "venue", "home_score", "away_score", "status", "updated_at", "source"};

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        boolean summary = SUMMARY.equals(uri);
        if (!summary && !MATCHES.equals(uri)) throw new IllegalArgumentException("Unknown matches URI");
        MatchRepository.Snapshot snapshot = MatchRepository.load(getContext(), BuildConfig.API_SPORTS_KEY);
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        int limit = summary ? 1 : 5;
        append(cursor, "upcoming", snapshot.feed.upcoming, limit, snapshot);
        append(cursor, "result", snapshot.feed.results, limit, snapshot);
        if (cursor.getCount() == 0) {
            cursor.addRow(new Object[] {"empty", 0L, "", "", "", "", "", "", "", "", snapshot.updated, snapshot.source});
        }
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    private void append(MatrixCursor cursor, String kind, java.util.List<ClubMatchFeed.Match> matches,
                        int limit, MatchRepository.Snapshot snapshot) {
        for (int i = 0; i < Math.min(limit, matches.size()); i++) {
            ClubMatchFeed.Match m = matches.get(i);
            cursor.addRow(new Object[] {kind, m.when.getTime(), m.homeTeam, m.awayTeam, m.competition,
                    m.round, m.venue, m.homeScore, m.awayScore, m.status, snapshot.updated, snapshot.source});
        }
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".match"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
