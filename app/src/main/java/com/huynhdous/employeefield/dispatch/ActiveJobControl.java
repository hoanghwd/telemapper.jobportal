package com.huynhdous.employeefield.dispatch;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.ui.Theme;
import org.json.JSONObject;

/** The assigned service job, restored from the server on sign-in or Refresh. */
public final class ActiveJobControl {
    public final LinearLayout view;
    private final AppHost host;
    private final Session session;
    private final Runnable changed;
    private long readVersion;
    private boolean updating;

    public ActiveJobControl(AppHost host, Session session, Runnable changed) {
        this.host=host; this.session=session; this.changed=changed;
        view=new LinearLayout(host.activity()); view.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*host.activity().getResources().getDisplayMetrics().density);
        view.setPadding(pad,pad,pad,pad); view.setBackground(Theme.cardBackground(host.activity()));
        view.setVisibility(View.GONE); refresh();
    }
    public void refresh() {
        if(updating)return;
        long version=++readVersion;
        try { host.request("dispatch/status",new JSONObject().put("token",session.token),null,response->{
            if(version!=readVersion)return;
            render(response.optBoolean("dispatch_enabled")?response.optJSONObject("active_job"):null);
        },true,null); } catch(Exception e) { throw new IllegalStateException(e); }
    }
    static String nextStage(String status) {
        switch(status) {
            case "accepted": return "on_the_way";
            case "on_the_way": return "arrived";
            case "arrived": return "started";
            case "started": return "completed";
            default: return "";
        }
    }
    private static String actionLabel(String stage) {
        switch(stage) {
            case "on_the_way": return "I'm on the way";
            case "arrived": return "I've arrived";
            case "started": return "Start work";
            case "completed": return "Complete job";
            default: return "";
        }
    }
    private void text(String value,int size) {
        TextView text=new TextView(host.activity());text.setText(value);text.setTextSize(size);
        text.setTextColor(Theme.TEXT_PRIMARY);text.setPadding(0,4,0,8);view.addView(text);
    }
    private void render(JSONObject job) {
        view.removeAllViews();view.setVisibility(job==null?View.GONE:View.VISIBLE);
        if(job==null)return;
        text("Current service job",18);text(job.optString("title"),18);
        text(job.optString("body"),14);text("Status: "+job.optString("status_label",job.optString("status")),14);
        String next=nextStage(job.optString("status"));
        if(!next.isEmpty()) {
            Button advance=Theme.filledButton(host.activity(),actionLabel(next),Theme.PRIMARY);
            view.addView(advance);
            advance.setOnClickListener(v->{
                if(next.equals("completed"))new AlertDialog.Builder(host.activity()).setTitle("Complete job?")
                    .setMessage("Confirm the work is finished.").setPositiveButton("Complete",(dialog,which)->advance(job,next,advance))
                    .setNegativeButton("Cancel",null).show();
                else advance(job,next,advance);
            });
        }
        Button refresh=Theme.filledButton(host.activity(),"Refresh job",Theme.NEUTRAL);
        view.addView(refresh);refresh.setOnClickListener(v->refresh());
    }
    private void advance(JSONObject job,String stage,Button button) {
        updating=true;
        ++readVersion; // Ignore reads started before this mutation.
        try { host.request("dispatch/progress",new JSONObject().put("token",session.token)
            .put("offer_id",job.getString("id")).put("stage",stage),button,response->{
                updating=false;++readVersion;render(response.optJSONObject("job"));changed.run();
                if("completed".equals(response.getJSONObject("job").optString("status")))
                    new AlertDialog.Builder(host.activity()).setMessage("Job completed. You can receive another offer when Ready.").setPositiveButton("OK",null).show();
            },false,(code,message)->{updating=false;if(code==409||code==403)refresh();return false;});
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
}
