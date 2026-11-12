package com.gathin.homedashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Activity light painted on the scene background, behind every card and the video. */
final class CameraGlow extends FrameLayout {
    private final float density;
    private boolean highlighted;
    private int colour;
    private Backdrop backdrop;
    CameraGlow(Context context){
        super(context);density=getResources().getDisplayMetrics().density;
        int inset=Math.round(8*density);setPadding(inset,inset,inset,inset);
    }
    void highlight(boolean on,int colour){
        if(highlighted==on&&this.colour==colour)return;highlighted=on;this.colour=colour;
        if(backdrop!=null)backdrop.invalidate();
    }

    /** Draw before all scene children so neighbouring cards mask the light naturally. */
    static final class Backdrop extends LinearLayout {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect bounds=new Rect();
        private final RectF edge=new RectF();
        private final Rect previous=new Rect();
        private final RectF[] areas=new RectF[8];
        private final Shader[] gradients=new Shader[8];
        private int previousColour;
        private CameraGlow camera;

        Backdrop(Context context){super(context);setWillNotDraw(false);}

        void attach(CameraGlow camera){this.camera=camera;camera.backdrop=this;invalidate();}

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(camera==null||!camera.highlighted||camera.getWidth()==0||camera.getHeight()==0)return;
            bounds.set(camera.getPaddingLeft(),camera.getPaddingTop(),
                    camera.getWidth()-camera.getPaddingRight(),camera.getHeight()-camera.getPaddingBottom());
            offsetDescendantRectToMyCoords(camera,bounds);
            float density=camera.density;
            if(!bounds.equals(previous)||previousColour!=camera.colour)prepareGradients();
            // Shared rectangular edges must cover whole pixels, avoiding hairline seams.
            paint.setAntiAlias(false);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
            for(int i=0;i<gradients.length;i++){paint.setShader(gradients[i]);canvas.drawRect(areas[i],paint);}
            paint.setShader(null);paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setColor(camera.colour);
            paint.setAlpha(210);paint.setStrokeWidth(density);
            edge.set(bounds);canvas.drawRoundRect(edge,18*density,18*density,paint);
        }

        private void prepareGradients(){
            previous.set(bounds);previousColour=camera.colour;
            float spread=96*camera.density,radius=Math.round(18*camera.density);
            float l=bounds.left,t=bounds.top,r=bounds.right,b=bounds.bottom;
            int[] colours=new int[17];float[] stops=new float[17];
            int[] cornerColours=new int[18];float[] cornerStops=new float[18];
            for(int i=0;i<colours.length;i++){
                float fraction=i/16f,distance=fraction*96;
                int alpha=(int)Math.round(150*Math.exp(-distance*distance/(2*34f*34f))*(1-fraction));
                colours[i]=Color.argb(alpha,Color.red(camera.colour),Color.green(camera.colour),Color.blue(camera.colour));
                stops[i]=fraction;cornerColours[i+1]=colours[i];cornerStops[i+1]=(radius+fraction*spread)/(radius+spread);
            }
            cornerColours[0]=colours[0];
            // Four straight gradients and four matching radial corners: no stroke bands,
            // bitmap blur, software rendering, or extra layer around the live video.
            areas[0]=new RectF(l+radius,t-spread,r-radius,t);
            gradients[0]=new LinearGradient(0,t,0,t-spread,colours,stops,Shader.TileMode.CLAMP);
            areas[1]=new RectF(l+radius,b,r-radius,b+spread);
            gradients[1]=new LinearGradient(0,b,0,b+spread,colours,stops,Shader.TileMode.CLAMP);
            areas[2]=new RectF(l-spread,t+radius,l,b-radius);
            gradients[2]=new LinearGradient(l,0,l-spread,0,colours,stops,Shader.TileMode.CLAMP);
            areas[3]=new RectF(r,t+radius,r+spread,b-radius);
            gradients[3]=new LinearGradient(r,0,r+spread,0,colours,stops,Shader.TileMode.CLAMP);
            areas[4]=new RectF(l-spread,t-spread,l+radius,t+radius);
            gradients[4]=new RadialGradient(l+radius,t+radius,radius+spread,cornerColours,cornerStops,Shader.TileMode.CLAMP);
            areas[5]=new RectF(r-radius,t-spread,r+spread,t+radius);
            gradients[5]=new RadialGradient(r-radius,t+radius,radius+spread,cornerColours,cornerStops,Shader.TileMode.CLAMP);
            areas[6]=new RectF(l-spread,b-radius,l+radius,b+spread);
            gradients[6]=new RadialGradient(l+radius,b-radius,radius+spread,cornerColours,cornerStops,Shader.TileMode.CLAMP);
            areas[7]=new RectF(r-radius,b-radius,r+spread,b+spread);
            gradients[7]=new RadialGradient(r-radius,b-radius,radius+spread,cornerColours,cornerStops,Shader.TileMode.CLAMP);
        }
    }
}
