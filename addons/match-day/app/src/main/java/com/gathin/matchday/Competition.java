package com.gathin.matchday;

enum Competition {
    PREMIER_LEAGUE("PL", "Premier League", "ENGLAND"),
    CHAMPIONS_LEAGUE("CL", "Champions League", "EUROPE"),
    LA_LIGA("PD", "La Liga", "SPAIN"),
    SERIE_A("SA", "Serie A", "ITALY"),
    BUNDESLIGA("BL1", "Bundesliga", "GERMANY"),
    LIGUE_1("FL1", "Ligue 1", "FRANCE");

    final String code;
    final String displayName;
    final String areaName;

    Competition(String code, String displayName, String areaName) {
        this.code = code;
        this.displayName = displayName;
        this.areaName = areaName;
    }
}
