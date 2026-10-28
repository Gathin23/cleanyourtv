package com.gathin.artframe;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Open-access museum collections searched by keyword. Only public-domain / CC0 works with an image are returned,
 * so whatever is saved may be shown at home without asking anyone. None needs an account or a key.
 */
final class Sources {
    static final int PAGE = 24;

    /** One search hit. Serialised through Intents as JSON. */
    static final class Result {
        String source = "";
        String title = "";
        String artist = "";
        String date = "";
        String thumb = "";
        String preview = "";
        String full = "";
        String url = "";
        String credit = "";
        /** Unsplash only: the address that records a download. */
        String download = "";
        /** The picture's shape where the source says (pixels, or for The Met the object's size); 0 when unknown. */
        double width;
        double height;

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("source", source).put("title", title).put("artist", artist).put("date", date)
                    .put("thumb", thumb).put("preview", preview).put("full", full).put("url", url).put("credit", credit)
                    .put("download", download).put("width", width).put("height", height);
        }

        static Result fromJson(JSONObject o) {
            Result r = new Result();
            r.source = o.optString("source");
            r.title = o.optString("title");
            r.artist = o.optString("artist");
            r.date = o.optString("date");
            r.thumb = o.optString("thumb");
            r.preview = o.optString("preview");
            r.full = o.optString("full");
            r.url = o.optString("url");
            r.credit = o.optString("credit");
            r.download = o.optString("download");
            r.width = o.optDouble("width", 0);
            r.height = o.optDouble("height", 0);
            return r;
        }
    }

    interface Source {
        String id();
        int name();
        /** Page 0, 1, 2 ... of hits for the words; an empty list when there are no more. */
        List<Result> search(String query, int page) throws IOException;

        /** The address of the largest picture to save; most sources know it from the search already. */
        default String resolveFull(Result r) throws IOException {
            return r.full;
        }
    }

    /** Unsplash is offered only in a build that has an access key (see app/build.gradle). */
    static final Source[] ALL = BuildConfig.UNSPLASH_KEY.isEmpty()
            ? new Source[]{new Aic(), new Cleveland(), new Met(), new Wikimedia(true), new Wikimedia(false), new Nasa()}
            : new Source[]{new Aic(), new Cleveland(), new Met(), new Wikimedia(true), new Wikimedia(false), new Nasa(),
            new Unsplash(BuildConfig.UNSPLASH_KEY)};

    static Source byId(String id) {
        for (Source s : ALL) if (s.id().equals(id)) return s;
        return ALL[0];
    }

    private Sources() {}

    /**
     * Whether a picture of this shape suits a screen of the given width/height ratio: cropping it to fill the screen
     * loses at most about a sixth of it. Unknown sizes count as suiting, so they are not hidden.
     */
    static boolean suits(double w, double h, double screenRatio) {
        if (w <= 0 || h <= 0) return true;
        double r = w / h;
        return r >= screenRatio * 0.84 && r <= screenRatio * 1.2;
    }

    static String clean(String s) {
        if (s == null || "null".equals(s)) return "";
        return s.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
    }

    /** Art Institute of Chicago: api.artic.edu, IIIF images; is_public_domain only. */
    static final class Aic implements Source {
        public String id() { return "aic"; }
        public int name() { return R.string.source_aic; }

        public List<Result> search(String query, int page) throws IOException {
            String url = Uri.parse("https://api.artic.edu/api/v1/artworks/search").buildUpon()
                    .appendQueryParameter("q", query)
                    .appendQueryParameter("query[term][is_public_domain]", "true")
                    .appendQueryParameter("fields", "id,title,artist_title,date_display,image_id,thumbnail")
                    .appendQueryParameter("limit", String.valueOf(PAGE))
                    .appendQueryParameter("page", String.valueOf(page + 1))
                    .build().toString();
            JSONObject o = Http.json(url);
            String iiif = o.optJSONObject("config") != null
                    ? o.optJSONObject("config").optString("iiif_url", "https://www.artic.edu/iiif/2")
                    : "https://www.artic.edu/iiif/2";
            if (!iiif.startsWith("https://")) iiif = "https://www.artic.edu/iiif/2";
            List<Result> out = new ArrayList<>();
            JSONArray data = o.optJSONArray("data");
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                JSONObject x = data.optJSONObject(i);
                if (x == null) continue;
                String image = clean(x.optString("image_id"));
                if (!image.matches("[0-9a-fA-F-]{8,64}")) continue;
                Result r = new Result();
                r.source = id();
                r.title = clean(x.optString("title"));
                r.artist = clean(x.optString("artist_title"));
                r.date = clean(x.optString("date_display"));
                String base = iiif + "/" + image + "/full/";
                r.thumb = base + "400,/0/default.jpg";
                r.preview = base + "1686,/0/default.jpg";
                r.full = base + "!3840,2160/0/default.jpg";
                r.url = "https://www.artic.edu/artworks/" + x.optInt("id");
                r.credit = "Art Institute of Chicago, public domain (CC0)";
                JSONObject th = x.optJSONObject("thumbnail");
                if (th != null) {
                    r.width = th.optDouble("width", 0);
                    r.height = th.optDouble("height", 0);
                }
                out.add(r);
            }
            return out;
        }
    }

    /** Cleveland Museum of Art Open Access: CC0 works with an image. */
    static final class Cleveland implements Source {
        public String id() { return "cma"; }
        public int name() { return R.string.source_cma; }

        public List<Result> search(String query, int page) throws IOException {
            Uri.Builder b = Uri.parse("https://openaccess-api.clevelandart.org/api/artworks/").buildUpon()
                    .appendQueryParameter("q", query)
                    .appendQueryParameter("cc0", "1")
                    .appendQueryParameter("has_image", "1")
                    .appendQueryParameter("limit", String.valueOf(PAGE));
            // "skip=0" is left out: Cleveland's cache answered some first pages asked for with it with no results.
            if (page > 0) b.appendQueryParameter("skip", String.valueOf(page * PAGE));
            String url = b.build().toString();
            JSONObject o = Http.json(url);
            List<Result> out = new ArrayList<>();
            JSONArray data = o.optJSONArray("data");
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                JSONObject x = data.optJSONObject(i);
                JSONObject images = x == null ? null : x.optJSONObject("images");
                if (images == null) continue;
                String web = imageUrl(images, "web");
                String print = imageUrl(images, "print");
                if (web.isEmpty()) continue;
                Result r = new Result();
                r.source = id();
                r.title = clean(x.optString("title"));
                JSONArray creators = x.optJSONArray("creators");
                if (creators != null && creators.length() > 0 && creators.optJSONObject(0) != null) {
                    // "Claude Monet (French, 1840-1926)": the name before the bracket.
                    r.artist = clean(creators.optJSONObject(0).optString("description").replaceAll("\\s*\\(.*$", ""));
                }
                r.date = clean(x.optString("creation_date"));
                r.thumb = web;
                r.preview = web;
                r.full = print.isEmpty() ? web : print;
                r.url = clean(x.optString("url"));
                r.credit = "Cleveland Museum of Art, CC0";
                JSONObject size = images.optJSONObject("web");
                if (size != null) {
                    r.width = size.optDouble("width", 0);
                    r.height = size.optDouble("height", 0);
                }
                out.add(r);
            }
            return out;
        }

        private static String imageUrl(JSONObject images, String kind) {
            JSONObject k = images.optJSONObject(kind);
            String u = k == null ? "" : clean(k.optString("url"));
            return u.startsWith("https://") ? u : "";
        }
    }

    /**
     * Wikimedia Commons: photographs and scans of 2500 px and wider, either the Featured pictures (the community's
     * hand-picked best) or everything. Only public domain, CC0 and CC BY / BY-SA files are offered; the author and
     * licence go with the piece as its credit. Pictures come at 3840 px from Commons' own scaler.
     */
    static final class Wikimedia implements Source {
        private final boolean featured;

        Wikimedia(boolean featured) {
            this.featured = featured;
        }

        public String id() { return featured ? "wmf" : "wm"; }
        public int name() { return featured ? R.string.source_wm_featured : R.string.source_wm; }

        public List<Result> search(String query, int page) throws IOException {
            String q = "filetype:bitmap filew:>2500 " + (featured ? "incategory:Featured_pictures_on_Wikimedia_Commons " : "") + query;
            String url = Uri.parse("https://commons.wikimedia.org/w/api.php").buildUpon()
                    .appendQueryParameter("action", "query")
                    .appendQueryParameter("format", "json")
                    .appendQueryParameter("generator", "search")
                    .appendQueryParameter("gsrsearch", q)
                    .appendQueryParameter("gsrnamespace", "6")
                    .appendQueryParameter("gsrlimit", String.valueOf(PAGE))
                    .appendQueryParameter("gsroffset", String.valueOf(page * PAGE))
                    .appendQueryParameter("prop", "imageinfo")
                    .appendQueryParameter("iiprop", "url|size|extmetadata")
                    .appendQueryParameter("iiextmetadatafilter", "LicenseShortName|Artist|ObjectName|DateTimeOriginal")
                    .appendQueryParameter("iiurlwidth", "3840")
                    .build().toString();
            JSONObject o = Http.json(url);
            JSONObject pages = o.optJSONObject("query") == null ? null : o.optJSONObject("query").optJSONObject("pages");
            List<Result> out = new ArrayList<>();
            if (pages == null) return out;
            List<JSONObject> list = new ArrayList<>();
            for (java.util.Iterator<String> it = pages.keys(); it.hasNext(); ) {
                JSONObject p = pages.optJSONObject(it.next());
                if (p != null) list.add(p);
            }
            list.sort((a, b) -> Integer.compare(a.optInt("index"), b.optInt("index")));
            for (JSONObject p : list) {
                JSONArray infos = p.optJSONArray("imageinfo");
                JSONObject info = infos == null ? null : infos.optJSONObject(0);
                if (info == null) continue;
                JSONObject meta = info.optJSONObject("extmetadata");
                String license = meta == null ? "" : clean(stripTags(field(meta, "LicenseShortName")));
                if (!freeLicense(license)) continue;
                String full = clean(info.optString("thumburl"));
                if (!full.startsWith("https://")) full = clean(info.optString("url"));
                if (!full.startsWith("https://")) continue;
                Result r = new Result();
                r.source = id();
                String objectName = meta == null ? "" : clean(stripTags(field(meta, "ObjectName")));
                r.title = !objectName.isEmpty() ? objectName : titleFromFile(p.optString("title"));
                r.artist = meta == null ? "" : clean(stripTags(field(meta, "Artist")));
                if (r.artist.length() > 80) r.artist = r.artist.substring(0, 80);
                String date = meta == null ? "" : clean(stripTags(field(meta, "DateTimeOriginal")));
                r.date = date.length() >= 4 && date.substring(0, 4).matches("\\d{4}") ? date.substring(0, 4) : "";
                r.full = full;
                r.thumb = full.contains("/3840px-") ? full.replace("/3840px-", "/500px-") : full;
                r.preview = full.contains("/3840px-") ? full.replace("/3840px-", "/1920px-") : full;
                r.url = clean(info.optString("descriptionurl"));
                r.credit = (r.artist.isEmpty() ? "" : r.artist + ", ") + license + ", Wikimedia Commons";
                r.width = info.optDouble("width", 0);
                r.height = info.optDouble("height", 0);
                out.add(r);
            }
            return out;
        }

        private static String field(JSONObject meta, String name) {
            JSONObject f = meta.optJSONObject(name);
            return f == null ? "" : f.optString("value");
        }

        /** Free to show and keep: public domain, CC0, CC BY and CC BY-SA (any version). */
        static boolean freeLicense(String l) {
            String s = l.toLowerCase(java.util.Locale.ROOT);
            if (s.matches(".*\\b(nc|nd)\\b.*")) return false;
            return s.startsWith("public domain") || s.startsWith("pd") || s.startsWith("cc0")
                    || s.startsWith("cc by") || s.startsWith("cc-by");
        }

        static String stripTags(String html) {
            return html == null ? "" : html.replaceAll("<[^>]*>", "").replace("&amp;", "&").replace("&nbsp;", " ")
                    .replace("&quot;", "\"").replace("&#039;", "'");
        }

        static String titleFromFile(String t) {
            String s = t == null ? "" : t.replaceFirst("^File:", "");
            int dot = s.lastIndexOf('.');
            if (dot > 0) s = s.substring(0, dot);
            return clean(s.replace('_', ' '));
        }
    }

    /**
     * Unsplash: photographs free to use under the Unsplash License, with the owner's own access key. As Unsplash's
     * API guidelines ask, saving a photo reports it through the photo's download_location, and the photographer is
     * credited ("Photo by ... on Unsplash"). Pictures come from Unsplash's image CDN fitted to 3840x2160.
     */
    static final class Unsplash implements Source {
        private final String key;

        Unsplash(String key) {
            this.key = key;
        }

        public String id() { return "unsplash"; }
        public int name() { return R.string.source_unsplash; }

        private String auth() {
            return "Authorization: Client-ID " + key;
        }

        public List<Result> search(String query, int page) throws IOException {
            String url = Uri.parse("https://api.unsplash.com/search/photos").buildUpon()
                    .appendQueryParameter("query", query)
                    .appendQueryParameter("page", String.valueOf(page + 1))
                    .appendQueryParameter("per_page", String.valueOf(PAGE))
                    .appendQueryParameter("content_filter", "high")
                    .build().toString();
            JSONObject o = Http.json(url, auth());
            JSONArray data = o.optJSONArray("results");
            List<Result> out = new ArrayList<>();
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                JSONObject x = data.optJSONObject(i);
                JSONObject urls = x == null ? null : x.optJSONObject("urls");
                if (urls == null) continue;
                String raw = clean(urls.optString("raw"));
                if (!raw.startsWith("https://")) continue;
                String sep = raw.contains("?") ? "&" : "?";
                Result r = new Result();
                r.source = id();
                String desc = clean(x.optString("description"));
                if (desc.isEmpty()) desc = clean(x.optString("alt_description"));
                if (!desc.isEmpty()) desc = Character.toUpperCase(desc.charAt(0)) + desc.substring(1);
                r.title = desc.length() > 90 ? desc.substring(0, 90).trim() + "…" : desc;
                JSONObject user = x.optJSONObject("user");
                r.artist = user == null ? "" : clean(user.optString("name"));
                String created = clean(x.optString("created_at"));
                r.date = created.length() >= 4 ? created.substring(0, 4) : "";
                r.thumb = raw + sep + "w=500&q=75&fm=jpg";
                r.preview = raw + sep + "w=1920&q=80&fm=jpg";
                r.full = raw + sep + "w=3840&h=2160&fit=max&q=90&fm=jpg";
                JSONObject links = x.optJSONObject("links");
                r.url = links == null ? "" : clean(links.optString("html"));
                // Kept in the result so the save can report the download, as the API guidelines require.
                r.download = links == null ? "" : clean(links.optString("download_location"));
                r.credit = "Photo by " + (r.artist.isEmpty() ? "an Unsplash photographer" : r.artist) + " on Unsplash";
                r.width = x.optDouble("width", 0);
                r.height = x.optDouble("height", 0);
                out.add(r);
            }
            return out;
        }

        @Override
        public String resolveFull(Result r) throws IOException {
            if (r.download.startsWith("https://api.unsplash.com/")) Http.json(r.download, auth());
            return r.full;
        }
    }

    /**
     * NASA Image and Video Library: space, Earth and aviation photographs, generally public domain. The search gives
     * a small picture; the largest JPEG or PNG is looked up in the item's file list when it is saved.
     */
    static final class Nasa implements Source {
        public String id() { return "nasa"; }
        public int name() { return R.string.source_nasa; }

        public List<Result> search(String query, int page) throws IOException {
            String url = Uri.parse("https://images-api.nasa.gov/search").buildUpon()
                    .appendQueryParameter("q", query)
                    .appendQueryParameter("media_type", "image")
                    .appendQueryParameter("page_size", String.valueOf(PAGE))
                    .appendQueryParameter("page", String.valueOf(page + 1))
                    .build().toString();
            JSONObject collection = Http.json(url).optJSONObject("collection");
            JSONArray items = collection == null ? null : collection.optJSONArray("items");
            List<Result> out = new ArrayList<>();
            if (items == null) return out;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                JSONArray data = item == null ? null : item.optJSONArray("data");
                JSONObject d = data == null ? null : data.optJSONObject(0);
                if (d == null) continue;
                String nasaId = clean(d.optString("nasa_id"));
                if (!nasaId.matches("[A-Za-z0-9._-]{1,120}")) continue;
                String base = "https://images-assets.nasa.gov/image/" + Uri.encode(nasaId) + "/" + Uri.encode(nasaId);
                Result r = new Result();
                r.source = id();
                r.title = clean(d.optString("title"));
                String who = clean(d.optString("photographer"));
                if (who.isEmpty()) who = clean(d.optString("secondary_creator"));
                if (who.isEmpty()) who = clean(d.optString("center"));
                r.artist = who.length() > 80 ? who.substring(0, 80) : who;
                String date = clean(d.optString("date_created"));
                r.date = date.length() >= 4 ? date.substring(0, 4) : "";
                r.thumb = base + "~thumb.jpg";
                r.preview = base + "~medium.jpg";
                r.full = "https://images-assets.nasa.gov/image/" + Uri.encode(nasaId) + "/collection.json";
                r.url = "https://images.nasa.gov/details/" + Uri.encode(nasaId);
                r.credit = "NASA" + (r.artist.isEmpty() ? "" : " / " + r.artist) + ", public domain";
                out.add(r);
            }
            return out;
        }

        /** Picks ~orig, then ~large, then ~medium, as JPEG or PNG, from the item's file list. */
        @Override
        public String resolveFull(Result r) throws IOException {
            byte[] body = Http.bytes(r.full, 1L << 20);
            JSONArray files;
            try {
                files = new JSONArray(new String(body, java.nio.charset.StandardCharsets.UTF_8));
            } catch (JSONException e) {
                throw new IOException("bad file list from NASA");
            }
            for (String size : new String[]{"~orig.", "~large.", "~medium."}) {
                for (int i = 0; i < files.length(); i++) {
                    String f = files.optString(i);
                    String lower = f.toLowerCase(java.util.Locale.ROOT);
                    if (lower.contains(size) && (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png"))) {
                        return f.replaceFirst("^http://", "https://");
                    }
                }
            }
            throw new IOException("no picture in NASA's file list");
        }
    }

    /**
     * The Met Collection API: the v1.1 search (paged by offset) returns object IDs; each object is read for its
     * image and public-domain flag, a few at a time.
     */
    static final class Met implements Source {
        private static final ExecutorService POOL = Executors.newFixedThreadPool(4);

        public String id() { return "met"; }
        public int name() { return R.string.source_met; }

        public List<Result> search(String query, int page) throws IOException {
            String url = Uri.parse("https://collectionapi.metmuseum.org/public/collection/v1.1/search").buildUpon()
                    .appendQueryParameter("q", query)
                    .appendQueryParameter("hasImages", "true")
                    .appendQueryParameter("limit", String.valueOf(PAGE))
                    .appendQueryParameter("offset", String.valueOf(page * PAGE))
                    .build().toString();
            JSONObject o = Http.json(url);
            JSONArray ids = o.optJSONArray("objectIDs");
            List<Result> out = new ArrayList<>();
            if (ids == null) return out;
            List<Future<Result>> jobs = new ArrayList<>();
            for (int i = 0; i < ids.length() && i < PAGE; i++) {
                long objectId = ids.optLong(i);
                if (objectId <= 0) continue;
                jobs.add(POOL.submit(() -> object(objectId)));
            }
            for (Future<Result> f : jobs) {
                try {
                    Result r = f.get();
                    if (r != null) out.add(r);
                } catch (Exception skipped) {
                    // One object that fails to load just isn't shown.
                }
            }
            return out;
        }

        private Result object(long objectId) throws IOException {
            JSONObject x = Http.json("https://collectionapi.metmuseum.org/public/collection/v1/objects/" + objectId);
            if (!x.optBoolean("isPublicDomain")) return null;
            String full = clean(x.optString("primaryImage"));
            String small = clean(x.optString("primaryImageSmall"));
            if (!full.startsWith("https://") || !small.startsWith("https://")) return null;
            Result r = new Result();
            r.source = id();
            r.title = clean(x.optString("title"));
            r.artist = clean(x.optString("artistDisplayName"));
            r.date = clean(x.optString("objectDate"));
            r.thumb = small;
            r.preview = small;
            r.full = full;
            r.url = clean(x.optString("objectURL"));
            r.credit = "The Metropolitan Museum of Art, public domain (CC0)";
            JSONArray ms = x.optJSONArray("measurements");
            JSONObject em = ms == null || ms.length() == 0 || ms.optJSONObject(0) == null ? null
                    : ms.optJSONObject(0).optJSONObject("elementMeasurements");
            if (em != null) {
                r.width = em.optDouble("Width", 0);
                r.height = em.optDouble("Height", 0);
            }
            return r;
        }
    }
}
