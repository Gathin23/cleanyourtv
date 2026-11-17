package io.github.luyzfernando08.sleeptimer;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;
import java.time.LocalDate;
import java.util.*;

/** Remote-friendly list editor. Changes are applied only by Save. */
public class ScheduleActivity extends Activity {
    private boolean enabled;
    private List<ScheduleRules.Rule> rules;
    private List<ScheduleRules.Pause> pauses;
    private LinearLayout list;
    private String[] days;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        days=new String[]{getString(R.string.sched_23),getString(R.string.sched_24),getString(R.string.sched_25),getString(R.string.sched_26),getString(R.string.sched_27),getString(R.string.sched_28),getString(R.string.sched_29)};
        enabled=Schedule.on(this); rules=Schedule.rules(this); pauses=Schedule.pauses(this);
        if (state!=null) {
            enabled=state.getBoolean("enabled"); rules.clear(); pauses.clear();
            int[] masks=state.getIntArray("days"), times=state.getIntArray("times");
            boolean[] wakes=state.getBooleanArray("wakes");
            if (masks!=null) for(int i=0;i<masks.length;i++) rules.add(new ScheduleRules.Rule(masks[i],times[i],wakes[i]));
            String[] ranges=state.getStringArray("ranges");
            if(ranges!=null) for(int i=0;i<ranges.length;i+=2) pauses.add(new ScheduleRules.Pause(LocalDate.parse(ranges[i]),LocalDate.parse(ranges[i+1])));
        }
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("enabled",enabled);
        int[] masks=new int[rules.size()], times=new int[rules.size()]; boolean[] wakes=new boolean[rules.size()];
        for(int i=0;i<rules.size();i++){ masks[i]=rules.get(i).days; times[i]=rules.get(i).minute; wakes[i]=rules.get(i).wake; }
        out.putIntArray("days",masks); out.putIntArray("times",times); out.putBooleanArray("wakes",wakes);
        String[] ranges=new String[pauses.size()*2];
        for(int i=0;i<pauses.size();i++){ ranges[2*i]=pauses.get(i).start.toString(); ranges[2*i+1]=pauses.get(i).end.toString(); }
        out.putStringArray("ranges",ranges);
    }
    private int focusIndex;
    private void render() {
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff0b1420);
        int pad=Ui.dp(this,28);root.setPadding(pad,pad,pad,pad);
        TextView title=Ui.text(this,30,Color.WHITE);title.setText(R.string.sched_0);root.addView(title);
        TextView hint=Ui.text(this,15,0xff9eb4c8);hint.setText(R.string.remote_page_hint);hint.setPadding(0,Ui.dp(this,6),0,Ui.dp(this,12));root.addView(hint);
        TextView toggle=Ui.row(root,880);toggle.setText(enabled?R.string.sched_2:R.string.sched_3);
        toggle.setTextColor(enabled?0xff8cf0d3:0xffffc878);toggle.setOnClickListener(v->{enabled=!enabled;focusIndex=0;render();});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        section(R.string.remote_events);
        for(ScheduleRules.Rule r:rules) {
            TextView row=button(label(r),()->edit(r));row.setTextColor(r.wake?0xff8cf0d3:0xffbcb5ff);
        }
        button(getString(R.string.sched_4),()->edit(null));
        section(R.string.remote_exclusions);
        for(ScheduleRules.Pause p:pauses) button(getString(R.string.sched_5)+p.start+"  -  "+p.end,()->{
            new AlertDialog.Builder(this).setTitle(getString(R.string.sched_6)).setItems(new String[]{getString(R.string.sched_7),getString(R.string.sched_8)},(d,n)->{
                if(n==1){pauses.remove(p);render();}else dateRange(p);
            }).show();
        });
        button(getString(R.string.sched_9),()->dateRange(null));
        TextView save=Ui.row(root,880);save.setText(R.string.sched_10);save.setGravity(android.view.Gravity.CENTER);save.setTextColor(0xff8cf0d3);
        save.setOnClickListener(v->{Schedule.save(this,enabled,rules,pauses);finish();});
        setContentView(root);
        if(focusIndex<=0||focusIndex>list.getChildCount()||!list.getChildAt(focusIndex-1).requestFocus())toggle.requestFocus();
    }
    private void section(int text) {
        TextView heading=Ui.text(this,14,0xff91a6bd);heading.setText(text);heading.setPadding(Ui.dp(this,8),Ui.dp(this,18),0,0);list.addView(heading);
    }
    private TextView button(String text,Runnable action) {
        TextView b=Ui.row(list,880);b.setText(text);b.setTextSize(19);
        b.setOnClickListener(v->{focusIndex=list.indexOfChild(b)+1;action.run();});return b;
    }
    private String label(ScheduleRules.Rule r) {
        StringBuilder s=new StringBuilder(String.format(Locale.getDefault(),"%02d:%02d",r.minute/60,r.minute%60));
        s.append("    ").append(getString(r.wake?R.string.sched_14:R.string.sched_15)).append("\n");
        for(int i=0;i<7;i++)if((r.days&(1<<i))!=0)s.append(shortDay(i)).append("  ");
        return s.toString();
    }
    private String shortDay(int index) {
        Locale locale=getResources().getConfiguration().getLocales().get(0);
        String name=java.time.DayOfWeek.of(index+1).getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE,locale);
        return name.substring(0,1).toUpperCase(locale)+name.substring(1);
    }
    private void edit(ScheduleRules.Rule old) {
        new AlertDialog.Builder(this).setTitle(old==null?getString(R.string.sched_13):label(old))
            .setItems(old==null?new String[]{getString(R.string.sched_14),getString(R.string.sched_15)}:new String[]{getString(R.string.sched_14),getString(R.string.sched_15),getString(R.string.sched_8)},(d,n)->{
                if(n==2){rules.remove(old);render();return;}
                ScheduleRules.Rule draft=new ScheduleRules.Rule(old==null?31:old.days,old==null?480:old.minute,n==0);
                boolean[] checked=new boolean[7];for(int i=0;i<7;i++)checked[i]=(draft.days&(1<<i))!=0;
                AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.sched_16)
                    .setMultiChoiceItems(days,checked,(x,i,on)->{if(on)draft.days|=1<<i;else draft.days&=~(1<<i);})
                    .setNegativeButton(R.string.sched_17,null).setPositiveButton(R.string.sched_18,null).create();
                dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                    if(draft.days==0){Toast.makeText(this,R.string.sched_19,Toast.LENGTH_SHORT).show();return;}
                    dialog.dismiss();
                    RemotePicker.time(this,getString(draft.wake?R.string.remote_wake_time:R.string.remote_sleep_time),draft.minute,values->{
                        draft.minute=values[0]*60+values[1];
                        if(old!=null)rules.set(rules.indexOf(old),draft);else rules.add(draft);
                        render();
                    });
                }));dialog.show();
            }).show();
    }
    private void dateRange(ScheduleRules.Pause old) {
        RemotePicker.date(this,getString(R.string.sched_22),old==null?LocalDate.now():old.start,first->{
            LocalDate from=LocalDate.of(first[2],first[1],first[0]);
            RemotePicker.date(this,getString(R.string.sched_21),old==null?from:old.end,last->{
                LocalDate to=LocalDate.of(last[2],last[1],last[0]);
                if(to.isBefore(from)){Toast.makeText(this,R.string.sched_20,Toast.LENGTH_LONG).show();return;}
                ScheduleRules.Pause range=new ScheduleRules.Pause(from,to);
                if(old!=null)pauses.set(pauses.indexOf(old),range);else pauses.add(range);
                render();
            });
        });
    }
}
