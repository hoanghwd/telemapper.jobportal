package com.huynhdous.employeefield.jobs;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JobTextTest {
    @Test
    public void orderNumberIsTheLastPartOfABakeryTitle() {
        assertEquals("TP202610150003", JobText.orderNumber("Bakery delivery sweethouse-TP202610150003"));
        assertEquals("Fix the sink", JobText.orderNumber("Fix the sink"));
        assertEquals("Order A - rush", JobText.orderNumber("Order A - rush"));
        assertEquals("Pickup-", JobText.orderNumber("Pickup-"));
        assertEquals("", JobText.orderNumber(null));
    }

    @Test
    public void milesUseOneDecimalUnderTen() {
        assertEquals("6.6", JobText.miles(6.6));
        assertEquals("0.7", JobText.miles(0.7));
        assertEquals("13", JobText.miles(12.6));
        assertEquals("10", JobText.miles(10.0));
    }

    @Test
    public void tripAndExtraLinesAreShort() {
        assertEquals("6.6 mi · 21 min to the pickup · trip 6.1 mi · 19 min", JobText.tripLine(6.6, 21, 6.1, 19));
        assertEquals("6.6 mi to the pickup · trip 6.1 mi", JobText.tripLine(6.6, 0, 6.1, 0));   // an older server sends no times
        assertEquals("Adds about 2.1 mi · 6 min to your route", JobText.extraLine(2.1, 6));
    }

    @Test
    public void theQuestionListsBothStopsAndOnlyMentionsExtraWhenHeHasOrders() {
        String idle = JobText.confirmMessage("8942 Garden Grove Blvd", "8271 Fontainbleau Way", 6.6, 21, 6.1, 19, "", false, 0, 0);
        assertEquals("Pick up: 8942 Garden Grove Blvd\nDrop off: 8271 Fontainbleau Way\n6.6 mi · 21 min to the pickup · trip 6.1 mi · 19 min", idle);
        String busy = JobText.confirmMessage("A", "B", 1.0, 4, 2.0, 7, "", true, 0.5, 3);
        assertEquals("Pick up: A\nDrop off: B\n1.0 mi · 4 min to the pickup · trip 2.0 mi · 7 min\nAdds about 0.5 mi · 3 min to your route", busy);
    }

    @Test
    public void whatTheDriverEarnsIsShownOnlyWhenTheServerWorkedItOut() {
        assertEquals("Pay: $1.33", JobText.earnLine("1.33"));
        assertEquals("", JobText.earnLine(""));
        assertEquals("", JobText.earnLine(null));
        assertEquals("", JobText.earnLine("null"));   // a JSON null read as text
        String withPay = JobText.confirmMessage("A", "B", 1.0, 4, 2.0, 7, "1.33", false, 0, 0);
        assertEquals("Pick up: A\nDrop off: B\n1.0 mi · 4 min to the pickup · trip 2.0 mi · 7 min\nPay: $1.33", withPay);
        String busyWithPay = JobText.confirmMessage("A", "B", 1.0, 4, 2.0, 7, "0.99", true, 0.5, 3);
        assertEquals("Pick up: A\nDrop off: B\n1.0 mi · 4 min to the pickup · trip 2.0 mi · 7 min\nPay: $0.99\nAdds about 0.5 mi · 3 min to your route", busyWithPay);
    }

    @Test
    public void theStatusLineSaysWhatThereIsToTakeOrWhyNot() {
        assertEquals("Your office has not turned on picking order requests yourself.", JobText.statusLine(false, 0, 0, 3, "Your office has not turned on picking order requests yourself."));
        assertEquals("No order requests are waiting near you right now.", JobText.statusLine(true, 0, 0, 3, ""));
        assertEquals("1 order request you can take. Tap the pin, or Accept below.", JobText.statusLine(true, 1, 0, 3, ""));
        assertEquals("2 order requests you can take on top of your orders. Tap the pin, or Accept below.", JobText.statusLine(true, 2, 1, 3, ""));
        assertEquals("2 order requests are waiting, but you already hold the most orders (3).", JobText.statusLine(false, 2, 3, 3, ""));
    }

}
