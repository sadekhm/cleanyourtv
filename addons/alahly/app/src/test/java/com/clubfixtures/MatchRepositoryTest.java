package com.clubfixtures;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import static org.junit.Assert.*;

public class MatchRepositoryTest {
    @Test public void fillsApiResultGapFromClubAndUsesOlderTimestamp() {
        ClubMatchFeed.Match fixture = new ClubMatchFeed.Match(new Date(2000), "Al Ahly", "Kitara", "Cup", "", "");
        ClubMatchFeed.Match result = new ClubMatchFeed.Match(new Date(1000), "Al Ahly", "Canal", "League", "3", "3");
        ClubMatchFeed.Feed api = new ClubMatchFeed.Feed(Collections.singletonList(fixture), new ArrayList<>());
        ClubMatchFeed.Feed club = new ClubMatchFeed.Feed(new ArrayList<>(), Collections.singletonList(result));
        MatchRepository.Snapshot snapshot = MatchRepository.select(api, 200, club, 100);
        assertEquals(1, snapshot.feed.upcoming.size());
        assertEquals(1, snapshot.feed.results.size());
        assertEquals(100, snapshot.updated);
    }

    @Test public void singleSourceDoesNotGetZeroTimestampFromEmptyFallback() {
        ClubMatchFeed.Match fixture = new ClubMatchFeed.Match(new Date(2000), "Al Ahly", "Kitara", "Cup", "", "");
        ClubMatchFeed.Feed api = new ClubMatchFeed.Feed(Collections.singletonList(fixture), new ArrayList<>());
        ClubMatchFeed.Feed empty = new ClubMatchFeed.Feed(new ArrayList<>(), new ArrayList<>());
        assertEquals(200, MatchRepository.select(api, 200, empty, 0).updated);
    }
}
