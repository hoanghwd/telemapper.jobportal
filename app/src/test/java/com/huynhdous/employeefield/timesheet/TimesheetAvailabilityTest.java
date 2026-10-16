package com.huynhdous.employeefield.timesheet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.events.EventsTab;
import org.junit.Test;

public class TimesheetAvailabilityTest {
    @Test
    public void theTimeSheetIsOnlyForWorkersWhoKeepOne() {
        TimesheetTab tab = new TimesheetTab();
        assertTrue(tab.isAvailableFor(null, true));
        assertTrue(tab.isAvailableFor("D2D", true));
        assertFalse("work paid per job has no time sheet", tab.isAvailableFor(null, false));
        assertFalse(tab.isAvailableFor("S2S", false));
    }

    @Test
    public void otherScreensAreNotAffectedByTheTimeSheetSetting() {
        EventsTab events = new EventsTab();
        assertTrue(events.isAvailableFor("S2S", false));
        assertFalse(events.isAvailableFor("D2D", false));
    }

    @Test
    public void aSessionKeepsATimeSheetUntilTheServerSaysOtherwise() {
        Session session = new Session();
        assertTrue(session.timeTracking);
        session.timeTracking = false;
        session.clear();
        assertTrue(session.timeTracking);
    }
}
