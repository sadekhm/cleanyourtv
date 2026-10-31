package com.gathin.tvhome;

import com.clubfixtures.ApiSportsFeed;
import com.clubfixtures.ClubMatchFeed;
import org.junit.Test;
import static org.junit.Assert.*;

public class ApiSportsFeedTest {
    @Test public void separatesAllCompetitionsResultsAndFutureFixtures() throws Exception {
        String json = "{\"errors\":[],\"response\":["
                + "{\"fixture\":{\"timestamp\":1792954800,\"status\":{\"short\":\"NS\"},\"venue\":{\"name\":\"Cairo Stadium\"}},"
                + "\"league\":{\"name\":\"CAF Confederation Cup\",\"round\":\"Round 32\"},"
                + "\"teams\":{\"home\":{\"name\":\"Al Ahly\"},\"away\":{\"name\":\"Kitara\"}},"
                + "\"goals\":{\"home\":null,\"away\":null}},"
                + "{\"fixture\":{\"timestamp\":1792609200,\"status\":{\"short\":\"FT\"}},"
                + "\"league\":{\"name\":\"Egyptian Premier League\"},"
                + "\"teams\":{\"home\":{\"name\":\"Al Ahly\"},\"away\":{\"name\":\"Canal SC\"}},"
                + "\"goals\":{\"home\":3,\"away\":3}}]}";
        ClubMatchFeed.Feed feed = ApiSportsFeed.parseFixtures(json, 1792800000000L);
        assertEquals(1, feed.upcoming.size());
        assertEquals("CAF Confederation Cup", feed.upcoming.get(0).competition);
        assertEquals(1, feed.results.size());
        assertEquals("3", feed.results.get(0).awayScore);
    }

    @Test public void reportsQuotaErrorsInsteadOfCachingThem() {
        try {
            ApiSportsFeed.parseFixtures("{\"errors\":{\"requests\":\"daily limit\"},\"response\":[]}", 0);
            fail("Expected provider error");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("API-Sports"));
        }
    }
}
