package io.github.nickld.frigateglance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;

/** Small helpers for Frigate's payloads. */
final class Json {
    private Json() {}

    static List<String> strings(JSONArray a) {
        if (a == null) return Collections.emptyList();
        List<String> out = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "");
            if (!s.isEmpty() && !"null".equals(s)) out.add(s);
        }
        return Collections.unmodifiableList(out);
    }
}
