package com.majnioui.tv.prayer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private WebView webView;
    private TextView status;

    private static final String WIDGET_URL =
            "https://sihat.kemenag.go.id/widget/jadwal?wilayah=340310&tampilan=mini";

    private static final String PAGE =
            "<!DOCTYPE html><html><head>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
            "<meta charset='UTF-8'>" +
            "<style>" +
            "*{box-sizing:border-box}" +
            "body{margin:0;background:#080b10;color:white;" +
            "font-family:Arial,sans-serif;min-height:100vh;" +
            "display:flex;align-items:center;justify-content:center;padding:32px}" +
            ".panel{width:100%;max-width:1100px;text-align:center}" +
            "h1{font-size:34px;font-weight:600;margin:0 0 12px}" +
            ".sub{font-size:18px;color:#aab5c4;margin-bottom:34px}" +
            ".widget{width:100%;background:white;border-radius:12px;" +
            "overflow:hidden;padding:8px}" +
            "iframe{width:100%;height:74px;border:0;display:block}" +
            ".source{font-size:14px;color:#8492a6;margin-top:22px}" +
            "</style></head><body>" +
            "<div class='panel'>" +
            "<h1>Jadwal Salat</h1>" +
            "<div class='sub'>Kecamatan Ponjong · Gunungkidul</div>" +
            "<div class='widget'>" +
            "<iframe src='" + WIDGET_URL + "'" +
            " title='Jadwal Salat SIHAT Kementerian Agama'" +
            " loading='eager' referrerpolicy='no-referrer-when-downgrade'>" +
            "</iframe></div>" +
            "<div class='source'>Sumber resmi: SIHAT Kementerian Agama RI</div>" +
            "</div></body></html>";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8, 11, 16));
        root.setPadding(24, 12, 24, 12);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(8, 11, 16));
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setPadding(8, 8, 8, 8);
        status.setText("Memuat jadwal salat Kemenag...");

        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                status.setText("SIHAT Kemenag · Kecamatan Ponjong");
            }

            @Override
            public void onReceivedError(WebView view,
                    WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    status.setText(
                            "Gagal memuat halaman. Periksa koneksi internet, lalu tekan OK untuk mencoba lagi."
                    );
                }
            }
        });

        status.setOnClickListener(v -> loadWidget());

        loadWidget();
    }

    private void loadWidget() {
        status.setText("Memuat jadwal salat Kemenag...");
        webView.loadDataWithBaseURL(
                "https://sihat.kemenag.go.id/",
                PAGE,
                "text/html",
                "UTF-8",
                null
        );
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            loadWidget();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
