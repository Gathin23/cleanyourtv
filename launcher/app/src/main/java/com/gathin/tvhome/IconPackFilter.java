package com.gathin.tvhome;

import java.util.HashMap;
import java.util.Map;

/**
 * The lookup table of an ADW/Nova icon pack's appfilter.xml: {@code <item component="ComponentInfo{pkg/cls}"
 * drawable="name"/>}. Plain Java so it runs in JVM tests; IconPack feeds it from the pack's XML.
 */
final class IconPackFilter {
    private final Map<String, String> byComponent = new HashMap<>();
    private final Map<String, String> byPackage = new HashMap<>();

    /** "com.a/com.a.Main" from "ComponentInfo{com.a/com.a.Main}", or "" when it is not that shape. */
    static String componentKey(String componentInfo) {
        if (componentInfo == null) return "";
        String s = componentInfo.trim();
        if (!s.startsWith("ComponentInfo{") || !s.endsWith("}")) return "";
        s = s.substring(14, s.length() - 1);
        int slash = s.indexOf('/');
        return slash <= 0 || slash == s.length() - 1 || s.indexOf('/', slash + 1) >= 0 ? "" : s;
    }

    /** "com.a" from "ComponentInfo{com.a/com.a.Main}", or "". */
    static String packageOf(String componentInfo) {
        String key = componentKey(componentInfo);
        return key.isEmpty() ? "" : key.substring(0, key.indexOf('/'));
    }

    /** Records one item; a malformed component or a drawable name that is empty, has "/" or ".." is ignored. */
    void add(String componentInfo, String drawable) {
        String key = componentKey(componentInfo);
        if (key.isEmpty() || drawable == null || drawable.isEmpty()
                || drawable.contains("/") || drawable.contains("..")) return;
        byComponent.putIfAbsent(key, drawable);
        byPackage.putIfAbsent(packageOf(componentInfo), drawable);
    }

    /** The drawable name for this component, else for any item of its package, else null. */
    String lookup(String pkg, String cls) {
        String exact = byComponent.get(pkg + '/' + cls);
        return exact != null ? exact : byPackage.get(pkg);
    }

    boolean isEmpty() {
        return byComponent.isEmpty();
    }
}
