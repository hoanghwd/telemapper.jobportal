package com.huynhdous.employeefield.schedule;

import java.time.LocalDate;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Keeps each office's actual week days when joining historical schedules. */
final class SchedulePeriod {
    static JSONObject combine(List<JSONObject> schedules) throws Exception {
        if (schedules.isEmpty()) throw new IllegalArgumentException("No schedules");
        JSONArray days = new JSONArray(), assignments = new JSONArray();
        JSONObject lockedDays = new JSONObject();
        for (JSONObject schedule : schedules) {
            LocalDate end = LocalDate.parse(schedule.getString("week_end"));
            for (LocalDate day = LocalDate.parse(schedule.getString("week_start")); !day.isAfter(end); day = day.plusDays(1)) {
                days.put(day.toString()); lockedDays.put(day.toString(), schedule.optBoolean("accounting_locked"));
            }
            JSONArray rows = schedule.getJSONArray("assignments");
            for (int i=0;i<rows.length();i++) assignments.put(rows.getJSONObject(i));
        }
        return new JSONObject().put("employee",schedules.get(0).getJSONObject("employee"))
                .put("week_start",schedules.get(0).getString("week_start"))
                .put("week_end",schedules.get(schedules.size()-1).getString("week_end"))
                .put("assignments",assignments).put("period_days",days).put("locked_days", lockedDays);
    }
}
