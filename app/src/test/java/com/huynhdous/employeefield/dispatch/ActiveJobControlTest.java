package com.huynhdous.employeefield.dispatch;
import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.session.Session;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ActiveJobControlTest {
    final List<Object[]> requests=new ArrayList<>();int changes;
    ActiveJobControl control() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        Session session=new Session();session.token="fixture";session.employeeId=7;
        AppHost host=(AppHost)Proxy.newProxyInstance(AppHost.class.getClassLoader(),new Class[]{AppHost.class},(p,m,args)->{
            if(m.getName().equals("activity"))return activity;
            if(m.getName().equals("request"))requests.add(args);
            return null;
        });
        return new ActiveJobControl(host,session,()->changes++);
    }
    JSONObject job(String stage)throws Exception{return new JSONObject().put("id","1").put("title","Repair sink").put("body","Fixture address").put("status",stage).put("status_label",stage);}
    void reply(int i,JSONObject response)throws Exception {((AppHost.Result)requests.get(i)[3]).accept(response);}
    JSONObject status(String stage)throws Exception {return new JSONObject().put("dispatch_enabled",true).put("active_job",job(stage));}
    Button button(ActiveJobControl control,String text){for(int i=0;i<control.view.getChildCount();i++)if(control.view.getChildAt(i) instanceof Button){Button b=(Button)control.view.getChildAt(i);if(b.getText().toString().equals(text))return b;}throw new AssertionError(text);}
    @Test public void restoresAcceptedJobAndSendsOnlyNextStage()throws Exception {
        ActiveJobControl control=control();reply(0,status("accepted"));assertEquals(View.VISIBLE,control.view.getVisibility());
        button(control,"I'm on the way").performClick();
        assertEquals("dispatch/progress",requests.get(1)[0]);JSONObject sent=(JSONObject)requests.get(1)[1];
        assertEquals("on_the_way",sent.getString("stage"));assertEquals("1",sent.getString("offer_id"));assertEquals("fixture",sent.getString("token"));
        reply(1,new JSONObject().put("job",job("on_the_way")));assertNotNull(button(control,"I've arrived"));assertEquals(1,changes);
    }
    @Test public void completionRequiresConfirmationAndRefreshCanClearJob()throws Exception {
        ActiveJobControl control=control();reply(0,status("started"));button(control,"Complete job").performClick();assertEquals(1,requests.size());
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("completed",((JSONObject)requests.get(1)[1]).getString("stage"));
        reply(1,new JSONObject().put("job",job("completed")));assertEquals(1,changes);
        control.refresh();reply(2,new JSONObject().put("dispatch_enabled",true).put("active_job",JSONObject.NULL));assertEquals(View.GONE,control.view.getVisibility());
    }
    @Test public void staleReadCannotReplaceFreshStage()throws Exception {
        ActiveJobControl control=control();control.refresh();reply(1,status("arrived"));reply(0,status("accepted"));assertNotNull(button(control,"Start work"));
    }
    @Test public void conflictReloadsServerJob()throws Exception {
        ActiveJobControl control=control();reply(0,status("accepted"));button(control,"I'm on the way").performClick();
        ((AppHost.ProblemHandler)requests.get(1)[5]).handle(409,"Cancelled");assertEquals("dispatch/status",requests.get(2)[0]);
        reply(2,new JSONObject().put("dispatch_enabled",true));assertEquals(View.GONE,control.view.getVisibility());
    }
}
