package com.gathin.homedashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Camera selection and feed editing stay on-TV; credentials are pushed privately. */
public class SettingsActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout list;
    @Override public void onCreate(Bundle b){super.onCreate(b);if(getIntent().getBooleanExtra("preview",false))startActivity(new Intent(this,DashboardActivity.class).putExtra("demo",getIntent().getBooleanExtra("demo",false)));}
    @Override public void onResume(){super.onResume();build();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void build(){
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(48),dp(28),dp(48),dp(28));list.setBackgroundColor(0xff070c13);
        TextView title=new TextView(this);title.setText("Home Dashboard");title.setTextSize(30);title.setTextColor(0xfff2f6fa);list.addView(title);
        TextView intro=new TextView(this);intro.setText("Cameras · Battery SOC · Solar and load power · RSS news");intro.setTextColor(0xff90a5ba);intro.setTextSize(16);list.addView(intro);
        try{
            Config c=Config.load(this);
            button("Open dashboard",()->startActivity(new Intent(this,DashboardActivity.class)));
            button(c.cameras.isEmpty()?"Cameras: all Frigate cameras (automatic)":"Cameras: "+c.cameras.size()+" selected",()->chooseCameras(c));
            button("Camera rotation: "+c.cameraSeconds+" seconds",()->new AlertDialog.Builder(this).setTitle("Seconds per camera").setItems(new String[]{"10 seconds","15 seconds","30 seconds","60 seconds"},(d,i)->{getSharedPreferences("choices",MODE_PRIVATE).edit().putInt("cameraSeconds",new int[]{10,15,30,60}[i]).apply();build();}).show());
            button("RSS feeds: "+c.feeds.size()+" configured",()->feeds(c));
            button("Person alert focus: "+(c.activityFirst?"On":"Off"),()->{
                if(!c.activityFirst&&c.mqttUrl.isEmpty()){toast("Configure the MQTT broker in your private setup file first");return;}
                getSharedPreferences("choices",MODE_PRIVATE).edit().putBoolean("activityFirst",!c.activityFirst).apply();build();
            });
            TextView hint=new TextView(this);hint.setText("In screensaver mode, any remote key returns to the previous app. The dashboard only reads your sensors and cameras. Server connections are configured from your private setup file.");hint.setTextColor(0xff90a5ba);hint.setTextSize(15);list.addView(hint);
        }catch(Exception e){TextView hint=new TextView(this);hint.setText("Connect your private configuration with setup.ps1 first. No credentials are shown or entered here.");hint.setTextColor(0xff90a5ba);list.addView(hint);}
        button("Show demonstration (sample values)",()->startActivity(new Intent(this,DashboardActivity.class).putExtra("demo",true)));
        ScrollView scroll=new ScrollView(this);scroll.addView(list);setContentView(scroll);
        if(list.getChildCount()>2)list.getChildAt(2).requestFocus();
    }
    private void button(String title,Runnable click){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(0xfff2f6fa);b.setOnClickListener(v->click.run());
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable focus=new android.graphics.drawable.GradientDrawable();focus.setColor(0xff203647);focus.setCornerRadius(dp(12));focus.setStroke(dp(2),0xff70d8b0);
        android.graphics.drawable.GradientDrawable normal=new android.graphics.drawable.GradientDrawable();normal.setColor(0xff111b28);normal.setCornerRadius(dp(12));
        states.addState(new int[]{android.R.attr.state_focused},focus);states.addState(new int[]{},normal);b.setBackground(states);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(6);list.addView(b,p);
    }
    private void chooseCameras(Config c){
        Toast.makeText(this,"Reading Frigate cameras…",Toast.LENGTH_SHORT).show();
        worker.execute(()->{try{
            List<String> names=new Api(c).cameras();
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(names.isEmpty()){toast("No enabled cameras found");return;}
                boolean[] checked=new boolean[names.size()];for(int i=0;i<checked.length;i++)checked[i]=c.cameras.isEmpty()||c.cameras.contains(names.get(i));
                AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Choose cameras")
                    .setMultiChoiceItems(names.toArray(new String[0]),checked,(d,i,on)->checked[i]=on)
                    .setNeutralButton("All (automatic)",(d,w)->{save("cameras",new JSONArray());build();})
                    .setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
                dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
                    List<String> picked=new ArrayList<>();for(int i=0;i<checked.length;i++)if(checked[i])picked.add(names.get(i));
                    if(picked.isEmpty()){toast("Choose at least one camera");return;}save("cameras",new JSONArray(picked));dialog.dismiss();build();
                }));dialog.show();
            });
        }catch(Exception e){runOnUiThread(()->toast("Could not read cameras; check Frigate connection"));}});
    }
    private void feeds(Config c){
        List<String> labels=new ArrayList<>();labels.add("Add RSS / Atom feed…");labels.addAll(c.feeds);
        new AlertDialog.Builder(this).setTitle("RSS feeds (HTTPS)").setItems(labels.toArray(new String[0]),(d,i)->editFeed(c,i-1)).setNegativeButton("Close",null).show();
    }
    private void editFeed(Config c,int index){
        if(index<0&&c.feeds.size()>=10){toast("Up to 10 feeds");return;}
        EditText url=new EditText(this);url.setSingleLine(true);url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);url.setHint("https://example.org/rss");if(index>=0)url.setText(c.feeds.get(index));
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(index<0?"Add feed":"Edit feed").setView(url).setNegativeButton("Cancel",null).setPositiveButton("Save",null);
        if(index>=0)builder.setNeutralButton("Remove",(d,w)->{List<String> next=new ArrayList<>(c.feeds);next.remove(index);save("feeds",new JSONArray(next));build();});
        AlertDialog dialog=builder.create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
            try{String value=url.getText().toString().trim();Config.base(value);List<String> next=new ArrayList<>(c.feeds);if(index<0)next.add(value);else next.set(index,value);save("feeds",new JSONArray(next));dialog.dismiss();build();}
            catch(Exception e){url.setError("Enter a full HTTPS feed address without embedded credentials");}
        }));dialog.show();
    }
    private void save(String key,JSONArray a){getSharedPreferences("choices",Context.MODE_PRIVATE).edit().putString(key,a.toString()).apply();}
    private void toast(String text){if(!isFinishing()&&!isDestroyed())Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
