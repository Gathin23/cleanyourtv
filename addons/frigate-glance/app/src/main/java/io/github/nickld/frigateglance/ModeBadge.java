package io.github.nickld.frigateglance;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;

/** The corner badge on the picture: a video camera on red while live video plays, stacked frames for snapshots. */
final class ModeBadge extends ImageView {
    private final GradientDrawable background = new GradientDrawable();

    /** size: the icon's height in dp, before padding. */
    ModeBadge(Context context, float size) {
        super(context);
        float dp = context.getResources().getDisplayMetrics().density;
        background.setCornerRadius(size * dp / 3f);
        setBackground(background);
        int pad = Math.round(size * dp / 5f);
        setPadding(pad * 2, pad, pad * 2, pad);
        setAdjustViewBounds(true);
        setMaxHeight(Math.round(size * dp) + 2 * pad);
        setLive(false);
    }

    void setLive(boolean live) {
        setImageResource(live ? R.drawable.ic_live : R.drawable.ic_frames);
        background.setColor(live ? 0xD9DC3C3C : 0x99000000);
        setContentDescription(live ? "Live video" : "Snapshots");
    }
}
