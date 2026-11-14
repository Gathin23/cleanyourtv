package com.gathin.matchday;

enum DataProvider {
    SPORT_SCORE("sportscore", "SportScore"),
    FOOTBALL_DATA("football_data", "football-data.org");

    final String preferenceValue;
    final String displayName;

    DataProvider(String preferenceValue, String displayName) {
        this.preferenceValue = preferenceValue;
        this.displayName = displayName;
    }

    static DataProvider fromPreference(String value) {
        for (DataProvider provider : values()) {
            if (provider.preferenceValue.equals(value)) return provider;
        }
        return SPORT_SCORE;
    }
}
