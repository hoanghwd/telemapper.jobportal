package com.huynhdous.employeefield.dispatch;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.ui.Popup;
import com.huynhdous.employeefield.core.ui.Theme;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The orders the worker holds, restored from the server on sign-in or Refresh. One order looks as it always did; with several (an extra order
 * he accepted) each order has its own icon controls and a numbered "Suggested route" lists the stops in the order that avoids zig-zagging.
 */
public final class ActiveJobControl {
    public final LinearLayout view;
    private final AppHost host;
    private final Session session;
    private final Runnable changed;
    private long readVersion;
    private boolean updating;
    private JSONArray jobs=new JSONArray();
    private JSONObject route;
    private DeliveryPhoto deliveryPhoto;

    public ActiveJobControl(AppHost host, Session session, Runnable changed) {
        this.host=host; this.session=session; this.changed=changed;
        view=new LinearLayout(host.activity()); view.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*host.activity().getResources().getDisplayMetrics().density);
        view.setPadding(pad,pad,pad,pad); view.setBackground(Theme.cardBackground(host.activity()));
        view.setVisibility(View.GONE); refresh();
    }
    /** The screen that takes the delivery photo; without it "Delivered" just asks to confirm (as in the tests and on an older home screen). */
    public void setDeliveryPhoto(DeliveryPhoto photo) { this.deliveryPhoto=photo; }
    public void refresh() {
        if(updating)return;
        long version=++readVersion;
        try { host.request("dispatch/status",new JSONObject().put("token",session.token),null,response->{
            if(version!=readVersion)return;
            applyStatus(response);
        },true,null); } catch(Exception e) { throw new IllegalStateException(e); }
    }
    /** All his orders (active_jobs) and the suggested route; an older server reply with just active_job counts as one order. */
    private void applyStatus(JSONObject response) {
        JSONArray list=new JSONArray();
        if(response.optBoolean("dispatch_enabled")) {
            JSONArray all=response.optJSONArray("active_jobs");
            if(all!=null) list=all;
            else { JSONObject one=response.optJSONObject("active_job"); if(one!=null) list.put(one); }
        }
        jobs=list; route=response.optJSONObject("route"); render();
    }
    static String nextStage(String status,boolean delivery) {
        switch(status) {
            case "accepted": return "on_the_way";
            case "on_the_way": return delivery?"started":"arrived";
            case "arrived": return "started";
            case "started": return "completed";
            default: return "";
        }
    }
    /** The caption under the stage button. A delivery goes to the pickup, then the drop-off, so its stages are named for that. */
    static String actionLabel(String stage,boolean delivery) {
        switch(stage) {
            case "on_the_way": return delivery?"To pickup":"On the way";
            case "arrived": return delivery?"At pickup":"Arrived";
            case "started": return delivery?"On Delivery":"Start";
            case "completed": return delivery?"Mark delivered":"Complete";
            default: return "";
        }
    }
    private TextView line(String value,int size,boolean bold,int color,int top,int bottom) {
        TextView text=new TextView(host.activity());text.setText(value);text.setTextSize(size);
        text.setTextColor(color);text.setPadding(0,top,0,bottom);
        if(bold) text.setTypeface(text.getTypeface(),Typeface.BOLD);
        return text;
    }
    private void text(String value,int size) { view.addView(line(value,size,false,Theme.TEXT_PRIMARY,4,8)); }
    private static int stageIcon(String stage,boolean delivery) {
        switch(stage) {
            case "on_the_way": return R.drawable.ic_job_on_the_way;
            case "arrived": return R.drawable.ic_job_arrived;
            case "started": return R.drawable.ic_job_start;
            default: return delivery?R.drawable.ic_delivery_package:R.drawable.ic_job_complete;
        }
    }
    private int dp(int value) { return Math.round(value*host.activity().getResources().getDisplayMetrics().density); }
    /** One round icon button with a small caption under it; the main action is filled, the others are tinted. */
    private ImageButton action(LinearLayout row,int icon,int color,boolean solid,String label) {
        LinearLayout item=new LinearLayout(host.activity());item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageButton button=Theme.iconButton(host.activity(),icon,solid?0xffffffff:color,label);
        if(solid) { GradientDrawable fill=new GradientDrawable();fill.setShape(GradientDrawable.OVAL);fill.setColor(color);button.setBackground(fill); }
        button.setTooltipText(label);
        item.addView(button,new LinearLayout.LayoutParams(dp(56),dp(56)));
        TextView caption=new TextView(host.activity());caption.setText(label);caption.setTextSize(11);caption.setTextColor(Theme.TEXT_SECONDARY);
        caption.setGravity(Gravity.CENTER);caption.setPadding(0,dp(8),0,dp(4));
        caption.setSingleLine(false);caption.setMaxLines(Integer.MAX_VALUE);caption.setEllipsize(null);
        item.addView(caption,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f);
        params.setMargins(dp(4),dp(8),dp(4),0);row.addView(item,params);
        return button;
    }
    private LinearLayout actionRow() {
        LinearLayout actions=new LinearLayout(host.activity());actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.TOP);
        actions.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        view.addView(actions);
        return actions;
    }
    private void render() {
        view.removeAllViews();view.setVisibility(jobs.length()==0?View.GONE:View.VISIBLE);
        if(jobs.length()==0)return;
        boolean several=jobs.length()>1;
        text(several?"Your delivery requests ("+jobs.length()+")":"Current delivery request",18);
        if(several) renderRoute();
        for(int i=0;i<jobs.length();i++) {
            JSONObject job=jobs.optJSONObject(i);
            if(job==null) continue;
            if(several) {
                View divider=new View(host.activity());divider.setBackgroundColor(Theme.OUTLINE);
                LinearLayout.LayoutParams dividerParams=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(1));dividerParams.setMargins(0,dp(12),0,dp(4));
                view.addView(divider,dividerParams);
            }
            renderJob(job,several);
        }
        if(several) refreshRow();
    }
    private void renderJob(JSONObject job,boolean several) {
        TextView title=line(job.optString("title"),several?16:18,several,Theme.TEXT_PRIMARY,4,several?0:8);
        view.addView(title);
        TextView body=line(job.optString("body"),14,false,Theme.TEXT_PRIMARY,4,8);
        body.setLineSpacing(dp(5),1f); // the drop off and pick up lines read as separate items
        view.addView(body);
        text("Status: "+job.optString("status_label",job.optString("status")),14);
        // Tapping the order shows everything the server knows about it (addresses, distance, ETA, pay, customer, notes).
        View.OnClickListener showDetails=v->new Popup.Builder(host.activity()).setTitle(job.optString("title"))
            .setMessage(job.optString("details",job.optString("body"))).setPositiveButton("Close",null).show();
        title.setOnClickListener(showDetails);body.setOnClickListener(showDetails);
        view.addView(line("Tap the order for details",12,false,Theme.TEXT_SECONDARY,0,4));
        LinearLayout actions=actionRow();
        String next=nextStage(job.optString("status"),job.optBoolean("delivery"));
        if(!next.isEmpty()) {
            ImageButton advance=action(actions,stageIcon(next,job.optBoolean("delivery")),Theme.PRIMARY,true,actionLabel(next,job.optBoolean("delivery")));
            advance.setOnClickListener(v->{
                if(next.equals("completed")&&job.optBoolean("delivery")&&deliveryPhoto!=null)new Popup.Builder(host.activity()).setTitle("Delivered?")
                    .setMessage("Take a photo of the order where you left it. The shop sees it once the office approves it.")
                    .setPositiveButton("Take photo",(dialog,which)->deliveryPhoto.deliver(job.optString("id")))
                    .setNeutralButton("No photo",(dialog,which)->advance(job,next,advance))
                    .setNegativeButton("Cancel",null).show();
                else if(next.equals("completed"))new AlertDialog.Builder(host.activity()).setTitle(job.optBoolean("delivery")?"Delivered?":"Complete job?")
                    .setMessage(job.optBoolean("delivery")?"Confirm the order was handed to the customer.":"Confirm the work is finished.").setPositiveButton(job.optBoolean("delivery")?"Delivered":"Complete",(dialog,which)->advance(job,next,advance))
                    .setNegativeButton("Cancel",null).show();
                else advance(job,next,advance);
            });
        }
        String status=job.optString("status");
        // A driver can hand a job back until the work starts; the dispatcher then offers it to the next driver.
        if("accepted".equals(status)||"on_the_way".equals(status)||"arrived".equals(status)) {
            ImageButton giveUp=action(actions,R.drawable.ic_job_cancel,Theme.ERROR,false,"Cancel job");
            giveUp.setOnClickListener(v->new Popup.Builder(host.activity()).setTitle("Cancel this job?")
                .setMessage("It will be offered to another driver, and you won't be offered this job again for a while. You can't cancel once the work has started.")
                .setPositiveButton("Cancel job",(dialog,which)->release(job,giveUp)).setNegativeButton("Keep job",null).show());
        }
        if(!several) action(actions,R.drawable.ic_refresh,Theme.NEUTRAL,false,"Refresh").setOnClickListener(v->refresh());
    }
    private void refreshRow() {
        action(actionRow(),R.drawable.ic_refresh,Theme.NEUTRAL,false,"Refresh").setOnClickListener(v->refresh());
    }
    /** What the route's miles and minutes are: along the roads (typical driving, no live traffic) or, when the server could not route, straight lines. */
    static String routeNote(String source) {
        return "road".equals(source) ? "by road, no live traffic" : "straight lines, an estimate";
    }
    /** The numbered stops, in the order the server worked out to avoid zig-zagging, with each leg's distance and the total. */
    private void renderRoute() {
        JSONArray stops=route==null?null:route.optJSONArray("stops");
        if(stops==null||stops.length()==0) return;
        view.addView(line("Suggested route",15,true,Theme.PRIMARY,4,2));
        view.addView(line("About "+route.optString("miles")+" mi · "+route.optString("minutes")+" min · "+stops.length()+" stops ("+routeNote(route.optString("source"))+")",12,false,Theme.TEXT_SECONDARY,0,8));
        for(int i=0;i<stops.length();i++) {
            JSONObject stop=stops.optJSONObject(i);
            if(stop==null) continue;
            LinearLayout row=new LinearLayout(host.activity());row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,dp(4),0,dp(4));
            TextView number=new TextView(host.activity());number.setText(String.valueOf(i+1));number.setTextSize(12);number.setTextColor(0xffffffff);number.setGravity(Gravity.CENTER);
            number.setTypeface(number.getTypeface(),Typeface.BOLD);
            GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor("pickup".equals(stop.optString("type"))?Theme.PRIMARY:Theme.SUCCESS);number.setBackground(circle);
            row.addView(number,new LinearLayout.LayoutParams(dp(26),dp(26)));
            LinearLayout words=new LinearLayout(host.activity());words.setOrientation(LinearLayout.VERTICAL);
            words.addView(line(("pickup".equals(stop.optString("type"))?"Pick up · ":"Deliver · ")+stop.optString("title"),14,true,Theme.TEXT_PRIMARY,0,0));
            words.addView(line(stop.optString("address")+" · "+stop.optString("miles")+" mi",12,false,Theme.TEXT_SECONDARY,0,0));
            LinearLayout.LayoutParams wordsParams=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f);wordsParams.leftMargin=dp(10);
            row.addView(words,wordsParams);
            view.addView(row);
        }
    }
    /** Takes a finished or given-up order out of the list; true when he still holds more than one order (so the route needs the server's new answer). */
    private boolean drop(String offerId) {
        JSONArray kept=new JSONArray();
        for(int i=0;i<jobs.length();i++) { JSONObject job=jobs.optJSONObject(i); if(job!=null&&!offerId.equals(job.optString("id"))) kept.put(job); }
        boolean moreLeft=jobs.length()>1;
        jobs=kept;
        if(kept.length()<2) route=null;
        return moreLeft;
    }
    /** Replaces one order with its newer state from the server. */
    private void update(JSONObject job) {
        for(int i=0;i<jobs.length();i++) {
            JSONObject current=jobs.optJSONObject(i);
            if(current!=null&&current.optString("id").equals(job.optString("id"))) { try { jobs.put(i,job); } catch(Exception e) { throw new IllegalStateException(e); } return; }
        }
    }
    private void release(JSONObject job,ImageButton button) {
        updating=true;button.setEnabled(false);
        ++readVersion; // Ignore reads started before this mutation.
        try { host.request("dispatch/release",new JSONObject().put("token",session.token).put("offer_id",job.getString("id")),null,response->{
                updating=false;++readVersion;boolean moreLeft=drop(job.optString("id"));render();changed.run();
                new Popup.Builder(host.activity()).setTitle("Job cancelled")
                    .setMessage("It will be offered to another driver. You can receive another offer when Ready.").setPositiveButton("OK",null).show();
                if(moreLeft) refresh();
            },false,(code,message)->{updating=false;button.setEnabled(true);if(code==409||code==403)refresh();return false;});
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    private void advance(JSONObject job,String stage,ImageButton button) {
        updating=true;button.setEnabled(false);
        ++readVersion; // Ignore reads started before this mutation.
        try { host.request("dispatch/progress",new JSONObject().put("token",session.token)
            .put("offer_id",job.getString("id")).put("stage",stage),null,response->{
                updating=false;++readVersion;
                JSONObject updated=response.getJSONObject("job");
                boolean completed="completed".equals(updated.optString("status")),moreLeft=false;
                if(completed) moreLeft=drop(updated.optString("id")); else update(updated);
                render();changed.run();
                if(completed) new AlertDialog.Builder(host.activity()).setMessage((updated.optBoolean("delivery")?"Delivery completed":"Job completed")+". You can receive another offer when Ready.").setPositiveButton("OK",null).show();
                if(moreLeft) refresh();
            },false,(code,message)->{updating=false;button.setEnabled(true);if(code==409||code==403)refresh();return false;});
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
}
