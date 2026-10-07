package com.huynhdous.employeefield.home;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.huynhdous.employeefield.checkin.CheckInTab;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.tab.ScheduleGate;
import com.huynhdous.employeefield.core.tab.TabManager;
import com.huynhdous.employeefield.core.tab.Tabs;
import com.huynhdous.employeefield.core.ui.Insets;
import com.huynhdous.employeefield.core.ui.Theme;
import com.huynhdous.employeefield.doors.DoorsTab;
import com.huynhdous.employeefield.events.EventsTab;
import com.huynhdous.employeefield.profile.ProfileTab;
import com.huynhdous.employeefield.programs.ProgramsTab;
import com.huynhdous.employeefield.schedule.ScheduleTab;
import com.huynhdous.employeefield.timesheet.TimesheetTab;
import com.huynhdous.employeefield.trip.TripTab;

/**
 * What the signed-in employee sees: the greeting card, the title of the open screen, the schedule banner, the feature tabs and the menu.
 * This is the one place that lists which tabs the app has and in which order the menu shows them.
 */
public final class HomeScreen {
    /** The menu, top to bottom. A tab only appears for the kinds of employee it is meant for (see {@code TabModule.isAvailableFor}). */
    private static final int[] MENU_ORDER = {Tabs.PROFILE, Tabs.SCHEDULE, Tabs.TRIP, Tabs.TIMESHEET, Tabs.EVENTS, Tabs.DOORS, Tabs.PROGRAMS, Tabs.CHECK_IN};

    private final Activity activity;
    private final Session session;
    private final ScheduleGate gate;
    private final TabManager tabs;
    private final AppHost host;
    private final Runnable signOut;
    private final Drawer drawer;

    private GreetingCard greeting;
    private GateBanner banner;
    private TextView title;
    private DoorsTab doors;

    public HomeScreen(Activity activity, Session session, ScheduleGate gate, TabManager tabs, AppHost host, Runnable signOut) {
        this.activity = activity;
        this.session = session;
        this.gate = gate;
        this.tabs = tabs;
        this.host = host;
        this.signOut = signOut;
        this.drawer = new Drawer(activity);
    }

    public void show(int firstTab) {
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (24 * density);
        LinearLayout panel = newPanel(pad);

        greeting = new GreetingCard(activity, session, drawer::open, signOut);
        LinearLayout.LayoutParams greetingParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        greetingParams.bottomMargin = (int) (8 * density);
        panel.addView(greeting.view, greetingParams);

        title = newTitleBar(density);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = (int) (10 * density);
        titleParams.bottomMargin = (int) (4 * density);
        panel.addView(title, titleParams);

        banner = new GateBanner(activity, () -> tabs.select(Tabs.SCHEDULE));
        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bannerParams.topMargin = (int) (6 * density);
        bannerParams.bottomMargin = (int) (4 * density);
        panel.addView(banner.view, bannerParams);

        tabs.setOnSelected(index -> {
            title.setText(tabs.titleOf(index));
            banner.update(gate, index == Tabs.SCHEDULE);
        });
        tabs.setAfterMenuPick(drawer::close);

        ScheduleTab schedule = new ScheduleTab();
        doors = new DoorsTab();
        tabs.mount(Tabs.SCHEDULE, schedule, panel, pad);
        tabs.mount(Tabs.TRIP, new TripTab(), panel, pad);
        tabs.mount(Tabs.TIMESHEET, new TimesheetTab(), panel, pad);
        tabs.mount(Tabs.PROFILE, new ProfileTab(), panel, pad);
        tabs.mount(Tabs.DOORS, doors, panel, pad);
        tabs.mount(Tabs.EVENTS, new EventsTab(), panel, pad);
        tabs.mount(Tabs.PROGRAMS, new ProgramsTab(), panel, pad);
        tabs.mount(Tabs.CHECK_IN, new CheckInTab(), panel, pad);

        tabs.select(firstTab);
        if (firstTab != Tabs.SCHEDULE) schedule.refreshGate();   // opening on another screen: still check the gate
        tabs.finishMounting();

        host.enableTracking();
        drawer.install(tabs.labels(session.programCode, MENU_ORDER));
    }

    /** The gate was locked or unlocked: dim or restore the screens and show or hide the banner. */
    public void applyGate() {
        tabs.dimGated();
        if (banner != null) banner.update(gate, tabs.current() == Tabs.SCHEDULE);
    }

    public void avatarChanged() {
        if (greeting != null) greeting.refreshAvatar();
    }

    /** The door-to-door tab, for a tap on a map position anywhere in the app. */
    public DoorsTab doors() {
        return doors;
    }

    /** Back closes the menu first. Returns true when it did. */
    public boolean onBack() {
        if (!drawer.isOpen()) return false;
        drawer.close();
        return true;
    }

    /** The empty, scrollable, padded column the home screen is built in. */
    private LinearLayout newPanel(int pad) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad, pad * 2, pad, pad);
        scroll.setBackgroundColor(Theme.BACKGROUND);
        scroll.setFillViewport(true);
        scroll.addView(panel);
        activity.setContentView(scroll);
        Insets.apply(scroll);
        return panel;
    }

    /** A persistent "what screen am I on" bar -- stays visible above whichever tab is showing. */
    private TextView newTitleBar(float density) {
        TextView bar = new TextView(activity);
        bar.setTextSize(15);
        bar.setTypeface(bar.getTypeface(), Typeface.BOLD);
        bar.setTextColor(Theme.PRIMARY);
        bar.setLetterSpacing(0.02f);
        int padH = (int) (14 * density), padV = (int) (10 * density);
        bar.setPadding(padH, padV, padH, padV);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(28, Color.red(Theme.PRIMARY), Color.green(Theme.PRIMARY), Color.blue(Theme.PRIMARY)));
        background.setCornerRadius(8 * density);
        bar.setBackground(background);
        return bar;
    }
}
