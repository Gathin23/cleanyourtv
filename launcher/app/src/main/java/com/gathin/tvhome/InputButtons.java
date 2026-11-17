package com.gathin.tvhome;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Big top-bar buttons (icon and name) for named HDMI devices (config.xml input_buttons, "match|icon|label" per entry, icon "tv" or
 * "gamepad"). A button opens the first input whose name (the one given in Settings > Inputs, or the CEC device's
 * own) contains "match". It is bright while the TV reports that input as connected and dimmed otherwise, so a
 * console that is off reads as off. Pressing a dimmed one still opens its input, which also wakes a CEC device.
 */
final class InputButtons {
    private static final float OFF_ALPHA = 0.35f;

    private static final class Entry {
        final String name, inputId;
        final int state;

        Entry(String name, String inputId, int state) {
            this.name = name;
            this.inputId = inputId;
            this.state = state;
        }
    }

    private final Context context;
    private final List<String> matches = new ArrayList<>();
    private final List<TextView> views = new ArrayList<>();
    private final String[] openIds;

    /** Adds one button per valid spec to {@code bar}, starting at {@code insertAt}. */
    InputButtons(Context context, ViewGroup bar, int insertAt, String[] specs,
                 Consumer<String> opener, View.OnKeyListener keys) {
        this.context = context;
        List<TextView> made = new ArrayList<>();
        float dp = context.getResources().getDisplayMetrics().density;
        for (String spec : specs) {
            String[] part = spec.split("\\|", -1);
            if (part.length < 3 || part[0].trim().isEmpty()) continue;
            TextView button = new TextView(context, null, 0, R.style.TopButton);
            button.setId(View.generateViewId());
            button.setText(part[2].trim());
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            button.setBackgroundResource(R.drawable.device_button_bg);
            button.setPadding(Math.round(26 * dp), 0, Math.round(30 * dp), 0);
            button.setCompoundDrawablePadding(Math.round(14 * dp));
            Drawable icon = context.getDrawable(
                    "gamepad".equalsIgnoreCase(part[1].trim()) ? R.drawable.ic_gamepad : R.drawable.ic_tv);
            icon.setBounds(0, 0, Math.round(36 * dp), Math.round(36 * dp));
            button.setCompoundDrawablesRelative(icon, null, null, null);
            // layout_* attributes of a style are not applied to a view made in code: size and margin here.
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(64 * dp));
            params.setMarginStart(Math.round(14 * dp));
            button.setLayoutParams(params);
            button.setNextFocusDownId(R.id.grid);
            button.setOnKeyListener(keys);
            matches.add(part[0].trim().toLowerCase(Locale.ROOT));
            made.add(button);
        }
        views.addAll(made);
        openIds = new String[made.size()];
        for (int i = 0; i < made.size(); i++) {
            final int index = i;
            TextView button = made.get(i);
            button.setOnClickListener(v -> {
                if (openIds[index] != null) opener.accept(openIds[index]);
            });
            bar.addView(button, insertAt + i);
        }
    }

    List<TextView> views() {
        return views;
    }

    /** Re-reads the inputs and sets each button's look; call on resume and whenever an input changes. */
    void refresh() {
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        List<Entry> entries = tim == null ? new ArrayList<>() : entries(tim);
        for (int i = 0; i < views.size(); i++) {
            Entry found = null;
            for (Entry e : entries) {
                if (e.name.contains(matches.get(i))) {
                    found = e;
                    break;
                }
            }
            openIds[i] = found == null ? null : found.inputId;
            boolean on = found != null && found.state == TvInputManager.INPUT_STATE_CONNECTED;
            views.get(i).setAlpha(on ? 1f : OFF_ALPHA);
        }
    }

    /** Every HDMI input as the Inputs dropdown names it: a CEC device by its own name, a bare port by its label. */
    private List<Entry> entries(TvInputManager tim) {
        List<Entry> out = new ArrayList<>();
        List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
        Set<String> ids = new HashSet<>();
        for (TvInputInfo info : inputs) ids.add(info.getId());
        Map<String, List<TvInputInfo>> devices = new HashMap<>();
        for (TvInputInfo info : inputs) {
            String parent = info.getParentId();
            if (parent == null || !ids.contains(parent) || !info.isPassthroughInput()) continue;
            devices.computeIfAbsent(parent, k -> new ArrayList<>()).add(info);
        }
        for (TvInputInfo info : inputs) {
            if (info.getType() != TvInputInfo.TYPE_HDMI || !info.isPassthroughInput()) continue;
            if (info.getParentId() != null && ids.contains(info.getParentId())) continue;
            String custom = text(InputNames.tidy(info.loadCustomLabel(context)));
            String label = text(info.loadLabel(context));
            List<TvInputInfo> onPort = devices.get(info.getId());
            if (onPort == null) {
                out.add(new Entry(lower(custom + " " + label), info.getId(), tim.getInputState(info.getId())));
                continue;
            }
            for (TvInputInfo device : onPort) {
                String own = text(InputNames.tidy(device.loadCustomLabel(context)));
                String deviceLabel = text(device.loadLabel(context));
                out.add(new Entry(lower(own + " " + custom + " " + deviceLabel + " " + label),
                        device.getId(), tim.getInputState(device.getId())));
            }
        }
        return out;
    }

    private static String text(CharSequence s) {
        return s == null ? "" : s.toString();
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
