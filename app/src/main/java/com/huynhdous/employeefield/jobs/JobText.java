package com.huynhdous.employeefield.jobs;

import java.util.Locale;

/** The words of the Jobs screen, kept apart from the views so they can be checked on their own. */
final class JobText {
    private JobText() {
    }

    /** "Bakery delivery sweethouse-TP202610150003" is shown as its order number, "TP202610150003"; any other title is left as it is. */
    static String orderNumber(String title) {
        if (title == null) return "";
        int dash = title.lastIndexOf('-');
        String tail = dash < 0 ? "" : title.substring(dash + 1);
        return !tail.isEmpty() && tail.matches("[A-Za-z0-9]+") ? tail : title;
    }

    /** Miles with one decimal under ten, whole miles from ten up. */
    static String miles(double value) {
        return value < 10 ? String.format(Locale.US, "%.1f", value) : String.valueOf(Math.round(value));
    }

    /** "6.6 mi · 21 min to the pickup · trip 6.1 mi · 19 min" (road values from the server); a time of 0 or less is left out (an older server sends none). */
    static String tripLine(double milesToPickup, int minutesToPickup, double tripMiles, int tripMinutes) {
        return miles(milesToPickup) + " mi" + (minutesToPickup > 0 ? " · " + minutesToPickup + " min" : "") + " to the pickup · trip "
                + miles(tripMiles) + " mi" + (tripMinutes > 0 ? " · " + tripMinutes + " min" : "");
    }

    /** What a job adds to a route he already has: "Adds about 2.1 mi · 6 min to your route". */
    static String extraLine(double extraMiles, int extraMinutes) {
        return "Adds about " + miles(extraMiles) + " mi · " + extraMinutes + " min to your route";
    }

    /** The question asked before a job is taken: both addresses, the distance and, when he already has orders, what it adds. */
    static String confirmMessage(String pickup, String dropoff, double milesToPickup, int minutesToPickup, double tripMiles, int tripMinutes, String pay, boolean holding, double extraMiles, int extraMinutes) {
        String text = "Pick up: " + pickup + "\nDrop off: " + dropoff + "\n" + tripLine(milesToPickup, minutesToPickup, tripMiles, tripMinutes);
        if (!earnLine(pay).isEmpty()) text += "\n" + earnLine(pay);
        return holding ? text + "\n" + extraLine(extraMiles, extraMinutes) : text;
    }

    /** "Pay: $1.33" from the pay the server worked out ("1.33"); empty when the job has none (an order without a quoted fee). */
    static String earnLine(String pay) {
        return pay == null || pay.isEmpty() || "null".equals(pay) ? "" : "Pay: $" + pay;
    }

    /** The line above the map: what is there to take, or why nothing is. */
    static String statusLine(boolean canTake, int jobs, int holding, int maxOrders, String message) {
        if (jobs == 0) return message == null || message.isEmpty() ? "No jobs are waiting near you right now." : message;
        if (!canTake) return jobs + (jobs == 1 ? " job is" : " jobs are") + " waiting, but you already hold the most orders (" + maxOrders + ").";
        return jobs + (jobs == 1 ? " job you can take" : " jobs you can take") + (holding > 0 ? " on top of your orders." : ".") + " Tap a red pin, or Take this job below.";
    }
}
