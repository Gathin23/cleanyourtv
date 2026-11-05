package com.gathin.tvhome;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Isolate complete measurements, not individual digits, in the Hebrew stats paragraph. */
final class StatsText {
    private static final Pattern MEASUREMENT = Pattern.compile("[0-9]+(?:[.,][0-9]+)?(?:\\s*/\\s*[0-9]+(?:[.,][0-9]+)?)?(?:\\s+(?:MB|GB)|%)");
    static String forLocale(String text, Locale locale) {
        String language = locale.getLanguage();
        if (!language.equals("he") && !language.equals("iw")) return text;
        Matcher matcher = MEASUREMENT.matcher(text);
        StringBuffer isolated = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(isolated, Matcher.quoteReplacement("\u200E\u202A" + matcher.group() + "\u202C\u200E"));
        matcher.appendTail(isolated);
        // Embeddings plus directional marks also work on TV text renderers that mishandle isolates.
        return "\u200F" + isolated.toString().replace("  ·  ", "\u200F  ·  \u200F");
    }
}
