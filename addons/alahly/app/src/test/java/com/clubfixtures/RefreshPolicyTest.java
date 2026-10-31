package com.clubfixtures;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class RefreshPolicyTest {
    private static final long HOUR = 60L * 60 * 1000;
    private static final long MINUTE = 60L * 1000;
    private static final long NOW = 1_800_000_000_000L;

    @Test public void distantFixtureWaitsThreeDays() {
        assertEquals(NOW + 72 * HOUR,
                RefreshPolicy.nextDue(NOW, NOW, NOW, NOW + 10 * 24 * HOUR, "NS"));
    }

    @Test public void fixtureWithinWeekWaitsOneDay() {
        assertEquals(NOW + 24 * HOUR,
                RefreshPolicy.nextDue(NOW, NOW, NOW, NOW + 3 * 24 * HOUR, "NS"));
    }

    @Test public void matchDayFetchesNinetyMinutesBeforeKickoff() {
        assertEquals(NOW + 8 * HOUR + 30 * MINUTE,
                RefreshPolicy.nextDue(NOW, NOW, NOW, NOW + 10 * HOUR, "NS"));
    }

    @Test public void matchWindowChecksEveryTwentyMinutes() {
        assertEquals(NOW + 20 * MINUTE,
                RefreshPolicy.nextDue(NOW, NOW, NOW, NOW - HOUR, "1H"));
    }

    @Test public void delayedResultBacksOffSixHours() {
        assertEquals(NOW + 6 * HOUR,
                RefreshPolicy.nextDue(NOW, NOW, NOW, NOW - 4 * HOUR, "NS"));
    }

    @Test public void failureBacksOffWithoutWaitingThreeDays() {
        assertEquals(NOW + 6 * HOUR,
                RefreshPolicy.nextDue(NOW, NOW, NOW - HOUR, NOW + 10 * 24 * HOUR, "NS"));
    }

    @Test public void noCachedAttemptIsDueImmediately() {
        assertEquals(NOW,
                RefreshPolicy.nextDue(NOW, 0, 0, Long.MAX_VALUE, ""));
    }

    @Test public void noKnownFixtureChecksDaily() {
        assertEquals(NOW + 24 * HOUR,
                RefreshPolicy.nextDue(NOW, NOW, NOW, Long.MAX_VALUE, ""));
    }

    @Test public void failedPreKickoffRequestWaitsBeforeRetrying() {
        assertEquals(NOW + 6 * HOUR,
                RefreshPolicy.nextDue(NOW, NOW, NOW - HOUR, NOW + HOUR, "NS"));
    }
}
