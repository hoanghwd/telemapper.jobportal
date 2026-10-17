package com.huynhdous.employeefield.inbox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Employee-scoped inbox state. A server offer ID is also its delivery deduplication key. */
public final class InboxState {
    public static final class Item {
        public final String id, title, body;
        public String mapJson, businessName = "";
        public boolean messageRead, notificationRead;
        public String requestHeading() { return businessName.trim().isEmpty() ? "Order Requests" : businessName.trim()+" - Order Request"; }
        /** Brief delivery preview using the exact route and driver pay already in the offer. */
        public String deliveryPreview() {
            StringBuilder preview = new StringBuilder();
            String[][] fields = {{"Drop off:", "Drop-off:"}, {"Total distance:", "Distance:"}, {"Offer Amount:", "Offer:"}};
            for (String[] field : fields) {
                for (String line : body.split("\r?\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith(field[0])) continue;
                    String value = trimmed.substring(field[0].length()).trim();
                    if (!value.isEmpty()) {
                        if (preview.length() > 0) preview.append('\n');
                        preview.append(field[1]).append(' ').append(value);
                    }
                    break;
                }
            }
            return preview.toString();
        }
        public Item(String id, String title, String body) { this(id,title,body,""); }
        public Item(String id, String title, String body, String mapJson) {
            if (id == null || id.trim().isEmpty() || title == null || body == null)
                throw new IllegalArgumentException("A work offer needs an ID, title and body.");
            this.id = id; this.title = title; this.body = body; this.mapJson = mapJson == null ? "" : mapJson;
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
