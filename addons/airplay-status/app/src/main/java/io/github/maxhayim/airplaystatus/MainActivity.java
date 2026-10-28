package io.github.maxhayim.airplaystatus;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The tile and module card open this. It looks for AirPlay receivers the way an iPhone does (mDNS on the home
 * network, for a few seconds while the screen is open) and says how this TV appears: its name, and whether
 * screen mirroring is offered or audio only. Nothing runs when the screen is closed.
 */
public class MainActivity extends Activity {
    private static final String TAG = "AirPlayStatus";
    private static final String SERVER = "io.github.jqssun.airplay";
    private static final long LOOK_MS = 6000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private NsdManager nsd;
    private final List<NsdManager.DiscoveryListener> discoveries = new ArrayList<>();
    /** Resolved one at a time: older Android versions refuse a second resolve while one runs. */
    private final ArrayDeque<NsdServiceInfo> toResolve = new ArrayDeque<>();
    private boolean resolving;
    private final List<Receivers.Seen> seen = new ArrayList<>();
    private TextView status;
    private Row nameRow, mirroringRow, othersRow, serverRow, againRow;
    private boolean looking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nsd = getSystemService(NsdManager.class);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0D1117);
        page.setPadding(px(48), px(32), px(48), px(24));
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xFF8B949E);
        status.setPadding(0, 0, 0, px(16));
        page.addView(title);
        page.addView(status);
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);

        nameRow = add(rows, new Row(this, "iPhones and Macs see this TV as", null, null));
        mirroringRow = add(rows, new Row(this, "Screen mirroring", null, null));
        othersRow = add(rows, new Row(this, "Other AirPlay receivers here", null, null));
        serverRow = add(rows, new Row(this, "AirPlay Server", "The app that makes this TV an AirPlay receiver.",
                this::openServer));
        againRow = add(rows, new Row(this, "Look again", "Checks the network once more.", this::look));
        againRow.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        look();
    }

    @Override
    protected void onPause() {
        stopLooking();
        super.onPause();
    }

    private Row add(LinearLayout rows, Row r) {
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void look() {
        stopLooking();
        seen.clear();
        toResolve.clear();
        looking = true;
        status.setText("Looking on the network…");
        for (String type : new String[]{Receivers.AIRPLAY, Receivers.RAOP}) {
            NsdManager.DiscoveryListener l = new NsdManager.DiscoveryListener() {
                @Override public void onServiceFound(NsdServiceInfo info) {
                    main.post(() -> {
                        toResolve.add(info);
                        resolveNext();
                    });
                }
                @Override public void onServiceLost(NsdServiceInfo info) { }
                @Override public void onDiscoveryStarted(String t) { }
                @Override public void onDiscoveryStopped(String t) { }
                @Override public void onStartDiscoveryFailed(String t, int code) { Log.w(TAG, "discovery " + t + ": " + code); }
                @Override public void onStopDiscoveryFailed(String t, int code) { }
            };
            try {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l);
                discoveries.add(l);
            } catch (RuntimeException e) {
                Log.w(TAG, "discovery not started: " + e.getMessage());
            }
        }
        main.postDelayed(() -> {
            stopLooking();
            show();
        }, LOOK_MS);
        show();
    }

    private void stopLooking() {
        main.removeCallbacksAndMessages(null);
        for (NsdManager.DiscoveryListener l : discoveries) {
            try {
                nsd.stopServiceDiscovery(l);
            } catch (RuntimeException ignored) {
                // Already stopped.
            }
        }
        discoveries.clear();
        looking = false;
    }

    @SuppressWarnings("deprecation") // resolveService works on every version this app supports.
    private void resolveNext() {
        if (resolving || toResolve.isEmpty()) return;
        resolving = true;
        NsdServiceInfo info = toResolve.poll();
        nsd.resolveService(info, new NsdManager.ResolveListener() {
            @Override public void onServiceResolved(NsdServiceInfo r) {
                InetAddress host = r.getHost();
                String type = r.getServiceType() == null ? "" : r.getServiceType().replaceFirst("^\\.", "");
                main.post(() -> {
                    seen.add(new Receivers.Seen(type, r.getServiceName(), host == null ? null : host.getHostAddress()));
                    resolving = false;
                    show();
                    resolveNext();
                });
            }

            @Override public void onResolveFailed(NsdServiceInfo r, int code) {
                main.post(() -> {
                    resolving = false;
                    resolveNext();
                });
            }
        });
    }

    private void show() {
        Set<String> own = ownAddresses();
        Receivers.Mine mine = Receivers.mine(seen, own);
        boolean installed = installed();
        String device = Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME);
        nameRow.setValue(mine.name != null ? mine.name : looking ? "…" : "Not seen");
        mirroringRow.setValue(mine.state == Receivers.State.MIRRORING ? "Offered"
                : mine.state == Receivers.State.AUDIO_ONLY ? "Audio only" : looking ? "…" : "No");
        List<String> others = Receivers.others(seen, own);
        othersRow.setValue(others.isEmpty() ? (looking ? "…" : "None") : String.valueOf(others.size()));
        othersRow.setDetail(others.isEmpty() ? null : String.join(", ", others));
        serverRow.setValue(installed ? "Open" : "Not installed");
        if (!looking) status.setText(Receivers.advice(mine, installed, device));
    }

    private Set<String> ownAddresses() {
        Set<String> out = new HashSet<>();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network n = cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        if (lp != null) for (LinkAddress la : lp.getLinkAddresses()) out.add(la.getAddress().getHostAddress());
        return out;
    }

    private boolean installed() {
        try {
            getPackageManager().getPackageInfo(SERVER, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void openServer() {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(SERVER);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(SERVER);
        if (i == null) return;
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ignored) {
            // Nothing to open.
        }
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
