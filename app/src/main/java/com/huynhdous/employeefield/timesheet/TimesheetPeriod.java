package com.huynhdous.employeefield.timesheet;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** Combines complete weekly answers; never presents partial history as a period total. */
final class TimesheetPeriod {
    static JSONObject combine(List<JSONObject> sheets) throws Exception {
        if (sheets.isEmpty()) throw new IllegalArgumentException("No weeks");
        int total = 0, approved = 0, pending = 0;
        boolean accepted = true;
        int lockedWeeks = 0;
        JSONArray days = new JSONArray();
        for (JSONObject sheet : sheets) {
            int minutes = sheet.getInt("total_minutes");
            int approvedMinutes = sheet.optInt("approved_minutes", 0);
            total += minutes; approved += approvedMinutes;
            pending += sheet.optInt("pending_minutes", minutes - approvedMinutes);
            accepted &= "accepted".equals(sheet.optString("submission_status"));
            if ("accepted".equals(sheet.optString("submission_status"))) lockedWeeks++;
            JSONArray weekDays = sheet.getJSONArray("days");
            for (int i = 0; i < weekDays.length(); i++) {
                JSONObject day = new JSONObject(weekDays.getJSONObject(i).toString());
                day.put("locked", "accepted".equals(sheet.optString("submission_status"))); days.put(day);
            }
        }
        return new JSONObject().put("week_start", sheets.get(0).getString("week_start"))
                .put("week_end", sheets.get(sheets.size() - 1).getString("week_end"))
                .put("total_minutes", total).put("approved_minutes", approved).put("pending_minutes", pending)
                .put("submission_status", accepted ? "accepted" : "pending").put("days", days)
                .put("locked_weeks", lockedWeeks).put("week_count", sheets.size());
    }
}
