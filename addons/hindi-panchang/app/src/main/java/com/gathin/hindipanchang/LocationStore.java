package com.gathin.hindipanchang;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages the user's selected city, coordinates, timezone, and language preference completely offline.
 * Supports:
 * 1. Bundled Indian cities (55), major US cities (20), and international hubs (9).
 * 2. Offline search filtering across all bundled cities.
 * 3. Manual latitude, longitude, and custom city label entry.
 */
final class LocationStore {
    private static final String PREFS = "panchang_prefs";

    static final int LANG_BILINGUAL = 0; // हिन्दी + English
    static final int LANG_HINDI = 1;     // हिन्दी (संस्कृत)
    static final int LANG_ENGLISH = 2;   // English

    static final class Place {
        final String name;
        final String detail;
        final double latitude;
        final double longitude;
        final String timezone;

        Place(String name, String detail, double latitude, double longitude, String timezone) {
            this.name = name;
            this.detail = detail;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timezone = timezone;
        }

        ZoneId zoneId() {
            try {
                if (timezone != null && !timezone.isEmpty()) return ZoneId.of(timezone);
            } catch (Exception ignored) { }
            return ZoneId.systemDefault();
        }

        String displayLabel() {
            return detail == null || detail.isEmpty() ? name : name + ", " + detail;
        }
    }

    static final Place[] INDIA_PRESETS = new Place[]{
        new Place("New Delhi", "Delhi, India", 28.6139, 77.2090, "Asia/Kolkata"),
        new Place("Varanasi (काशी)", "Uttar Pradesh, India", 25.3176, 82.9739, "Asia/Kolkata"),
        new Place("Ujjain (उज्जैन)", "Madhya Pradesh, India", 23.1765, 75.7885, "Asia/Kolkata"),
        new Place("Ayodhya (अयोध्या)", "Uttar Pradesh, India", 26.7922, 82.1998, "Asia/Kolkata"),
        new Place("Prayagraj (प्रयागराज)", "Uttar Pradesh, India", 25.4358, 81.8463, "Asia/Kolkata"),
        new Place("Haridwar (हरिद्वार)", "Uttarakhand, India", 29.9457, 78.1642, "Asia/Kolkata"),
        new Place("Rishikesh (ऋषिकेश)", "Uttarakhand, India", 30.0869, 78.2676, "Asia/Kolkata"),
        new Place("Mathura (मथुरा)", "Uttar Pradesh, India", 27.4924, 77.6737, "Asia/Kolkata"),
        new Place("Puri (पुरी)", "Odisha, India", 19.8135, 85.8312, "Asia/Kolkata"),
        new Place("Tirupati (तिरुपति)", "Andhra Pradesh, India", 13.6288, 79.4192, "Asia/Kolkata"),
        new Place("Dwarka (द्वारका)", "Gujarat, India", 22.2442, 68.9685, "Asia/Kolkata"),
        new Place("Nashik (नाशिक)", "Maharashtra, India", 19.9975, 73.7898, "Asia/Kolkata"),
        new Place("Madurai (मदुरै)", "Tamil Nadu, India", 9.9252, 78.1198, "Asia/Kolkata"),
        new Place("Amritsar (अमृतसर)", "Punjab, India", 31.6340, 74.8723, "Asia/Kolkata"),
        new Place("Mumbai", "Maharashtra, India", 19.0760, 72.8777, "Asia/Kolkata"),
        new Place("Bengaluru", "Karnataka, India", 12.9716, 77.5946, "Asia/Kolkata"),
        new Place("Hyderabad", "Telangana, India", 17.3850, 78.4867, "Asia/Kolkata"),
        new Place("Chennai", "Tamil Nadu, India", 13.0827, 80.2707, "Asia/Kolkata"),
        new Place("Kolkata", "West Bengal, India", 22.5726, 88.3639, "Asia/Kolkata"),
        new Place("Pune", "Maharashtra, India", 18.5204, 73.8567, "Asia/Kolkata"),
        new Place("Ahmedabad", "Gujarat, India", 23.0225, 72.5714, "Asia/Kolkata"),
        new Place("Jaipur", "Rajasthan, India", 26.9124, 75.7873, "Asia/Kolkata"),
        new Place("Lucknow", "Uttar Pradesh, India", 26.8467, 80.9462, "Asia/Kolkata"),
        new Place("Kanpur", "Uttar Pradesh, India", 26.4499, 80.3319, "Asia/Kolkata"),
        new Place("Agra", "Uttar Pradesh, India", 27.1767, 78.0081, "Asia/Kolkata"),
        new Place("Patna", "Bihar, India", 25.5941, 85.1376, "Asia/Kolkata"),
        new Place("Gaya", "Bihar, India", 24.7914, 85.0002, "Asia/Kolkata"),
        new Place("Bhopal", "Madhya Pradesh, India", 23.2599, 77.4126, "Asia/Kolkata"),
        new Place("Indore", "Madhya Pradesh, India", 22.7196, 75.8577, "Asia/Kolkata"),
        new Place("Jabalpur", "Madhya Pradesh, India", 23.1815, 79.9864, "Asia/Kolkata"),
        new Place("Nagpur", "Maharashtra, India", 21.1458, 79.0882, "Asia/Kolkata"),
        new Place("Surat", "Gujarat, India", 21.1702, 72.8311, "Asia/Kolkata"),
        new Place("Vadodara", "Gujarat, India", 22.3072, 73.1812, "Asia/Kolkata"),
        new Place("Rajkot", "Gujarat, India", 22.3039, 70.8022, "Asia/Kolkata"),
        new Place("Jodhpur", "Rajasthan, India", 26.2389, 73.0243, "Asia/Kolkata"),
        new Place("Udaipur", "Rajasthan, India", 24.5854, 73.7125, "Asia/Kolkata"),
        new Place("Chandigarh", "Chandigarh, India", 30.7333, 76.7794, "Asia/Kolkata"),
        new Place("Ludhiana", "Punjab, India", 30.9010, 75.8573, "Asia/Kolkata"),
        new Place("Dehradun", "Uttarakhand, India", 30.3165, 78.0322, "Asia/Kolkata"),
        new Place("Shimla", "Himachal Pradesh, India", 31.1048, 77.1734, "Asia/Kolkata"),
        new Place("Jammu", "Jammu & Kashmir, India", 32.7266, 74.8570, "Asia/Kolkata"),
        new Place("Srinagar", "Jammu & Kashmir, India", 34.0837, 74.7973, "Asia/Kolkata"),
        new Place("Ranchi", "Jharkhand, India", 23.3441, 85.3096, "Asia/Kolkata"),
        new Place("Bhubaneswar", "Odisha, India", 20.2961, 85.8245, "Asia/Kolkata"),
        new Place("Raipur", "Chhattisgarh, India", 21.2514, 81.6296, "Asia/Kolkata"),
        new Place("Guwahati", "Assam, India", 26.1445, 91.7362, "Asia/Kolkata"),
        new Place("Siliguri", "West Bengal, India", 26.7271, 88.3953, "Asia/Kolkata"),
        new Place("Visakhapatnam", "Andhra Pradesh, India", 17.6868, 83.2185, "Asia/Kolkata"),
        new Place("Vijayawada", "Andhra Pradesh, India", 16.5062, 80.6480, "Asia/Kolkata"),
        new Place("Mysuru", "Karnataka, India", 12.2958, 76.6394, "Asia/Kolkata"),
        new Place("Mangaluru", "Karnataka, India", 12.9141, 74.8560, "Asia/Kolkata"),
        new Place("Coimbatore", "Tamil Nadu, India", 11.0168, 76.9558, "Asia/Kolkata"),
        new Place("Kochi", "Kerala, India", 9.9312, 76.2673, "Asia/Kolkata"),
        new Place("Thiruvananthapuram", "Kerala, India", 8.5241, 76.9366, "Asia/Kolkata"),
        new Place("Panaji", "Goa, India", 15.4909, 73.8278, "Asia/Kolkata")
    };

