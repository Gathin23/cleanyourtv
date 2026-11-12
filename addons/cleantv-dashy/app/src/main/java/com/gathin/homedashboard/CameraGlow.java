package com.gathin.homedashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.FrameLayout;

/** Soft activity halo drawn behind the camera; the video itself is untouched. */
final class CameraGlow extends FrameLayout {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF edge=new RectF();
    private final float density;
    private boolean highlighted;
    private int colour;
    CameraGlow(Context context){
        super(context);density=getResources().getDisplayMetrics().density;
        int inset=Math.round(8*density);setPadding(inset,inset,inset,inset);setWillNotDraw(false);
    }
    void highlight(boolean on,int colour){
        if(highlighted==on&&this.colour==colour)return;highlighted=on;this.colour=colour;invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(!highlighted)return;
        edge.set(getPaddingLeft(),getPaddingTop(),getWidth()-getPaddingRight(),getHeight()-getPaddingBottom());
        paint.setStyle(Paint.Style.STROKE);paint.setColor(colour);
        for(int i=12;i>=1;i--){paint.setAlpha(6+(12-i)*3);paint.setStrokeWidth(i*1.3f*density);canvas.drawRoundRect(edge,18*density,18*density,paint);}
        paint.setAlpha(200);paint.setStrokeWidth(density);canvas.drawRoundRect(edge,18*density,18*density,paint);
    }
}
