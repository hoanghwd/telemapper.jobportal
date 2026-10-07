package com.huynhdous.employeefield.location;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

final class LocationQueue extends SQLiteOpenHelper {
    LocationQueue(Context context) {
        super(context, "employee_locations.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE points (sample_id TEXT PRIMARY KEY,employee_id INTEGER NOT NULL,captured_ms INTEGER NOT NULL,payload TEXT NOT NULL)");
        db.execSQL("CREATE INDEX points_owner_time ON points(employee_id,captured_ms)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported location database version");
    }

    void add(long employee, JSONObject point) throws Exception {
        ContentValues row = new ContentValues();
        row.put("sample_id", point.getString("sample_id"));
        row.put("employee_id", employee);
        row.put("captured_ms", point.getLong("captured_ms"));
        row.put("payload", point.toString());
        getWritableDatabase().insertOrThrow("points", null, row);
    }

    JSONArray batch(long employee) throws Exception {
        getWritableDatabase().delete("points", "captured_ms < ?", new String[]{String.valueOf(System.currentTimeMillis() - 7L * 86400000)});
        JSONArray points = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT payload FROM points WHERE employee_id=? ORDER BY captured_ms LIMIT 100", new String[]{String.valueOf(employee)})) {
            while (c.moveToNext()) points.put(new JSONObject(c.getString(0)));
        }
        return points;
    }

    void acknowledge(long employee, JSONArray ids) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < ids.length(); i++)
                db.delete("points", "employee_id=? AND sample_id=?", new String[]{String.valueOf(employee), ids.getString(i)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    int count(long employee) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM points WHERE employee_id=?", new String[]{String.valueOf(employee)})) {
            c.moveToFirst();
            return c.getInt(0);
        }
    }
}
