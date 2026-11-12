package com.gathin.homedashboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.FrameLayout;
import org.json.JSONArray;
import java.io.ByteArrayInputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Compact native cards and a single muted live camera, only while visible. */
final class Scene {
    final LinearLayout root;
    private final Context context;
    private final boolean demo;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextView clock, date, cameraStatus, newsSource, footer;
    private final LinearLayout sensorRow;
    private final SensorTile[] sensorTiles=new SensorTile[3];
    private final TextView[] newsTitles = new TextView[3];
    private final TextView[] newsPublishers = new TextView[3];
    private final ImageView picture;
    private final LiveView live;
    private final CameraGlow cameraGlow;
    private volatile boolean livePlaying;
    private String requestedCamera="";
    private long lastSnapshot,lastLiveAttempt;
    private ScheduledExecutorService workers;
    private volatile boolean active;
    private volatile int generation;
    private Config config;
    private Api api;
    private final PersonFocus personFocus=new PersonFocus();
    private ActivityFeed activityFeed;
    private List<String> cameras = new ArrayList<>();
    private List<Headline> headlines = new ArrayList<>();
    private NewsSelection.Rotation newsRotation=new NewsSelection.Rotation(new ArrayList<>());
    private long nextNewsAt;
    private static final long NEWS_ROTATION_MS=15000;
    private long started;
    private boolean feedFailed;