    static final Place[] US_PRESETS = new Place[]{
        new Place("New York", "New York, USA", 40.7128, -74.0060, "America/New_York"),
        new Place("Los Angeles", "California, USA", 34.0522, -118.2437, "America/Los_Angeles"),
        new Place("Chicago", "Illinois, USA", 41.8781, -87.6298, "America/Chicago"),
        new Place("Houston", "Texas, USA", 29.7604, -95.3698, "America/Chicago"),
        new Place("Dallas", "Texas, USA", 32.7767, -96.7970, "America/Chicago"),
        new Place("Austin", "Texas, USA", 30.2672, -97.7431, "America/Chicago"),
        new Place("San Francisco", "California, USA", 37.7749, -122.4194, "America/Los_Angeles"),
        new Place("San Jose", "California, USA", 37.3382, -121.8863, "America/Los_Angeles"),
        new Place("San Diego", "California, USA", 32.7157, -117.1611, "America/Los_Angeles"),
        new Place("Seattle", "Washington, USA", 47.6062, -122.3321, "America/Los_Angeles"),
        new Place("Atlanta", "Georgia, USA", 33.7490, -84.3880, "America/New_York"),
        new Place("Boston", "Massachusetts, USA", 42.3601, -71.0589, "America/New_York"),
        new Place("Washington, DC", "District of Columbia, USA", 38.9072, -77.0369, "America/New_York"),
        new Place("Philadelphia", "Pennsylvania, USA", 39.9526, -75.1652, "America/New_York"),
        new Place("Miami", "Florida, USA", 25.7617, -80.1918, "America/New_York"),
        new Place("Orlando", "Florida, USA", 28.5383, -81.3792, "America/New_York"),
        new Place("Phoenix", "Arizona, USA", 33.4484, -112.0740, "America/Phoenix"),
        new Place("Denver", "Colorado, USA", 39.7392, -104.9903, "America/Denver"),
        new Place("Detroit", "Michigan, USA", 42.3314, -83.0458, "America/Detroit"),
        new Place("Minneapolis", "Minnesota, USA", 44.9778, -93.2650, "America/Chicago")
    };

