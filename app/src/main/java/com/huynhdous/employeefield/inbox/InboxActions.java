package com.huynhdous.employeefield.inbox;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.ui.Theme;
import com.huynhdous.employeefield.main;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** Internal messages are reserved; eligible service work offers use notifications only. */
public final class InboxActions {
    private static final String CHANNEL = "work_offers";
    private final Activity activity;
    private final Context context;
    private final long employeeId;
    private final SharedPreferences preferences;
    private InboxState state = new InboxState();
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    public interface Answer { void respond(String id, String decision, Runnable done); }
    private Answer answer;
    public void setAnswer(Answer answer) { this.answer = answer; }
    private TextView messagesBadge, alertsBadge;
    private View messagesButton, alertsButton;

    public InboxActions(Context context, long employeeId) {
        this.context = context;
        this.activity = context instanceof Activity ? (Activity) context : null;
        this.employeeId = employeeId;
        preferences = context.getSharedPreferences("work_inbox_" + employeeId, Activity.MODE_PRIVATE);
        reload();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "New work offers", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Sound and alerts when a new work offer arrives");
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }

    public void setWorkOffersEnabled(boolean enabled) {
        preferences.edit().putBoolean("offers_enabled", enabled).commit();
        if (!enabled) {
            for (InboxState.Item item : state.items())
                context.getSystemService(NotificationManager.class).cancel(employeeId + ":" + item.id, 1);
        }
        reload();
        if (enabled) save();
        refresh();
    }