    Scene(Context context, boolean demo) {
        this.context=context; this.demo=demo;
        CameraGlow.Backdrop backdrop=new CameraGlow.Backdrop(context);
        backdrop.setOrientation(LinearLayout.VERTICAL);root=backdrop;
        root.setBackgroundColor(Color.rgb(7,12,19)); root.setPadding(dp(16),dp(16),dp(16),dp(16));
        LinearLayout bar = row(); bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout title = column();
        title.addView(text("cleantv-dashy" + (demo ? "  /  DEMO" : ""), 14, "#70D8B0"));
        date = text("", 14, "#90A5BA"); title.addView(date);
        bar.addView(title, new LinearLayout.LayoutParams(0,-2,1));
        clock = text("", 26, "#F2F6FA"); bar.addView(clock);
        root.addView(bar);
        LinearLayout content = row();
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,0,1);cp.topMargin=dp(10);root.addView(content,cp);
        LinearLayout left=column();content.addView(left,new LinearLayout.LayoutParams(0,-1,1.45f));
        LinearLayout cam = panel();
        cam.setPadding(0,0,0,0);cam.setClipToOutline(true);
        FrameLayout videoBox=new FrameLayout(context);
        picture = new ImageView(context); picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        videoBox.addView(picture,new FrameLayout.LayoutParams(-1,-1));
        cameraStatus = text("",10,"#90A5BA");cameraStatus.setVisibility(View.GONE);
        live=demo?null:new LiveView(context,new LiveView.Listener(){
            public void playing(){if(!active)return;livePlaying=true;picture.setVisibility(View.INVISIBLE);cameraStatus.setVisibility(View.GONE);}
            public void failed(){if(!active)return;livePlaying=false;picture.setVisibility(View.VISIBLE);cameraStatus.setText("Live unavailable · showing snapshots");cameraStatus.setVisibility(View.VISIBLE);}
        });
        if(live!=null)videoBox.addView(live.view,new FrameLayout.LayoutParams(-1,-1));
        picture.bringToFront();
        cam.addView(videoBox,new LinearLayout.LayoutParams(-1,0,1));cam.addView(cameraStatus);
        cameraGlow=new CameraGlow(context);cameraGlow.addView(cam,new FrameLayout.LayoutParams(-1,-1));
        left.addView(cameraGlow,new LinearLayout.LayoutParams(-1,0,1));
        backdrop.attach(cameraGlow);
        sensorRow=row();
        for(int i=0;i<sensorTiles.length;i++){
            sensorTiles[i]=new SensorTile();
            LinearLayout.LayoutParams tileParams=new LinearLayout.LayoutParams(0,-1,1);
            if(i>0)tileParams.leftMargin=dp(12);sensorRow.addView(sensorTiles[i].panel,tileParams);
        }
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(88));ep.topMargin=dp(12);left.addView(sensorRow,ep);
        LinearLayout news=panel();
        newsSource=text("NEWS",10,"#70D8B0"); news.addView(newsSource);newsSource.setVisibility(View.GONE);
        for(int i=0;i<newsTitles.length;i++){
            LinearLayout story=column();story.setGravity(Gravity.CENTER_VERTICAL);
            newsTitles[i]=text(i==0?"Add your RSS feeds in Dashboard settings":"",22,"#F2F6FA");newsTitles[i].setIncludeFontPadding(false);newsTitles[i].setGravity(Gravity.CENTER_VERTICAL);newsTitles[i].setMaxLines(4);newsTitles[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
            story.addView(newsTitles[i]);
            newsPublishers[i]=text("",12,"#90A5BA");newsPublishers[i].setIncludeFontPadding(false);newsPublishers[i].setMaxLines(1);newsPublishers[i].setEllipsize(android.text.TextUtils.TruncateAt.END);newsPublishers[i].setVisibility(View.GONE);
            LinearLayout.LayoutParams sourceParams=new LinearLayout.LayoutParams(-1,-2);sourceParams.topMargin=dp(5);story.addView(newsPublishers[i],sourceParams);
            LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,0,1);np.topMargin=dp(5);news.addView(story,np);
            if(i<newsTitles.length-1){
                View divider=new View(context);divider.setBackgroundColor(0xff2a3b4a);
                LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,dp(1));line.topMargin=dp(4);news.addView(divider,line);
            }
        }
        LinearLayout.LayoutParams nrp=new LinearLayout.LayoutParams(0,-1,1);nrp.leftMargin=dp(12);content.addView(news,nrp);
        footer=text("",11,"#73889B");root.addView(footer);footer.setVisibility(View.GONE);
    }
    private int dp(float n) { return Math.round(n*context.getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(context); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=column(); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout panel() {
        LinearLayout l=column(); l.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable g=new GradientDrawable(); g.setColor(Color.rgb(17,27,40)); g.setCornerRadius(dp(18)); l.setBackground(g); return l;
    }
    private TextView text(String s,float size,String color) {
        TextView t=new TextView(context); t.setText(s); t.setTextSize(size); t.setTextColor(Color.parseColor(color)); t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL)); return t;
    }
    private final class SensorTile {
        final LinearLayout panel=panel();
        final TextView label=text("",12,"#90A5BA"),value=text("—",32,"#70D8B0"),status=text("Waiting for entity",10,"#90A5BA");
        final ProgressBar percent=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);
        SensorTile(){
            panel.setPadding(dp(12),dp(8),dp(12),dp(8));
            label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);panel.addView(label);
            value.setMaxLines(1);value.setIncludeFontPadding(false);value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            value.setAutoSizeTextTypeUniformWithConfiguration(14,32,1,android.util.TypedValue.COMPLEX_UNIT_SP);
            panel.addView(value,new LinearLayout.LayoutParams(-1,dp(38)));
            percent.setMax(100);panel.addView(percent,new LinearLayout.LayoutParams(-1,dp(5)));
            status.setIncludeFontPadding(false);status.setMaxLines(1);status.setEllipsize(android.text.TextUtils.TruncateAt.END);panel.addView(status);
        }
        void bind(SensorCard card){
            panel.setVisibility(card.enabled?View.VISIBLE:View.GONE);label.setText(card.label);
            value.setTextColor(Color.parseColor(card.colour));
            percent.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(card.colour)));
            percent.setVisibility(card.mode.equals("percentage")?View.INVISIBLE:View.GONE);
        }
        void show(SensorCard card,Metric metric,String stamp){
            value.setText(metric.text);status.setText(stamp);
            if(card.mode.equals("percentage")){
                percent.setVisibility(Double.isNaN(metric.value)?View.INVISIBLE:View.VISIBLE);
                if(!Double.isNaN(metric.value))percent.setProgress((int)Math.round(metric.value));
            }
        }
    }
    private void bindCards(List<SensorCard> cards){
        int shown=0;
        for(int i=0;i<sensorTiles.length;i++){
            SensorCard card=cards.get(i);SensorTile tile=sensorTiles[i];tile.bind(card);
            LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)tile.panel.getLayoutParams();
            params.leftMargin=card.enabled&&shown>0?dp(12):0;tile.panel.setLayoutParams(params);
            if(card.enabled)shown++;
        }
        sensorRow.setVisibility(shown==0?View.GONE:View.VISIBLE);
    }
    void start() {
        if (active) return;
        active=true; int epoch=++generation; started=SystemClock.elapsedRealtime();
        main.post(tick);
        if (demo) {
            int colour=Color.parseColor("#FF3B30");boolean enabled=true;
            try{Config sampleConfig=Config.load(context);colour=Color.parseColor(sampleConfig.activityGlowColor);enabled=sampleConfig.activityGlow;}catch(Exception ignored){}
            cameraGlow.highlight(enabled,colour);
            cameraStatus.setVisibility(View.GONE);
            picture.setImageResource(R.drawable.banner);
            try{
                List<SensorCard> samples=SensorCard.read(new org.json.JSONObject().put("sensorCards",new JSONArray()
                    .put(new org.json.JSONObject().put("label","LIVING ROOM").put("entity","sensor.example_temperature").put("decimals",1))
                    .put(new org.json.JSONObject().put("label","HUMIDITY").put("entity","sensor.example_humidity").put("mode","percentage").put("decimals",0))
                    .put(new org.json.JSONObject().put("label","FRONT DOOR").put("entity","binary_sensor.example_door"))));
                bindCards(samples);
                sensorTiles[0].show(samples.get(0),new Metric("22.4 °C","",22.4),"Illustrative reading");
                sensorTiles[1].show(samples.get(1),new Metric("46 %","",46),"Illustrative reading");
                sensorTiles[2].show(samples.get(2),new Metric("Off","",Double.NaN),"Illustrative reading");
            }catch(org.json.JSONException e){throw new IllegalStateException(e);}
            newsTitles[0].setText("Your cameras and Home Assistant entities at a glance");
            newsTitles[1].setText("Three latest news titles appear together");
            newsTitles[2].setText("Choose cameras and RSS feeds from your remote");
            for(TextView publisher:newsPublishers){publisher.setText("SAMPLE NEWS");publisher.setVisibility(View.VISIBLE);}return;
        }
        try { config=Config.load(context); api=new Api(config); }
        catch(Exception e) { footer.setText("Setup needed · Open cleantv-dashy for instructions");footer.setVisibility(View.VISIBLE); cameraStatus.setText("No valid configuration"); return; }
        bindCards(config.cards);
        workers=Executors.newScheduledThreadPool(3);
        workers.scheduleWithFixedDelay(()->camera(epoch),0,1,TimeUnit.SECONDS);
        workers.scheduleWithFixedDelay(()->sensors(epoch),0,15,TimeUnit.SECONDS);
        workers.scheduleWithFixedDelay(()->news(epoch),0,15,TimeUnit.MINUTES);
        if(config.activityFirst&&!config.mqttUrl.isEmpty())activityFeed=new ActivityFeed(config,api,personFocus,()->{
            ScheduledExecutorService executor=workers;if(active&&generation==epoch&&executor!=null)try{executor.execute(()->camera(epoch));}catch(java.util.concurrent.RejectedExecutionException ignored){}
        });
    }
    private void post(int epoch,Runnable r) { main.post(()->{if(active&&generation==epoch) r.run();}); }
    private final Runnable tick=new Runnable(){public void run(){
        if(!active)return;
        clock.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
        date.setText(DateFormat.getDateInstance(DateFormat.FULL).format(new Date()));
        // Shift slightly to avoid a completely stationary dashboard.
        double phase=SystemClock.elapsedRealtime()/120000.0;
        root.setTranslationX(dp(3)*(float)Math.sin(phase)); root.setTranslationY(dp(3)*(float)Math.cos(phase));
        long now=SystemClock.elapsedRealtime();
        if(!demo&&!headlines.isEmpty()&&now>=nextNewsAt){
            nextNewsAt=now+NEWS_ROTATION_MS;if(newsRotation.advance())showStories();
        }
        main.postDelayed(this,1000);
    }};
    private synchronized void camera(int epoch) {
        try {
            if(!active||generation!=epoch)return;
            if(cameras.isEmpty()) {
                cameras=api.cameras();
                context.getSharedPreferences("choices",Context.MODE_PRIVATE).edit().putString("knownCameras",new JSONArray(cameras).toString()).apply();
                if(!config.cameras.isEmpty())cameras.retainAll(config.cameras);
            }
            if(cameras.isEmpty())throw new java.io.IOException();
            int index=(int)((SystemClock.elapsedRealtime()-started)/(config.cameraSeconds*1000L))%cameras.size();
            long now=SystemClock.elapsedRealtime();String focused=config.activityFirst?personFocus.choose(cameras,now):null;
            String name=focused==null?cameras.get(index):focused;
            post(epoch,()->cameraGlow.highlight(config.activityGlow&&focused!=null,Color.parseColor(config.activityGlowColor)));
            boolean changed=!name.equals(requestedCamera);
            if(changed||(!livePlaying&&now-lastLiveAttempt>30000)){
                requestedCamera=name;lastLiveAttempt=now;lastSnapshot=0;livePlaying=false;
                android.util.Log.i("HomeDashboard","Camera changed: "+name+(focused==null?" (rotation)":" (person alert)"));
                String cookie=api.sessionCookie(),stream=api.stream(name);
                post(epoch,()->{picture.setVisibility(View.VISIBLE);cameraStatus.setVisibility(View.GONE);live.start(config.frigateUrl,cookie,stream);});
            }
            if(livePlaying||now-lastSnapshot<5000)return;
            lastSnapshot=now;byte[] bytes=api.frame(name);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>8192||bounds.outHeight>8192)throw new java.io.IOException();
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
            while(bounds.outWidth/options.inSampleSize>1600||bounds.outHeight/options.inSampleSize>1000)options.inSampleSize*=2;
            Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)throw new java.io.IOException();
            if(!active||generation!=epoch){bitmap.recycle();return;}
            post(epoch,()->{picture.setImageBitmap(bitmap);picture.setAlpha(1f);});
        }catch(Exception e){post(epoch,()->{if(!livePlaying){picture.setAlpha(.5f);cameraStatus.setText("Camera unavailable · retrying");cameraStatus.setVisibility(View.VISIBLE);}});}
    }
    private void sensors(int epoch) {
        for(int i=0;i<sensorTiles.length;i++)if(config.cards.get(i).enabled)readSensor(epoch,config.cards.get(i),sensorTiles[i]);
    }
    private void readSensor(int epoch,SensorCard card,SensorTile tile){
        try{
            Metric m=api.sensor(card);String stamp=sourceTime(m.reported);
            post(epoch,()->tile.show(card,m,stamp));
        }catch(Exception e){post(epoch,()->tile.status.setText("Refresh failed · last value retained"));}
    }
    private static String sourceTime(String stamp) {
        try {
            long millis=java.time.OffsetDateTime.parse(stamp).toInstant().toEpochMilli();
            return "Reported "+DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(millis));
        }catch(Exception e){return "Fetched "+DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());}
    }
    private void news(int epoch) {
        if(config.feeds.isEmpty())return;
        List<Headline> fresh=new ArrayList<>();boolean failed=false;
        for(String url:config.feeds)try{
            List<Headline> items=FeedParser.parse(new ByteArrayInputStream(api.feed(url)),new java.net.URI(url).getHost()).items;
            fresh.addAll(items);
        }catch(Exception e){failed=true;}
        boolean finalFailed=failed;
        List<Headline> recent=NewsSelection.recent(fresh,System.currentTimeMillis());
        post(epoch,()->{
            feedFailed=finalFailed;
            if(!recent.isEmpty()||!finalFailed){headlines=recent;newsRotation=new NewsSelection.Rotation(headlines);nextNewsAt=SystemClock.elapsedRealtime()+NEWS_ROTATION_MS;}
            showStories();
        });
    }
    private void showStories(){
        newsSource.setText(feedFailed?"NEWS · refresh incomplete":"NEWS");
        newsSource.setVisibility(feedFailed?View.VISIBLE:View.GONE);
        List<Headline> visible=newsRotation.visible();
        for(int i=0;i<newsTitles.length;i++){
            newsTitles[i].setText(i<visible.size()?visible.get(i).title:i==0?"No recent headlines available":"");
            String publisher=i<visible.size()?visible.get(i).source:"";newsPublishers[i].setText(publisher);
            newsPublishers[i].setVisibility(publisher.isEmpty()?View.GONE:View.VISIBLE);
        }
    }
    void stop(){active=false;generation++;main.removeCallbacksAndMessages(null);if(activityFeed!=null){activityFeed.stop();activityFeed=null;}if(workers!=null){workers.shutdownNow();workers=null;}if(live!=null)live.stop();picture.setImageDrawable(null);}
}
