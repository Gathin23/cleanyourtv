package com.majnioui.tv.prayer;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.text.Html;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String wilayah = getString(R.string.wilayah);
        String place = Html.escapeHtml(getString(R.string.place));
        String widgetUrl = "https://sihat.kemenag.go.id/widget/jadwal?wilayah="
                + wilayah + "&tampilan=mini";

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        webView.setWebViewClient(new WebViewClient());
        setContentView(webView);

        String page =
            "<!DOCTYPE html><html lang='id'><head>"
            + "<meta charset='UTF-8'>"
            + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
            + "<title>Jadwal Salat</title>"
            + "<style>"
            + "*{box-sizing:border-box}"
            + "body{margin:0;padding:20px;background:#101820;color:#fff;"
            + "font-family:Arial,sans-serif;text-align:center}"
            + "h1{font-size:26px;margin:8px 0;color:#2BB673}"
            + ".place{font-size:16px;color:#ddd;margin:8px 0 22px}"
            + ".widget{width:100%;max-width:700px;margin:0 auto}"
            + "iframe{width:100%;height:100px;border:0;display:block}"
            + ".footer{font-size:13px;color:#aaa;margin-top:22px}"
            + "</style></head><body>"
            + "<h1>Jadwal Salat</h1>"
            + "<div class='place'>" + place + "</div>"
            + "<div class='widget'>"
            + "<iframe src='" + widgetUrl + "' "
            + "title='Jadwal Salat SIHAT Kementerian Agama' "
            + "loading='eager' "
            + "referrerpolicy='no-referrer-when-downgrade'></iframe>"
            + "</div>"
            + "<div class='footer'>Sumber resmi: SIHAT Kementerian Agama RI</div>"
            + "</body></html>";

        webView.loadDataWithBaseURL(
                "https://sihat.kemenag.go.id/",
                page,
                "text/html",
                "UTF-8",
                null
        );
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (webView != null) webView.reload();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
