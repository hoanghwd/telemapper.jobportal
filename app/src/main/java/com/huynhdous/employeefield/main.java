package com.huynhdous.employeefield;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.huynhdous.employeefield.app.App;

/** The app's entrance. It only hands what Android tells it to {@link App}, which wires everything together. */
public final class main extends Activity {
    private App app;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        app = new App(this, state);
        app.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        app.resume();
    }

    @Override
    protected void onPause() {
        app.pause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        app.saveState(out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        app.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        app.onPermissionResult(requestCode, permissions, grants);
    }

    @Override
    protected void onDestroy() {
        app.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!app.onBack()) super.onBackPressed();
    }
}
