package com.gathin.homedashboard;

import org.json.JSONObject;
import java.util.Locale;

final class Metric {
    final String text, reported;
    final double value;
    Metric(String text, String reported, double value) { this.text=text; this.reported=reported; this.value=value; }
    static Metric parse(JSONObject o, SensorCard card) {
        String unit = o.optJSONObject("attributes") == null ? "" : o.optJSONObject("attributes").optString("unit_of_measurement");
        String reported = o.optString("last_reported", o.optString("last_updated"));
        String state=o.optString("state").trim();
        if(state.isEmpty()||state.equals("unknown")||state.equals("unavailable")||state.equalsIgnoreCase("nan")||state.equalsIgnoreCase("infinity")||state.equalsIgnoreCase("-infinity"))return new Metric("Unavailable",reported,Double.NaN);
        try {
            double value = Double.parseDouble(state);
            if (!Double.isFinite(value)) return new Metric("Unavailable",reported,Double.NaN);
            if (card.mode.equals("percentage")) {
                if (!"%".equals(unit) || value < 0 || value > 100) return new Metric("Check % sensor", reported, Double.NaN);
                return new Metric(number(value,card.decimals,false)+" %", reported, value);
            }
            if(card.mode.equals("power")){
                double kw;
                if ("W".equals(unit)) kw = value / 1000;
                else if ("kW".equals(unit)) kw = value;
                else return new Metric("Check power unit", reported, Double.NaN);
                return new Metric(number(kw,card.decimals,false)+" kW", reported, kw);
            }
            String safeUnit=clean(unit,24);
            return new Metric(number(value,card.decimals,true)+(safeUnit.isEmpty()?"":" "+safeUnit),reported,value);
        } catch (NumberFormatException e) {
            if(!card.mode.equals("auto"))return new Metric("Check numeric sensor",reported,Double.NaN);
            String text=state.equals("on")?"On":state.equals("off")?"Off":clean(state,80);
            return new Metric(text,reported,Double.NaN);
        }
    }
    private static String clean(String s,int limit){String text=s.replaceAll("[\\p{Cntrl}]"," ").trim();return text.length()>limit?text.substring(0,limit):text;}
    private static String number(double value,int decimals,boolean trim){
        if(Math.abs(value)>=1e12)return String.format(Locale.US,"%.3g",value);
        String text=String.format(Locale.US,"%."+decimals+"f",value);
        if(trim&&decimals>0)text=text.replaceAll("0+$","").replaceAll("\\.$","");
        return text.matches("-0(?:\\.0+)?")?text.substring(1):text;
    }
}
