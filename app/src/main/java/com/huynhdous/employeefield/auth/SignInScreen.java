package com.huynhdous.employeefield.auth;

import android.app.Activity;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.ui.Insets;
import com.huynhdous.employeefield.core.ui.Popup;

import org.json.JSONObject;

/** The sign-in form (username + password). The answer goes back through {@link AuthHost#signedIn}. */
public final class SignInScreen {
    private final AuthHost host;
    private final Activity activity;
    private EditText email, password;

    private SignInScreen(AuthHost host) {
        this.host = host;
        this.activity = host.activity();
    }

    public static void show(AuthHost host) {
        new SignInScreen(host).build();
    }

    private void build() {
        activity.setContentView(R.layout.screen_sign_in);
        Insets.apply(activity.findViewById(R.id.root));
        email = activity.findViewById(R.id.email);
        password = activity.findViewById(R.id.password);
        ((CheckBox) activity.findViewById(R.id.show_password)).setOnCheckedChangeListener((button, checked) -> {
            int cursor = password.getSelectionEnd();
            password.setTransformationMethod(checked ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
            password.setSelection(Math.max(0, Math.min(cursor, password.length())));
        });
        activity.findViewById(R.id.sign_in).setTag(activity.findViewById(R.id.sign_in_progress));
        activity.findViewById(R.id.sign_in).setOnClickListener(v -> login());
        password.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) {
                login();
                return true;
            }
            return false;
        });
        activity.findViewById(R.id.help).setOnClickListener(v -> new Popup.Builder(activity).setTitle(R.string.help).setMessage(R.string.help_message).setPositiveButton(R.string.ok, null).show());
    }

    private void login() {
        if (host.isBusy()) return;
        email.setError(null);
        password.setError(null);
        if (email.getText().toString().trim().isEmpty()) {
            email.setError(activity.getString(R.string.email_error));
            return;
        }
        if (password.length() == 0) {
            password.setError(activity.getString(R.string.password_error));
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("username", email.getText().toString().trim());
            body.put("password", password.getText().toString());
        } catch (Exception e) {
            return;
        }
        password.getText().clear();
        host.request("login", body, (Button) activity.findViewById(R.id.sign_in), r -> host.signedIn(r.getString("token"), r.getBoolean("must_change_password")));
    }
}
