package com.gathin.tvhome;

import java.util.Map;
import java.util.function.BiFunction;

/** Selects a configured, package-scoped action only when it resolves; otherwise keeps the normal launch. */
final class LaunchActions {
    private LaunchActions() {}

    static <T> T select(String pkg, Map<String, String> actions, T fallback,
                        BiFunction<String, String, T> resolver) {
        String action = actions.get(pkg);
        if (action == null) return fallback;
        T custom = resolver.apply(pkg, action);
        return custom != null ? custom : fallback;
    }
}
