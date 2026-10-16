package com.huynhdous.employeefield.schedule;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertEquals;

/** The line a dispatch job shows in the Schedule tab's day box. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class JobStatusTextTest {
    private static final java.time.ZoneId LA = java.time.ZoneId.of("America/Los_Angeles");

    @Test public void showsTheLatestStageInOfficeTime() throws Exception {
        JSONObject job = new JSONObject().put("status_label", "On the way").put("accepted_utc", "2026-10-16 03:28:39")
                .put("on_the_way_utc", "2026-10-16 03:30:26").put("arrived_utc", JSONObject.NULL).put("completed_utc", JSONObject.NULL);
        assertEquals("On the way 8:30 PM", ScheduleTab.jobStatusText(job, LA));
    }

    @Test public void completedJobShowsItsCompletionTime() throws Exception {
        JSONObject job = new JSONObject().put("status_label", "Completed").put("accepted_utc", "2026-10-16 03:28:39")
                .put("on_the_way_utc", "2026-10-16 03:30:26").put("completed_utc", "2026-10-16 04:05:00");
        assertEquals("Completed 9:05 PM", ScheduleTab.jobStatusText(job, LA));
    }

    @Test public void acceptedJobFallsBackToTheAcceptedTime() throws Exception {
        JSONObject job = new JSONObject().put("status_label", "Accepted").put("accepted_utc", "2026-10-16 03:28:39");
        assertEquals("Accepted 8:28 PM", ScheduleTab.jobStatusText(job, LA));
    }

    @Test public void aCancelledJobShowsWhenItWasCancelledNotWhenHeWentOnTheWay() throws Exception {
        JSONObject job = new JSONObject().put("status", "declined").put("status_label", "Cancelled").put("accepted_utc", "2026-10-16 04:09:14")
                .put("on_the_way_utc", "2026-10-16 03:30:26");
        assertEquals("Cancelled 9:09 PM", ScheduleTab.jobStatusText(job, LA));
    }

    @Test public void aJobWithNoTimesStillShowsItsStatus() throws Exception {
        assertEquals("Accepted", ScheduleTab.jobStatusText(new JSONObject(), LA));
    }
}
