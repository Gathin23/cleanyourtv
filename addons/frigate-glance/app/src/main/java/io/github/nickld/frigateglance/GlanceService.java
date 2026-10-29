package io.github.nickld.frigateglance;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.net.SocketFactory;

import org.json.JSONObject;

/**
 * Stays connected to MQTT and turns Frigate's messages into cards. One thread runs the connection and
 * reconnects with backoff; another owns the CardController, so card decisions never race.
 */
public class GlanceService extends Service {
    private static final String TAG = ConfigStore.TAG;
    private static final String CHANNEL = "status";
    private static final int NOTIFICATION_ID = 1;

    private static volatile GlanceService instance;
    private static volatile String status = "Starting";

    private HandlerThread cardThread;
    private Handler cards;
    private Thread network;
    private volatile boolean running;
    /** Bumped on every restart; a connection loop from an older generation stops. */
    private volatile int generation;
    private volatile MqttClient mqtt;
    private volatile GlanceConfig config;
    private volatile FrigateApi frigate;
    private volatile Map<String, int[]> detectSizes = Collections.emptyMap();
    private volatile Map<String, String> liveStreams = Collections.emptyMap();
    private volatile List<String> cameraNames = Collections.emptyList();
    private volatile JSONObject frigateConfig;
    private CardController controller;
    /** Re-reads Frigate's config off the network thread when an unknown camera appears (a replay, a new camera). */
    private final java.util.concurrent.ExecutorService refetcher = java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile long lastRefetch;
    private static final long REFETCH_EVERY_MS = 60_000;
    private OverlayCard card;
    private volatile boolean mqttUp, frigateUp;
    /** Wall-clock times for the settings screen's Overview: 0 means never. */
    private volatile long mqttSince, frigateSince, lastMessageAt;
    private volatile String mqttError = "", frigateError = "";

    public static void start(Context context) {
        context.startForegroundService(new Intent(context, GlanceService.class));
    }

    /** Re-reads the config and reconnects; for the settings screen and a newly pushed config. */
    public static void reload(Context context) {
        GlanceService s = instance;
        if (s == null) start(context);
        else s.restartNetwork();
    }

    /** Applies a change from the settings screen without reconnecting: filters, card look, keys. */
    public static void settingsChanged(Context context) {
        GlanceService s = instance;
        if (s == null) {
            start(context);
            return;
        }
        GlanceConfig c = new ConfigStore(s).load();
        if (c == null) return;
        s.config = c;
        s.cards.post(() -> s.setUpCards(c));
    }

    /** A test card asked for before the service was ready; shown once cards are set up. "" = first camera. */
    private static volatile String pendingTest;

    public static void showTestCard(Context context, String camera) {
        GlanceService s = instance;
        if (s == null || s.cards == null || s.controller == null) {
            pendingTest = camera == null ? "" : camera;
            if (s == null) start(context);
            return;
        }
        s.cards.post(() -> {
            if (s.controller == null) return;
            String cam = camera;
            if (cam == null) cam = s.firstCamera();
            s.controller.showTest(cam);
        });
    }

    public static void dismissCard() {
        GlanceService s = instance;
        if (s != null && s.cards != null) s.cards.post(() -> {
            if (s.controller == null) return;
            s.controller.dismiss();
            Log.i(TAG, "card dismissed");
        });
    }

    public static void snoozeCard() {
        GlanceService s = instance;
        if (s != null && s.cards != null) {
            s.cards.post(() -> {
                if (s.controller == null || s.config == null) return;
                s.controller.snooze(s.config.snoozeMinutes);
                Log.i(TAG, "snoozed for " + s.config.snoozeMinutes + " min");
            });
        }
    }

    public static void resume() {
        GlanceService s = instance;
        if (s != null && s.cards != null) s.cards.post(() -> { if (s.controller != null) s.controller.resume(); });
    }

    /** When snooze ends, in System.currentTimeMillis() terms, or 0. */
    public static long snoozedUntilWallClock() {
        GlanceService s = instance;
        CardController c = s == null ? null : s.controller;
        if (c == null) return 0;
        long until = c.snoozedUntil();
        return until == 0 ? 0 : System.currentTimeMillis() + (until - SystemClock.elapsedRealtime());
    }

