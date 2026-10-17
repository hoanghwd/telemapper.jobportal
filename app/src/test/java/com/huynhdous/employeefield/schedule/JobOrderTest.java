package com.huynhdous.employeefield.schedule;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertEquals;

/** The jobs of one day in the Schedule tab are numbered newest first. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class JobOrderTest {
    private static JSONObject job(String title, String status, String accepted, String completed) throws Exception {
        JSONObject job = new JSONObject().put("title", title).put("status", status).put("accepted_utc", accepted);
        if (completed != null) job.put("completed_utc", completed);
        return job;
    }

    @Test public void theLatestStageDecidesWhenAJobLastChanged() throws Exception {
        assertEquals("2026-10-16 22:54:41", ScheduleTab.latestStageTime(job("Oka", "completed", "2026-10-16 20:00:00", "2026-10-16 22:54:41")));
        assertEquals("2026-10-16 20:00:00", ScheduleTab.latestStageTime(job("Accepted", "accepted", "2026-10-16 20:00:00", null)));
        // A cancelled job shows when it was cancelled (stored in accepted_utc), not when it went on the way.
        JSONObject cancelled = job("Cancelled", "declined", "2026-10-16 23:03:00", null).put("on_the_way_utc", "2026-10-16 21:00:00");
        assertEquals("2026-10-16 23:03:00", ScheduleTab.latestStageTime(cancelled));
        assertEquals("", ScheduleTab.latestStageTime(new JSONObject().put("title", "x")));
    }

    @Test public void jobsAreListedNewestFirst() throws Exception {
        List<JSONObject> given = new ArrayList<>();
        given.add(job("Bakery delivery sweethouse-TP3", "declined", "2026-10-16 04:09:00", null));   // the order the server sent them in
        given.add(job("Bakery delivery sweethouse-TP5", "declined", "2026-10-16 05:50:00", null));
        given.add(job("Bakery delivery sweethouse-TP2", "declined", "2026-10-16 06:03:00", null));
        List<JSONObject> sorted = ScheduleTab.latestFirst(given);
        assertEquals("Bakery delivery sweethouse-TP2", sorted.get(0).getString("title"));
        assertEquals("Bakery delivery sweethouse-TP5", sorted.get(1).getString("title"));
        assertEquals("Bakery delivery sweethouse-TP3", sorted.get(2).getString("title"));
        assertEquals("the original list is left alone", "Bakery delivery sweethouse-TP3", given.get(0).getString("title"));
    }

    @Test public void aCompletedJobIsOrderedByWhenItWasCompleted() throws Exception {
        List<JSONObject> given = new ArrayList<>();
        given.add(job("Coffee Delivery", "declined", "2026-10-16 19:04:00", null));
        given.add(job("Oka", "completed", "2026-10-16 18:00:00", "2026-10-16 22:54:00"));
        assertEquals("Oka", ScheduleTab.latestFirst(given).get(0).getString("title"));
    }
    private static final java.time.ZoneId LA = java.time.ZoneId.of("America/Los_Angeles");

    @Test public void theOrderNumberIsPulledFromTheTitle() {
        assertEquals("TP202610150003", ScheduleTab.orderNumber("Bakery delivery sweethouse-TP202610150003"));
        assertEquals("Oka", ScheduleTab.orderNumber("Oka"));
        assertEquals("", ScheduleTab.orderNumber(null));
    }

    @Test public void aCompletedDeliveryShowsItsDetailsThenWhenEachStageHappened() throws Exception {
        JSONObject job = new JSONObject().put("delivery", true).put("status", "completed")
                .put("details", "Pick up: 155 W Center St\nDrop off: 8572 Salt Lake Dr\nTotal distance: 15 mi\nETA: about 45 min\nOffer Amount: $8.38")
                .put("accepted_utc", "2026-10-16 22:00:00").put("on_the_way_utc", "2026-10-16 22:05:00").put("arrived_utc", "2026-10-16 22:20:00")
                .put("started_utc", "2026-10-16 22:25:00").put("completed_utc", "2026-10-16 22:54:00");
        assertEquals("Pick up: 155 W Center St\nDrop off: 8572 Salt Lake Dr\nTotal distance: 15 mi\nETA: about 45 min\nOffer Amount: $8.38\n\n"
                + "Accepted Fri Oct 16, 3:00 PM\nOn the way to pickup Fri Oct 16, 3:05 PM\nAt pickup Fri Oct 16, 3:20 PM\nPicked up Fri Oct 16, 3:25 PM\nDelivered Fri Oct 16, 3:54 PM\n(Times are Los Angeles time)", ScheduleTab.jobDetailsText(job, LA));
    }

    @Test public void aCancelledJobShowsWhenItWasCancelledNotAcceptedAndKeepsTheStagesItReached() throws Exception {
        JSONObject job = new JSONObject().put("delivery", true).put("status", "declined").put("details", "Pick up: B\nDrop off: A")
                .put("accepted_utc", "2026-10-16 07:03:00").put("on_the_way_utc", "2026-10-16 06:30:00");
        assertEquals("Pick up: B\nDrop off: A\n\nOn the way to pickup Thu Oct 15, 11:30 PM\nCancelled Fri Oct 16, 12:03 AM\n(Times are Los Angeles time)", ScheduleTab.jobDetailsText(job, LA));
    }

    @Test public void aServiceJobUsesTheGeneralStageWordsAndAJobWithoutDetailsStillShowsItsStages() throws Exception {
        JSONObject job = new JSONObject().put("status", "completed").put("accepted_utc", "2026-10-16 22:00:00").put("completed_utc", "2026-10-16 22:54:00");
        assertEquals("Accepted Fri Oct 16, 3:00 PM\nCompleted Fri Oct 16, 3:54 PM\n(Times are Los Angeles time)", ScheduleTab.jobDetailsText(job, LA));
    }
}
