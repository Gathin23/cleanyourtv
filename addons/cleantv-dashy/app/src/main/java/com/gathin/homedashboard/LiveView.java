package com.gathin.homedashboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** App-owned HTML only, a session cookie and one muted MSE decoder. No JavaScript bridge. */
final class LiveView {
    interface Listener { void playing(); void failed(); }
    final WebView view;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private int generation;
    private boolean alive,playing,destroyed;
    private String base;
    @SuppressLint("SetJavaScriptEnabled")
    LiveView(Context context,Listener listener){
        this.listener=listener;view=new WebView(context);view.setBackgroundColor(0xff111b28);
        view.setFocusable(false);view.setFocusableInTouchMode(false);
        WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        view.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){return true;}
        });
    }
    void start(String base,String cookie,String stream){
        int epoch=++generation;alive=true;playing=false;this.base=base;main.removeCallbacksAndMessages(null);
        view.loadUrl("about:blank");
        try{
            if(!stream.matches("[A-Za-z0-9_.-]+"))throw new IllegalArgumentException();
            URI uri=new URI(base);String origin="wss://"+uri.getRawAuthority();
            String address=base.replaceFirst("^https://","wss://")+"/live/mse/api/ws?src="+URLEncoder.encode(stream,"UTF-8");
            String html;
            try(java.io.InputStream input=view.getContext().getAssets().open("live-player.html")){
                java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();byte[] block=new byte[4096];
                for(int n;(n=input.read(block))!=-1;)output.write(block,0,n);
                html=new String(output.toByteArray(),StandardCharsets.UTF_8);
            }
            final String page=html.replace("__CONNECT_ORIGIN__",origin).replace("__STREAM_URL__",JSONObject.quote(address));
            CookieManager cookies=CookieManager.getInstance();cookies.setAcceptThirdPartyCookies(view,false);
            String value=cookie==null?"frigate_token=; Max-Age=0":cookie;
            cookies.setCookie(base,value+"; Path=/; Secure; HttpOnly; SameSite=Strict",ok->{
                if(!alive||generation!=epoch)return;
                view.loadDataWithBaseURL(base+"/",page,"text/html","UTF-8",null);main.postDelayed(poll,1500);
                main.postDelayed(()->{if(alive&&generation==epoch&&!playing)fail();},15000);
            });
        }catch(Exception e){fail();}
    }
    private final Runnable poll=new Runnable(){public void run(){
        if(!alive)return;int epoch=generation;
        view.evaluateJavascript("window.health ? window.health() : 'connecting'",state->{
            if(!alive||generation!=epoch)return;
            if("\"failed\"".equals(state)){fail();return;}
            if("\"playing\"".equals(state)&&!playing){playing=true;listener.playing();}
            main.postDelayed(this,1500);
        });
    }};
    private void fail(){if(!alive)return;alive=false;main.removeCallbacksAndMessages(null);view.loadUrl("about:blank");listener.failed();}
    void stop(){
        if(destroyed)return;destroyed=true;
        alive=false;generation++;main.removeCallbacksAndMessages(null);view.loadUrl("about:blank");view.stopLoading();view.onPause();view.removeAllViews();view.destroy();
        if(base!=null)CookieManager.getInstance().setCookie(base,"frigate_token=; Max-Age=0; Path=/; Secure; HttpOnly");
    }
}