    /** Width over height of a camera's frame; 16/9 when unknown. */
    static float aspect(String camera) {
        GlanceService s = instance;
        int[] size = s == null ? null : s.detectSizes.get(camera);
        return size == null ? 16f / 9f : (float) size[0] / size[1];
    }

    /** The camera's RTSP address, whatever the live video setting (the full screen view always tries it). */
    static String liveUrl(String camera) {
        GlanceService s = instance;
        GlanceConfig cfg = s == null ? null : s.config;
        // A debug replay plays a recording inside Frigate; go2rtc has no stream for it, so there is no live video.
        if (cfg == null || camera.startsWith(ReviewFilter.REPLAY_PREFIX)) return null;
        String stream = cfg.streams.containsKey(camera) ? cfg.streams.get(camera) : s.streamFor(camera);
        return LiveVideo.rtspUrl(cfg.frigateHost(), cfg.rtspUser, cfg.rtspPassword, stream);
    }

    /** A full-size snapshot for the full screen view. */
    static byte[] snapshot(String camera) throws IOException {
        GlanceService s = instance;
        FrigateApi f = s == null ? null : s.frigate;
        return f == null ? null : f.latestJpeg(camera, 1080);
    }

    /** The full screen key: the card closes and its camera opens full screen over whatever was playing. */
    public static void openFullscreen() {
        GlanceService s = instance;
        if (s == null || s.cards == null) return;
        s.cards.post(() -> {
            if (s.controller == null || s.controller.shownCamera() == null) return;
            String camera = s.controller.shownCamera();
            String base = ReviewFilter.baseCamera(camera);
            GlanceConfig c = s.config;
            String title = c != null && c.cameras.containsKey(base) ? c.cameras.get(base) : CardController.pretty(base);
            s.controller.dismiss();
            Intent i = new Intent(s, FullscreenActivity.class)
                    .putExtra(FullscreenActivity.EXTRA_CAMERA, camera)
                    .putExtra(FullscreenActivity.EXTRA_TITLE, title)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
            s.startActivity(i);
            Log.i(TAG, "full screen: " + camera);
        });
    }

    /** A hold key is down on a card: show its progress. action is KeyRules.DISMISS, SNOOZE or FULLSCREEN. */
    static void holdStarted(int action) {
        GlanceService s = instance;
        if (s != null && s.card != null) s.card.showHold(action);
    }

    static void holdEnded() {
        GlanceService s = instance;
        if (s != null && s.card != null) s.card.endHold();
    }

    public static boolean isCardShowing() {
        GlanceService s = instance;
        return s != null && s.card != null && s.card.reviewId() != null;
    }

    /** What the Overview shows; read from the settings screen's thread, so a copy of volatile fields. */
    static final class Overview {
        boolean running, mqttUp, frigateUp;
        String mqttHost = "", frigateHost = "", mqttError = "", frigateError = "";
        /** SHA-256 of the pinned certificate and of a different one Frigate now presents ("" for none). */
        String pin = "", offeredPin = "";
        long mqttSince, frigateSince, lastMessageAt;
        int cameras;
    }

    /** Reads Frigate's config again, first trusting the certificate it now presents if asked; the Overview's Frigate row. */
    public static void checkFrigate(boolean trustNewCertificate) {
        GlanceService s = instance;
        FrigateApi f = s == null ? null : s.frigate;
        if (f == null) return;
        s.refetcher.execute(() -> {
            if (trustNewCertificate && f.trustOffered()) Log.i(TAG, "trusted Frigate's new certificate " + f.pin().substring(0, 12));
            s.fetchFrigateConfig();
        });
    }

    static Overview overview() {
        Overview o = new Overview();
        GlanceService s = instance;
        if (s == null) return o;
        o.running = true;
        o.mqttUp = s.mqttUp;
        o.frigateUp = s.frigateUp;
        o.mqttSince = s.mqttSince;
        o.frigateSince = s.frigateSince;
        o.lastMessageAt = s.lastMessageAt;
        o.mqttError = s.mqttError;
        o.frigateError = s.frigateError;
        o.cameras = s.cameraNames.size();
        FrigateApi f = s.frigate;
        if (f != null) {
            o.pin = f.pin();
            o.offeredPin = f.offeredPin();
        }
        GlanceConfig c = s.config;
        if (c != null) {
            o.mqttHost = hostPort(c.mqttUrl, 1883);
            o.frigateHost = hostPort(c.frigateUrl, -1);
        }
        return o;
    }

