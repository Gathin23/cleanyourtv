package io.github.yourvoldemort.panchang;

import java.time.ZoneId;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A city the panchang is worked out for. A TV gives no position without a permission this add-on
 * does not ask for, so the owner picks one of these and sunrise follows it.
 */
final class City {
    final String name;
    final double lat, lon;
    final ZoneId zone;

    static final ZoneId UTC = ZoneId.of("UTC");

    private City(String name, double lat, double lon, String zone) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.zone = ZoneId.of(zone);
    }

    static final City[] ALL = {
            new City("Ujjain", 23.1765, 75.7885, "Asia/Kolkata"),
            new City("Delhi", 28.6139, 77.2090, "Asia/Kolkata"),
            new City("Mumbai", 19.0760, 72.8777, "Asia/Kolkata"),
            new City("Bengaluru", 12.9716, 77.5946, "Asia/Kolkata"),
            new City("Chennai", 13.0827, 80.2707, "Asia/Kolkata"),
            new City("Kolkata", 22.5726, 88.3639, "Asia/Kolkata"),
            new City("Hyderabad", 17.3850, 78.4867, "Asia/Kolkata"),
            new City("Ahmedabad", 23.0225, 72.5714, "Asia/Kolkata"),
            new City("Pune", 18.5204, 73.8567, "Asia/Kolkata"),
            new City("Jaipur", 26.9124, 75.7873, "Asia/Kolkata"),
            new City("Lucknow", 26.8467, 80.9462, "Asia/Kolkata"),
            new City("Kanpur", 26.4499, 80.3319, "Asia/Kolkata"),
            new City("Nagpur", 21.1458, 79.0882, "Asia/Kolkata"),
            new City("Indore", 22.7196, 75.8577, "Asia/Kolkata"),
            new City("Bhopal", 23.2599, 77.4126, "Asia/Kolkata"),
            new City("Patna", 25.5941, 85.1376, "Asia/Kolkata"),
            new City("Varanasi", 25.3176, 82.9739, "Asia/Kolkata"),
            new City("Kochi", 9.9312, 76.2673, "Asia/Kolkata"),
            new City("Thiruvananthapuram", 8.5241, 76.9366, "Asia/Kolkata"),
            new City("Guwahati", 26.1445, 91.7362, "Asia/Kolkata"),
            new City("Bhubaneswar", 20.2961, 85.8245, "Asia/Kolkata"),
            new City("Haridwar", 29.9457, 78.1642, "Asia/Kolkata"),
            new City("Amritsar", 31.6340, 74.8723, "Asia/Kolkata"),
            new City("Chandigarh", 30.7333, 76.7794, "Asia/Kolkata"),
            new City("Surat", 21.1702, 72.8311, "Asia/Kolkata"),
            new City("Nashik", 19.9975, 73.7898, "Asia/Kolkata"),
            new City("Madurai", 9.9252, 78.1198, "Asia/Kolkata"),
            new City("Tirupati", 13.6288, 79.4192, "Asia/Kolkata"),
            new City("London", 51.5072, -0.1276, "Europe/London"),
            new City("New York", 40.7128, -74.0060, "America/New_York"),
            new City("Toronto", 43.6532, -79.3832, "America/Toronto"),
            new City("Chicago", 41.8781, -87.6298, "America/Chicago"),
            new City("Houston", 29.7604, -95.3698, "America/Chicago"),
            new City("San Francisco", 37.7749, -122.4194, "America/Los_Angeles"),
            new City("Dubai", 25.2048, 55.2708, "Asia/Dubai"),
            new City("Singapore", 1.3521, 103.8198, "Asia/Singapore"),
            new City("Sydney", -33.8688, 151.2093, "Australia/Sydney"),
            new City("Melbourne", -37.8136, 144.9631, "Australia/Melbourne"),
            new City("Auckland", -36.8509, 174.7645, "Pacific/Auckland"),
            new City("Nairobi", -1.2921, 36.8219, "Africa/Nairobi"),
            new City("Port Louis", -20.1609, 57.5012, "Indian/Mauritius"),
            new City("Port of Spain", 10.6918, -61.2225, "America/Port_of_Spain"),
            new City("Paramaribo", 5.8520, -55.2038, "America/Paramaribo"),
            new City("Suva", -18.1416, 178.4419, "Pacific/Fiji"),
    };

    static String[] names() {
        String[] names = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) names[i] = ALL[i].name;
        return names;
    }

    static int indexOf(String name) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].name.equals(name)) return i;
        return 0;
    }

    /**
     * The city the TV's own time zone suggests, so the first screen is usually right already. India
     * writes its zone two ways and both cover far more than one city from this list, so Delhi is the
     * first guess there; the owner picks another once and the choice is remembered.
     */
    static City forTimeZone() {
        String id = TimeZone.getDefault().getID();
        if (id.equals("Asia/Kolkata") || id.equals("Asia/Calcutta")) return cityNamed("Delhi");
        for (City city : ALL) if (city.zone.getId().equals(id)) return city;
        return cityNamed("Delhi");
    }

    static City cityNamed(String name) {
        for (City city : ALL) if (city.name.equalsIgnoreCase(name)) return city;
        return ALL[1];
    }

    /** "28.6°N 77.2°E", the small line under the sunrise that says where the reading is for. */
    String coordinates() {
        return String.format(Locale.ROOT, "%.1f°%s %.1f°%s",
                Math.abs(lat), lat >= 0 ? "N" : "S", Math.abs(lon), lon >= 0 ? "E" : "W");
    }
}
