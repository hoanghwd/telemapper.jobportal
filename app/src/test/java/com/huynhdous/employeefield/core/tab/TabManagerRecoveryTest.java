package com.huynhdous.employeefield.core.tab;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import com.huynhdous.employeefield.R;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TabManagerRecoveryTest {
    @Test public void repeatedRecreationBeforeAuthenticationPreservesDraftAndCameraAnswer() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppHost host = (AppHost) Proxy.newProxyInstance(AppHost.class.getClassLoader(), new Class[]{AppHost.class},
                (proxy, method, args) -> method.getName().equals("activity") ? activity : null);
        TabManager first = new TabManager(activity, host, new ScheduleGate());
        Bundle initial = new Bundle();
        initial.putInt("current_tab", Tabs.CHECK_IN);
        initial.putString("pending_selfie_path", "/pending/photo.jpg");
        first.setRestoredState(initial);
        first.detachAll(false); // Authentication is pending; recovery must remain owned.
        first.onActivityResult(66, Activity.RESULT_OK, new Intent().putExtra("camera_result", "saved"));
        Bundle next = new Bundle();
        first.saveState(next);
        next.putInt("current_tab", first.current());

        TabManager recreated = new TabManager(activity, host, new ScheduleGate());
        recreated.setRestoredState(next);
        Bundle again = new Bundle();
        recreated.saveState(again);
        assertEquals("/pending/photo.jpg", again.getString("pending_selfie_path"));
        assertEquals(Tabs.CHECK_IN, recreated.current());
        RecordingTab tab = new RecordingTab();
        recreated.mount(Tabs.CHECK_IN, tab, new LinearLayout(activity), 24);
        recreated.finishMounting();
        assertEquals("/pending/photo.jpg", tab.photo);
        assertEquals("saved", tab.result);
        assertTrue(tab.restoredBeforeResult);
        recreated.detachAll();
        Bundle signedOut = new Bundle();
        recreated.saveState(signedOut);
        assertFalse(signedOut.containsKey("pending_selfie_path"));
    }

    private static class RecordingTab extends TabModule {
        String photo, result;
        boolean restoredBeforeResult;
        public String title() { return "Check in"; }
        public int iconRes() { return R.drawable.ic_tab_checkin; }
        public void buildContent(LinearLayout content) { }
        public void onShown() { }
        public int[] requestCodes() { return new int[]{66}; }
        public void restoreState(Bundle state) { photo = state.getString("pending_selfie_path"); }
        public void onActivityResult(int code, int resultCode, Intent data) {
            restoredBeforeResult = photo != null;
            result = data.getStringExtra("camera_result");
        }
    }
}
