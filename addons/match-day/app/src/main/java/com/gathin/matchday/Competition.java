package com.gathin.matchday;

enum Competition {
    PREMIER_LEAGUE("PL", "Premier League", "ENGLAND", "english-premier-league"),
    CHAMPIONS_LEAGUE("CL", "Champions League", "EUROPE", "uefa-champions-league"),
    LA_LIGA("PD", "La Liga", "SPAIN", "spanish-la-liga"),
    SERIE_A("SA", "Serie A", "ITALY", "italian-serie-a"),
    BUNDESLIGA("BL1", "Bundesliga", "GERMANY", "bundesliga"),
    LIGUE_1("FL1", "Ligue 1", "FRANCE", "french-ligue-1");

    final String code;
    final String displayName;
    final String areaName;
    final String sportScoreSlug;

    Competition(String code, String displayName, String areaName, String sportScoreSlug) {
        this.code = code;
        this.displayName = displayName;
        this.areaName = areaName;
        this.sportScoreSlug = sportScoreSlug;
    }
}