    static final Place[] OTHER_PRESETS = new Place[]{
        new Place("Kathmandu", "Nepal", 27.7172, 85.3240, "Asia/Kathmandu"),
        new Place("Dubai", "UAE", 25.2048, 55.2708, "Asia/Dubai"),
        new Place("Singapore", "Singapore", 1.3521, 103.8198, "Asia/Singapore"),
        new Place("London", "United Kingdom", 51.5074, -0.1278, "Europe/London"),
        new Place("Toronto", "Ontario, Canada", 43.6532, -79.3832, "America/Toronto"),
        new Place("Vancouver", "BC, Canada", 49.2827, -123.1207, "America/Vancouver"),
        new Place("Sydney", "NSW, Australia", -33.8688, 151.2093, "Australia/Sydney"),
        new Place("Melbourne", "VIC, Australia", -37.8136, 144.9631, "Australia/Melbourne"),
        new Place("Auckland", "New Zealand", -36.8485, 174.7633, "Pacific/Auckland")
    };

    static final Place[] PRESETS = buildAllPresets();

    private static Place[] buildAllPresets() {
        Place[] all = new Place[INDIA_PRESETS.length + US_PRESETS.length + OTHER_PRESETS.length];
        int pos = 0;
        System.arraycopy(INDIA_PRESETS, 0, all, pos, INDIA_PRESETS.length);
        pos += INDIA_PRESETS.length;
        System.arraycopy(US_PRESETS, 0, all, pos, US_PRESETS.length);
        pos += US_PRESETS.length;
        System.arraycopy(OTHER_PRESETS, 0, all, pos, OTHER_PRESETS.length);
        return all;
    }

    private LocationStore() { }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean hasSavedPlace(Context ctx) {
        return prefs(ctx).contains("name");
    }

    static Place load(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        if (sp.contains("name")) {
            String name = sp.getString("name", "New Delhi");
            String detail = sp.getString("detail", "India");
            double lat = Double.longBitsToDouble(sp.getLong("lat", Double.doubleToRawLongBits(28.6139)));
            double lon = Double.longBitsToDouble(sp.getLong("lon", Double.doubleToRawLongBits(77.2090)));
            String tz = sp.getString("tz", ZoneId.systemDefault().getId());
            return new Place(name, detail, lat, lon, tz);
        }
        // Smart default based on device timezone
        String sysTz = ZoneId.systemDefault().getId();
        if (sysTz.contains("Chicago") || sysTz.contains("Central")) {
            return new Place("Central Time", "USA", 38.0, -90.0, sysTz);
        } else if (sysTz.contains("New_York") || sysTz.contains("Eastern")) {
            return new Place("Eastern Time", "USA", 40.7128, -74.0060, sysTz);
        } else if (sysTz.contains("Los_Angeles") || sysTz.contains("Pacific")) {
            return new Place("Pacific Time", "USA", 37.7749, -122.4194, sysTz);
        }
        return PRESETS[0]; // New Delhi default
    }

    static void save(Context ctx, Place p) {
        prefs(ctx).edit()
                .putString("name", p.name)
                .putString("detail", p.detail)
                .putLong("lat", Double.doubleToRawLongBits(p.latitude))
                .putLong("lon", Double.doubleToRawLongBits(p.longitude))
                .putString("tz", p.timezone)
                .apply();
    }

    static int getLang(Context ctx) {
        return prefs(ctx).getInt("lang", LANG_BILINGUAL);
    }

    static int cycleLang(Context ctx) {
        int next = (getLang(ctx) + 1) % 3;
        prefs(ctx).edit().putInt("lang", next).apply();
        return next;
    }

    /** Filters all bundled cities offline by matching city name or region/state/country. */
    static List<Place> searchOffline(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Place> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        for (Place p : PRESETS) {
            if (p.name.toLowerCase(Locale.ROOT).contains(q)
                    || (p.detail != null && p.detail.toLowerCase(Locale.ROOT).contains(q))) {
                out.add(p);
            }
        }
        return out;
    }
}