    /** host:port of a URL, without the user and password it may carry. */
    static String hostPort(String url, int defaultPort) {
        try {
            java.net.URI u = new java.net.URI(url);
            int port = u.getPort() > 0 ? u.getPort() : defaultPort;
            return u.getHost() + (port > 0 ? ":" + port : "");
        } catch (java.net.URISyntaxException | RuntimeException e) {
            return "";
        }
    }

    public static String statusLine() {
        return status;
    }

    /** Frigate's config as last fetched, or null; for the settings screen's camera and object lists. */
    public static JSONObject frigateConfig() {
        GlanceService s = instance;
        return s == null ? null : s.frigateConfig;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN));
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification());
        }
        cardThread = new HandlerThread("glance-cards");
        cardThread.start();
        cards = new Handler(cardThread.getLooper());
        cards.postDelayed(this::tick, 1000);
        restartNetwork();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        MqttClient m = mqtt;
        if (m != null) m.stop();
        if (card != null) card.hide();
        cardThread.quitSafely();
        refetcher.shutdownNow();
        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void restartNetwork() {
        int gen = ++generation;
        running = true;
        MqttClient m = mqtt;
        if (m != null) m.stop();
        network = new Thread(() -> connectLoop(gen), "glance-network");
        network.start();
    }

    private void connectLoop(int gen) {
        ConfigStore store = new ConfigStore(this);
        store.importPushed();
        GlanceConfig c = store.load();
        if (c == null) {
            setStatus("No config yet: push one with install.sh");
            Log.w(TAG, "mqtt error: no config");
            return;
        }
        config = c;
        frigate = new FrigateApi(c.frigateUrl, c.frigateUser, c.frigatePassword, store.certPin(), store::saveCertPin);
        cards.post(() -> setUpCards(c));
        String clientId = "frigate-glance-" + androidIdPrefix();
        String p = c.topicPrefix;
        List<String> topics = List.of(p + "/reviews", p + "/events", p + "/available");
        for (int attempt = 0; running && gen == generation; ) {
            fetchFrigateConfig();
            boolean[] connected = {false};
            MqttClient client;
            try {
                client = new MqttClient(c.mqttUrl, clientId, topics, new MqttClient.Listener() {
                @Override
                public void onConnected() {
                    connected[0] = true;
                    mqttUp = true;
                    mqttSince = System.currentTimeMillis();
                    mqttError = "";
                    Log.i(TAG, "mqtt connected");
                    updateStatus();
                }

                @Override
                public void onMessage(String topic, byte[] payload) {
                    lastMessageAt = System.currentTimeMillis();
                    route(topic, new String(payload, StandardCharsets.UTF_8));
                }

                @Override
                public void onDisconnected(Exception cause) {
                    mqttUp = false;
                    mqttSince = System.currentTimeMillis();
                    mqttError = cause.getMessage() == null ? "" : cause.getMessage();
                    if (running && gen == generation) Log.w(TAG, "mqtt error: " + cause.getMessage());
                    updateStatus();
                }
            });
            } catch (RuntimeException e) {
                Log.w(TAG, "mqtt error: the mqtt address in the config is not usable");
                return;
            }
            mqtt = client;
            client.run(SocketFactory.getDefault());
            if (connected[0]) attempt = 0;
            if (!running || gen != generation) break;
            try {
                Thread.sleep(MqttClient.backoffMs(attempt++));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void fetchFrigateConfig() {
        try {
            JSONObject fc = frigate.config();
            frigateConfig = fc;
            detectSizes = FrigateApi.detectSizes(fc);
            liveStreams = FrigateApi.liveStreams(fc);
            List<String> names = new ArrayList<>();
            JSONObject cams = fc.optJSONObject("cameras");
            if (cams != null) for (java.util.Iterator<String> it = cams.keys(); it.hasNext(); ) names.add(it.next());
            Collections.sort(names);
            cameraNames = names;
            if (!frigateUp) frigateSince = System.currentTimeMillis();
            frigateUp = true;
            frigateError = "";
            Log.i(TAG, "frigate ok");
        } catch (IOException | RuntimeException e) {
            if (frigateUp || frigateSince == 0) frigateSince = System.currentTimeMillis();
            frigateUp = false;
            frigateError = e.getMessage() == null ? "" : e.getMessage();
            Log.w(TAG, "frigate error: " + e.getMessage());
        }
        updateStatus();
    }

    private void setUpCards(GlanceConfig c) {
        if (card == null) {
            card = new OverlayCard(this, c, new OverlayCard.Source() {
                @Override
                public byte[] snapshot(String camera) throws IOException {
                    return frigate.latestJpeg(camera, 360);
                }

                @Override
                public float aspect(String camera) {
                    return GlanceService.aspect(camera);
                }

                @Override
                public String liveUrl(String camera) {
                    GlanceConfig cfg = config;
                    return cfg == null || !cfg.liveVideo ? null : GlanceService.liveUrl(camera);
                }

                @Override
                public boolean newCertificate() {
                    FrigateApi f = frigate;
                    return f != null && !f.offeredPin().isEmpty();
                }
            }, KeyService::setFiltering);
        } else {
            card.setConfig(c);
            card.hide();
        }
        long snoozed = controller == null ? 0 : controller.snoozedUntil();
        KeyService.reloadKeys(this);
        controller = new CardController(card, new ReviewFilter(c), c.cameras, c.dismissSeconds,
                c.dismissOnStationary, SystemClock::elapsedRealtime);
        if (snoozed > 0) controller.snooze((int) Math.max(1, (snoozed - SystemClock.elapsedRealtime()) / 60_000));
        String test = pendingTest;
        pendingTest = null;
        if (test != null) controller.showTest(test.isEmpty() ? firstCamera() : test);
    }

    private void route(String topic, String payload) {
        GlanceConfig c = config;
        if (c == null) return;
        String p = c.topicPrefix;
        if (topic.equals(p + "/reviews")) {
            Review r = Review.parse(payload);
            if (r != null) refetchIfUnknown(r.camera);
            if (r != null) cards.post(() -> { if (controller != null) controller.onReview(r); });
        } else if (topic.equals(p + "/events")) {
            Review replay = Review.fromReplayEvent(payload);
            if (replay != null) {
                refetchIfUnknown(replay.camera);
                cards.post(() -> { if (controller != null) controller.onReview(replay); });
            }
            Detection d = Detection.parse(payload, detectSizes);
            if (d != null) cards.post(() -> { if (controller != null) controller.onDetection(d); });
        } else if (topic.equals(p + "/available")) {
            boolean online = "online".equals(payload.trim());
            cards.post(() -> { if (controller != null) controller.onAvailable(online); });
        }
    }

    private void refetchIfUnknown(String camera) {
        long now = SystemClock.elapsedRealtime();
        if (detectSizes.containsKey(camera) || frigate == null || now - lastRefetch < REFETCH_EVERY_MS) return;
        lastRefetch = now;
        refetcher.execute(this::fetchFrigateConfig);
    }

    private void tick() {
        if (controller != null) controller.tick();
        cards.postDelayed(this::tick, 1000);
    }

    private String firstCamera() {
        GlanceConfig c = config;
        if (c != null && !c.cameras.isEmpty()) return c.cameras.keySet().iterator().next();
        List<String> names = cameraNames;
        return names.isEmpty() ? "camera" : names.get(0);
    }

    /** go2rtc stream for a camera, for live video. */
    String streamFor(String camera) {
        String s = liveStreams.get(camera);
        return s == null ? camera : s;
    }

    private String androidIdPrefix() {
        String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        return id == null || id.length() < 8 ? "tv" : id.substring(0, 8);
    }

    private void updateStatus() {
        String overlay = Settings.canDrawOverlays(this) ? "" : " · overlay not allowed";
        setStatus("MQTT " + (mqttUp ? "connected" : "not connected") + " · Frigate " + (frigateUp ? "ok" : "not reached") + overlay);
    }

    private void setStatus(String s) {
        status = s;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, notification());
    }

    private Notification notification() {
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_foreground)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(status)
                .setOngoing(true)
                .build();
    }
}
