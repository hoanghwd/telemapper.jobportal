package com.huynhdous.employeefield.auth;

import android.app.Activity;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.huynhdous.employeefield.core.ui.Insets;
import com.huynhdous.employeefield.core.ui.Theme;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/** Shown right after sign-in when the office gave the employee a temporary password: it must be replaced before anything else. */
public final class ChangePasswordScreen {
    private final AuthHost host;
    private final Activity activity;

    private ChangePasswordScreen(AuthHost host) {
        this.host = host;
        this.activity = host.activity();
    }

    public static void show(AuthHost host) {
        new ChangePasswordScreen(host).build();
    }

    private LinearLayout screen(String title, String description) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * activity.getResources().getDisplayMetrics().density);
        panel.setPadding(pad, pad * 2, pad, pad);
        scroll.setBackgroundColor(Theme.BACKGROUND);
        scroll.setFillViewport(true);
        scroll.addView(panel);
        activity.setContentView(scroll);
        Insets.apply(scroll);
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextSize(26);
        heading.setTextColor(Theme.TEXT_PRIMARY);
        panel.addView(heading);
        TextView desc = new TextView(activity);
        desc.setText(description);
        desc.setTextSize(16);
        desc.setPadding(0, pad, 0, pad);
        panel.addView(desc);
        return panel;
    }

    private EditText passwordField(LinearLayout panel, String title) {
        TextView label = new TextView(activity);
        label.setText(title);
        panel.addView(label);
        EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        // Apply last: setSingleLine can replace the password transformation.
        field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        field.setSaveEnabled(false);
        field.setHint(title);
        Theme.styleInput(field);
        panel.addView(field);
        return field;
    }

    private void build() {
        LinearLayout panel = screen("Change your password", "You must replace your temporary password before accessing your employee account. Use 12–72 characters.");
        EditText next = passwordField(panel, "New password"), confirm = passwordField(panel, "Confirm new password");
        Button save = new Button(activity);
        save.setText("Save password and sign in");
        panel.addView(save);
        save.setOnClickListener(v -> {
            if (host.isBusy()) return;
            String value = next.getText().toString();
            if (value.getBytes(StandardCharsets.UTF_8).length < 12 || value.getBytes(StandardCharsets.UTF_8).length > 72) {
                next.setError("Use 12–72 characters (up to 72 UTF-8 bytes).");
                return;
            }
            if (!value.equals(confirm.getText().toString())) {
                confirm.setError("Passwords do not match.");
                return;
            }
            JSONObject body = new JSONObject();
            try {
                body.put("token", host.token());
                body.put("password", value);
                body.put("confirmation", confirm.getText().toString());
            } catch (Exception e) {
                return;
            }
            next.getText().clear();
            confirm.getText().clear();
            host.request("change-password", body, save, r -> host.passwordChanged(r.getString("token")));
        });
        Button cancel = new Button(activity);
        cancel.setText("Back to sign in");
        panel.addView(cancel);
        cancel.setOnClickListener(v -> {
            if (!host.isBusy()) host.signOut();
        });
    }
}
