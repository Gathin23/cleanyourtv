package com.lionlev.tv.fps;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Debug;
import android.os.Process;
import java.io.*;
import java.util.concurrent.*;

/** App-UID sampler using public APIs and explicit, one-time diagnostic/usage grants. */
final class LocalSampler implements AutoCloseable {
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "frames-reader"); thread.setDaemon(true); return thread;
    });
    private final FpsService owner;
    private final Context context;
    private final FrameStats rates = new FrameStats();
    private volatile boolean closed;
    private boolean collecting;
    private long lastQuery, latestResume;
    private String app, component;

    static boolean available(Context context) {
        if (context.checkSelfPermission(Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) return false;
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName())
                == AppOpsManager.MODE_ALLOWED;
    }
    LocalSampler(FpsService owner) { this.owner = owner; context = owner.getApplicationContext(); }
    void start() { WORKER.execute(this::tick); }

    private void foreground() {
        long now = System.currentTimeMillis();
        if (now < lastQuery) { latestResume = 0; app = null; component = null; }
        long begin = lastQuery == 0 ? now - 120000 : Math.max(now - 120000, lastQuery - 2000);
        UsageEvents events = context.getSystemService(UsageStatsManager.class).queryEvents(begin, now);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND && event.getTimeStamp() >= latestResume) {
                latestResume = event.getTimeStamp();
                app = event.getPackageName();
                String name = event.getClassName();
                if (name != null && name.startsWith(".")) name = app + name;
                component = app == null || name == null ? null : app + "/" + name;
            }
        }
        lastQuery = now;
    }
    private boolean home() {
        if (app == null || app.equals(context.getPackageName()) || app.equals("com.gathin.tvhome")) return true;
        ResolveInfo resolved = context.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && resolved.activityInfo != null && app.equals(resolved.activityInfo.packageName);
    }
    private void tick() {
        if (closed) return;
        try {
            if (!available(context)) { owner.postAccessLost(); close(); return; }
            foreground();
            String power = dump("power");
            boolean awake = java.util.regex.Pattern.compile("(?m)^\\s*mWakefulness=Awake\\s*$").matcher(power).find();
            if (!awake || home() || component == null) {
                stopCapture(); owner.postSample(-1, null);
            } else {
                if (!collecting) { dump("SurfaceFlinger", "--timestats", "-enable"); collecting = true; }
                String target = component, targetApp = app;
                long before = System.nanoTime();
                String live = dump("SurfaceFlinger", "--list");
                String stats = dump("SurfaceFlinger", "--timestats", "-dump");
                long now = before + (System.nanoTime() - before) / 2;
                FrameStats.Reading reading = FrameStats.select(target, live, stats);
                double value = rates.sample(reading, now);
                foreground();
                if (!target.equals(component) || home()) { value = -1; rates.reset(); }
                owner.postSample(value, targetApp);
            }
        } catch (IOException | RuntimeException error) {
            stopCapture(); owner.postSample(-1, null);
        } finally {
            if (!closed) WORKER.schedule(this::tick, 1, TimeUnit.SECONDS);
        }
    }
    private void stopCapture() {
        if (collecting) {
            try { dump("SurfaceFlinger", "--timestats", "-disable"); } catch (IOException | RuntimeException ignored) { }
            collecting = false;
        }
        rates.reset();
    }
    private String dump(String service, String... args) throws IOException {
        // All callers above use fixed service names/arguments, never Intent or provider input.
        if (!(service.equals("power") && args.length == 0) && !service.equals("SurfaceFlinger"))
            throw new IllegalArgumentException("Unsupported diagnostic service");
        File file = File.createTempFile("frames-query", ".tmp", context.getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                if (!Debug.dumpService(service, output.getFD(), args)) throw new IOException("Service unavailable");
            }
            if (file.length() > 2097152) throw new IOException("Diagnostic output too large");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
            }
            String result = output.toString("UTF-8");
            if (result.contains("Permission Denial") || result.contains("Permission denied")) throw new IOException("Access denied");
            return result;
        } finally { file.delete(); }
    }
    public void close() { closed = true; WORKER.execute(this::stopCapture); }
}
