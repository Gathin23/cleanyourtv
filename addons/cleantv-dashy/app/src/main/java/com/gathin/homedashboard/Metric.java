package com.gathin.homedashboard;

import org.json.JSONObject;
import java.util.Locale;

final class Metric {
    final String text, reported;
    final double value;
    Metric(String text, String reported, double value) { this.text=text; this.reported=reported; this.value=value; }
    static Metric parse(JSONObject o, boolean battery) {
        String unit = o.optJSONObject("attributes") == null ? "" : o.optJSONObject("attributes").optString("unit_of_measurement");
        String reported = o.optString("last_reported", o.optString("last_updated"));
        try {
            double value = Double.parseDouble(o.optString("state"));
            if (!Double.isFinite(value)) throw new NumberFormatException();
            if (battery) {
                if (!"%".equals(unit) || value < 0 || value > 100) return new Metric("Check SOC sensor", reported, Double.NaN);
                return new Metric(String.format(Locale.US, "%.0f %%", value), reported, value);
            }
            double kw;
            if ("W".equals(unit)) kw = value / 1000;
            else if ("kW".equals(unit)) kw = value;
            else return new Metric("Check power unit", reported, Double.NaN);
            return new Metric(String.format(Locale.US, "%.2f kW", kw), reported, kw);
        } catch (NumberFormatException e) { return new Metric("Unavailable", reported, Double.NaN); }
    }
}
