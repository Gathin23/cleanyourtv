package com.gathin.artframe;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * Send pictures from a phone: scan the QR (or type the address) on a phone on the same Wi-Fi, choose pictures, and
 * they arrive in "My uploads". The little web server runs only while this screen is open.
 */
public class UploadActivity extends Activity {
    private UploadServer server;
    private QrView qr;
    private TextView address;
    private TextView log;
    private int received;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = Ui.dp(this, 48);
        LinearLayout root = Ui.row(this);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_VERTICAL);

        qr = new QrView(this);
        int side = Ui.dp(this, 340);
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(side, side);
        qp.setMargins(0, 0, Ui.dp(this, 48), 0);
        root.addView(qr, qp);

        LinearLayout text = Ui.column(this);
        text.addView(Ui.heading(this, getString(R.string.upload_phone)), Ui.gap(this, 0, 12));
        text.addView(Ui.text(this, getString(R.string.upload_steps), 19, Ui.TEXT), Ui.gap(this, 0, 16));
        address = Ui.text(this, "", 22, Ui.ACCENT);
        text.addView(address, Ui.gap(this, 0, 16));
        log = Ui.text(this, getString(R.string.upload_waiting), 17, Ui.MUTED);
        text.addView(log, Ui.gap(this, 0, 24));
        TextView done = Ui.button(this, getString(R.string.done), v -> finish());
        text.addView(done);
        root.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Ui.screen(this, root);
        done.requestFocus();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStart() {
        super.onStart();
        String ip = localAddress();
        if (ip == null) {
            address.setText(R.string.upload_no_network);
            qr.setText(null);
            return;
        }
        server = new UploadServer(Library.get(this), getCacheDir(), new UploadServer.Listener() {
            @Override
            public void received(Artwork a) {
                runOnUiThread(() -> {
                    received++;
                    log.setText(getString(R.string.upload_got, received, a.title, a.width, a.height));
                });
            }

            @Override
            public void failed(String name, String why) {
                runOnUiThread(() -> log.setText(getString(R.string.upload_failed, name, why)));
            }
        });
        server.screenRatio = Images.screenRatio(this);
        try {
            int port = server.start();
            String url = "http://" + ip + ":" + port + "/" + server.token();
            address.setText(url);
            qr.setText(url);
        } catch (Exception e) {
            address.setText(getString(R.string.upload_no_server, String.valueOf(e.getMessage())));
        }
    }

    @Override
    protected void onStop() {
        if (server != null) server.stop();
        server = null;
        super.onStop();
    }

    /** The TV's IPv4 address on the home network (Wi-Fi or Ethernet). */
    static String localAddress() {
        try {
            String fallback = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || !a.isSiteLocalAddress()) continue;
                    String name = ni.getName();
                    if (name.startsWith("wlan") || name.startsWith("eth")) return a.getHostAddress();
                    if (fallback == null) fallback = a.getHostAddress();
                }
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }
}
