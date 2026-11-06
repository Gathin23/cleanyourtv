package io.github.luyzfernando08.sleeptimer;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.time.LocalDate;
import java.util.Locale;

/** Numeric wheels controlled entirely by D-pad; never opens the keyboard. */
final class RemotePicker extends Dialog {
    interface Result { void accept(int[] values); }
    private final int[] values, limits, labels;
    private final Result result;
    private final String title;
    private final boolean date;
    private LinearLayout[] wheels;
    private TextView[] numbers;
    private TextView save;
    static void time(Activity a, String title, int minute, Result result) {
        new RemotePicker(a,title,new int[]{minute/60,minute%60},new int[]{24,60},
                new int[]{R.string.remote_hours,R.string.remote_minutes},false,result).show();
    }
    static void date(Activity a, String title, LocalDate date, Result result) {
        new RemotePicker(a,title,new int[]{date.getDayOfMonth(),date.getMonthValue(),date.getYear()},new int[]{31,12,2100},
                new int[]{R.string.remote_day,R.string.remote_month,R.string.remote_year},true,result).show();
    }
    private RemotePicker(Activity a,String title,int[] values,int[] limits,int[] labels,boolean date,Result result) {
        super(a); this.title=title; this.values=values; this.limits=limits; this.labels=labels; this.date=date; this.result=result;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(getContext());root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(32),dp(24),dp(32),dp(24));root.setBackground(Ui.pill(getContext(),0xff142332));
        TextView heading=Ui.text(getContext(),26,Color.WHITE);heading.setText(title);root.addView(heading);
        TextView hint=Ui.text(getContext(),15,0xff9eb4c8);hint.setText(R.string.remote_hint);hint.setPadding(0,dp(8),0,dp(20));root.addView(hint);
        LinearLayout body=new LinearLayout(getContext());body.setGravity(Gravity.CENTER_VERTICAL);root.addView(body);
        LinearLayout fields=new LinearLayout(getContext());fields.setGravity(Gravity.CENTER);
        body.addView(fields,new LinearLayout.LayoutParams(0,dp(156),1));
        wheels=new LinearLayout[values.length];
        numbers=new TextView[values.length];
        for(int i=0;i<values.length;i++) {
            final int index=i;
            LinearLayout wheel=new LinearLayout(getContext());wheels[i]=wheel;
            wheel.setOrientation(LinearLayout.VERTICAL);wheel.setGravity(Gravity.CENTER);
            wheel.setFocusable(true);wheel.setClickable(true);
            wheel.setPadding(dp(8),dp(10),dp(8),dp(10));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(156),1);lp.setMargins(dp(6),0,dp(6),0);fields.addView(wheel,lp);
            TextView caption=part(wheel,17,24,0xff9eb4c8);caption.setText(labels[i]);
            TextView upArrow=part(wheel,20,24,Color.WHITE);upArrow.setText("\u25b2");
            TextView number=part(wheel,42,58,Color.WHITE);numbers[i]=number;
            TextView downArrow=part(wheel,20,24,Color.WHITE);downArrow.setText("\u25bc");
            wheel.setOnFocusChangeListener((v,focused)->{
                wheel.setBackground(Ui.pill(getContext(),focused?0xff245b67:0xff1c3043));
                int color=focused?0xff8cf0d3:Color.WHITE;
                upArrow.setTextColor(color);number.setTextColor(color);downArrow.setTextColor(color);
                caption.setTextColor(focused?0xffb8e8e3:0xff9eb4c8);
            });
            wheel.setBackground(Ui.pill(getContext(),0xff1c3043));
            wheel.setOnClickListener(v->advance(index));
            wheel.setOnKeyListener((v,key,event)->{
                boolean up=key==KeyEvent.KEYCODE_DPAD_UP, down=key==KeyEvent.KEYCODE_DPAD_DOWN;
                if(up||down) {
                    if(event.getAction()==KeyEvent.ACTION_DOWN) change(index,(up?1:-1)*(event.getRepeatCount()>5?5:1));
                    return true;
                }
                if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if(event.getAction()==KeyEvent.ACTION_DOWN) {
                        int next=index+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);
                        if(next>=values.length)save.requestFocus();else wheels[Math.max(0,next)].requestFocus();
                    }
                    return true;
                }
                return false;
            });
        }
        LinearLayout actions=new LinearLayout(getContext());actions.setOrientation(LinearLayout.VERTICAL);actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(dp(180),dp(156));actionLp.leftMargin=dp(16);body.addView(actions,actionLp);
        TextView cancel=Ui.row(actions,180);cancel.setText(R.string.sched_17);cancel.setOnClickListener(v->dismiss());
        save=Ui.row(actions,180);save.setText(R.string.sched_10);save.setGravity(Gravity.CENTER);save.setOnClickListener(v->{result.accept(values.clone());dismiss();});
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnKeyListener((v,key,event)->{
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_DOWN) {
                if(event.getAction()==KeyEvent.ACTION_DOWN) {if(key==KeyEvent.KEYCODE_DPAD_LEFT)wheels[wheels.length-1].requestFocus();else save.requestFocus();}
                return true;
            }return false;
        });
        save.setOnKeyListener((v,key,event)->{
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_UP) {
                if(event.getAction()==KeyEvent.ACTION_DOWN) {if(key==KeyEvent.KEYCODE_DPAD_LEFT)wheels[wheels.length-1].requestFocus();else cancel.requestFocus();}
                return true;
            }return false;
        });
        refresh();setContentView(root);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setLayout(dp(720),WindowManager.LayoutParams.WRAP_CONTENT);
        wheels[0].requestFocus();
    }
    private int dp(int n){return Ui.dp(getContext(),n);}
    private void advance(int i){if(i+1<wheels.length)wheels[i+1].requestFocus();else save.requestFocus();}
    private void change(int i,int delta) {
        if(date) {
            if(i==2) values[i]=Math.max(2020,Math.min(2100,values[i]+delta));
            else {int max=i==0?LocalDate.of(values[2],values[1],1).lengthOfMonth():12; values[i]=Math.floorMod(values[i]-1+delta,max)+1;}
            values[0]=Math.min(values[0],LocalDate.of(values[2],values[1],1).lengthOfMonth());
        } else values[i]=Math.floorMod(values[i]+delta,limits[i]);
        refresh();
    }
    private TextView part(LinearLayout parent,int size,int height,int color) {
        TextView view=Ui.text(getContext(),size,color);
        view.setGravity(Gravity.CENTER);view.setIncludeFontPadding(false);
        parent.addView(view,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(height)));
        return view;
    }
    private void refresh() {
        for(int i=0;i<values.length;i++) numbers[i].setText(String.format(Locale.getDefault(),"%02d",values[i]));
    }
}
