package io.github.vitorcamillo.wolfgames;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A game shelf: covers in one row, launching through Moonlight, with optional Wolf session control. */
public class GamesActivity extends Activity {
    private static final String MOONLIGHT="com.limelight";
    private static final String PREFS="wolfgames";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final ExecutorService syncer=Executors.newSingleThreadExecutor();
    private final List<TextView> cards=new ArrayList<>();
    private final List<TextView> captions=new ArrayList<>();
    private final List<Bitmap> bitmaps=new ArrayList<>();
    private FrameLayout root;
    private LinearLayout row;
    private HorizontalScrollView scroll;
    private TextView pcChoice,status;
    private MoonlightCatalog catalog;
    private WolfSessionMenu sessionMenu;
    private List<String> hostNames=new ArrayList<>();
    private List<String> hostUuids=new ArrayList<>();
    private String pickedName,pickedUuid;
    private float density;
    private boolean destroyed,busy,syncing,reloadPending;
    private long loadedStamp=Long.MIN_VALUE;
    private int selected;
    private String activeGameId;
    private File directory;
    private View activePlay;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density=getResources().getDisplayMetrics().density;
        File external=getExternalFilesDir(null);
        directory=external==null?null:new File(external,"moonlight");
        if(directory!=null)directory.mkdirs(); // Made by the app itself: a folder adb creates is not readable by the app.
        build();
    }

    @Override protected void onStart() {
        super.onStart();
        Provisioning.accept(this);
        if(!moonlightInstalled()) {
            loadedStamp=Long.MIN_VALUE;
            MoonlightGate.sync(this);
            showStatus("O Moonlight não está instalado. Instale o Moonlight na TV; o Wolf Games volta para a tela inicial quando a TV reiniciar.");
            return;
        }
        hideStatus();
        if(!busy)load();
        maybeSync();
        if(sessionMenu!=null && hasGameCatalog())sessionMenu.resume();
    }

    @Override protected void onStop() {
        super.onStop();
        if(sessionMenu!=null)sessionMenu.pause();
    }

    @Override protected void onDestroy() {
        destroyed=true;
        if(sessionMenu!=null)sessionMenu.close();
        loader.shutdownNow();
        syncer.shutdownNow();
        recycle();
        super.onDestroy();
    }

    private void build() {
        root=new FrameLayout(this);
        root.setBackgroundColor(0xff101216);
        setContentView(root);
        TextView title=new TextView(this);
        title.setText("Wolf Games");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xf2ffffff);
        title.setPadding(dp(48),dp(24),dp(48),dp(8));
        root.addView(title,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START));

        pcChoice=pill("PC:");
        pcChoice.setVisibility(View.GONE);
        pcChoice.setOnClickListener(v->chooseHost());
        pcChoice.setOnKeyListener((v,key,event)-> {
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_UP)return true;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){focusGames();return true;}
            return false;
        });
        FrameLayout.LayoutParams pillParams=new FrameLayout.LayoutParams(-2,dp(30),Gravity.TOP|Gravity.END);
        pillParams.rightMargin=dp(48);pillParams.topMargin=dp(26);
        root.addView(pcChoice,pillParams);

        status=new TextView(this);
        status.setTextSize(16);
        status.setTextColor(0xb3ffffff);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(60),dp(20),dp(60),dp(20));
        status.setVisibility(View.GONE);
        root.addView(status,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));

        scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setFocusable(false);
        scroll.setPadding(dp(48),dp(8),dp(48),dp(16));
        row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(dp(5),dp(5),dp(5),dp(5));
        scroll.addView(row,new ViewGroup.LayoutParams(-2,-1));
        FrameLayout.LayoutParams scrollParams=new FrameLayout.LayoutParams(-1,-1,Gravity.TOP);
        scrollParams.topMargin=dp(84);
        root.addView(scroll,scrollParams);
    }

    /** Reads and validates the catalog from the external directory the owner fills with adb push. */
    private void load() {
        if(destroyed || busy || directory==null)return;
        final long stamp=new File(directory,"catalog.json").lastModified();
        if(stamp==loadedStamp)return;
        busy=true;
        loader.execute(()-> {
            MoonlightCatalog result=null;
            List<Bitmap> images=new ArrayList<>();
            List<String> names=new ArrayList<>();
            List<String> uuids=new ArrayList<>();
            try {
                File source=MoonlightCatalog.localFile(directory,"catalog.json");
                JSONObject json=new JSONObject(read(source));
                if(json.getInt("schema_version")!=1)throw new IOException("Unsupported catalog");
                org.json.JSONArray entries=json.getJSONArray("games");
                if(entries.length()>MoonlightCatalog.MAX_GAMES)throw new IOException("Too many games");
                List<MoonlightCatalog.Game> games=new ArrayList<>();
                for(int i=0;i<entries.length();i++) {
                    org.json.JSONObject entry=entries.getJSONObject(i);
                    games.add(new MoonlightCatalog.Game(entry.getString("name"),entry.optString("app_id",null),entry.optString("image",null)));
                }
                result=new MoonlightCatalog(json.getString("host_name"),json.getString("host_uuid"),games);
                names.add(result.hostName);uuids.add(result.hostUuid);
                org.json.JSONArray hosts=json.optJSONArray("hosts");
                if(hosts!=null && hosts.length()>8)throw new IOException("Too many hosts");
                if(hosts!=null)for(int i=0;i<hosts.length();i++) {
                    org.json.JSONObject host=hosts.getJSONObject(i);
                    MoonlightCatalog validated=new MoonlightCatalog(host.getString("name"),host.getString("uuid"),games);
                    if(!uuids.contains(validated.hostUuid)) {names.add(validated.hostName);uuids.add(validated.hostUuid);}
                }
                long remaining=12L*1024*1024;
                for(MoonlightCatalog.Game game:result.games) {
                    if(Thread.currentThread().isInterrupted())throw new IOException("Cancelled");
                    Bitmap image=remaining>0?cover(game.image):null;
                    if(image!=null) {
                        if(image.getAllocationByteCount()>remaining) {image.recycle();image=null;}
                        else remaining-=image.getAllocationByteCount();
                    }
                    images.add(image);
                }
            } catch(Exception e) {android.util.Log.w("WolfGames","Catalog rejected",e);result=null;}
            final MoonlightCatalog ready=result;
            main.post(()-> {
                busy=false;
                if(destroyed) {recycle(images);return;}
                loadedStamp=stamp;
                if(ready==null) {recycle(images);showFallback();}
                else {clearShelf();catalog=ready;hostNames=names;hostUuids=uuids;bitmaps.addAll(images);buildShelf();}
                if(reloadPending) {reloadPending=false;loadedStamp=Long.MIN_VALUE;load();} // A cover changed under a load in flight: reload once.
            });
        });
    }

    private static String read(File file) throws IOException {
        if(!file.isFile() || file.length()>MoonlightCatalog.MAX_CATALOG_BYTES)throw new IOException("Catalog unavailable");
        try(FileInputStream in=new FileInputStream(file); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1) {
                if(out.size()+n>MoonlightCatalog.MAX_CATALOG_BYTES)throw new IOException("Catalog too large");
                out.write(buffer,0,n);
            }
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }

    private Bitmap cover(String name) {
        if(name==null)return null;
        try {
            File file=MoonlightCatalog.localFile(directory,name);
            if(!file.isFile() || file.length()>4L*1024*1024)return null;
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(file.getPath(),options);
            if(options.outWidth<1 || options.outHeight<1 || options.outWidth>8192 || options.outHeight>8192)return null;
            options.inSampleSize=1;
            while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>512)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;
            return BitmapFactory.decodeFile(file.getPath(),options);
        } catch(IOException ignored) {return null;}
    }

    /** Keeps the catalog fresh from the Wolf when the session controller is provisioned. */
    private void maybeSync() {
        if(syncing || directory==null || destroyed || !new File(getFilesDir(),"wolf-control.json").isFile())return;
        syncing=true;
        syncer.execute(()-> {
            boolean changed=false;
            try {
                WolfControlClient client=WolfControlClient.load(new File(getFilesDir(),"wolf-control.json"));
                if(client!=null && (directory.isDirectory() || directory.mkdirs()))
                    changed=WolfCatalogSync.sync(adapter(client),directory,SystemClock.uptimeMillis()+20000,SystemClock::uptimeMillis);
            } catch(Exception e) {android.util.Log.w("WolfGames","Catalog sync failed, keeping the current catalog",e);}
            final boolean updated=changed;
            main.post(()-> {
                syncing=false;
                if(updated && !destroyed) {if(busy)reloadPending=true;else {loadedStamp=Long.MIN_VALUE;load();}}
            });
        });
    }

    private static WolfCatalogSync.Source adapter(WolfControlClient client) {
        return new WolfCatalogSync.Source() {
            public String hostUuid() {return client.hostUuid();}
            public List<WolfCatalogSync.Entry> games() throws WolfControlClient.ControlException {return client.games();}
            public byte[] cover(String id) throws WolfControlClient.ControlException {return client.cover(id);}
        };
    }

    private void buildShelf() {
        status.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
        String saved=getSharedPreferences(PREFS,0).getString("game_name","");
        selected=0;
        for(int i=0;i<catalog.games.size();i++)if(catalog.games.get(i).name.equals(saved))selected=i;
        for(int i=0;i<catalog.games.size();i++) {
            final int index=i;
            MoonlightCatalog.Game game=catalog.games.get(i);
            FrameLayout tile=new FrameLayout(this);
            tile.setClipChildren(false);
            ImageView cover=new ImageView(this);
            cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cover.setBackground(shape(0xff242126,0,dp(12)));
            cover.setClipToOutline(true);
            if(bitmaps.get(i)!=null)cover.setImageBitmap(bitmaps.get(i));
            tile.addView(cover,new FrameLayout.LayoutParams(-1,-1));
            if(bitmaps.get(i)!=null) {
                TextView caption=new TextView(this);
                caption.setText(game.name);caption.setTextSize(13);caption.setTextColor(Color.WHITE);
                caption.setTypeface(Typeface.DEFAULT_BOLD);caption.setMaxLines(2);
                caption.setEllipsize(TextUtils.TruncateAt.END);
                caption.setGravity(Gravity.BOTTOM|Gravity.START);
                caption.setPadding(dp(10),dp(28),dp(10),dp(10));
                caption.setShadowLayer(dp(3),0,dp(1),0x99000000);
                GradientDrawable shade=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xd9000000});
                shade.setCornerRadii(new float[]{0,0,0,0,dp(12),dp(12),dp(12),dp(12)});
                caption.setBackground(shade);
                caption.setAlpha(0f);caption.setFocusable(false);caption.setClickable(false);
                caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.addView(caption,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
                captions.add(caption);
            } else captions.add(null);
            TextView card=text(bitmaps.get(i)==null?game.name:"",14,Color.WHITE);
            card.setGravity(Gravity.CENTER);card.setPadding(dp(8),dp(8),dp(8),dp(8));
            card.setBackground(shape(Color.TRANSPARENT,0,dp(12)));
            card.setFocusable(true);card.setClickable(true);card.setContentDescription(game.name);
            tile.addView(card,new FrameLayout.LayoutParams(-1,-1));
            cards.add(card);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(125),dp(167));lp.rightMargin=dp(12);
            row.addView(tile,lp);
            card.setOnFocusChangeListener((v,focused)-> {
                card.setBackground(shape(Color.TRANSPARENT,focused?0x99ffffff:0,dp(12)));
                float scale=focused?1.10f:1f;
                tile.animate().scaleX(scale).scaleY(scale).setDuration(160).start();
                TextView caption=captions.size()>index?captions.get(index):null;
                if(caption!=null) {
                    caption.animate().cancel();
                    caption.animate().alpha(focused?1f:0f).setDuration(focused?160:120).start();
                }
                if(focused) {
                    selected=index;
                    getSharedPreferences(PREFS,0).edit().putString("game_name",game.name).apply();
                    scroll.smoothScrollTo(Math.max(0,tile.getLeft()-dp(8)),0);
                }
            });
            card.setOnClickListener(v->open(index));
            card.setOnKeyListener((v,key,event)-> {
                if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
                int place=row.indexOfChild(tile);
                if(key==KeyEvent.KEYCODE_DPAD_UP) {if(pcChoice!=null && pcChoice.getVisibility()==View.VISIBLE)pcChoice.requestFocusFromTouch();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_DOWN)return true;
                if(key==KeyEvent.KEYCODE_DPAD_LEFT && place==0)return true;
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT && row!=null && place==row.getChildCount()-1)return true;
                return false;
            });
        }
        boolean multi=hostNames.size()>1;
        pcChoice.setText("PC: "+pickedHostName()+" ▾");
        pcChoice.setVisibility(multi?View.VISIBLE:View.GONE);
        sessionMenu=new WolfSessionMenu(this,catalog.hostUuid,new WolfSessionMenu.Listener() {
            @Override public void launch(String id,String name) {launchControlled(id,name);}
            @Override public void focusShelf() {focusGames();}
            @Override public void sessionState(WolfSession session) {showActiveGame(session);}
        });
        if(isFinishing() || destroyed)return;
        sessionMenu.resume();
        cards.get(selected).post(()-> {if(!destroyed)cards.get(selected).requestFocus();});
    }

    private String pickedHostName() {
        int index=hostUuids.indexOf(pickedUuid);
        if(index<0)index=0;
        pickedName=hostNames.get(index);pickedUuid=hostUuids.get(index);
        getSharedPreferences(PREFS,0).edit().putString("host_uuid",pickedUuid).apply();
        return pickedName;
    }

    private void chooseHost() {
        if(catalog==null)return;
        int current=Math.max(0,hostUuids.indexOf(pickedUuid));
        new AlertDialog.Builder(this).setTitle("PC para jogar")
                .setSingleChoiceItems(hostNames.toArray(new String[0]),current,(dialog,which)-> {
                    pickedUuid=hostUuids.get(which);pickedName=hostNames.get(which);
                    getSharedPreferences(PREFS,0).edit().putString("host_uuid",pickedUuid).apply();
                    pcChoice.setText("PC: "+pickedName+" ▾");
                    dialog.dismiss();
                }).setNeutralButton("Gerenciar no Moonlight",(dialog,which)->openMoonlight())
                .setNegativeButton("Cancelar",null).show();
    }

    /** OK on a game: with a provisioned controller and an app id, confirm through the session menu; otherwise Moonlight opens it. */
    private void open(int index) {
        if(catalog==null || index<0 || index>=catalog.games.size())return;
        MoonlightCatalog.Game game=catalog.games.get(index);
        boolean controlled=sessionMenu!=null && pickedUuid!=null && pickedUuid.equals(catalog.hostUuid)
                && new File(getFilesDir(),"wolf-control.json").isFile();
        if(controlled && game.appId!=null) {sessionMenu.open(game.appId,game.name);return;}
        start(new Intent(Intent.ACTION_VIEW).setClassName(MOONLIGHT,MOONLIGHT+".ShortcutTrampoline")
                .putExtra("Name",pickedName).putExtra("UUID",pickedUuid).putExtra("AppName",game.name));
    }

    private void launchControlled(String id,String name) {
        if(destroyed || catalog==null)return;
        start(new Intent(Intent.ACTION_VIEW).setClassName(MOONLIGHT,MOONLIGHT+".ShortcutTrampoline")
                .putExtra("Name",catalog.hostName).putExtra("UUID",catalog.hostUuid)
                .putExtra("AppName",name).putExtra("AppId",id));
    }

    private void openMoonlight() {
        start(new Intent(Intent.ACTION_MAIN).setClassName(MOONLIGHT,MOONLIGHT+".PcView"));
    }

    private void start(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {startActivity(intent);}
        catch(ActivityNotFoundException|SecurityException e) {
            Toast.makeText(this,"Não foi possível abrir o Moonlight. Confira se ele está instalado.",Toast.LENGTH_LONG).show();
        }
    }

    private boolean moonlightInstalled() {
        try {return getPackageManager().getPackageInfo(MOONLIGHT,0)!=null;}
        catch(Exception ignored) {return false;}
    }

    private boolean hasGameCatalog() {return catalog!=null && catalog.hostUuid.equals(pickedUuid);}

    private void focusGames() {
        if(!cards.isEmpty() && hasGameCatalog()) {cards.get(selected).requestFocusFromTouch();return;}
        if(pcChoice!=null && pcChoice.getVisibility()==View.VISIBLE)pcChoice.requestFocusFromTouch();
    }

    /** Marks the game the Wolf reports as running: soft outline, a play badge and a spoken hint. */
    private void showActiveGame(WolfSession session) {
        activeGameId=session!=null && session.active?session.appId:null;
        if(catalog==null || row==null)return;
        if(activePlay!=null && activePlay.getParent() instanceof ViewGroup)((ViewGroup)activePlay.getParent()).removeView(activePlay);
        activePlay=null;
        int active=-1;
        if(activeGameId!=null)for(int i=0;i<cards.size();i++)if(activeGameId.equals(catalog.games.get(i).appId)) {active=i;break;}
        for(int i=0;i<cards.size();i++) {
            View tile=(View)cards.get(i).getParent();
            boolean on=i==active;
            tile.setForeground(on?shape(Color.TRANSPARENT,0xff8deaff,dp(12)):null);
            tile.setElevation(on?dp(12):0);
            cards.get(i).setContentDescription(catalog.games.get(i).name+(on?" · Jogo em andamento, pressione OK para continuar":""));
        }
        if(active>=0) {
            View tile=(View)cards.get(active).getParent();
            TextView play=text("▶",20,0xff171419);
            play.setGravity(Gravity.CENTER);
            GradientDrawable circle=new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(0xe6ffffff);
            play.setBackground(circle);
            play.setFocusable(false);
            play.setClickable(false);
            play.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ((FrameLayout)tile).addView(play,new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.CENTER));
            activePlay=play;
        }
    }

    /** Minimal screen when Moonlight is installed but no valid catalog is available. */
    private void showFallback() {
        clearShelf();
        status.setVisibility(View.VISIBLE);
        status.setText("Nenhum catálogo de jogos encontrado. Gere o catálogo no Moonlight e envie os arquivos com adb push (veja o README do Wolf Games).");
        scroll.setVisibility(View.GONE);
        if(sessionMenu!=null) {sessionMenu.close();sessionMenu=null;}
    }

    private void showStatus(String message) {
        status.setVisibility(View.VISIBLE);
        status.setText(message);
        scroll.setVisibility(View.GONE);
    }

    private void hideStatus() {
        status.setVisibility(View.GONE);
    }

    private void clearShelf() {
        if(sessionMenu!=null) {sessionMenu.close();sessionMenu=null;}
        if(activePlay!=null && activePlay.getParent() instanceof ViewGroup)((ViewGroup)activePlay.getParent()).removeView(activePlay);
        activePlay=null;
        row.removeAllViews();
        cards.clear();captions.clear();
        recycle();
        catalog=null;activeGameId=null;
    }

    private void recycle() {for(Bitmap b:bitmaps)if(b!=null && !b.isRecycled())b.recycle();bitmaps.clear();}
    private void recycle(List<Bitmap> images) {for(Bitmap b:images)if(b!=null && !b.isRecycled())b.recycle();}

    private TextView text(String value,int size,int color) {
        TextView view=new TextView(this);
        view.setSingleLine(true);view.setEllipsize(TextUtils.TruncateAt.END);
        view.setText(value);view.setTextSize(size);view.setTextColor(color);
        return view;
    }

    private TextView pill(String name) {
        TextView pill=text(name,12,0xff171419);
        pill.setGravity(Gravity.CENTER);
        pill.setFocusable(true);pill.setClickable(true);
        pill.setBackground(shape(Color.WHITE,0,dp(15)));
        pill.setPadding(dp(14),0,dp(14),0);
        pill.setOnFocusChangeListener((v,focused)->pill.setBackground(shape(focused?0xffc8dfff:Color.WHITE,focused?0xff8abaff:0,dp(15))));
        return pill;
    }

    private GradientDrawable shape(int color,int stroke,float radius) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if(stroke!=0)d.setStroke(dp(2),stroke);
        return d;
    }

    private int dp(float value) {return Math.round(value*density);}
}
