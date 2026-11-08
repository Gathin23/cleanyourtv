package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

final class ElppaUi {
  static final int TEXT = 0xFFF4F2EF,
      MUTED = 0xFFCDCBC8,
      SURFACE = 0xFF191D25,
      BACKGROUND = 0xFF090B10;

  private ElppaUi() {}

  static TextView text(Context c, float size) {
    TextView v = new TextView(c);
    v.setTextColor(TEXT);
    v.setTypeface(UiFont.get(c, R.string.font_regular));
    v.setTextSize(size);
    v.setIncludeFontPadding(false);
    return v;
  }

  static GradientDrawable shape(int colour, float radius, boolean focus) {
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(colour);
    bg.setCornerRadius(radius);
    if (focus) bg.setStroke(2, TEXT);
    return bg;
  }

  static void focus(View view, boolean on, boolean motion, float radius) {
    view.setBackground(shape(on ? 0xCC353943 : 0x7722262E, radius, on));
    view.animate().cancel();
    float scale = on ? 1.035f : 1f;
    if (motion && ValueAnimator.areAnimatorsEnabled())
      view.animate().scaleX(scale).scaleY(scale).setDuration(130).start();
    else {
      view.setScaleX(scale);
      view.setScaleY(scale);
    }
  }

  static TextView button(Context c, String text) {
    TextView v = text(c, 15);
    v.setText(text);
    v.setGravity(Gravity.CENTER);
    v.setFocusable(true);
    v.setClickable(true);
    v.setId(View.generateViewId());
    v.setBackground(shape(0x77343840, 24, false));
    return v;
  }

  static void type(TextView view, float logical, float scale) {
    float font = view.getResources().getConfiguration().fontScale;
    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, logical * scale * Math.min(font, 1.5f));
  }
}
