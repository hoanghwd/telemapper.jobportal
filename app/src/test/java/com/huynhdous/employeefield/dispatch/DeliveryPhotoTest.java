package com.huynhdous.employeefield.dispatch;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.ui.Popup;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

/** The delivery photo: camera permission, the camera answer, and finishing the delivery with or without the picture. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class DeliveryPhotoTest {
    final List<Object[]> requests=new ArrayList<>();
    final List<Object[]> permissionAsks=new ArrayList<>();
    final List<Object[]> cameraAsks=new ArrayList<>();
    int refreshes;
    Activity activity;

    AppHost host() {
        activity=Robolectric.buildActivity(Activity.class).setup().get();
        return (AppHost)Proxy.newProxyInstance(AppHost.class.getClassLoader(),new Class[]{AppHost.class},(p,m,args)->{
            switch(m.getName()) {
                case "activity": return activity;
                case "token": return "fixture";
                case "request": requests.add(args);return null;
                case "requestPermissions": permissionAsks.add(args);return null;
                case "startActivityForResult": cameraAsks.add(args);return null;
                case "refreshDispatch": refreshes++;return null;
                default: return null;
            }
        });
    }
    DeliveryPhoto photo() {
        DeliveryPhoto photo=new DeliveryPhoto() {
            @Override protected android.net.Uri outputUri(File file) { return android.net.Uri.fromFile(file); }   // no file provider in a unit test
        };
        photo.attach(host());
        return photo;
    }
    List<String> shown() {
        List<String> out=new ArrayList<>();
        Dialog dialog=ShadowDialog.getLatestDialog();
        if(dialog!=null)walk(dialog.getWindow().getDecorView(),out);
        return out;
    }
    private void walk(View view,List<String> out) {
        if(view instanceof TextView)out.add(((TextView)view).getText().toString());
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)walk(((ViewGroup)view).getChildAt(i),out);
    }
    void press(int which) {((Popup)ShadowDialog.getLatestDialog()).getButton(which).performClick();}

    @Test public void withoutTheCameraPermissionItAsksForItFirst() {
        DeliveryPhoto photo=photo();
        photo.deliver("41");
        assertEquals(1,permissionAsks.size());assertEquals(0,cameraAsks.size());
        assertEquals(DeliveryPhoto.REQUEST_CAMERA_PERMISSION,permissionAsks.get(0)[2]);
        assertArrayEquals(new String[]{android.Manifest.permission.CAMERA},(String[])permissionAsks.get(0)[1]);
    }
    @Test public void withThePermissionTheCameraOpensForThatPicture() {
        DeliveryPhoto photo=photo();
        Shadows.shadowOf(activity).grantPermissions(android.Manifest.permission.CAMERA);
        photo.deliver("41");
        assertEquals(0,permissionAsks.size());assertEquals(1,cameraAsks.size());
        Intent intent=(Intent)cameraAsks.get(0)[1];
        assertEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE,intent.getAction());
        assertEquals(DeliveryPhoto.REQUEST_TAKE_PHOTO,cameraAsks.get(0)[2]);
        assertNotNull(intent.getParcelableExtra(android.provider.MediaStore.EXTRA_OUTPUT));
    }
    @Test public void aRefusedPermissionOffersToFinishWithoutAPhoto() throws Exception {
        DeliveryPhoto photo=photo();
        photo.deliver("41");
        photo.onPermissionResult(DeliveryPhoto.REQUEST_CAMERA_PERMISSION,new String[]{android.Manifest.permission.CAMERA},new int[]{android.content.pm.PackageManager.PERMISSION_DENIED});
        assertTrue(shown().contains("Camera needed"));
        press(Popup.BUTTON_NEUTRAL);   // No photo
        assertEquals(1,requests.size());
        assertEquals("dispatch/progress",requests.get(0)[0]);
        JSONObject sent=(JSONObject)requests.get(0)[1];
        assertEquals("41",sent.getString("offer_id"));assertEquals("completed",sent.getString("stage"));assertEquals("fixture",sent.getString("token"));
    }
    @Test public void aPictureThatDidNotSaveCanBeRetakenOrSkipped() {
        DeliveryPhoto photo=photo();
        Shadows.shadowOf(activity).grantPermissions(android.Manifest.permission.CAMERA);
        photo.deliver("41");
        photo.onActivityResult(DeliveryPhoto.REQUEST_TAKE_PHOTO,Activity.RESULT_OK,null);   // the camera wrote nothing
        assertTrue(shown().contains("Photo not saved"));
        press(Popup.BUTTON_POSITIVE);   // Try again
        assertEquals(2,cameraAsks.size());
    }
    @Test public void aSavedPictureIsShownForApprovalBeforeItIsSent() throws Exception {
        DeliveryPhoto photo=photo();
        Shadows.shadowOf(activity).grantPermissions(android.Manifest.permission.CAMERA);
        photo.deliver("41");
        Intent intent=(Intent)cameraAsks.get(0)[1];
        assertNotNull(intent);
        // The camera app would now have written the picture into the file it was given; do the same.
        File[] cache=activity.getCacheDir().listFiles((dir,name)->name.startsWith("delivery_"));
        assertNotNull(cache);assertEquals(1,cache.length);
        java.nio.file.Files.write(cache[0].toPath(),new byte[]{1,2,3});
        photo.onActivityResult(DeliveryPhoto.REQUEST_TAKE_PHOTO,Activity.RESULT_OK,null);
        assertTrue(shown().contains("Delivery photo"));
        press(Popup.BUTTON_NEUTRAL);   // Retake
        assertEquals(2,cameraAsks.size());
        assertEquals(0,requests.size());   // nothing is sent or completed until the driver approves the picture
    }
    @Test public void finishingTheDeliveryUpdatesTheScreenAndOnlyOnce() throws Exception {
        DeliveryPhoto photo=photo();
        photo.deliver("41");
        photo.complete();
        assertEquals(1,requests.size());
        ((AppHost.Result)requests.get(0)[3]).accept(new JSONObject().put("job",new JSONObject().put("status","completed")));
        assertEquals(1,refreshes);
        assertTrue(shown().get(0).startsWith("Delivery completed"));
        photo.complete();   // nothing left to finish
        assertEquals(1,requests.size());
    }
    @Test public void answersForAnotherOrCancelledDeliveryAreIgnored() {
        DeliveryPhoto photo=photo();
        photo.onActivityResult(DeliveryPhoto.REQUEST_TAKE_PHOTO,Activity.RESULT_OK,null);
        photo.onPermissionResult(DeliveryPhoto.REQUEST_CAMERA_PERMISSION,new String[0],new int[0]);
        photo.complete();
        assertEquals(0,requests.size());assertNull(ShadowDialog.getLatestDialog());
    }
}