    private void reload() {
        state = new InboxState();
        if (!preferences.getBoolean("offers_enabled", false)) return;
        try {
            JSONArray saved = new JSONArray(preferences.getString("items", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject o = saved.getJSONObject(i);
                if (!o.getString("id").startsWith("offer:")) continue;
                InboxState.Item item = new InboxState.Item(o.getString("id"), o.getString("title"), o.getString("body"), o.optString("map", ""));
                item.messageRead = o.optBoolean("message_read");
                item.notificationRead = o.optBoolean("notification_read");
                state.receive(item);
            }
        } catch (Exception e) { android.util.Log.w("WorkInbox", "Could not restore inbox", e); }
    }

    public void close() { if (listener != null) preferences.unregisterOnSharedPreferenceChangeListener(listener); }

    public void attach(LinearLayout header) {
        listener = (prefs, key) -> { if ("items".equals(key) || "offers_enabled".equals(key) || "pending_offer".equals(key) || (key != null && key.startsWith("answer_"))) { reload(); refresh(); } };
        preferences.registerOnSharedPreferenceChangeListener(listener);
        header.addView(button(true), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams alertsParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        alertsParams.leftMargin = dp(8); // a gap so the round buttons do not touch
        header.addView(button(false), alertsParams);
        refresh();
    }

    private View button(boolean messages) {
        FrameLayout frame = new FrameLayout(activity);
        ImageButton icon = Theme.iconButton(activity, messages ? R.drawable.ic_inbox_message : R.drawable.ic_inbox_bell,
                Theme.NEUTRAL, messages ? "Messages" : "Notifications");
        frame.addView(icon, new FrameLayout.LayoutParams(dp(48), dp(48)));
        icon.setOnClickListener(v -> show(messages));
        TextView badge = new TextView(activity);
        badge.setTextColor(Color.WHITE); badge.setTextSize(10); badge.setTypeface(null, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER); badge.setPadding(dp(3), 0, dp(3), 0);
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable background = new GradientDrawable(); background.setColor(0xffd71920); background.setCornerRadius(dp(10));
        badge.setBackground(background);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(18), Gravity.TOP | Gravity.END);
        params.topMargin = dp(1); params.rightMargin = dp(1);
        badge.setMinWidth(dp(18)); frame.addView(badge, params);
        if (messages) { messagesBadge = badge; messagesButton = icon; }
        else { alertsBadge = badge; alertsButton = icon; }
        return frame;
    }

    /** The one offer the server says is open right now ("offer:ID"), or "" when there is none. Set on every poll, so a stale entry cannot be accepted. */
    public void setPendingOffer(String id) { preferences.edit().putString("pending_offer", id).apply(); }

    /** Call on the UI thread with an immutable server offer ID; retries never sound twice. */
    public void receiveWorkOffer(String id, String title, String body) { receiveWorkOffer(id,title,body,""); }
    public void receiveWorkOffer(String id, String title, String body, String mapJson) {
        if (!preferences.getBoolean("offers_enabled", false) || !id.startsWith("offer:")) return;
        synchronized (InboxActions.class) {
            reload();
            if (!state.receive(new InboxState.Item(id, title, body, mapJson))) {
                // Enrich an old cached offer silently; polling the same ID never rings again.
                for(InboxState.Item item:state.items()) if(item.id.equals(id) && !mapJson.isEmpty() && !mapJson.equals(item.mapJson)) {
                    item.mapJson=mapJson;save();break;
                }
                return;
            }
            save();
        }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) return;
        Intent intent = new Intent(context, main.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String preview = new InboxState.Item(id, title, body).deliveryPreview();
        String summary = preview.isEmpty() ? body : preview;
        android.app.Notification.Builder builder = new android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_inbox_bell).setContentTitle(title).setContentText(summary)
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true);
        if (preview.isEmpty()) builder.setStyle(new android.app.Notification.BigTextStyle().bigText(body));
        else {
            // Keep all the same offer fields together in the ringing banner and expanded notification.
            android.widget.RemoteViews content = new android.widget.RemoteViews(context.getPackageName(), R.layout.notification_work_offer);
            content.setTextViewText(R.id.notification_offer_title, title);
            content.setTextViewText(R.id.notification_offer_summary, summary);
            builder.setStyle(new android.app.Notification.DecoratedCustomViewStyle())
                    .setCustomHeadsUpContentView(content).setCustomBigContentView(content);
        }
        android.app.Notification notification = builder.build();
        manager.notify(employeeId + ":" + id, 1, notification);
    }

    private void show(boolean messages) {
        if (messages) {
            new AlertDialog.Builder(activity).setTitle("Messages").setMessage("No messages yet.").setPositiveButton("Close", null).show();
            return;
        }
        reload();
        // Only the offer that is open right now is listed; the ones that were answered, expired or withdrawn are not kept on screen.
        List<InboxState.Item> items = openOffers();
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle(messages ? "Messages" : "Notifications").setPositiveButton("Close", null);
        if (items.isEmpty()) dialog.setMessage(messages ? "No messages yet." : "No open offers right now.");
        else {
            CharSequence[] labels = new CharSequence[items.size()];
            for (int i = 0; i < items.size(); i++) {
                InboxState.Item item = items.get(i);
                String heading = (item.notificationRead ? "" : "● ") + item.title;
                String preview = item.deliveryPreview();
                android.text.SpannableString label = new android.text.SpannableString(
                        heading + (preview.isEmpty() ? "" : "\n" + preview));
                label.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, heading.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                labels[i] = label;
            }
            dialog.setItems(labels, (d, which) -> {
                InboxState.Item item = items.get(which);
                synchronized (InboxActions.class) { reload(); state.read(item.id, messages); save(); }
                refresh();
                context.getSystemService(NotificationManager.class).cancel(employeeId + ":" + item.id, 1);
                // Only the one offer the server still has open can be answered; older entries are history and say what became of them.
                boolean isOffer = item.id.startsWith("offer:");
                boolean open = isOffer && answer != null && !preferences.contains("answer_" + item.id) && item.id.equals(preferences.getString("pending_offer", ""));
                String saved = preferences.getString("answer_" + item.id, "");
                String note = !isOffer || open ? "" : "\n\n" + (saved.isEmpty() ? "This offer is no longer open." : "You " + saved.toLowerCase(java.util.Locale.ROOT) + " this offer.");
                AlertDialog.Builder detail = new AlertDialog.Builder(activity).setTitle(item.title).setMessage(item.body + note);
                android.webkit.WebView offerMap=OfferMap.create(activity,item.mapJson);
                if(offerMap!=null) {
                    android.widget.ScrollView scroll=new android.widget.ScrollView(activity);
                    LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
                    content.setPadding(dp(24),dp(8),dp(24),dp(8));
                    TextView description=new TextView(activity);description.setText(item.body+note);
                    description.setTextSize(17);description.setTextColor(Theme.TEXT_PRIMARY);description.setLineSpacing(dp(7),1f);
                    content.addView(description);
                    LinearLayout.LayoutParams mapParams=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(180));
                    mapParams.topMargin=dp(12);content.addView(offerMap,mapParams);scroll.addView(content);
                    detail.setMessage(null).setView(scroll);
                }
                if (open) {
                    detail.setPositiveButton("Accept", (choiceDialog, whichItem) -> answer.respond(item.id.substring(6), "accepted", () -> answered(item.id, "Accepted")));
                    detail.setNegativeButton("Decline", (choiceDialog, whichItem) -> answer.respond(item.id.substring(6), "declined", () -> answered(item.id, "Declined")));
                    detail.setNeutralButton("Close", null);
                } else detail.setPositiveButton("Close", null);
                AlertDialog shown = detail.show();
                if(offerMap!=null) shown.setOnDismissListener(dismissed->{offerMap.stopLoading();offerMap.destroy();});
                // Open the lines up a little: the order number, addresses, distance and ETA read as separate items.
                TextView text = shown.findViewById(android.R.id.message);
                if (text != null) text.setLineSpacing(dp(7), 1f);
            });
        }
        dialog.show();
    }

    /** Remove work alerts when the signed-in session ends; retain GPS service notifications. */
    public static void dismissAlerts(Activity activity) {
        NotificationManager manager = activity.getSystemService(NotificationManager.class);
        for (android.service.notification.StatusBarNotification alert : manager.getActiveNotifications()) {
            if (CHANNEL.equals(alert.getNotification().getChannelId())) manager.cancel(alert.getTag(), alert.getId());
        }
    }

    private void answered(String id, String status) {
        preferences.edit().putString("answer_" + id, status).apply();
        new AlertDialog.Builder(activity).setMessage("Work offer " + status.toLowerCase(java.util.Locale.ROOT) + ".").setPositiveButton("OK", null).show();
    }

    // Commit before sounding so a repeated background delivery cannot notify twice.
    @android.annotation.SuppressLint("ApplySharedPref")
    private void save() {
        JSONArray saved = new JSONArray();
        for (InboxState.Item item : state.items()) {
            try { saved.put(new JSONObject().put("id", item.id).put("title", item.title).put("body", item.body).put("map", item.mapJson)
                    .put("message_read", item.messageRead).put("notification_read", item.notificationRead)); }
            catch (Exception e) { throw new IllegalStateException("Could not save inbox", e); }
        }
        preferences.edit().putString("items", saved.toString()).commit();
    }
    /** The offers the server says are open and that he has not answered: at most the one in {@code pending_offer}. */
    private List<InboxState.Item> openOffers() {
        String open = preferences.getString("pending_offer", "");
        List<InboxState.Item> found = new java.util.ArrayList<>();
        for (InboxState.Item item : state.items())
            if (item.id.equals(open) && !preferences.contains("answer_" + item.id)) found.add(item);
        return found;
    }
    private void refresh() {
        int unread = 0;
        for (InboxState.Item item : openOffers()) if (!item.notificationRead) unread++;
        if (messagesBadge != null) badge(messagesBadge, messagesButton, "Messages", 0);
        if (alertsBadge != null) badge(alertsBadge, alertsButton, "Notifications", unread);
    }
    private void badge(TextView view, View button, String label, int count) {
        view.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        view.setText(count > 9 ? "9+" : Integer.toString(count));
        button.setContentDescription(label + ", " + count + " unread");
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
