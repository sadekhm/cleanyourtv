package com.gathin.tvhome;

import com.clubfixtures.ClubMatchFeed;
import org.junit.Test;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.*;

public class ClubMatchFeedTest {
    private static final String HOME = "<div class=\"w-[250px] h-[170px] card\">"
            + "<p>Sunday, October 25</p><img alt=\"Al Ahly FC\"><p>7:00</p><p>PM</p>"
            + "<img alt=\"Kitara\"><div>Al Ahly FC <span>Vs</span> Kitara</div></div>"
            + "<div class=\"w-[250px] h-[170px] card\">"
            + "<p>Thursday, October 29</p><img alt=\"Modern Sport\"><p>5:00</p><p>PM</p>"
            + "<img alt=\"Al Ahly FC\"><div>Modern Sport <span>Vs</span> Al Ahly FC</div></div>";
    private static final String RESULTS = "<div class=\"bg-[#181818] rounded-lg result\">"
            + "<div>2026-10-21</div><img alt=\"Al Ahly FC\"><span>Al Ahly FC</span>"
            + "<span>3 - 3</span><img alt=\"Canal SC\"><span>Canal SC</span>"
            + "<div>الدوري المصري</div></div>"
            + "<div class=\"bg-[#181818] rounded-lg result\">"
            + "<div>2026-10-17</div><img alt=\"Kitara\"><span>Kitara</span>"
            + "<span>1 - 2</span><img alt=\"Al Ahly FC\"><span>Al Ahly FC</span>"
            + "<div>الكونفدرالية</div></div>";

    @Test public void parsesHomeAndAwayFixturesAndRecentScores() {
        ClubMatchFeed.Feed feed = ClubMatchFeed.parse(HOME, RESULTS, 1792800000000L);
        assertEquals(2, feed.upcoming.size());
        assertEquals("Kitara", feed.upcoming.get(0).awayTeam);
        assertEquals("Modern Sport", feed.upcoming.get(1).homeTeam);
        assertEquals(2, feed.results.size());
        assertEquals("3", feed.results.get(0).homeScore);
        assertEquals("2", feed.results.get(1).awayScore);
        assertEquals("Kitara", feed.results.get(1).homeTeam);
    }

    @Test public void rejectsMissingTimeAndNeverInventsResults() {
        String incomplete = HOME.replace("<p>7:00</p><p>PM</p>", "<p>TBC</p>");
        ClubMatchFeed.Feed feed = ClubMatchFeed.parse(incomplete, "Loading...", 1792800000000L);
        assertEquals(1, feed.upcoming.size());
        assertTrue(feed.results.isEmpty());
    }

    @Test public void formatsCairoTimeAndRollsDecemberIntoNextYear() {
        String html = "<div class=\"w-[250px] h-[170px]\"><p>Friday, January 1</p>"
                + "<img alt=\"Al Ahly FC\"><p>8:00</p><p>PM</p><img alt=\"Pyramids\"></div>";
        ClubMatchFeed.Feed feed = ClubMatchFeed.parse(html, "", 1798675200000L);
        assertEquals(1, feed.upcoming.size());
        assertEquals("2027", ClubMatchFeed.format(feed.upcoming.get(0).when, "yyyy", TimeZone.getTimeZone("Africa/Cairo")));
    }

    @Test public void doesNotReviveOldHomepageFixtureNextYear() {
        ClubMatchFeed.Feed feed = ClubMatchFeed.parse(HOME, "", 1793491200000L);
        assertTrue(feed.upcoming.isEmpty());
    }
}
