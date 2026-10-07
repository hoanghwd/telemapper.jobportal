package com.huynhdous.employeefield.doors;

import com.huynhdous.employeefield.core.ui.Theme;

/** The outcomes a door can end with: their codes (as the server knows them), the names the rep sees and the badge colours. */
final class DoorStatus {
    /** Offered in this order wherever the rep picks an outcome. */
    static final String[] CODES = {"sold", "not_home", "already_serviced", "not_interested", "not_owner", "callback", "do_not_call", "other"};
    static final String[] LABELS = {"Sold", "Not Home", "Already Had Service", "Not Interested", "Not the Owner", "Come Back Another Time", "Do Not Call", "Other"};
    /** Outcomes that close a house: recording another one at the same address needs a written reason. */
    static final java.util.Set<String> CLOSING = new java.util.HashSet<>(java.util.Arrays.asList("sold", "do_not_call", "already_serviced"));

    private DoorStatus() {
    }

    static String label(String status) {
        switch (status) {
            case "sold": return "Sold";
            case "already_serviced": return "Already Had Service";
            case "not_interested": return "Not Interested";
            case "not_owner": return "Not the Owner";
            case "callback": return "Come Back Another Time";
            case "do_not_call": return "Do Not Call";
            case "not_home": return "Not Home";
            default: return "Other";
        }
    }

    static int color(String status) {
        switch (status) {
            case "sold": return Theme.SUCCESS;
            case "already_serviced": return 0xff3b82f6;
            case "not_interested": return 0xffef4444;
            case "not_owner": return 0xfff97316;
            case "callback": return 0xffa855f7;
            case "do_not_call": return 0xff111827;
            default: return Theme.NEUTRAL;
        }
    }
}
