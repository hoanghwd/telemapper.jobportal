package com.huynhdous.employeefield.inbox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Employee-scoped inbox state. A server offer ID is also its delivery deduplication key. */
public final class InboxState {
    public static final class Item {
        public final String id, title, body;
        public boolean messageRead, notificationRead;
        public Item(String id, String title, String body) {
            if (id == null || id.trim().isEmpty() || title == null || body == null)
                throw new IllegalArgumentException("A work offer needs an ID, title and body.");
            this.id = id; this.title = title; this.body = body;
        }
    }
    private final Map<String, Item> items = new LinkedHashMap<>();
    public boolean receive(Item item) {
        if (items.containsKey(item.id)) return false;
        items.put(item.id, item);
        return true;
    }
    public List<Item> items() { return new ArrayList<>(items.values()); }
    public int unread(boolean messages) {
        int count = 0;
        for (Item item : items.values()) if (!(messages ? item.messageRead : item.notificationRead)) count++;
        return count;
    }
    public void read(String id, boolean messages) {
        Item item = items.get(id);
        if (item == null) return;
        if (messages) item.messageRead = true; else item.notificationRead = true;
    }
}
