package io.github.maxhayim.manager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.util.LruCache;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The catalog for the screens: read from the repository, kept on the TV so it opens without the network, and
 * the add-ons' package names, to tell which are installed. Loading is blocking: call it off the main thread.
 */
final class Repo {
    static volatile List<Catalog.Item> addons = new ArrayList<>();
    static volatile List<Catalog.Item> themes = new ArrayList<>();
    static volatile List<Catalog.Item> switches = new ArrayList<>();
    /** Add-on folder to package name. */
    static final Map<String, String> packages = new ConcurrentHashMap<>();
    static volatile boolean fresh;
    private static final LruCache<String, Bitmap> pictures = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private Repo() {
    }

    /**
     * Reads the READMEs, from the repository when it answers, else the copies from last time; {@code listed} runs
     * once they are in, so the lists show while the add-ons' package names are still being looked up.
     */
    static void load(Context c, Runnable listed) {
        String addonsMd = fetch(c, "addons/README.md");
        String themesMd = fetch(c, "themes/README.md");
        String switchesMd = fetch(c, "docs/switches.md");
        fresh = addonsMd != null && !addonsMd.startsWith(CACHED);
        if (addonsMd != null) addons = Catalog.addons(strip(addonsMd));
        if (themesMd != null) themes = Catalog.themes(strip(themesMd));
        if (themesMd != null) switches = Catalog.switches(strip(themesMd), switchesMd == null ? null : strip(switchesMd));
        listed.run();
        // One request after another took minutes on a slow link: look them up a few at a time.
        ExecutorService pool = Executors.newFixedThreadPool(6);
        for (Catalog.Item a : addons) {
            if (packages.containsKey(a.id)) continue;
            pool.execute(() -> {
                String gradle = fetch(c, "addons/" + a.id + "/app/build.gradle");
                String pkg = Catalog.packageOf(gradle == null ? null : strip(gradle));
                if (pkg == null) {
                    gradle = fetch(c, "addons/" + a.id + "/app/build.gradle.kts");
                    pkg = Catalog.packageOf(gradle == null ? null : strip(gradle));
                }
                if (pkg != null) packages.put(a.id, pkg);
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(2, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final String CACHED = "\u0000cached\n";

    /** The file from the repository, saved for next time; else last time's copy, marked as such. */
    private static String fetch(Context c, String path) {
        File f = new File(c.getCacheDir(), "repo/" + path.replace('/', '_'));
        String s = Net.text(path);
        if (s != null) {
            try {
                f.getParentFile().mkdirs();
                Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // Only the offline copy is lost.
            }
            return s;
        }
        try {
            return f.isFile() ? CACHED + new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String strip(String s) {
        return s.startsWith(CACHED) ? s.substring(CACHED.length()) : s;
    }

    static boolean installed(Context c, Catalog.Item a) {
        String pkg = packages.get(a.id);
        if (pkg == null) return false;
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** A screenshot, scaled for the screen. Blocking. */
    static Bitmap picture(String path) {
        if (path == null) return null;
        Bitmap b = pictures.get(path);
        if (b != null) return b;
        byte[] data = Net.get(path);
        if (data == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= 960) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (b != null) pictures.put(path, b);
        return b;
    }

    static Catalog.Item find(Catalog.Kind kind, String id) {
        List<Catalog.Item> list = kind == Catalog.Kind.ADDON ? addons : kind == Catalog.Kind.THEME ? themes : switches;
        for (Catalog.Item i : list) if (i.id.equals(id)) return i;
        return null;
    }

    /** This TV's address on the home network, for the commands shown. */
    static String ip(Context c) {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        Network n = cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        if (lp == null) return null;
        for (LinkAddress la : lp.getLinkAddresses()) {
            if (la.getAddress() instanceof Inet4Address) return la.getAddress().getHostAddress();
        }
        return null;
    }
}
