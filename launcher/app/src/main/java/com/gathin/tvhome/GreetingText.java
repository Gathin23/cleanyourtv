package com.gathin.tvhome;

/** Which line the greeting shows: a festival's own greeting, else greeting_text, else the time-of-day greeting. */
final class GreetingText {

    private GreetingText() {
    }

    /** Null means the time-of-day greeting (with greeting_name, as before). */
    static String fixed(String festive, String greetingText) {
        if (festive != null && !festive.isEmpty()) return festive;
        return greetingText != null && !greetingText.trim().isEmpty() ? greetingText.trim() : null;
    }
}
