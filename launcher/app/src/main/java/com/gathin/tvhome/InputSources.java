package com.gathin.tvhome;

import java.util.List;

/** Vendor source numbers for passthrough inputs, such as Xiaomi's EXTSRC_PLAY route. */
final class InputSources {
    private InputSources() {
    }

    /** Returns the mapped source, -1 for a hidden input, or null when the input is not mapped. */
    static Integer mapped(List<String> mappings, String inputId) {
        if (inputId == null || mappings == null) return null;
        String input = inputId.substring(inputId.lastIndexOf('/') + 1);
        for (String mapping : mappings) {
            if (mapping == null) continue;
            int equals = mapping.indexOf('=');
            if (equals <= 0 || !mapping.substring(0, equals).trim().equals(input)) continue;
            String value = mapping.substring(equals + 1).trim();
            if (value.equals("-")) return -1;
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** True when `input_action` names an activity ("package/.Activity", "package/package.Activity") rather than an
     *  intent action. Actions never contain a slash, so the two forms cannot be confused. */
    static boolean isComponent(String inputAction) {
        if (inputAction == null) return false;
        int slash = inputAction.indexOf('/');
        return slash > 0 && slash < inputAction.length() - 1 && inputAction.indexOf('/', slash + 1) < 0;
    }
}
