package com.huynhdous.employeefield.programs;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.tab.TabModule;
import com.huynhdous.employeefield.core.ui.Theme;

import org.json.JSONObject;

/**
 * D2D's "what am I selling right now" screen -- the program a manager assigned under Employee Management > Program Assignment on the web,
 * shown only while the program itself is still active and unexpired (an expired program isn't "current" just because it's the latest
 * assignment). D2D's equivalent of the S2S "My Events" screen.
 */
public final class ProgramsTab extends TabModule {
    private LinearLayout container;

    @Override
    public String title() {
        return "Programs";
    }

    @Override
    public int iconRes() {
        return R.drawable.ic_tab_events;
    }

    /** D2D reps only: S2S reps have no program to sell. */
    @Override
    public boolean isAvailableFor(String programCode) {
        return "D2D".equals(programCode);
    }

    @Override
    public void buildContent(LinearLayout content) {
        float density = density();
        LinearLayout headerRow = new LinearLayout(context());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(headerRow);
        TextView heading = new TextView(context());
        heading.setText("My Programs");
        heading.setTextSize(18);
        heading.setTypeface(heading.getTypeface(), android.graphics.Typeface.BOLD);
        heading.setTextColor(Theme.TEXT_PRIMARY);
        headerRow.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout refresh = Theme.iconTextButton(context(), R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        headerRow.addView(refresh);
        refresh.setOnClickListener(v -> load());

        container = new LinearLayout(context());
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.topMargin = (int) (12 * density);
        content.addView(container, containerParams);
    }

    @Override
    public void onShown() {
        load();
    }

    @Override
    public void onDetach() {
        container = null;
    }

    private void load() {
        if (container == null) return;
        try {
            host().request("telemapper/program/my-program", new JSONObject().put("token", host().token()), null,
                    r -> render(r.isNull("program") ? null : r.getJSONObject("program")));
        } catch (Exception ignored) {
        }
    }

    private void render(JSONObject program) throws Exception {
        if (container == null) return;
        container.removeAllViews();
        if (program == null) {
            TextView empty = new TextView(context());
            empty.setText("No program assigned right now.");
            empty.setTextSize(13);
            empty.setTextColor(Theme.TEXT_SECONDARY);
            container.addView(empty);
            return;
        }
        float density = density();

        LinearLayout card = new LinearLayout(context());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
        card.setBackground(Theme.cardBackground(context()));
        container.addView(card);

        TextView nameText = new TextView(context());
        nameText.setText(program.getString("name"));
        nameText.setTextSize(15);
        nameText.setTypeface(nameText.getTypeface(), android.graphics.Typeface.BOLD);
        nameText.setTextColor(Theme.TEXT_PRIMARY);
        card.addView(nameText);

        String valueLine = program.optString("program_value", "");
        if (!program.isNull("duration_months") && program.optInt("duration_months", 0) > 0) {
            valueLine += " · " + program.optInt("duration_months") + " months";
        }
        TextView valueText = new TextView(context());
        valueText.setText(valueLine);
        valueText.setTextSize(13);
        valueText.setTextColor(Theme.PRIMARY);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = (int) (2 * density);
        card.addView(valueText, valueParams);

        if (!program.isNull("discount_type") && !program.isNull("discount_value")) {
            double discountValue = program.optDouble("discount_value", 0);
            String discountText = "dollar".equals(program.optString("discount_type", ""))
                    ? "$" + (discountValue == Math.floor(discountValue) ? String.valueOf((int) discountValue) : String.valueOf(discountValue)) + " off"
                    : (discountValue == Math.floor(discountValue) ? String.valueOf((int) discountValue) : String.valueOf(discountValue)) + "% off";
            TextView discountLabel = new TextView(context());
            discountLabel.setText(discountText);
            discountLabel.setTextSize(13);
            discountLabel.setTextColor(Theme.SUCCESS);
            LinearLayout.LayoutParams discountParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            discountParams.topMargin = (int) (4 * density);
            card.addView(discountLabel, discountParams);
        }

        String terms = program.optString("offer_description", "");
        if (!terms.isEmpty()) {
            TextView termsText = new TextView(context());
            termsText.setText(terms);
            termsText.setTextSize(13);
            termsText.setTextColor(Theme.TEXT_PRIMARY);
            LinearLayout.LayoutParams termsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            termsParams.topMargin = (int) (8 * density);
            card.addView(termsText, termsParams);
        }

        TextView expiresText = new TextView(context());
        if (program.isNull("expires_date")) {
            expiresText.setText("No expiration");
        } else {
            java.time.LocalDate expires = java.time.LocalDate.parse(program.getString("expires_date"));
            expiresText.setText("Expires " + java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy").format(expires));
        }
        expiresText.setTextSize(12);
        expiresText.setTextColor(Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams expiresParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        expiresParams.topMargin = (int) (8 * density);
        card.addView(expiresText, expiresParams);
    }
}
