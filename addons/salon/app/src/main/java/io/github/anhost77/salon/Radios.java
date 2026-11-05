package io.github.anhost77.salon;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The radios of the launcher's TV panel (its Radios tab): content://...now/radio gives name, logo PNG, playing (0/1)
 * and kind; {@link RadioService} plays them. Streams from the stations' own servers (checked with Radio Browser, an
 * open directory, on 05/11/2026). Logos are the stations' site icons, fetched once and kept in files/radio.
 */
final class Radios {
    static final Uri URI = Uri.parse("content://io.github.anhost77.salon.now/radio");
    private static final String[] COLUMNS = {"name", "logo", "playing", "kind"};
    /** Name, stream, site (for the logo), kind. */
    static final String[][] STATIONS = {
            {"France Inter", "https://icecast.radiofrance.fr/franceinter-hifi.aac", "radiofrance.fr/franceinter", "Infos"},
            {"franceinfo", "https://icecast.radiofrance.fr/franceinfo-hifi.aac", "francetvinfo.fr", "Infos"},
            {"RTL", "http://icecast.rtl.fr/rtl-1-44-128", "rtl.fr", "Infos"},
            {"Europe 1", "https://stream.europe1.fr/europe1.aac", "europe1.fr", "Infos"},
            {"RMC", "https://audio.bfmtv.com/rmcradio_128.mp3", "rmc.bfmtv.com", "Infos"},
            {"NRJ", "https://streaming.nrjaudio.fm/ou5k6tirv93h", "nrj.fr", "Musique"},
            {"Fun Radio", "http://icecast.funradio.fr/fun-1-44-128", "funradio.fr", "Musique"},
            {"Skyrock", "http://icecast.skyrock.net/s/natio_mp3_128k", "skyrock.com", "Musique"},
            {"RTL2", "http://icecast.rtl2.fr/rtl2-1-44-128", "rtl2.fr", "Musique"},
            {"Nostalgie", "https://streaming.nrjaudio.fm/ouwgwqsk6j4d", "nostalgie.fr", "Musique"},
            {"Chérie FM", "https://cdnradio.streamakaci.com/cheriefm1.mp3", "cheriefm.fr", "Musique"},
            {"Virgin Radio", "http://virageradio.ice.infomaniak.ch/virageradio-natio.mp3", "virginradio.fr", "Musique"},
            {"FIP", "https://icecast.radiofrance.fr/fip-hifi.aac", "radiofrance.fr/fip", "Musique"},
            {"France Musique", "https://icecast.radiofrance.fr/francemusique-hifi.aac", "radiofrance.fr/francemusique", "Classique"},
            {"Radio Classique", "https://str0.creacast.com/classique1", "radioclassique.fr", "Classique"},
            {"TSF Jazz", "http://tsfjazz.ice.infomaniak.ch/tsfjazz-high.mp3", "tsfjazz.com", "Jazz"},
            {"Pomme d'Api", "https://www.radiopommedapi.com/radio.aac", "radiopommedapi.com", "Enfants"},
            {"Radio Junior", "https://stream.votreradiosurlenet.eu/radiojunior.mp3", "radiojunior.com", "Enfants"}};
    private static final AtomicBoolean fetching = new AtomicBoolean();

    private Radios() { }

    /** The stations Réglages leaves on, as indexes into {@link #STATIONS}: the launcher's rows, in order. */
    static int[] shown() {
        int[] out = new int[STATIONS.length];
        int n = 0;
        for (int i = 0; i < STATIONS.length; i++) if (!Settings.off(Settings.RADIOS, STATIONS[i][0])) out[n++] = i;
        return java.util.Arrays.copyOf(out, n);
    }

    static Cursor query(Context ctx) {
        MatrixCursor out = new MatrixCursor(COLUMNS);
        int playing = RadioService.playing(ctx);
        File dir = new File(ctx.getFilesDir(), "radio");
        boolean missing = false;
        for (int i : shown()) {
            File f = new File(dir, i + ".png");
            byte[] logo = null;
            try {
                if (f.isFile()) logo = Files.readAllBytes(f.toPath());
                else missing = true;
            } catch (Exception ignored) {
                // Name only.
            }
            out.addRow(new Object[] {STATIONS[i][0], logo, i == playing ? 1 : 0, STATIONS[i][3]});
        }
        if (missing) fetchLogos(ctx.getApplicationContext());
        out.setNotificationUri(ctx.getContentResolver(), URI);
        return out;
    }

    private static void fetchLogos(Context ctx) {
        if (!fetching.compareAndSet(false, true)) return;
        new Thread(() -> {
            File dir = new File(ctx.getFilesDir(), "radio");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                fetching.set(false);
                return;
            }
            boolean added = false;
            for (int i = 0; i < STATIONS.length; i++) {
                File f = new File(dir, i + ".png");
                if (f.isFile()) continue;
                String host = STATIONS[i][2].split("/")[0];
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL("https://www.google.com/s2/favicons?sz=128&domain=" + host).openConnection();
                    c.setConnectTimeout(8_000);
                    c.setReadTimeout(10_000);
                    Bitmap b;
                    try (InputStream in = c.getInputStream()) {
                        b = BitmapFactory.decodeStream(in);
                    }
                    if (b == null || b.getWidth() < 32) continue;
                    try (FileOutputStream o = new FileOutputStream(f)) {
                        b.compress(Bitmap.CompressFormat.PNG, 100, o);
                    }
                    added = true;
                } catch (Exception skip) {
                    // That station keeps its name.
                } finally {
                    if (c != null) c.disconnect();
                }
            }
            fetching.set(false);
            if (added) ctx.getContentResolver().notifyChange(URI, null);
        }, "radio-logos").start();
    }
}
