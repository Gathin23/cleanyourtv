package io.github.anhost77.salon;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * The household's streaming platforms and "Voir sur…": open the platform's app as close to the title as it allows.
 * See {@link #intentFor} for what each one opens. Build the intent off the main thread (network requests).
 */
final class Platforms {
    static final String MYCANAL = "com.canal.android.canal";

    private Platforms() { }

    /** Installed platforms, and the myCANAL ones: name, TMDB provider, package, the app the button names. */
    static List<String[]> household(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        boolean hasCanal = pm.getLeanbackLaunchIntentForPackage(MYCANAL) != null;
        List<String[]> out = new ArrayList<>();
        for (String[] p : Tmdb.PLATFORMS) {
            if (Settings.off(Settings.PLATFORMS, p[1])) continue; // Réglages
            if (pm.getLeanbackLaunchIntentForPackage(p[2]) != null) out.add(new String[] {p[0], p[1], p[2], p[0]});
            else if (hasCanal && Tmdb.VIA_MYCANAL.contains(p[1])) out.add(new String[] {p[0], p[1], MYCANAL, "myCANAL"});
        }
        return out;
    }

    /** TF1+ shows a programme at tf1.fr/{channel}/{slug}; the channels of the group, most likely first. */
    private static final String[] TF1_CHANNELS = {"tf1", "tmc", "tfx", "tf1-series-films", "lci"};

    /**
     * The intent that shows {@code title} in the platform's app, or its home. Blocking (network): call it off the
     * main thread. Checked on the TV (Nokia, Android 11, 05/11/2026), each one also through the profile chooser:
     * Netflix opens the title page (Wikidata id); Prime Video and Disney+ open their search filled with the title;
     * TF1+ opens the programme page when tf1.fr has one. myCANAL (and Paramount+, Apple TV, HBO Max inside it) and
     * M6+ ignore every search link and need internal ids, so they open on their home.
     */
    static Intent intentFor(Context ctx, String[] platform, Tmdb.Title title) {
        String pkg = platform[2];
        if (title != null && title.name != null && !title.name.isEmpty()) {
            String q = Uri.encode(title.name);
            switch (platform[1]) {
                case "8": {
                    // Wikidata misses ~10 % of the catalogue (and answers 502 now and then): JustWatch as backup.
                    String id = Tmdb.wikidataId(title.id, title.tv, "P1874");
                    if (id == null) id = Tmdb.wikidataId(title.id, title.tv, "P1874");
                    if (id != null) return netflix("https://www.netflix.com/title/" + id);
                    String url = Tmdb.justWatchUrl(title.id, title.tv, title.name, "netflix");
                    if (url != null) return netflix(url);
                    break;
                }
                // Apple TV (tv.apple.com) and Paramount+ (paramountplus.com) open their own links; JustWatch has one
                // for every title. Without the app (pkg = myCANAL) they fall through to myCANAL's page below.
                case "350":
                case "531": {
                    if (MYCANAL.equals(pkg)) break;
                    String url = Tmdb.justWatchUrl(title.id, title.tv, title.name,
                            "350".equals(platform[1]) ? "appletvplus" : "paramountplus");
                    if (url == null) break;
                    // Paramount+ links point at an episode: keep the show page (…/shows/{slug}/).
                    java.util.regex.Matcher show = java.util.regex.Pattern
                            .compile("^(https://www\\.paramountplus\\.com/shows/[^/]+/)").matcher(url);
                    return view(show.find() ? show.group(1) : url, pkg);
                }
                case "147": {
                    String url = Tmdb.justWatchUrl(title.id, title.tv, title.name, "sixplay");
                    if (url != null) return view(url, pkg);
                    break;
                }
                case "119":
                    if (!MYCANAL.equals(pkg)) return view("https://app.primevideo.com/search?phrase=" + q, pkg);
                    break;
                case "337":
                    if (!MYCANAL.equals(pkg)) return view("https://www.disneyplus.com/search?q=" + q, pkg);
                    break;
                case "1754": {
                    String url = tf1Page(title.name);
                    if (url != null) return view(url, pkg);
                    break;
                }
                default:
                    break;
            }
            // myCANAL ignores search links but opens its title pages (…/h/{id}), which JustWatch lists for Canal+
            // and for the Apple TV, Paramount+ and HBO Max titles the CANAL+ offer carries.
            if (MYCANAL.equals(pkg)) {
                String url = Tmdb.justWatchUrl(title.id, title.tv, title.name, "canal");
                if (url != null && (url.contains("canalplus.com/") || url.contains("mycanal.fr/"))) {
                    return view(url, pkg);
                }
            }
        }
        return ctx.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
    }

    /**
     * Netflix's TV app lands on its home for a plain link: it opens the title only when the link names its main
     * screen and carries source=30 (the partner launch). Checked by eye on the TV on 05/11/2026 (screens are secure).
     */
    private static Intent netflix(String url) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setClassName("com.netflix.ninja", "com.netflix.ninja.MainActivity").putExtra("source", "30");
    }

    private static Intent view(String url, String pkg) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg);
    }

    /** "Joséphine, ange gardien" → "josephine-ange-gardien", the way tf1.fr writes its programme addresses. */
    static String slug(String name) {
        String s = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT)
                .replace("&", " et ").replace("'", " ").replace("’", " ");
        return s.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    /** The tf1.fr page of the programme, tried on each channel of the group, or null when there is none. */
    private static String tf1Page(String name) {
        String slug = slug(name);
        if (slug.isEmpty()) return null;
        for (String channel : TF1_CHANNELS) {
            String url = "https://www.tf1.fr/" + channel + "/" + slug;
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setRequestMethod("HEAD");
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0");
                int code = c.getResponseCode();
                if (code == 200) return url;
                // Another channel of the group owns it: tf1.fr points there.
                String moved = c.getHeaderField("Location");
                if (code >= 300 && code < 400 && moved != null) {
                    return moved.startsWith("http") ? moved : "https://www.tf1.fr" + moved;
                }
            } catch (java.io.IOException offline) {
                return null;
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return null;
    }

    static void start(Context ctx, Intent i, String pkg) {
        try {
            if (i != null) {
                // A fresh task, so an app already open lands on the title instead of where it was left.
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                return;
            }
        } catch (RuntimeException refused) {
            // Falls back to the app's home below.
        }
        Intent home = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (home != null) ctx.startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
