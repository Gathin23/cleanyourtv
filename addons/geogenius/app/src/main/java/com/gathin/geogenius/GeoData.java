package com.gathin.geogenius;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Offline 200-Country World Geography Dataset, 24 World Wonders, 7 World Atlas Decks,
 * and Combinatorial 50,000+ Fact/Lie Question Generator for GeoGenius v2.
 */
final class GeoData {

    static final int CONT_AMERICAS = 0;
    static final int CONT_EUROPE = 1;
    static final int CONT_AFRICA = 2;
    static final int CONT_ASIA = 3;
    static final int CONT_OCEANIA = 4;

    static final int REGION_ALL_MASK = 0b11111; // All 5 regions enabled

    static final String[] CONTINENT_NAMES = {
        "Americas", "Europe", "Africa", "Asia", "Oceania"
    };

    static final String[] CONTINENT_ICONS = {
        "🌎", "🌍", "🌍", "🌏", "🏝️"
    };

    // Flag geometric patterns
    static final int PAT_H3 = 0;
    static final int PAT_V3 = 1;
    static final int PAT_H2 = 2;
    static final int PAT_V2 = 3;
    static final int PAT_SOLID = 4;
    static final int PAT_NORDIC = 5;
    static final int PAT_CENTER_DISK = 6;
    static final int PAT_USA = 7;
    static final int PAT_UK = 8;
    static final int PAT_BRAZIL = 9;
    static final int PAT_SWISS = 10;
    static final int PAT_GREECE = 11;
    static final int PAT_LEFT_TRI = 12;
    static final int PAT_LEFT_TRI_H3 = 13;
    static final int PAT_LEFT_TRI_H4 = 14;
    static final int PAT_SALTIRE = 15;
    static final int PAT_SALTIRE_DISK = 16;
    static final int PAT_KOREA = 17;
    static final int PAT_NORTH_KOREA = 18;
    static final int PAT_SPAIN = 19;
    static final int PAT_PORTUGAL = 20;
    static final int PAT_CHILE = 21;
    static final int PAT_COLOMBIA = 22;
    static final int PAT_THAI = 23;
    static final int PAT_CUBA = 24;
    static final int PAT_PANAMA = 25;
    static final int PAT_URUGUAY = 26;
    static final int PAT_BELIZE = 27;
    static final int PAT_DOMINICAN = 28;
    static final int PAT_DIAG_BAND = 29;
    static final int PAT_DIAG_SPLIT = 30;
    static final int PAT_DIAG_X = 31;
    static final int PAT_GUYANA = 32;
    static final int PAT_SURINAME = 33;
    static final int PAT_ANTIGUA = 34;
    static final int PAT_ST_LUCIA = 35;
    static final int PAT_CROSS_CENTER = 36;
    static final int PAT_LATVIA = 37;
    static final int PAT_BELARUS = 38;
    static final int PAT_MACEDONIA = 39;
    static final int PAT_BORDER = 40;
    static final int PAT_BORDER_H = 41;
    static final int PAT_BOSNIA = 42;
    static final int PAT_SOUTH_AFRICA = 43;
    static final int PAT_KENYA = 44;
    static final int PAT_MADAGASCAR = 45;
    static final int PAT_BOTSWANA = 46;
    static final int PAT_CABO_VERDE = 47;
    static final int PAT_CAR = 48;
    static final int PAT_H4 = 49;
    static final int PAT_SEYCHELLES = 50;
    static final int PAT_TOGO = 51;
    static final int PAT_UGANDA = 52;
    static final int PAT_ZAMBIA = 53;
    static final int PAT_UAE = 54;
    static final int PAT_PAKISTAN = 55;
    static final int PAT_NEPAL = 56;
    static final int PAT_SRI_LANKA = 57;
    static final int PAT_ISRAEL = 58;
    static final int PAT_QATAR = 59;
    static final int PAT_AUSTRALIA = 60;

    // Flag vector emblems
    static final int EMB_NONE = 0;
    static final int EMB_CHAKRA = 1;
    static final int EMB_MAPLE = 2;
    static final int EMB_MEXICO = 3;
    static final int EMB_SPAIN = 4;
    static final int EMB_STAR = 5;
    static final int EMB_STAR_LEFT = 6;
    static final int EMB_STAR_TL = 7;
    static final int EMB_TWO_STARS = 8;
    static final int EMB_THREE_STARS = 9;
    static final int EMB_FOUR_STARS = 10;
    static final int EMB_FIVE_STARS = 11;
    static final int EMB_FIVE_STARS_TL = 12;
    static final int EMB_ARC_STARS = 13;
    static final int EMB_RING_STARS = 14;
    static final int EMB_CHINA = 15;
    static final int EMB_CRESCENT_STAR = 16;
    static final int EMB_CRESCENT_TL = 17;
    static final int EMB_CRESCENT_WHITE = 18;
    static final int EMB_TUNISIA = 19;
    static final int EMB_SUN = 20;
    static final int EMB_SUN_LEFT = 21;
    static final int EMB_SUN_TL = 22;
    static final int EMB_SUN_TR = 23;
    static final int EMB_SUN_TOP = 24;
    static final int EMB_EAGLE = 25;
    static final int EMB_COAT = 26;
    static final int EMB_COAT_LEFT = 27;
    static final int EMB_COAT_RIGHT = 28;
    static final int EMB_SQUARE_COAT = 29;
    static final int EMB_RING = 30;
    static final int EMB_TRIANGLE_EMBLEM = 31;
    static final int EMB_TRIDENT = 32;
    static final int EMB_THREE_GEMS = 33;
    static final int EMB_CIRCLE_BIRD = 34;
    static final int EMB_CROSS_TL = 35;
    static final int EMB_CYPRUS = 36;
    static final int EMB_CROWN_TL = 37;
    static final int EMB_CROWN_CENTER = 38;
    static final int EMB_SHIELD = 39;
    static final int EMB_PENTAGRAM = 40;
    static final int EMB_BLUE_DISK_STAR = 41;
    static final int EMB_GEAR_STAR = 42;
    static final int EMB_SAUDI = 43;
    static final int EMB_LION = 44;
    static final int EMB_DRAGON = 45;
    static final int EMB_TEXT_GREEN = 46;
    static final int EMB_CEDAR = 47;
    static final int EMB_HEXAGRAM = 48;
    static final int EMB_SOYOMBO_LEFT = 49;
    static final int EMB_TEMPLE = 50;
    static final int EMB_FLOWER = 51;
    static final int EMB_BIRD_STARS = 52;

    static final class Country {
        final String code;
        final String name;
        final String capital;
        final String trapCity;
        final int continent;
        final int tier; // 1 = Novice, 2 = Expert
        final int pattern;
        final int c1;
        final int c2;
        final int c3;
        final int emblem;
        final int emblemColor;
        final String currency;
        final String language;
        final String landmark;
        final String flagColors;
        final String absentColor;
        final String funFact;

        Country(String code, String name, String capital, String trapCity, int continent, int tier,
                int pattern, int c1, int c2, int c3, int emblem, int emblemColor,
                String currency, String language, String landmark, String flagColors, String absentColor, String funFact) {
            this.code = code;
            this.name = name;
            this.capital = capital;
            this.trapCity = trapCity;
            this.continent = continent;
            this.tier = tier;
            this.pattern = pattern;
            this.c1 = c1;
            this.c2 = c2;
            this.c3 = c3;
            this.emblem = emblem;
            this.emblemColor = emblemColor;
            this.currency = currency;
            this.language = language;
            this.landmark = landmark;
            this.flagColors = flagColors;
            this.absentColor = absentColor;
            this.funFact = funFact;
        }
    }

    static final class Wonder {
        final String name;
        final String countryName;
        final int continent;
        final String clue;
        final String funFact;
        final int artType;

        Wonder(String name, String countryName, int continent, String clue, String funFact, int artType) {
            this.name = name;
            this.countryName = countryName;
            this.continent = continent;
            this.clue = clue;
            this.funFact = funFact;
            this.artType = artType;
        }
    }

    static final class OddQuestion {
        final boolean isProfileStyle; // true = Country Profile 4-Attribute Inspector, false = 4-Statement Spot-the-Lie
        final Country heroCountry;    // non-null when isProfileStyle
        final String title;
        final String subtitle;
        final String[] options;       // 4 choices (3 true, 1 wrong/impostor)
        final int wrongIndex;         // The correct choice to click (the lie/impostor!)
        final String explanation;     // Shown at bottom when answered

        OddQuestion(boolean isProfileStyle, Country heroCountry, String title, String subtitle,
                    String[] options, int wrongIndex, String explanation) {
            this.isProfileStyle = isProfileStyle;
            this.heroCountry = heroCountry;
            this.title = title;
            this.subtitle = subtitle;
            this.options = options;
            this.wrongIndex = wrongIndex;
            this.explanation = explanation;
        }
    }

    static final class AtlasEntry {
        final int rank;
        final String title;
        final String metric;
        final String countryName;
        final String subtitle;
        final String detailFact;

        AtlasEntry(int rank, String title, String metric, String countryName, String subtitle, String detailFact) {
            this.rank = rank;
            this.title = title;
            this.metric = metric;
            this.countryName = countryName;
            this.subtitle = subtitle;
            this.detailFact = detailFact;
        }
    }

    static final Country[] COUNTRIES = {
        new Country("US", "UNITED STATES", "Washington, D.C.", "New York City", 0, 1, PAT_USA, 0xFFB22234, 0xFFFFFFFF, 0xFF3C3B6E, EMB_NONE, 0,
                "US Dollar", "English", "Statue of Liberty & Grand Canyon", "Red, White, and Blue", "Green", "Has 50 stars for 50 states and 13 stripes for the original colonies!"),
        new Country("CA", "CANADA", "Ottawa", "Toronto", 0, 1, PAT_V3, 0xFFFF0000, 0xFFFFFFFF, 0xFFFF0000, EMB_MAPLE, 0xFFFF0000,
                "Canadian Dollar", "English & French", "Niagara Falls & Banff", "Red and White", "Blue", "Has more freshwater lakes than the rest of the world combined!"),
        new Country("MX", "MEXICO", "Mexico City", "Cancun", 0, 1, PAT_V3, 0xFF006847, 0xFFFFFFFF, 0xFFCE1126, EMB_MEXICO, 0xFF8B5A2B,
                "Mexican Peso", "Spanish", "Chichen Itza Pyramid", "Green, White, and Red", "Purple", "The flag's center shows an eagle perched on a prickly-pear cactus!"),
        new Country("BR", "BRAZIL", "Brasilia", "Rio de Janeiro", 0, 1, PAT_BRAZIL, 0xFF009739, 0xFFFEDD00, 0xFF012169, EMB_NONE, 0,
                "Brazilian Real", "Portuguese", "Christ the Redeemer & Amazon", "Green, Yellow, Blue, and White", "Red", "Home to 60% of the Amazon Rainforest!"),
        new Country("AR", "ARGENTINA", "Buenos Aires", "Cordoba", 0, 1, PAT_H3, 0xFF74ACDF, 0xFFFFFFFF, 0xFF74ACDF, EMB_SUN, 0xFFF6B40E,
                "Argentine Peso", "Spanish", "Iguazu Falls & Patagonia", "Light Blue, White, and Gold", "Red", "Features the golden Sun of May in the center of its flag!"),
        new Country("PE", "PERU", "Lima", "Cusco", 0, 1, PAT_V3, 0xFFD91023, 0xFFFFFFFF, 0xFFD91023, EMB_COAT, 0xFFD4AF37,
                "Peruvian Sol", "Spanish & Quechua", "Machu Picchu Citadel", "Red and White", "Blue", "Home to the ancient Inca cloud city of Machu Picchu!"),
        new Country("CL", "CHILE", "Santiago", "Valparaiso", 0, 1, PAT_CHILE, 0xFFFFFFFF, 0xFFD52B1E, 0xFF0039A6, EMB_STAR, 0xFFFFFFFF,
                "Chilean Peso", "Spanish", "Atacama Desert & Easter Island", "Red, White, and Blue", "Green", "Stretches over 2,600 miles along the Pacific coast!"),
        new Country("CO", "COLOMBIA", "Bogota", "Medellin", 0, 1, PAT_COLOMBIA, 0xFFFCD116, 0xFF003893, 0xFFCE1126, EMB_NONE, 0,
                "Colombian Peso", "Spanish", "Cano Cristales Rainbow River", "Yellow, Blue, and Red", "Green", "The only South American nation with both Pacific and Caribbean coasts!"),
        new Country("JM", "JAMAICA", "Kingston", "Montego Bay", 0, 1, PAT_SALTIRE, 0xFF009B3A, 0xFFFED100, 0xFF000000, EMB_NONE, 0,
                "Jamaican Dollar", "English", "Blue Mountains & Dunn's River", "Green, Gold, and Black", "Blue", "One of the only national flags without red, white, or blue!"),
        new Country("CU", "CUBA", "Havana", "Varadero", 0, 1, PAT_CUBA, 0xFF002A8F, 0xFFFFFFFF, 0xFFCF142B, EMB_STAR, 0xFFFFFFFF,
                "Cuban Peso", "Spanish", "Old Havana & Vinales Valley", "Blue, White, and Red", "Yellow", "The largest island nation in the Caribbean Sea!"),
        new Country("PA", "PANAMA", "Panama City", "Colon", 0, 1, PAT_PANAMA, 0xFFFFFFFF, 0xFFDA121A, 0xFF072357, EMB_STAR, 0xFF072357,
                "Panamanian Balboa", "Spanish", "Panama Canal", "White, Red, and Blue", "Green", "The Panama Canal connects the Atlantic and Pacific Oceans!"),
        new Country("CR", "COSTA RICA", "San Jose", "Limon", 0, 1, PAT_THAI, 0xFF002B7F, 0xFFFFFFFF, 0xFFCE1126, EMB_NONE, 0,
                "Costa Rican Colon", "Spanish", "Arenal Volcano & Cloud Forests", "Blue, White, and Red", "Black", "Contains 5% of the world's biodiversity in its lush rainforests!"),
        new Country("UY", "URUGUAY", "Montevideo", "Punta del Este", 0, 1, PAT_URUGUAY, 0xFF0038A8, 0xFFFFFFFF, 0xFFFCD116, EMB_SUN, 0xFFFCD116,
                "Uruguayan Peso", "Spanish", "Casapueblo & Colonia", "White, Blue, and Gold", "Red", "Has nine horizontal stripes and a golden Sun of May in the corner!"),
        new Country("VE", "VENEZUELA", "Caracas", "Maracaibo", 0, 1, PAT_H3, 0xFFFCC300, 0xFF00247D, 0xFFCF142B, EMB_ARC_STARS, 0xFFFFFFFF,
                "Venezuelan Bolivar", "Spanish", "Angel Falls Waterfall", "Yellow, Blue, Red, and White", "Green", "Home to Angel Falls, the highest uninterrupted waterfall on Earth!"),
        new Country("BO", "BOLIVIA", "La Paz & Sucre", "Santa Cruz", 0, 2, PAT_H3, 0xFFD52B1E, 0xFFF9E300, 0xFF007934, EMB_COAT, 0xFFD4AF37,
                "Bolivian Boliviano", "Spanish & Quechua", "Salar de Uyuni Salt Flats", "Red, Yellow, and Green", "Blue", "Salar de Uyuni becomes the world's largest mirror when it rains!"),
        new Country("EC", "ECUADOR", "Quito", "Guayaquil", 0, 2, PAT_COLOMBIA, 0xFFFFDD00, 0xFF034EA2, 0xFFED1C24, EMB_COAT, 0xFF8B5A2B,
                "US Dollar", "Spanish", "Galapagos Islands", "Yellow, Blue, and Red", "Purple", "Named after the Equator line that runs right through it!"),
        new Country("PY", "PARAGUAY", "Asuncion", "Ciudad del Este", 0, 2, PAT_H3, 0xFFD52B1E, 0xFFFFFFFF, 0xFF0038A8, EMB_RING, 0xFFD4AF37,
                "Paraguayan Guarani", "Spanish & Guarani", "Itaipu Dam", "Red, White, and Blue", "Orange", "Its flag has two different emblems on the front and back sides!"),
        new Country("GT", "GUATEMALA", "Guatemala City", "Antigua", 0, 2, PAT_V3, 0xFF4997D0, 0xFFFFFFFF, 0xFF4997D0, EMB_COAT, 0xFF2E8B57,
                "Guatemalan Quetzal", "Spanish", "Tikal Mayan Temples", "Sky Blue and White", "Red", "Its currency is named after the colourful resplendent quetzal bird!"),
        new Country("HN", "HONDURAS", "Tegucigalpa", "San Pedro Sula", 0, 2, PAT_H3, 0xFF00BCE4, 0xFFFFFFFF, 0xFF00BCE4, EMB_FIVE_STARS, 0xFF00BCE4,
                "Honduran Lempira", "Spanish", "Copan Mayan Ruins", "Turquoise Blue and White", "Red", "Five stars in the center represent the five Central American nations!"),
        new Country("SV", "EL SALVADOR", "San Salvador", "Santa Ana", 0, 2, PAT_H3, 0xFF0F47AF, 0xFFFFFFFF, 0xFF0F47AF, EMB_COAT, 0xFFD4AF37,
                "US Dollar", "Spanish", "Santa Ana Volcano", "Cobalt Blue and White", "Red", "Known as the Land of Volcanoes in Central America!"),
        new Country("NI", "NICARAGUA", "Managua", "Granada", 0, 2, PAT_H3, 0xFF0067C6, 0xFFFFFFFF, 0xFF0067C6, EMB_TRIANGLE_EMBLEM, 0xFFD4AF37,
                "Nicaraguan Cordoba", "Spanish", "Lake Nicaragua & Ometepe", "Blue and White", "Black", "Lake Nicaragua is home to rare freshwater sharks!"),
        new Country("BZ", "BELIZE", "Belmopan", "Belize City", 0, 2, PAT_BELIZE, 0xFF003F87, 0xFFCE1126, 0xFFFFFFFF, EMB_RING, 0xFF2E8B57,
                "Belize Dollar", "English", "Great Blue Hole Reef", "Royal Blue, Red, and White", "Purple", "Belmopan replaced Belize City as the capital in 1970!"),
        new Country("DO", "DOMINICAN REPUBLIC", "Santo Domingo", "Punta Cana", 0, 2, PAT_DOMINICAN, 0xFF002D62, 0xFFCE1126, 0xFFFFFFFF, EMB_COAT, 0xFF2E8B57,
                "Dominican Peso", "Spanish", "Colonial Santo Domingo", "Blue, Red, and White", "Orange", "Santo Domingo is the oldest European-founded city in the Americas!"),
        new Country("HT", "HAITI", "Port-au-Prince", "Cap-Haitien", 0, 2, PAT_H2, 0xFF00209F, 0xFFD21034, 0xFFFFFFFF, EMB_SQUARE_COAT, 0xFFFFFFFF,
                "Haitian Gourde", "French & Haitian Creole", "Citadelle Laferriere", "Blue and Red", "Purple", "Home to the giant mountaintop fortress Citadelle Laferriere!"),
        new Country("BS", "BAHAMAS", "Nassau", "Freeport", 0, 2, PAT_LEFT_TRI_H3, 0xFF00778B, 0xFFFFC72C, 0xFF000000, EMB_NONE, 0,
                "Bahamian Dollar", "English", "Exuma Cays Coral Islands", "Aquamarine, Gold, and Black", "Red", "An archipelago of over 700 coral islands in the Atlantic!"),
        new Country("BB", "BARBADOS", "Bridgetown", "Speightstown", 0, 2, PAT_V3, 0xFF00267F, 0xFFFFC726, 0xFF00267F, EMB_TRIDENT, 0xFF000000,
                "Barbadian Dollar", "English", "Harrison's Cave", "Ultramarine Blue, Gold, and Black", "Red", "Features a black broken trident head in the golden center stripe!"),
        new Country("TT", "TRINIDAD AND TOBAGO", "Port of Spain", "San Fernando", 0, 2, PAT_DIAG_BAND, 0xFFCE1126, 0xFF000000, 0xFFFFFFFF, EMB_NONE, 0,
                "Trinidad Dollar", "English", "Pitch Lake & Asa Wright", "Red, Black, and White", "Blue", "Birthplace of the steelpan musical instrument!"),
        new Country("GY", "GUYANA", "Georgetown", "Linden", 0, 2, PAT_GUYANA, 0xFF009E49, 0xFFFCD116, 0xFFCE1126, EMB_NONE, 0,
                "Guyanese Dollar", "English", "Kaieteur Falls", "Green, Yellow, Red, Black, and White", "Blue", "Its flag is nicknamed The Golden Arrowhead!"),
        new Country("SR", "SURINAME", "Paramaribo", "Lelydorp", 0, 2, PAT_SURINAME, 0xFF377E3F, 0xFFB40A2D, 0xFFFFFFFF, EMB_STAR, 0xFFECC81D,
                "Surinamese Dollar", "Dutch", "Central Suriname Nature Reserve", "Green, White, Red, and Yellow", "Blue", "Over 90% of Suriname is covered by pristine tropical rainforest!"),
        new Country("AG", "ANTIGUA AND BARBUDA", "Saint John's", "Codrington", 0, 2, PAT_ANTIGUA, 0xFFCE1126, 0xFF000000, 0xFF0072C6, EMB_SUN, 0xFFFCD116,
                "East Caribbean Dollar", "English", "Nelson's Dockyard", "Red, Black, Blue, White, and Gold", "Green", "Said to have 365 beaches—one for every day of the year!"),
        new Country("LC", "SAINT LUCIA", "Castries", "Soufriere", 0, 2, PAT_ST_LUCIA, 0xFF66CCFF, 0xFF000000, 0xFFFCD116, EMB_NONE, 0,
                "East Caribbean Dollar", "English", "The Twin Piton Peaks", "Cerulean Blue, Yellow, Black, and White", "Red", "The triangles on its flag represent the twin volcanic Piton peaks!"),
        new Country("GD", "GRENADA", "Saint George's", "Gouyave", 0, 2, PAT_DIAG_X, 0xFFCE1126, 0xFFFCD116, 0xFF007A5E, EMB_STAR, 0xFFFCD116,
                "East Caribbean Dollar", "English", "Grand Etang Crater Lake", "Red, Gold, and Green", "Blue", "Nicknamed the Island of Spice for its nutmeg harvests!"),
        new Country("VC", "SAINT VINCENT", "Kingstown", "Georgetown", 0, 2, PAT_V3, 0xFF0072C6, 0xFFFCD116, 0xFF009E60, EMB_THREE_GEMS, 0xFF009E60,
                "East Caribbean Dollar", "English", "Tobago Cays", "Blue, Gold, and Green", "Red", "Three green diamonds in the center represent the Gems of the Antilles!"),
        new Country("DM", "DOMINICA", "Roseau", "Portsmouth", 0, 2, PAT_CROSS_CENTER, 0xFF006B3F, 0xFFFCD116, 0xFF000000, EMB_CIRCLE_BIRD, 0xFF9400D3,
                "East Caribbean Dollar", "English", "Boiling Lake & Morne Trois Pitons", "Green, Yellow, Black, White, Red, and Purple", "Orange", "One of the only national flags with purple—on its Sisserou parrot!"),
        new Country("KN", "SAINT KITTS AND NEVIS", "Basseterre", "Charlestown", 0, 2, PAT_DIAG_BAND, 0xFF009E49, 0xFF000000, 0xFFCE1126, EMB_TWO_STARS, 0xFFFFFFFF,
                "East Caribbean Dollar", "English", "Brimstone Hill Fortress", "Green, Red, Black, Yellow, and White", "Blue", "The smallest sovereign country in the Americas!"),
        new Country("GB", "UNITED KINGDOM", "London", "Manchester", 1, 1, PAT_UK, 0xFF012169, 0xFFFFFFFF, 0xFFC8102E, EMB_NONE, 0,
                "British Pound", "English", "Big Ben & Stonehenge", "Blue, White, and Red", "Green", "The Union Jack combines the crosses of England, Scotland, and Ireland!"),
        new Country("FR", "FRANCE", "Paris", "Marseille", 1, 1, PAT_V3, 0xFF002654, 0xFFFFFFFF, 0xFFED2939, EMB_NONE, 0,
                "Euro", "French", "Eiffel Tower & Louvre", "Blue, White, and Red", "Yellow", "The Eiffel Tower was built for the 1889 World's Fair in Paris!"),
        new Country("DE", "GERMANY", "Berlin", "Munich", 1, 1, PAT_H3, 0xFF000000, 0xFFDD0000, 0xFFFFCE00, EMB_NONE, 0,
                "Euro", "German", "Brandenburg Gate & Neuschwanstein", "Black, Red, and Gold", "Blue", "Home to over 20,000 historic castles across its landscapes!"),
        new Country("IT", "ITALY", "Rome", "Milan", 1, 1, PAT_V3, 0xFF009246, 0xFFFFFFFF, 0xFFCE2B37, EMB_NONE, 0,
                "Euro", "Italian", "Colosseum & Leaning Tower of Pisa", "Green, White, and Red", "Blue", "Italy has more UNESCO World Heritage sites than any other nation!"),
        new Country("ES", "SPAIN", "Madrid", "Barcelona", 1, 1, PAT_SPAIN, 0xFFAA151B, 0xFFF1BF00, 0xFFAA151B, EMB_SPAIN, 0xFFAA151B,
                "Euro", "Spanish", "Sagrada Familia & Alhambra", "Red and Yellow", "Green", "Madrid sits right at the geographic center of Spain!"),
        new Country("CH", "SWITZERLAND", "Bern", "Zurich", 1, 1, PAT_SWISS, 0xFFFF0000, 0xFFFFFFFF, 0xFFFF0000, EMB_NONE, 0,
                "Swiss Franc", "German, French & Italian", "Matterhorn Peak in the Alps", "Red and White", "Blue", "One of only two square sovereign national flags in the world!"),
        new Country("SE", "SWEDEN", "Stockholm", "Gothenburg", 1, 1, PAT_NORDIC, 0xFF006AA7, 0xFFFECC02, 0, EMB_NONE, 0,
                "Swedish Krona", "Swedish", "Stockholm Archipelago", "Blue and Yellow", "Red", "Stockholm is built across 14 islands connected by 57 bridges!"),
        new Country("NO", "NORWAY", "Oslo", "Bergen", 1, 1, PAT_NORDIC, 0xFFBA0C2F, 0xFFFFFFFF, 0xFF00205B, EMB_NONE, 0,
                "Norwegian Krone", "Norwegian", "Geirangerfjord & Northern Lights", "Red, White, and Blue", "Green", "Famous for deep coastal fjords carved by ancient glaciers!"),
        new Country("GR", "GREECE", "Athens", "Thessaloniki", 1, 1, PAT_GREECE, 0xFF0D5EAF, 0xFFFFFFFF, 0, EMB_NONE, 0,
                "Euro", "Greek", "Parthenon on the Acropolis", "Blue and White", "Red", "Birthplace of the ancient and modern Olympic Games!"),
        new Country("NL", "NETHERLANDS", "Amsterdam", "Rotterdam", 1, 1, PAT_H3, 0xFFAE1C28, 0xFFFFFFFF, 0xFF21468B, EMB_NONE, 0,
                "Euro", "Dutch", "Kinderdijk Windmills & Canals", "Red, White, and Blue", "Green", "There are more bicycles than people in the Netherlands!"),
        new Country("IE", "IRELAND", "Dublin", "Cork", 1, 1, PAT_V3, 0xFF169B62, 0xFFFFFFFF, 0xFFFF883E, EMB_NONE, 0,
                "Euro", "Irish & English", "Cliffs of Moher", "Green, White, and Orange", "Blue", "Nicknamed the Emerald Isle for its rolling green hills!"),
        new Country("PT", "PORTUGAL", "Lisbon", "Porto", 1, 1, PAT_PORTUGAL, 0xFF006600, 0xFFFF0000, 0xFFFFCC00, EMB_COAT, 0xFFFFCC00,
                "Euro", "Portuguese", "Belem Tower & Pena Palace", "Green, Red, and Gold", "Purple", "Lisbon is one of the oldest cities in Western Europe!"),
        new Country("PL", "POLAND", "Warsaw", "Krakow", 1, 1, PAT_H2, 0xFFFFFFFF, 0xFFDC143C, 0, EMB_NONE, 0,
                "Polish Zloty", "Polish", "Wawel Castle & Wieliczka Salt Mine", "White and Red", "Blue", "Home to Europe's last primeval lowland forest, Bialowieza!"),
        new Country("UA", "UKRAINE", "Kyiv", "Lviv", 1, 1, PAT_H2, 0xFF0057B7, 0xFFFFDD00, 0, EMB_NONE, 0,
                "Ukrainian Hryvnia", "Ukrainian", "Saint Sophia Cathedral", "Blue and Yellow", "Red", "The flag shows blue sky above golden fields of wheat!"),
        new Country("RU", "RUSSIA", "Moscow", "Saint Petersburg", 1, 1, PAT_H3, 0xFFFFFFFF, 0xFF0039A6, 0xFFD52B1E, EMB_NONE, 0,
                "Russian Ruble", "Russian", "Red Square & Lake Baikal", "White, Blue, and Red", "Green", "The largest country on Earth by land area, spanning 11 time zones!"),
        new Country("CZ", "CZECH REPUBLIC", "Prague", "Brno", 1, 2, PAT_LEFT_TRI, 0xFFFFFFFF, 0xFFD7141A, 0xFF11457E, EMB_NONE, 0,
                "Czech Koruna", "Czech", "Charles Bridge & Prague Castle", "White, Red, and Blue", "Green", "Prague Castle is the largest ancient castle complex in the world!"),
        new Country("AT", "AUSTRIA", "Vienna", "Salzburg", 1, 2, PAT_H3, 0xFFED2939, 0xFFFFFFFF, 0xFFED2939, EMB_NONE, 0,
                "Euro", "German", "Schonbrunn Palace & Austrian Alps", "Red and White", "Blue", "Vienna is renowned as the world capital of classical music!"),
        new Country("BE", "BELGIUM", "Brussels", "Antwerp", 1, 2, PAT_V3, 0xFF000000, 0xFFFDDA24, 0xFFEF3340, EMB_NONE, 0,
                "Euro", "Dutch, French & German", "Grand Place & Atomium", "Black, Yellow, and Red", "Blue", "Brussels hosts the headquarters of the European Union!"),
        new Country("DK", "DENMARK", "Copenhagen", "Aarhus", 1, 2, PAT_NORDIC, 0xFFC8102E, 0xFFFFFFFF, 0, EMB_NONE, 0,
                "Danish Krone", "Danish", "Nyhavn Harbor & Kronborg Castle", "Red and White", "Blue", "Its flag, the Dannebrog, has been used since 1219!"),
        new Country("FI", "FINLAND", "Helsinki", "Tampere", 1, 2, PAT_NORDIC, 0xFFFFFFFF, 0xFF002F6C, 0, EMB_NONE, 0,
                "Euro", "Finnish & Swedish", "Lapland & 188,000 Lakes", "White and Blue", "Red", "Called the Land of a Thousand Lakes—it actually has over 188,000!"),
        new Country("IS", "ICELAND", "Reykjavik", "Akureyri", 1, 2, PAT_NORDIC, 0xFF02529C, 0xFFFFFFFF, 0xFFDC1E35, EMB_NONE, 0,
                "Icelandic Krona", "Icelandic", "Blue Lagoon & Gullfoss Geysers", "Blue, White, and Red", "Green", "Reykjavik is the world's northernmost capital of a sovereign state!"),
        new Country("HU", "HUNGARY", "Budapest", "Debrecen", 1, 2, PAT_H3, 0xFFCE2939, 0xFFFFFFFF, 0xFF477050, EMB_NONE, 0,
                "Hungarian Forint", "Hungarian", "Hungarian Parliament on the Danube", "Red, White, and Green", "Blue", "The Danube River divides Budapest into historic Buda and flat Pest!"),
        new Country("RO", "ROMANIA", "Bucharest", "Cluj-Napoca", 1, 2, PAT_V3, 0xFF002B7F, 0xFFFCD116, 0xFFCE1126, EMB_NONE, 0,
                "Romanian Leu", "Romanian", "Bran Castle & Carpathian Mountains", "Blue, Yellow, and Red", "Green", "The Danube Delta in Romania is a vast wildlife sanctuary!"),
        new Country("BG", "BULGARIA", "Sofia", "Plovdiv", 1, 2, PAT_H3, 0xFFFFFFFF, 0xFF00966E, 0xFFD62612, EMB_NONE, 0,
                "Bulgarian Lev", "Bulgarian", "Rila Monastery", "White, Green, and Red", "Blue", "Produces over half of the world's fragrant rose oil!"),
        new Country("HR", "CROATIA", "Zagreb", "Dubrovnik", 1, 2, PAT_H3, 0xFFFF0000, 0xFFFFFFFF, 0xFF171796, EMB_COAT, 0xFFFF0000,
                "Euro", "Croatian", "Plitvice Lakes & Dubrovnik Walls", "Red, White, and Blue", "Green", "Has over 1,200 islands scattered along its Adriatic Sea coast!"),
        new Country("RS", "SERBIA", "Belgrade", "Novi Sad", 1, 2, PAT_H3, 0xFFC6363C, 0xFF0C4076, 0xFFFFFFFF, EMB_COAT_LEFT, 0xFFD4AF37,
                "Serbian Dinar", "Serbian", "Belgrade Fortress at Two Rivers", "Red, Blue, and White", "Green", "Belgrade sits right where the Sava and Danube rivers meet!"),
        new Country("SI", "SLOVENIA", "Ljubljana", "Maribor", 1, 2, PAT_H3, 0xFFFFFFFF, 0xFF005DA4, 0xFFED1C24, EMB_COAT_LEFT, 0xFF005DA4,
                "Euro", "Slovene", "Lake Bled & Postojna Cave", "White, Blue, and Red", "Yellow", "Its flag's crest shows Mount Triglav, Slovenia's highest peak!"),
        new Country("SK", "SLOVAKIA", "Bratislava", "Kosice", 1, 2, PAT_H3, 0xFFFFFFFF, 0xFF0B4EA2, 0xFFEE1C25, EMB_COAT_LEFT, 0xFFEE1C25,
                "Euro", "Slovak", "High Tatras & Spis Castle", "White, Blue, and Red", "Green", "Bratislava borders two countries—Austria and Hungary!"),
        new Country("EE", "ESTONIA", "Tallinn", "Tartu", 1, 2, PAT_H3, 0xFF0072CE, 0xFF000000, 0xFFFFFFFF, EMB_NONE, 0,
                "Euro", "Estonian", "Medieval Old Town of Tallinn", "Blue, Black, and White", "Red", "Over half of Estonia's mainland is covered in forest!"),
        new Country("LV", "LATVIA", "Riga", "Jurmala", 1, 2, PAT_LATVIA, 0xFF9E3039, 0xFFFFFFFF, 0xFF9E3039, EMB_NONE, 0,
                "Euro", "Latvian", "House of the Blackheads in Riga", "Carmine Red and White", "Blue", "Features Europe's widest waterfall, Ventas Rumba!"),
        new Country("LT", "LITHUANIA", "Vilnius", "Kaunas", 1, 2, PAT_H3, 0xFFFDB913, 0xFF006A44, 0xFFC1272D, EMB_NONE, 0,
                "Euro", "Lithuanian", "Trakai Island Castle", "Yellow, Green, and Red", "Blue", "Trakai Castle sits on an island in Lake Galve!"),
        new Country("BY", "BELARUS", "Minsk", "Brest", 1, 2, PAT_BELARUS, 0xFFC8313E, 0xFF4AA657, 0xFFFFFFFF, EMB_NONE, 0,
                "Belarusian Ruble", "Belarusian & Russian", "Mir Castle Complex", "Red, Green, and White", "Blue", "Has a traditional woven red-and-white pattern along the hoist!"),
        new Country("MD", "MOLDOVA", "Chisinau", "Tiraspol", 1, 2, PAT_V3, 0xFF003DA5, 0xFFFFD100, 0xFFC8102E, EMB_COAT, 0xFF8B5A2B,
                "Moldovan Leu", "Romanian", "Orheiul Vechi Monastery", "Blue, Yellow, and Red", "Green", "Famous for having the largest underground cellars in Europe!"),
        new Country("AL", "ALBANIA", "Tirana", "Durres", 1, 2, PAT_SOLID, 0xFFE41E20, 0xFFE41E20, 0, EMB_EAGLE, 0xFF000000,
                "Albanian Lek", "Albanian", "Berat & Albanian Alps", "Red and Black", "White", "In Albanian, the country is called Shqiperia—Land of the Eagles!"),
        new Country("MK", "NORTH MACEDONIA", "Skopje", "Ohrid", 1, 2, PAT_MACEDONIA, 0xFFD20000, 0xFFFFE600, 0, EMB_NONE, 0,
                "Macedonian Denar", "Macedonian", "Lake Ohrid", "Red and Yellow", "Blue", "Lake Ohrid is one of Europe's deepest and oldest lakes!"),
        new Country("ME", "MONTENEGRO", "Podgorica", "Kotor", 1, 2, PAT_BORDER, 0xFFC40308, 0xFFD4AF37, 0, EMB_COAT, 0xFFD4AF37,
                "Euro", "Montenegrin", "Bay of Kotor & Durmitor", "Red and Gold", "Green", "The Tara River Canyon here is the deepest canyon in Europe!"),
        new Country("BA", "BOSNIA AND HERZEGOVINA", "Sarajevo", "Mostar", 1, 2, PAT_BOSNIA, 0xFF002395, 0xFFFECB00, 0xFFFFFFFF, EMB_NONE, 0,
                "Convertible Mark", "Bosnian, Croatian & Serbian", "Stari Most Bridge in Mostar", "Blue, Yellow, and White", "Red", "Sarajevo hosted the 1984 Winter Olympic Games!"),
        new Country("LU", "LUXEMBOURG", "Luxembourg City", "Esch-sur-Alzette", 1, 2, PAT_H3, 0xFFEF3340, 0xFFFFFFFF, 0xFF00A3E0, EMB_NONE, 0,
                "Euro", "Luxembourgish, French & German", "Vianden Castle & Casemates", "Red, White, and Sky Blue", "Green", "Made all public trains, trams, and buses completely free in 2020!"),
        new Country("MT", "MALTA", "Valletta", "Mdina", 1, 2, PAT_V2, 0xFFFFFFFF, 0xFFCF142B, 0, EMB_CROSS_TL, 0xFFCCCCCC,
                "Euro", "Maltese & English", "Grand Harbour & Megalithic Temples", "White and Red", "Blue", "Its prehistoric stone temples are older than the Egyptian Pyramids!"),
        new Country("CY", "CYPRUS", "Nicosia", "Limassol", 1, 2, PAT_SOLID, 0xFFFFFFFF, 0xFFFFFFFF, 0, EMB_CYPRUS, 0xFFD47600,
                "Euro", "Greek & Turkish", "Troodos Mountains & Kourion", "White, Copper Orange, and Olive Green", "Red", "One of the only flags in the world that displays a map of the country!"),
        new Country("MC", "MONACO", "Monaco", "Monte Carlo", 1, 2, PAT_H2, 0xFFCE1126, 0xFFFFFFFF, 0, EMB_NONE, 0,
                "Euro", "French", "Prince's Palace & Oceanographic Museum", "Red and White", "Blue", "The second-smallest country on Earth after Vatican City!"),
        new Country("LI", "LIECHTENSTEIN", "Vaduz", "Schaan", 1, 2, PAT_H2, 0xFF002B7F, 0xFFCE1126, 0, EMB_CROWN_TL, 0xFFFFD700,
                "Swiss Franc", "German", "Vaduz Castle above the Rhine", "Blue, Red, and Gold", "Green", "A doubly landlocked Alpine country between Switzerland and Austria!"),
        new Country("AD", "ANDORRA", "Andorra la Vella", "Escaldes", 1, 2, PAT_V3, 0xFF10069F, 0xFFFEDD00, 0xFFD50032, EMB_COAT, 0xFFD4AF37,
                "Euro", "Catalan", "Pyrenees Mountain Valleys", "Blue, Yellow, and Red", "Green", "Andorra la Vella is the highest capital city in Europe at 1,023 meters!"),
        new Country("SM", "SAN MARINO", "San Marino", "Serravalle", 1, 2, PAT_H2, 0xFFFFFFFF, 0xFF5EB6E4, 0, EMB_COAT, 0xFFD4AF37,
                "Euro", "Italian", "Three Towers of Mount Titano", "White and Light Blue", "Red", "Founded in the year 301 AD, making it the oldest republic in the world!"),
        new Country("VA", "VATICAN CITY", "Vatican City", "Rome", 1, 2, PAT_V2, 0xFFFFE000, 0xFFFFFFFF, 0, EMB_COAT, 0xFFD4AF37,
                "Euro", "Italian & Latin", "St. Peter's Basilica", "Yellow and White", "Blue", "The smallest independent country on Earth at just 0.49 square kilometers!"),
        new Country("EG", "EGYPT", "Cairo", "Alexandria", 2, 1, PAT_H3, 0xFFCE1126, 0xFFFFFFFF, 0xFF000000, EMB_EAGLE, 0xFFC09300,
                "Egyptian Pound", "Arabic", "Great Pyramids of Giza & Nile", "Red, White, Black, and Gold", "Blue", "The Great Pyramid of Giza was the tallest structure on Earth for 3,800 years!"),
        new Country("ZA", "SOUTH AFRICA", "Pretoria, Cape Town & Bloemfontein", "Johannesburg", 2, 1, PAT_SOUTH_AFRICA, 0xFF007749, 0xFFE03C31, 0xFF001489, EMB_NONE, 0,
                "South African Rand", "11 Official Languages", "Table Mountain & Kruger Park", "Red, Blue, Green, Yellow, Black, and White", "Purple", "Uniquely has three capital cities—executive, legislative, and judicial!"),
        new Country("KE", "KENYA", "Nairobi", "Mombasa", 2, 1, PAT_KENYA, 0xFF000000, 0xFFBB0000, 0xFF006600, EMB_SHIELD, 0xFFFFFFFF,
                "Kenyan Shilling", "Swahili & English", "Maasai Mara & Mount Kenya", "Black, Red, Green, and White", "Blue", "A traditional Maasai shield and two crossed spears guard the center of its flag!"),
        new Country("NG", "NIGERIA", "Abuja", "Lagos", 2, 1, PAT_V3, 0xFF008751, 0xFFFFFFFF, 0xFF008751, EMB_NONE, 0,
                "Nigerian Naira", "English, Hausa, Yoruba & Igbo", "Zuma Rock & Niger River", "Green and White", "Red", "The most populous country on the African continent!"),
        new Country("MA", "MOROCCO", "Rabat", "Casablanca", 2, 1, PAT_SOLID, 0xFFC1272D, 0xFFC1272D, 0, EMB_PENTAGRAM, 0xFF006233,
                "Moroccan Dirham", "Arabic & Amazigh", "Atlas Mountains & Sahara Dunes", "Red and Green", "Blue", "Has coastlines on both the Atlantic Ocean and the Mediterranean Sea!"),
        new Country("GH", "GHANA", "Accra", "Kumasi", 2, 1, PAT_H3, 0xFFCE1126, 0xFFFCD116, 0xFF006B3F, EMB_STAR, 0xFF000000,
                "Ghanaian Cedi", "English", "Lake Volta & Kakum Canopy", "Red, Gold, Green, and Black", "Blue", "Lake Volta in Ghana is the largest artificial reservoir in the world by surface area!"),
        new Country("ET", "ETHIOPIA", "Addis Ababa", "Gondar", 2, 1, PAT_H3, 0xFF078930, 0xFFFCDD09, 0xFFDA121A, EMB_BLUE_DISK_STAR, 0xFF0F47AF,
                "Ethiopian Birr", "Amharic", "Rock-Hewn Churches of Lalibela", "Green, Yellow, Red, and Blue", "Black", "Follows its own ancient calendar that has 13 months!"),
        new Country("TZ", "TANZANIA", "Dodoma", "Dar es Salaam", 2, 1, PAT_DIAG_BAND, 0xFF1EB53A, 0xFF000000, 0xFF00A3DD, EMB_NONE, 0,
                "Tanzanian Shilling", "Swahili & English", "Mount Kilimanjaro & Serengeti", "Green, Yellow, Black, and Blue", "Red", "Home to Mount Kilimanjaro, the highest peak in Africa!"),
        new Country("DZ", "ALGERIA", " Algiers", "Oran", 2, 1, PAT_V2, 0xFF006233, 0xFFFFFFFF, 0, EMB_CRESCENT_STAR, 0xFFD21034,
                "Algerian Dinar", "Arabic & Tamazight", "Tassili n'Ajjer Sahara Plateau", "Green, White, and Red", "Blue", "The largest country in Africa by land area!"),
        new Country("TN", "TUNISIA", "Tunis", "Sfax", 2, 1, PAT_SOLID, 0xFFE70013, 0xFFE70013, 0, EMB_TUNISIA, 0xFFFFFFFF,
                "Tunisian Dinar", "Arabic", "Ancient Ruins of Carthage", "Red and White", "Green", "The northernmost country on the African continent!"),
        new Country("SN", "SENEGAL", "Dakar", "Saint-Louis", 2, 1, PAT_V3, 0xFF00853F, 0xFFFDEF42, 0xFFE31B23, EMB_STAR, 0xFF00853F,
                "West African CFA Franc", "French & Wolof", "Pink Lake Retba & Goree", "Green, Yellow, and Red", "Blue", "Dakar sits on the westernmost peninsula of mainland Africa!"),
        new Country("MG", "MADAGASCAR", "Antananarivo", "Toamasina", 2, 1, PAT_MADAGASCAR, 0xFFFFFFFF, 0xFFFC3D32, 0xFF007E3A, EMB_NONE, 0,
                "Malagasy Ariary", "Malagasy & French", "Avenue of the Baobabs", "White, Red, and Green", "Blue", "Around 90% of its wildlife—including lemurs—is found nowhere else on Earth!"),
        new Country("AO", "ANGOLA", "Luanda", "Huambo", 2, 2, PAT_H2, 0xFFCC092F, 0xFF000000, 0, EMB_GEAR_STAR, 0xFFFFCB00,
                "Angolan Kwanza", "Portuguese", "Kalandula Falls", "Red, Black, and Yellow", "Blue", "Kalandula Falls is one of the largest waterfalls by volume in Africa!"),
        new Country("BJ", "BENIN", "Porto-Novo", "Cotonou", 2, 2, PAT_MADAGASCAR, 0xFF008751, 0xFFFCD116, 0xFFE8112D, EMB_NONE, 0,
                "West African CFA Franc", "French", "Ganvie Stilt Village", "Green, Yellow, and Red", "Blue", "Porto-Novo is the official capital, while Cotonou is the largest city!"),
        new Country("BW", "BOTSWANA", "Gaborone", "Francistown", 2, 2, PAT_BOTSWANA, 0xFF6DA9D2, 0xFF000000, 0xFFFFFFFF, EMB_NONE, 0,
                "Botswana Pula", "English & Setswana", "Okavango Delta", "Light Blue, Black, and White", "Red", "Its currency, Pula, literally means Rain in Setswana!"),
        new Country("BF", "BURKINA FASO", "Ouagadougou", "Bobo-Dioulasso", 2, 2, PAT_H2, 0xFFEF2B2D, 0xFF009E49, 0, EMB_STAR, 0xFFFCD116,
                "West African CFA Franc", "French", "Peaks of Sindou", "Red, Green, and Yellow", "Blue", "Burkina Faso translates to Land of Incorruptible People!"),
        new Country("BI", "BURUNDI", "Gitega", "Bujumbura", 2, 2, PAT_SALTIRE_DISK, 0xFFCE1126, 0xFF1EB53A, 0xFFFFFFFF, EMB_THREE_STARS, 0xFFCE1126,
                "Burundian Franc", "Kirundi & French", "Lake Tanganyika Shore", "Red, Green, and White", "Blue", "Borders Lake Tanganyika, the second-deepest lake in the world!"),
        new Country("CV", "CABO VERDE", "Praia", "Mindelo", 2, 2, PAT_CABO_VERDE, 0xFF003893, 0xFFCF2027, 0xFFFFFFFF, EMB_RING_STARS, 0xFFF7D116,
                "Cape Verdean Escudo", "Portuguese", "Pico do Fogo Volcano", "Blue, White, Red, and Yellow", "Green", "An island nation of 10 volcanic islands in the central Atlantic Ocean!"),
        new Country("CM", "CAMEROON", "Yaounde", "Douala", 2, 2, PAT_V3, 0xFF007A5E, 0xFFCE1126, 0xFFFCD116, EMB_STAR, 0xFFFCD116,
                "Central African CFA Franc", "French & English", "Mount Cameroon & Waza Park", "Green, Red, and Yellow", "Blue", "Often called Africa in Miniature for its diverse climates and landscapes!"),
        new Country("CF", "CENTRAL AFRICAN REPUBLIC", "Bangui", "Bimbo", 2, 2, PAT_CAR, 0xFF003082, 0xFF289728, 0xFFFFCE00, EMB_STAR_TL, 0xFFFFCE00,
                "Central African CFA Franc", "French & Sango", "Dzanga-Sangha Rainforest", "Blue, White, Green, Yellow, and Red", "Black", "Has five colors on its flag crossed by a central vertical red stripe!"),
        new Country("TD", "CHAD", "N'Djamena", "Moundou", 2, 2, PAT_V3, 0xFF002664, 0xFFFECB00, 0xFFC60C30, EMB_NONE, 0,
                "Central African CFA Franc", "French & Arabic", "Ennedi Plateau & Lake Chad", "Blue, Gold, and Red", "Green", "Named after Lake Chad, once one of Africa's largest lakes!"),
        new Country("KM", "COMOROS", "Moroni", "Mutsamudu", 2, 2, PAT_LEFT_TRI_H4, 0xFFFFD100, 0xFFFFFFFF, 0xFF3A75C4, EMB_CRESCENT_STAR, 0xFFFFFFFF,
                "Comorian Franc", "Comorian, Arabic & French", "Mount Karthala Volcano", "Yellow, White, Red, Blue, and Green", "Black", "Nicknamed the Perfume Islands for its fragrant ylang-ylang flowers!"),
        new Country("CG", "REPUBLIC OF THE CONGO", "Brazzaville", "Pointe-Noire", 2, 2, PAT_DIAG_BAND, 0xFF009543, 0xFFFBDE4A, 0xFFDC241F, EMB_NONE, 0,
                "Central African CFA Franc", "French", "Odzala-Kokoua National Park", "Green, Yellow, and Red", "Blue", "Brazzaville and Kinshasa face each other directly across the Congo River!"),
        new Country("CD", "DR CONGO", "Kinshasa", "Lubumbashi", 2, 2, PAT_DIAG_BAND, 0xFF007FFF, 0xFFCE1021, 0xFFF7D618, EMB_STAR_TL, 0xFFF7D618,
                "Congolese Franc", "French", "Virunga Mountains & Congo River", "Sky Blue, Red, and Yellow", "Green", "The Congo River is the deepest river on Earth at over 720 feet deep!"),
        new Country("CI", "IVORY COAST", "Yamoussoukro", "Abidjan", 2, 2, PAT_V3, 0xFFF77F00, 0xFFFFFFFF, 0xFF009E60, EMB_NONE, 0,
                "West African CFA Franc", "French", "Basilica of Our Lady of Peace", "Orange, White, and Green", "Blue", "The world's largest producer of cocoa beans used to make chocolate!"),
        new Country("DJ", "DJIBOUTI", "Djibouti", "Ali Sabieh", 2, 2, PAT_LEFT_TRI, 0xFF6AB2E7, 0xFF12AD2B, 0xFFFFFFFF, EMB_STAR_LEFT, 0xFFD7141A,
                "Djiboutian Franc", "Arabic & French", "Lake Assal Crater Lake", "Light Blue, Green, White, and Red", "Yellow", "Lake Assal is the lowest point on land in Africa and one of the saltiest lakes!"),
        new Country("GQ", "EQUATORIAL GUINEA", "Malabo", "Bata", 2, 2, PAT_LEFT_TRI_H3, 0xFF3E9A00, 0xFFFFFFFF, 0xFF0073CE, EMB_COAT, 0xFF2E8B57,
                "Central African CFA Franc", "Spanish, French & Portuguese", "Pico Basile on Bioko Island", "Green, White, Red, and Blue", "Purple", "Its capital, Malabo, sits on Bioko Island while most land is on the mainland!"),
        new Country("ER", "ERITREA", "Asmara", "Massawa", 2, 2, PAT_LEFT_TRI, 0xFF4189DD, 0xFF12AD2B, 0xFFEA0437, EMB_SUN, 0xFFFCD116,
                "Eritrean Nakfa", "Tigrinya, Arabic & English", "Dahlak Marine Archipelago", "Green, Blue, Red, and Gold", "Black", "Has an extensive coastline along the Red Sea!"),
        new Country("SZ", "ESWATINI", "Mbabane & Lobamba", "Manzini", 2, 2, PAT_KENYA, 0xFF3E5EB9, 0xFFB10C0C, 0xFFFFD900, EMB_SHIELD, 0xFFFFFFFF,
                "Swazi Lilangeni", "Swazi & English", "Sibebe Rock Monolith", "Blue, Yellow, Crimson, Black, and White", "Green", "Sibebe Rock is the second-largest exposed granite dome in the world!"),
        new Country("GA", "GABON", "Libreville", "Port-Gentil", 2, 2, PAT_H3, 0xFF009E60, 0xFFFCD116, 0xFF3A75C4, EMB_NONE, 0,
                "Central African CFA Franc", "French", "Loango National Park", "Green, Yellow, and Blue", "Red", "Over 85% of Gabon is covered by dense rainforest!"),
        new Country("GM", "GAMBIA", "Banjul", "Serekunda", 2, 2, PAT_KENYA, 0xFFCE1126, 0xFF0C1C8C, 0xFF3A7728, EMB_NONE, 0,
                "Gambian Dalasi", "English", "Gambia River Valley", "Red, Blue, Green, and White", "Yellow", "The smallest country on mainland Africa, shaped around the Gambia River!"),
        new Country("GN", "GUINEA", "Conakry", "Nzerekore", 2, 2, PAT_V3, 0xFFCE1126, 0xFFFCD116, 0xFF009460, EMB_NONE, 0,
                "Guinean Franc", "French", "Fouta Djallon Highlands", "Red, Yellow, and Green", "Blue", "The Niger, Senegal, and Gambia rivers all begin in Guinea's highlands!"),
        new Country("GW", "GUINEA-BISSAU", "Bissau", "Bafata", 2, 2, PAT_MADAGASCAR, 0xFFCE1126, 0xFFFCD116, 0xFF009E49, EMB_STAR_LEFT, 0xFF000000,
                "West African CFA Franc", "Portuguese", "Bijagos Archipelago", "Red, Yellow, Green, and Black", "Blue", "The Bijagos Islands are a UNESCO Biosphere Reserve off its coast!"),
        new Country("LS", "LESOTHO", "Maseru", "Teyateyaneng", 2, 2, PAT_H3, 0xFF00209F, 0xFFFFFFFF, 0xFF009543, EMB_COAT, 0xFF000000,
                "Lesotho Loti", "Sesotho & English", "Maletsunyane Falls", "Blue, White, Green, and Black", "Red", "Nicknamed the Kingdom in the Sky—every part of it sits above 1,400 meters!"),
        new Country("LR", "LIBERIA", "Monrovia", "Gbarnga", 2, 2, PAT_USA, 0xFFBF0A30, 0xFFFFFFFF, 0xFF002868, EMB_NONE, 0,
                "Liberian Dollar", "English", "Sapo National Park", "Red, White, and Blue", "Green", "Its flag has 11 red-and-white stripes and a single white star in a blue square!"),
        new Country("LY", "LIBYA", "Tripoli", "Benghazi", 2, 2, PAT_H3, 0xFFE70013, 0xFF000000, 0xFF239E46, EMB_CRESCENT_STAR, 0xFFFFFFFF,
                "Libyan Dinar", "Arabic", "Leptis Magna Roman Ruins", "Red, Black, Green, and White", "Blue", "Home to Leptis Magna, one of the best-preserved Roman cities in the Mediterranean!"),
        new Country("MW", "MALAWI", "Lilongwe", "Blantyre", 2, 2, PAT_H3, 0xFF000000, 0xFFCE1126, 0xFF339E35, EMB_SUN_TOP, 0xFFCE1126,
                "Malawian Kwacha", "English & Chichewa", "Lake Malawi", "Black, Red, and Green", "Blue", "Lake Malawi has more species of fish than any other lake on Earth!"),
        new Country("ML", "MALI", "Bamako", "Timbuktu", 2, 2, PAT_V3, 0xFF14B53A, 0xFFFCD116, 0xFFCE1126, EMB_NONE, 0,
                "West African CFA Franc", "French & Bambara", "Ancient Libraries of Timbuktu", "Green, Gold, and Red", "Blue", "Timbuktu was a legendary medieval center of scholarship and astronomy!"),
        new Country("MR", "MAURITANIA", "Nouakchott", "Nouadhibou", 2, 2, PAT_BORDER_H, 0xFF006233, 0xFFD01C1F, 0, EMB_CRESCENT_STAR, 0xFFFFD700,
                "Mauritanian Ouguiya", "Arabic", "Richat Structure (Eye of the Sahara)", "Green, Gold, and Red", "Blue", "Home to the Eye of the Sahara, a giant circular geological formation visible from space!"),
        new Country("MU", "MAURITIUS", "Port Louis", "Curepipe", 2, 2, PAT_H4, 0xFFEA2839, 0xFF1A206D, 0xFFFFD500, EMB_NONE, 0,
                "Mauritian Rupee", "English & French", "Le Morne Brabant & Seven Colored Earths", "Red, Blue, Yellow, and Green", "Black", "Was the only known home of the now-extinct flightless Dodo bird!"),
        new Country("MZ", "MOZAMBIQUE", "Maputo", "Beira", 2, 2, PAT_LEFT_TRI_H3, 0xFF007168, 0xFF000000, 0xFFFCE100, EMB_STAR_LEFT, 0xFFD21034,
                "Mozambican Metical", "Portuguese", "Bazaruto Archipelago", "Green, Black, Yellow, White, and Red", "Blue", "Features an open book on its flag symbolizing the importance of education!"),
        new Country("NA", "NAMIBIA", "Windhoek", "Swakopmund", 2, 2, PAT_DIAG_BAND, 0xFF003580, 0xFFD21034, 0xFF009543, EMB_SUN_TL, 0xFFFFCE00,
                "Namibian Dollar", "English", "Sossusvlei Red Dunes & Namib Desert", "Blue, Red, Green, White, and Gold", "Purple", "The Namib is considered the oldest desert in the world!"),
        new Country("NE", "NIGER", "Niamey", "Zinder", 2, 2, PAT_H3, 0xFFE05206, 0xFFFFFFFF, 0xFF0DB02B, EMB_SUN, 0xFFE05206,
                "West African CFA Franc", "French", "Air Mountains & Tenere Desert", "Orange, White, and Green", "Blue", "An orange circle in the center represents the bright Sahara sun!"),
        new Country("RW", "RWANDA", "Kigali", "Butare", 2, 2, PAT_COLOMBIA, 0xFF00A1DE, 0xFFFAD201, 0xFF20603D, EMB_SUN_TR, 0xFFE5BE01,
                "Rwandan Franc", "Kinyarwanda, French & English", "Volcanoes National Park", "Sky Blue, Yellow, and Green", "Red", "Nicknamed the Land of a Thousand Hills and home to mountain gorillas!"),
        new Country("ST", "SAO TOME AND PRINCIPE", "Sao Tome", "Santo Antonio", 2, 2, PAT_LEFT_TRI_H3, 0xFF12AD2B, 0xFFFFCE00, 0xFFD21034, EMB_TWO_STARS, 0xFF000000,
                "Sao Tome Dobra", "Portuguese", "Pico Cao Grande Volcanic Spire", "Green, Yellow, Red, and Black", "Blue", "Lies right near the Equator in the Gulf of Guinea!"),
        new Country("SC", "SEYCHELLES", "Victoria", "Anse Boileau", 2, 2, PAT_SEYCHELLES, 0xFF003F87, 0xFFFCD856, 0xFFD62828, EMB_NONE, 0,
                "Seychellois Rupee", "Seychellois Creole, English & French", "Aldabra Giant Tortoise Atoll", "Blue, Yellow, Red, White, and Green", "Black", "Victoria is the smallest capital city in Africa, on an island of 115 coral & granite islands!"),
        new Country("SL", "SIERRA LEONE", "Freetown", "Bo", 2, 2, PAT_H3, 0xFF1EB53A, 0xFFFFFFFF, 0xFF0072C6, EMB_NONE, 0,
                "Sierra Leonean Leone", "English", "Tacugama Sanctuary & Loma Mountains", "Green, White, and Blue", "Red", "Freetown has one of the largest natural harbors in the world!"),
        new Country("SO", "SOMALIA", "Mogadishu", "Hargeisa", 2, 2, PAT_SOLID, 0xFF4189DD, 0xFF4189DD, 0, EMB_STAR, 0xFFFFFFFF,
                "Somali Shilling", "Somali & Arabic", "Laas Geel Cave Paintings", "Light Blue and White", "Red", "Has the longest coastline on mainland Africa!"),
        new Country("SS", "SOUTH SUDAN", "Juba", "Wau", 2, 2, PAT_LEFT_TRI_H3, 0xFF000000, 0xFFDA121A, 0xFF078930, EMB_STAR_LEFT, 0xFFFCDD09,
                "South Sudanese Pound", "English", "Sudd Wetlands on the White Nile", "Black, Red, Green, Blue, Yellow, and White", "Purple", "Became the world's newest internationally recognized country in 2011!"),
        new Country("SD", "SUDAN", "Khartoum", "Omdurman", 2, 2, PAT_LEFT_TRI_H3, 0xFFD21034, 0xFFFFFFFF, 0xFF000000, EMB_NONE, 0,
                "Sudanese Pound", "Arabic & English", "Nubian Pyramids of Meroe", "Red, White, Black, and Green", "Blue", "Sudan actually has more ancient pyramids than Egypt—over 200 at Meroe!"),
        new Country("TG", "TOGO", "Lome", "Sokode", 2, 2, PAT_TOGO, 0xFF006A4E, 0xFFFFCE00, 0xFFD21034, EMB_STAR_TL, 0xFFFFFFFF,
                "West African CFA Franc", "French", "Koutammakou Valley", "Green, Yellow, Red, and White", "Blue", "Its flag's dimensions are designed in the exact mathematical Golden Ratio!"),
        new Country("UG", "UGANDA", "Kampala", "Entebbe", 2, 2, PAT_UGANDA, 0xFF000000, 0xFFFCDC04, 0xFFD90000, EMB_RING, 0xFFFFFFFF,
                "Ugandan Shilling", "English & Swahili", "Source of the Nile & Bwindi", "Black, Yellow, Red, and White", "Blue", "Features the grey crowned crane in a white disk at the center of its flag!"),
        new Country("ZM", "ZAMBIA", "Lusaka", "Livingstone", 2, 2, PAT_ZAMBIA, 0xFF198A00, 0xFFDE2010, 0xFFEF7D00, EMB_EAGLE, 0xFFEF7D00,
                "Zambian Kwacha", "English", "Victoria Falls (Mosi-oa-Tunya)", "Green, Red, Black, and Orange", "Blue", "Shares the thundering Victoria Falls with neighboring Zimbabwe!"),
        new Country("ZW", "ZIMBABWE", "Harare", "Bulawayo", 2, 2, PAT_LEFT_TRI_H3, 0xFF006400, 0xFFFFD200, 0xFFD40000, EMB_STAR_LEFT, 0xFFD40000,
                "Zimbabwe Gold", "English, Shona & Ndebele", "Victoria Falls & Great Zimbabwe", "Green, Gold, Red, Black, and White", "Blue", "Home to the ancient stone city of Great Zimbabwe!"),
        new Country("IN", "INDIA", "New Delhi", "Mumbai", 3, 1, PAT_H3, 0xFFFF671F, 0xFFFFFFFF, 0xFF046A38, EMB_CHAKRA, 0xFF06038D,
                "Indian Rupee", "Hindi & English", "Taj Mahal & Himalayas", "Saffron, White, Green, and Navy Blue", "Purple", "The blue Ashoka Chakra wheel in the center has 24 spokes!"),
        new Country("JP", "JAPAN", "Tokyo", "Osaka", 3, 1, PAT_CENTER_DISK, 0xFFFFFFFF, 0xFFBC002D, 0, EMB_NONE, 0,
                "Japanese Yen", "Japanese", "Mount Fuji & Kyoto Temples", "White and Crimson Red", "Blue", "An island nation of over 6,800 islands called the Land of the Rising Sun!"),
        new Country("CN", "CHINA", "Beijing", "Shanghai", 3, 1, PAT_SOLID, 0xFFDE2910, 0xFFDE2910, 0, EMB_CHINA, 0xFFFFDE00,
                "Chinese Yuan (Renminbi)", "Mandarin Chinese", "Great Wall of China & Forbidden City", "Red and Gold", "Blue", "The Great Wall of China stretches over 13,000 miles across mountains and deserts!"),
        new Country("KR", "SOUTH KOREA", "Seoul", "Busan", 3, 1, PAT_KOREA, 0xFFFFFFFF, 0xFFCD2E3A, 0xFF0047A0, EMB_NONE, 0,
                "South Korean Won", "Korean", "Gyeongbokgung Palace & Jeju Island", "White, Red, Blue, and Black", "Green", "The red-and-blue Taegeuk circle represents harmony, surrounded by four black trigrams!"),
        new Country("KP", "NORTH KOREA", "Pyongyang", "Kaesong", 3, 1, PAT_NORTH_KOREA, 0xFF024FA2, 0xFFED1C27, 0xFFFFFFFF, EMB_STAR_LEFT, 0xFFED1C27,
                "North Korean Won", "Korean", "Mount Paektu Crater Lake", "Blue, White, and Red", "Green", "Occupies the northern half of the Korean Peninsula!"),
        new Country("SA", "SAUDI ARABIA", "Riyadh", "Jeddah", 3, 1, PAT_SOLID, 0xFF006C35, 0xFF006C35, 0, EMB_SAUDI, 0xFFFFFFFF,
                "Saudi Riyal", "Arabic", "Al-Ula Hegra & Rub' al Khali", "Green and White", "Red", "Home to the Rub' al Khali, the largest continuous sand desert in the world!"),
        new Country("AE", "UNITED ARAB EMIRATES", "Abu Dhabi", "Dubai", 3, 1, PAT_UAE, 0xFF00732F, 0xFFFFFFFF, 0xFF000000, EMB_NONE, 0,
                "UAE Dirham", "Arabic", "Burj Khalifa & Sheikh Zayed Mosque", "Red, Green, White, and Black", "Blue", "Abu Dhabi is the capital, while Dubai has the world's tallest tower, Burj Khalifa!"),
        new Country("TR", "TURKEY", "Ankara", "Istanbul", 3, 1, PAT_SOLID, 0xFFE30A17, 0xFFE30A17, 0, EMB_CRESCENT_STAR, 0xFFFFFFFF,
                "Turkish Lira", "Turkish", "Hagia Sophia & Cappadocia", "Red and White", "Blue", "Ankara is the capital, while Istanbul famously spans both Europe and Asia!"),
        new Country("TH", "THAILAND", "Bangkok", "Chiang Mai", 3, 1, PAT_THAI, 0xFFA51931, 0xFFFFFFFF, 0xFF2D2A4A, EMB_NONE, 0,
                "Thai Baht", "Thai", "Grand Palace & Wat Arun", "Red, White, and Navy Blue", "Green", "Known worldwide as the Land of Smiles!"),
        new Country("VN", "VIETNAM", "Hanoi", "Ho Chi Minh City", 3, 1, PAT_SOLID, 0xFFDA251D, 0xFFDA251D, 0, EMB_STAR, 0xFFFFFE00,
                "Vietnamese Dong", "Vietnamese", "Ha Long Bay Limestone Islands", "Red and Gold", "Blue", "Ha Long Bay has nearly 2,000 towering limestone islands in emerald waters!"),
        new Country("ID", "INDONESIA", "Jakarta", "Bali", 3, 1, PAT_H2, 0xFFFF0000, 0xFFFFFFFF, 0, EMB_NONE, 0,
                "Indonesian Rupiah", "Indonesian", "Borobudur Temple & Komodo Island", "Red and White", "Blue", "The world's largest archipelago nation with over 17,000 islands!"),
        new Country("SG", "SINGAPORE", "Singapore", "Jurong", 3, 1, PAT_H2, 0xFFEF3340, 0xFFFFFFFF, 0, EMB_CRESCENT_TL, 0xFFFFFFFF,
                "Singapore Dollar", "English, Malay, Mandarin & Tamil", "Marina Bay Sands & Gardens by the Bay", "Red and White", "Blue", "An island city-state whose name comes from Sanskrit Singapura—Lion City!"),
        new Country("MY", "MALAYSIA", "Kuala Lumpur", "Penang", 3, 1, PAT_USA, 0xFFCC0001, 0xFFFFFFFF, 0xFF010066, EMB_CRESCENT_TL, 0xFFFFCC00,
                "Malaysian Ringgit", "Malay", "Petronas Twin Towers & Mount Kinabalu", "Red, White, Blue, and Yellow", "Green", "The Petronas Twin Towers in Kuala Lumpur are the tallest twin towers on Earth!"),
        new Country("PH", "PHILIPPINES", "Manila", "Cebu City", 3, 1, PAT_LEFT_TRI, 0xFF0038A8, 0xFFCE1126, 0xFFFFFFFF, EMB_SUN_LEFT, 0xFFFCD116,
                "Philippine Peso", "Filipino & English", "Banaue Rice Terraces & Palawan", "Blue, Red, White, and Gold", "Green", "Made up of 7,641 tropical islands in the western Pacific Ocean!"),
        new Country("PK", "PAKISTAN", "Islamabad", "Karachi", 3, 1, PAT_PAKISTAN, 0xFF01411C, 0xFFFFFFFF, 0, EMB_CRESCENT_STAR, 0xFFFFFFFF,
                "Pakistani Rupee", "Urdu & English", "K2 Peak & Badshahi Mosque", "Dark Green and White", "Red", "Home to K2, the second-highest mountain on Earth at 8,611 meters!"),
        new Country("BD", "BANGLADESH", "Dhaka", "Chittagong", 3, 2, PAT_CENTER_DISK, 0xFF006A4E, 0xFFF42A41, 0, EMB_NONE, 0,
                "Bangladeshi Taka", "Bengali", "Sundarbans Mangrove Forest", "Bottle Green and Red", "Yellow", "Home to the Sundarbans, the largest mangrove forest in the world!"),
        new Country("NP", "NEPAL", "Kathmandu", "Pokhara", 3, 2, PAT_NEPAL, 0xFFDC143C, 0xFF003893, 0xFFFFFFFF, EMB_NONE, 0,
                "Nepalese Rupee", "Nepali", "Mount Everest & Pashupatinath", "Crimson Red, Blue, and White", "Green", "The only country in the world whose flag is not rectangular—it has two triangles!"),
        new Country("LK", "SRI LANKA", "Sri Jayawardenepura Kotte & Colombo", "Kandy", 3, 2, PAT_SRI_LANKA, 0xFF8D153A, 0xFFFFBE29, 0xFF00534E, EMB_LION, 0xFFFFBE29,
                "Sri Lankan Rupee", "Sinhala & Tamil", "Sigiriya Lion Rock Fortress", "Maroon, Gold, Orange, and Green", "Blue", "Nicknamed the Pearl of the Indian Ocean and famous for Ceylon tea!"),
        new Country("BT", "BHUTAN", "Thimphu", "Paro", 3, 2, PAT_DIAG_SPLIT, 0xFFFFCC00, 0xFFFF4E12, 0, EMB_DRAGON, 0xFFFFFFFF,
                "Bhutanese Ngultrum", "Dzongkha", "Paro Taktsang (Tiger's Nest Monastery)", "Yellow, Orange, and White", "Blue", "Thimphu is one of the only capital cities in the world with no traffic lights!"),
        new Country("MV", "MALDIVES", "Male", "Addu City", 3, 2, PAT_BORDER, 0xFF007E3A, 0xFFD21034, 0, EMB_CRESCENT_WHITE, 0xFFFFFFFF,
                "Maldivian Rufiyaa", "Dhivehi", "26 Coral Atolls in the Indian Ocean", "Red, Green, and White", "Blue", "The lowest-lying country on Earth, made of 1,192 coral islands!"),
        new Country("AF", "AFGHANISTAN", "Kabul", "Kandahar", 3, 2, PAT_V3, 0xFF000000, 0xFFD32011, 0xFF007A36, EMB_RING, 0xFFFFFFFF,
                "Afghan Afghani", "Pashto & Dari", "Band-e Amir Blue Lakes", "Black, Red, Green, and White", "Blue", "Home to the Hindu Kush mountain range and Band-e Amir lakes!"),
        new Country("IR", "IRAN", "Tehran", "Isfahan", 3, 2, PAT_H3, 0xFF239F40, 0xFFFFFFFF, 0xFFDA0000, EMB_COAT, 0xFFDA0000,
                "Iranian Rial", "Persian (Farsi)", "Persepolis & Naqsh-e Jahan Square", "Green, White, and Red", "Blue", "Home to ancient Persepolis and Mount Damavand, Asia's highest volcano!"),
        new Country("IQ", "IRAQ", "Baghdad", "Basra", 3, 2, PAT_H3, 0xFFCE1126, 0xFFFFFFFF, 0xFF000000, EMB_TEXT_GREEN, 0xFF007A3D,
                "Iraqi Dinar", "Arabic & Kurdish", "Tigris & Euphrates Rivers (Mesopotamia)", "Red, White, Black, and Green", "Blue", "Ancient Mesopotamia between the Tigris and Euphrates is called the Cradle of Civilization!"),
        new Country("JO", "JORDAN", "Amman", "Aqaba", 3, 2, PAT_LEFT_TRI_H3, 0xFF000000, 0xFFFFFFFF, 0xFF007A3D, EMB_STAR_LEFT, 0xFFCE1126,
                "Jordanian Dinar", "Arabic", "Ancient Rose-Red City of Petra", "Black, White, Green, and Red", "Blue", "Petra's Treasury was carved directly into rose-red sandstone cliffs over 2,000 years ago!"),
        new Country("LB", "LEBANON", "Beirut", "Tripoli", 3, 2, PAT_LATVIA, 0xFFED1C24, 0xFFFFFFFF, 0xFFED1C24, EMB_CEDAR, 0xFF00A651,
                "Lebanese Pound", "Arabic", "Baalbek Temples & Cedar Forests", "Red, White, and Green", "Blue", "Features the green Lebanon Cedar tree in the center of its flag!"),
        new Country("IL", "ISRAEL", "Jerusalem", "Tel Aviv", 3, 2, PAT_ISRAEL, 0xFFFFFFFF, 0xFF0038B8, 0, EMB_HEXAGRAM, 0xFF0038B8,
                "Israeli New Shekel", "Hebrew & Arabic", "Old City of Jerusalem & Dead Sea", "White and Blue", "Red", "Borders the Dead Sea, the lowest point on dry land on Earth at 430 meters below sea level!"),
        new Country("SY", "SYRIA", "Damascus", "Aleppo", 3, 2, PAT_H3, 0xFFCE1126, 0xFFFFFFFF, 0xFF000000, EMB_TWO_STARS, 0xFF007A3D,
                "Syrian Pound", "Arabic", "Ancient City of Damascus & Palmyra", "Red, White, Black, and Green", "Blue", "Damascus is one of the oldest continuously inhabited cities in the world!"),
        new Country("KW", "KUWAIT", "Kuwait City", "Al Ahmadi", 3, 2, PAT_LEFT_TRI_H3, 0xFF007A3D, 0xFFFFFFFF, 0xFFCE1126, EMB_NONE, 0,
                "Kuwaiti Dinar", "Arabic", "Kuwait Towers", "Green, White, Red, and Black", "Blue", "The Kuwaiti Dinar is the highest-valued currency unit in the world!"),
        new Country("QA", "QATAR", "Doha", "Al Wakrah", 3, 2, PAT_QATAR, 0xFF8A1538, 0xFFFFFFFF, 0, EMB_NONE, 0,
                "Qatari Riyal", "Arabic", "Museum of Islamic Art in Doha", "Maroon and White", "Blue", "Its flag is the only national flag whose width is more than twice its height!"),
        new Country("BH", "BAHRAIN", "Manama", "Riffa", 3, 2, PAT_QATAR, 0xFFCE1126, 0xFFFFFFFF, 0, EMB_NONE, 0,
                "Bahraini Dinar", "Arabic", "Qal'at al-Bahrain Fort", "Red and White", "Blue", "Five white triangles form a serrated line representing the Five Pillars of Islam!"),
        new Country("OM", "OMAN", "Muscat", "Salalah", 3, 2, PAT_UAE, 0xFFFFFFFF, 0xFFDB161B, 0xFF008000, EMB_NONE, 0,
                "Omani Rial", "Arabic", "Sultan Qaboos Mosque & Jebel Shams", "Red, White, and Green", "Blue", "Muscat is framed by dramatic mountains and the Arabian Sea!"),
        new Country("YE", "YEMEN", "Sana'a", "Aden", 3, 2, PAT_H3, 0xFFCE1126, 0xFFFFFFFF, 0xFF000000, EMB_NONE, 0,
                "Yemeni Rial", "Arabic", "Socotra Island Dragon Blood Trees", "Red, White, and Black", "Green", "Socotra Island is famous for its umbrella-shaped Dragon's Blood trees!"),
        new Country("KZ", "KAZAKHSTAN", "Astana", "Almaty", 3, 2, PAT_SOLID, 0xFF00AFCA, 0xFF00AFCA, 0, EMB_SUN, 0xFFFEC50C,
                "Kazakhstani Tenge", "Kazakh & Russian", "Charyn Canyon & Bayterek Tower", "Sky Blue and Gold", "Red", "The largest landlocked country in the world!"),
        new Country("UZ", "UZBEKISTAN", "Tashkent", "Samarkand", 3, 2, PAT_H3, 0xFF0099B5, 0xFFFFFFFF, 0xFF1EB53A, EMB_CRESCENT_TL, 0xFFFFFFFF,
                "Uzbekistani Som", "Uzbek", "Registan Square in Samarkand", "Turquoise Blue, White, Green, and Red", "Black", "Samarkand and Bukhara were jewel cities along the ancient Silk Road!"),
        new Country("TM", "TURKMENISTAN", "Ashgabat", "Turkmenabat", 3, 2, PAT_SOLID, 0xFF00843D, 0xFF00843D, 0, EMB_CRESCENT_TL, 0xFFFFFFFF,
                "Turkmen New Manat", "Turkmen", "Darvaza Gas Crater & Karakum Desert", "Green, Red, and White", "Blue", "Ashgabat holds the world record for the highest concentration of white marble buildings!"),
        new Country("KG", "KYRGYZSTAN", "Bishkek", "Osh", 3, 2, PAT_SOLID, 0xFFE8112D, 0xFFE8112D, 0, EMB_SUN, 0xFFFFEF00,
                "Kyrgyzstani Som", "Kyrgyz & Russian", "Issyk-Kul Alpine Lake & Tian Shan", "Red and Gold", "Blue", "Lake Issyk-Kul is the second-largest high-altitude alpine lake in the world!"),
        new Country("TJ", "TAJIKISTAN", "Dushanbe", "Khujand", 3, 2, PAT_H3, 0xFFCC0000, 0xFFFFFFFF, 0xFF006600, EMB_CROWN_CENTER, 0xFFF8C300,
                "Tajikistani Somoni", "Tajik", "Pamir Mountains (Roof of the World)", "Red, White, Green, and Gold", "Blue", "Over 90% of Tajikistan is covered by the towering Pamir and Alay mountains!"),
        new Country("MN", "MONGOLIA", "Ulaanbaatar", "Erdenet", 3, 2, PAT_V3, 0xFFC4272F, 0xFF015197, 0xFFC4272F, EMB_SOYOMBO_LEFT, 0xFFF9CF02,
                "Mongolian Tugrik", "Mongolian", "Gobi Desert & Orkhon Valley", "Red, Blue, and Gold", "Green", "The least densely populated sovereign country in the world!"),
        new Country("MM", "MYANMAR", "Naypyidaw", "Yangon", 3, 2, PAT_H3, 0xFFFECB00, 0xFF34B233, 0xFFEA2839, EMB_STAR, 0xFFFFFFFF,
                "Myanmar Kyat", "Burmese", "Bagan Ancient Temples & Shwedagon", "Yellow, Green, Red, and White", "Blue", "The plains of Bagan are dotted with over 2,000 ancient brick temples and pagodas!"),
        new Country("KH", "CAMBODIA", "Phnom Penh", "Siem Reap", 3, 2, PAT_LATVIA, 0xFF032EA1, 0xFFE00025, 0xFF032EA1, EMB_TEMPLE, 0xFFFFFFFF,
                "Cambodian Riel", "Khmer", "Angkor Wat Temple Complex", "Blue, Red, and White", "Green", "Angkor Wat is the largest religious monument ever built and appears right on its flag!"),
        new Country("LA", "LAOS", "Vientiane", "Luang Prabang", 3, 2, PAT_LATVIA, 0xFFCE1126, 0xFF002868, 0xFFCE1126, EMB_SUN, 0xFFFFFFFF,
                "Lao Kip", "Lao", "Kuang Si Falls & Luang Prabang", "Red, Blue, and White", "Green", "The white disk on its flag symbolizes a full moon shining over the Mekong River!"),
        new Country("BN", "BRUNEI", "Bandar Seri Begawan", "Kuala Belait", 3, 2, PAT_DIAG_BAND, 0xFFF7E017, 0xFF000000, 0xFFFFFFFF, EMB_COAT, 0xFFCF1126,
                "Brunei Dollar", "Malay", "Omar Ali Saifuddien Mosque", "Yellow, White, Black, and Red", "Blue", "Located on the northern coast of the island of Borneo!"),
        new Country("TL", "TIMOR-LESTE", "Dili", "Baucau", 3, 2, PAT_LEFT_TRI, 0xFFDA291C, 0xFFDA291C, 0xFF000000, EMB_STAR_LEFT, 0xFFFFFFFF,
                "US Dollar", "Tetum & Portuguese", "Mount Ramelau & Atauro Reef", "Red, Yellow, Black, and White", "Blue", "Atauro Island off Dili has some of the most biodiverse coral reefs on Earth!"),
        new Country("GE", "GEORGIA", "Tbilisi", "Batumi", 3, 2, PAT_SWISS, 0xFFFFFFFF, 0xFFFF0000, 0, EMB_CROSS_TL, 0xFFFF0000,
                "Georgian Lari", "Georgian", "Caucasus Mountains & Ushguli", "White and Red", "Blue", "Its Five-Cross Flag features the large cross of St. George and four smaller crosses!"),
        new Country("AM", "ARMENIA", "Yerevan", "Gyumri", 3, 2, PAT_H3, 0xFFD90012, 0xFF0033A0, 0xFFF2A800, EMB_NONE, 0,
                "Armenian Dram", "Armenian", "Geghard Monastery & Lake Sevan", "Red, Blue, and Apricot Orange", "Green", "Yerevan is called the Pink City because its buildings are made of pink volcanic stone!"),
        new Country("AZ", "AZERBAIJAN", "Baku", "Ganja", 3, 2, PAT_H3, 0xFF0092BC, 0xFFE4002B, 0xFF00AF66, EMB_CRESCENT_STAR, 0xFFFFFFFF,
                "Azerbaijani Manat", "Azerbaijani", "Flame Towers & Old City of Baku", "Blue, Red, Green, and White", "Yellow", "Known as the Land of Fire for its natural eternal hillside flames!"),
        new Country("TW", "TAIWAN", "Taipei", "Kaohsiung", 3, 2, PAT_CHILE, 0xFFFE0000, 0xFFFE0000, 0xFF000095, EMB_SUN_TL, 0xFFFFFFFF,
                "New Taiwan Dollar", "Mandarin Chinese", "Taipei 101 & Taroko Gorge", "Red, Blue, and White", "Green", "Taipei 101 has a giant 660-ton golden pendulum inside to steady it against typhoons!"),
        new Country("HK", "HONG KONG", "Hong Kong", "Kowloon", 3, 2, PAT_SOLID, 0xFFDE2910, 0xFFDE2910, 0, EMB_FLOWER, 0xFFFFFFFF,
                "Hong Kong Dollar", "Cantonese & English", "Victoria Harbour & Victoria Peak", "Red and White", "Blue", "Features a white five-petaled Bauhinia orchid flower in the center of its flag!"),
        new Country("AU", "AUSTRALIA", "Canberra", "Sydney", 4, 1, PAT_AUSTRALIA, 0xFF012169, 0xFFFFFFFF, 0xFFE4002B, EMB_NONE, 0,
                "Australian Dollar", "English", "Sydney Opera House, Uluru & Great Barrier Reef", "Blue, White, and Red", "Green", "Canberra is the capital—built as a planned city halfway between Sydney and Melbourne!"),
        new Country("NZ", "NEW ZEALAND", "Wellington", "Auckland", 4, 1, PAT_AUSTRALIA, 0xFF00247D, 0xFFFFFFFF, 0xFFCC142B, EMB_FOUR_STARS, 0xFFCC142B,
                "New Zealand Dollar", "English & Maori", "Milford Sound & Mount Cook (Aoraki)", "Blue, White, and Red", "Yellow", "Wellington is the southernmost capital city of any sovereign nation on Earth!"),
        new Country("FJ", "FIJI", "Suva", "Nadi", 4, 1, PAT_AUSTRALIA, 0xFF68BFE5, 0xFFFFFFFF, 0xFFCE1126, EMB_COAT_RIGHT, 0xFFD4AF37,
                "Fijian Dollar", "English, Fijian & Hindi", "Mamanuca Coral Islands", "Sky Blue, Red, and White", "Black", "Made up of 333 tropical islands in the South Pacific Ocean!"),
        new Country("PG", "PAPUA NEW GUINEA", "Port Moresby", "Lae", 4, 1, PAT_DIAG_SPLIT, 0xFFCE1126, 0xFF000000, 0, EMB_BIRD_STARS, 0xFFFCD116,
                "Papua New Guinean Kina", "English, Tok Pisin & Hiri Motu", "Kokoda Track & Coral Triangle", "Red, Black, Gold, and White", "Blue", "Over 800 different indigenous languages are spoken across Papua New Guinea!"),
        new Country("WS", "SAMOA", "Apia", "Salelologa", 4, 2, PAT_CHILE, 0xFFCE1126, 0xFFCE1126, 0xFF002B7F, EMB_FIVE_STARS_TL, 0xFFFFFFFF,
                "Samoan Tala", "Samoan & English", "To Sua Ocean Trench", "Red, Blue, and White", "Green", "Shows the five white stars of the Southern Cross constellation in its blue canton!"),
        new Country("TO", "TONGA", "Nuku'alofa", "Neiafu", 4, 2, PAT_CHILE, 0xFFC10000, 0xFFC10000, 0xFFFFFFFF, EMB_CROSS_TL, 0xFFC10000,
                "Tongan Pa'anga", "Tongan & English", "Ha'amonga 'a Maui Stone Trilithon", "Red and White", "Blue", "Nicknamed the Friendly Islands in the South Pacific!"),
        new Country("VU", "VANUATU", "Port Vila", "Luganville", 4, 2, PAT_SOUTH_AFRICA, 0xFF009543, 0xFFD21034, 0xFF000000, EMB_SUN_LEFT, 0xFFFDCE12,
                "Vanuatu Vatu", "Bislama, English & French", "Mount Yasur Active Volcano", "Red, Green, Black, and Gold", "Blue", "Mount Yasur on Tanna Island is one of the world's most accessible active volcanoes!"),
        new Country("SB", "SOLOMON ISLANDS", "Honiara", "Gizo", 4, 2, PAT_DIAG_BAND, 0xFF0051BA, 0xFFFCD116, 0xFF215B33, EMB_FIVE_STARS_TL, 0xFFFFFFFF,
                "Solomon Islands Dollar", "English", "Marovo Saltwater Lagoon", "Blue, Green, Yellow, and White", "Red", "Home to Marovo Lagoon, the largest double-barrier saltwater lagoon in the world!"),
        new Country("KI", "KIRIBATI", "Tarawa", "Betio", 4, 2, PAT_H2, 0xFFCE1126, 0xFF003F87, 0, EMB_SUN, 0xFFFCD116,
                "Australian Dollar", "English & Gilbertese", "33 Coral Atolls Across the Equator", "Red, Blue, White, and Gold", "Green", "The only country in the world located in all four hemispheres—North, South, East, and West!"),
        new Country("FM", "MICRONESIA", "Palikir", "Weno", 4, 2, PAT_SOLID, 0xFF75B2DD, 0xFF75B2DD, 0, EMB_FOUR_STARS, 0xFFFFFFFF,
                "US Dollar", "English", "Nan Madol Ancient Basalt City", "Light Blue and White", "Red", "Four white stars represent its four island states: Chuuk, Kosrae, Pohnpei, and Yap!"),
        new Country("MH", "MARSHALL ISLANDS", "Majuro", "Ebeye", 4, 2, PAT_DIAG_BAND, 0xFF003893, 0xFFDD7500, 0xFFFFFFFF, EMB_SUN_TL, 0xFFFFFFFF,
                "US Dollar", "Marshallese & English", "Bikini & Majuro Coral Atolls", "Blue, Orange, and White", "Red", "Traditional Marshallese navigators mapped ocean swells using woven stick charts!"),
        new Country("PW", "PALAU", "Ngerulmud", "Koror", 4, 2, PAT_CENTER_DISK, 0xFF0099FF, 0xFFFFEE00, 0, EMB_NONE, 0,
                "US Dollar", "Palauan & English", "Rock Islands Southern Lagoon", "Sky Blue and Golden Yellow", "Red", "Unlike Japan's sun flag, Palau's golden circle represents the full moon over the ocean!"),
        new Country("NR", "NAURU", "Yaren", "Denigomodu", 4, 2, PAT_BOTSWANA, 0xFF002B7F, 0xFFFFC61E, 0xFFFFC61E, EMB_STAR_LEFT, 0xFFFFFFFF,
                "Australian Dollar", "Nauruan & English", "Anibare Bay Coral Cliffs", "Blue, Gold, and White", "Red", "The smallest island nation on Earth at just 21 square kilometers!"),
        new Country("TV", "TUVALU", "Funafuti", "Savave", 4, 2, PAT_AUSTRALIA, 0xFF009FCA, 0xFFFFFFFF, 0xFFCE1126, EMB_ARC_STARS, 0xFFFFCE00,
                "Tuvaluan Dollar & Australian Dollar", "Tuvaluan & English", "Funafuti Conservation Atoll", "Sky Blue, Gold, Red, and White", "Green", "Nine golden stars on its flag arranged like a map of its nine coral islands!"),
        new Country("CK", "COOK ISLANDS", "Avarua", "Arorangi", 4, 2, PAT_AUSTRALIA, 0xFF00247D, 0xFFFFFFFF, 0xFFCF142B, EMB_RING_STARS, 0xFFFFFFFF,
                "New Zealand Dollar", "English & Cook Islands Maori", "Aitutaki Turquoise Lagoon", "Blue, White, and Red", "Yellow", "Features a circle of 15 white stars representing its 15 islands!"),
        new Country("NU", "NIUE", "Alofi", "Tuapa", 4, 2, PAT_AUSTRALIA, 0xFFFEDD00, 0xFF012169, 0xFFC8102E, EMB_STAR_TL, 0xFFFEDD00,
                "New Zealand Dollar", "Niuean & English", "Talava Sea Arches & Coral Chasms", "Golden Yellow, Blue, Red, and White", "Green", "Nicknamed the Rock of Polynesia—one of the largest raised coral atolls on Earth!"),
        new Country("PF", "FRENCH POLYNESIA", "Papeete", "Bora Bora", 4, 2, PAT_LATVIA, 0xFFCE1126, 0xFFFFFFFF, 0xFFCE1126, EMB_RING, 0xFFFCD116,
                "CFP Franc", "French & Tahitian", "Mount Otemanu in Bora Bora & Tahiti", "Red, White, Blue, and Gold", "Green", "Features an outrigger canoe sailing under a golden sun in the center of its flag!"),
        new Country("GU", "GUAM", "Hagatna", "Dededo", 4, 2, PAT_BORDER, 0xFF002868, 0xFFBF0A30, 0, EMB_RING, 0xFFFCD116,
                "US Dollar", "English & Chamorro", "Two Lovers Point & Tumon Bay", "Dark Blue, Red, and Gold", "Purple", "The largest and southernmost of the Mariana Islands in the western Pacific!")
    };

    // World Wonders Art Types
    static final int ART_TAJ = 0;
    static final int ART_EIFFEL = 1;
    static final int ART_PYRAMID = 2;
    static final int ART_LIBERTY = 3;
    static final int ART_BIGBEN = 4;
    static final int ART_WALL = 5;
    static final int ART_COLOSSEUM = 6;
    static final int ART_FUJI = 7;
    static final int ART_REDEEMER = 8;
    static final int ART_MACHU = 9;
    static final int ART_CHICHEN = 10;
    static final int ART_ACROPOLIS = 11;

    static final Wonder[] WONDERS = {
        new Wonder("Taj Mahal", "INDIA", CONT_ASIA,
                "Ivory-white marble dome mausoleum on the Yamuna River in Agra.",
                "Built by Emperor Shah Jahan with shimmering white Makrana marble!", ART_TAJ),
        new Wonder("Eiffel Tower", "FRANCE", CONT_EUROPE,
                "330-meter iron lattice tower on the Champ de Mars in Paris.",
                "Summer heat makes the iron expand so the tower grows 6 inches taller!", ART_EIFFEL),
        new Wonder("Great Pyramids of Giza", "EGYPT", CONT_AFRICA,
                "Ancient limestone royal tombs guarded by the Great Sphinx near Cairo.",
                "Built over 4,500 years ago from 2.3 million stone blocks!", ART_PYRAMID),
        new Wonder("Statue of Liberty", "UNITED STATES", CONT_AMERICAS,
                "Colossal green copper statue holding a torch in New York Harbor.",
                "Gifted to the United States by the people of France in 1886!", ART_LIBERTY),
        new Wonder("Big Ben & Elizabeth Tower", "UNITED KINGDOM", CONT_EUROPE,
                "Iconic four-faced gothic clock tower beside the River Thames in London.",
                "Each of Big Ben's four clock dials is 23 feet in diameter!", ART_BIGBEN),
        new Wonder("Great Wall of China", "CHINA", CONT_ASIA,
                "Ancient stone fortifications winding across northern mountain ridges.",
                "Stretches over 13,170 miles across mountains, steppes, and deserts!", ART_WALL),
        new Wonder("The Colosseum", "ITALY", CONT_EUROPE,
                "Giant oval travertine amphitheater in the center of Rome.",
                "Completed in 80 AD and could seat up to 50,000 spectators!", ART_COLOSSEUM),
        new Wonder("Mount Fuji", "JAPAN", CONT_ASIA,
                "Symmetrical snow-capped volcanic cone southwest of Tokyo.",
                "Japan's tallest mountain peak standing at 3,776 meters (12,389 ft)!", ART_FUJI),
        new Wonder("Christ the Redeemer", "BRAZIL", CONT_AMERICAS,
                "Art Deco statue with open arms atop Mount Corcovado in Rio.",
                "Its outstretched arms span 92 feet wide above Rio de Janeiro!", ART_REDEEMER),
        new Wonder("Machu Picchu", "PERU", CONT_AMERICAS,
                "15th-century Inca stone citadel perched high in the Andes Mountains.",
                "Cut from granite stones that fit so tightly a knife blade cannot pass between them!", ART_MACHU),
        new Wonder("Chichen Itza (El Castillo)", "MEXICO", CONT_AMERICAS,
                "Stepped Mayan pyramid temple in the Yucatan Peninsula.",
                "Has 91 steps on each of its 4 sides plus the top platform = 365 days of the year!", ART_CHICHEN),
        new Wonder("The Parthenon", "GREECE", CONT_EUROPE,
                "Marble Doric column temple crowning the Acropolis of Athens.",
                "Built in 438 BC and dedicated to Athena, goddess of wisdom!", ART_ACROPOLIS),
        new Wonder("Treasury of Petra", "JORDAN", CONT_ASIA,
                "Ancient Nabataean temple facade carved directly into rose-red cliffs.",
                "Nicknamed the Rose City for the coral-pink color of its carved rock walls!", ART_ACROPOLIS),
        new Wonder("Angkor Wat", "CAMBODIA", CONT_ASIA,
                "Vast lotus-tower temple complex surrounded by a wide moat in Siem Reap.",
                "The largest religious monument in the world, covering 402 acres!", ART_TAJ),
        new Wonder("Sydney Opera House", "AUSTRALIA", CONT_OCEANIA,
                "Performing arts center with gleaming white sail-shaped shells on Sydney Harbour.",
                "Its roof shells are covered with more than 1 million Swedish ceramic tiles!", ART_TAJ),
        new Wonder("Burj Khalifa", "UNITED ARAB EMIRATES", CONT_ASIA,
                "828-meter needle-shaped supertall skyscraper rising over Downtown Dubai.",
                "The tallest building on Earth with 163 floors and the fastest elevators!", ART_BIGBEN),
        new Wonder("Mount Kilimanjaro", "TANZANIA", CONT_AFRICA,
                "Snow-crowned dormant volcano rising above the East African savannah.",
                "The highest mountain in Africa and the tallest free-standing mountain on Earth!", ART_FUJI),
        new Wonder("Sagrada Familia", "SPAIN", CONT_EUROPE,
                "Soaring sculpted basilica designed by Antoni Gaudi in Barcelona.",
                "Construction began in 1882 with tree-like stone columns inside!", ART_BIGBEN),
        new Wonder("Neuschwanstein Castle", "GERMANY", CONT_EUROPE,
                "Fairytale white limestone palace perched on a rugged hill in Bavaria.",
                "Built in the 19th century high above the Alpine foothills of southern Germany!", ART_BIGBEN),
        new Wonder("Victoria Falls", "ZAMBIA", CONT_AFRICA,
                "Mile-wide curtain of thundering water on the Zambezi River.",
                "Known locally as Mosi-oa-Tunya—The Smoke That Thunders!", ART_FUJI),
        new Wonder("Moai of Easter Island", "CHILE", CONT_AMERICAS,
                "Nearly 1,000 giant carved volcanic stone heads on Rapa Nui in the Pacific.",
                "Carved by Polynesian Rapa Nui sculptors between 1250 and 1500 AD!", ART_PYRAMID),
        new Wonder("Gyeongbokgung Palace", "SOUTH KOREA", CONT_ASIA,
                "Grand 14th-century royal palace framed by Bukhansan Mountain in Seoul.",
                "The main royal palace of the Joseon dynasty, built in 1395!", ART_ACROPOLIS),
        new Wonder("Charles Bridge & Prague Castle", "CZECH REPUBLIC", CONT_EUROPE,
                "Medieval gothic stone bridge crossing the Vltava River below a vast castle.",
                "Founded in 1357 by King Charles IV and lined with 30 baroque statues!", ART_WALL),
        new Wonder("Matterhorn Peak", "SWITZERLAND", CONT_EUROPE,
                "Chiseled four-faced pyramid mountain peak straddling the Pennine Alps.",
                "Soars 4,478 meters high above the car-free village of Zermatt!", ART_FUJI)
    };

    // ==================== 7 WORLD ATLAS LEARNING DECKS ====================
    static final String[] ATLAS_TOPIC_TITLES = {
        "🏔️ Top 10 Mountains",
        "🌊 Top 10 Longest Rivers",
        "🗺️ Top 10 Largest Countries",
        "👨‍👩‍👧‍👦 Top 10 Most Populous",
        "🏛️ World Wonders",
        "🔬 Top 10 Smallest Countries",
        "🏜️ Extreme Earth Records"
    };

    static final AtlasEntry[][] ATLAS_DECKS = {
        // 0: Top 10 Highest Mountains
        {
            new AtlasEntry(1, "Mount Everest", "8,849 m (29,032 ft)", "NEPAL", "Asia · Himalayas (Nepal / China)", "The highest point on Earth above sea level; called Sagarmatha in Nepal!"),
            new AtlasEntry(2, "K2 (Godwin-Austen)", "8,611 m (28,251 ft)", "PAKISTAN", "Asia · Karakoram (Pakistan / China)", "The second-highest peak on Earth and tallest in the Karakoram range!"),
            new AtlasEntry(3, "Kangchenjunga", "8,586 m (28,169 ft)", "INDIA", "Asia · Himalayas (India / Nepal)", "Means The Five Treasures of High Snow; highest peak in India!"),
            new AtlasEntry(4, "Lhotse", "8,516 m (27,940 ft)", "NEPAL", "Asia · Himalayas (Nepal / China)", "Connected directly to Mount Everest via the South Col ridge!"),
            new AtlasEntry(5, "Makalu", "8,485 m (27,838 ft)", "NEPAL", "Asia · Himalayas (Nepal / China)", "Famous for its steep four-sided pyramid summit shape!"),
            new AtlasEntry(6, "Aconcagua", "6,961 m (22,838 ft)", "ARGENTINA", "Americas · Andes Mountains", "The highest mountain in both the Americas and the Southern Hemisphere!"),
            new AtlasEntry(7, "Denali (Mount McKinley)", "6,190 m (20,310 ft)", "UNITED STATES", "Americas · Alaska Range", "The highest mountain peak in North America!"),
            new AtlasEntry(8, "Mount Kilimanjaro", "5,895 m (19,341 ft)", "TANZANIA", "Africa · East African Rift", "The highest peak in Africa and tallest free-standing volcano on Earth!"),
            new AtlasEntry(9, "Mount Elbrus", "5,642 m (18,510 ft)", "RUSSIA", "Europe · Caucasus Mountains", "Twin-coned dormant volcano and the highest mountain in Europe!"),
            new AtlasEntry(10, "Matterhorn", "4,478 m (14,692 ft)", "SWITZERLAND", "Europe · Pennine Alps (Switzerland / Italy)", "One of the most iconic pyramid-shaped peaks in the European Alps!")
        },
        // 1: Top 10 Longest Rivers
        {
            new AtlasEntry(1, "Nile River", "6,650 km (4,132 mi)", "EGYPT", "Africa · Flows through 11 countries to the Mediterranean", "The longest river on Earth, flowing north from East Africa to Egypt!"),
            new AtlasEntry(2, "Amazon River", "6,400 km (3,977 mi)", "BRAZIL", "Americas · Peru, Colombia & Brazil", "Carries more freshwater than the next 7 largest rivers combined!"),
            new AtlasEntry(3, "Yangtze River", "6,300 km (3,915 mi)", "CHINA", "Asia · Tibetan Plateau to East China Sea", "The longest river in Asia and the longest to flow entirely within one country!"),
            new AtlasEntry(4, "Mississippi-Missouri", "6,275 km (3,902 mi)", "UNITED STATES", "Americas · North America to Gulf of Mexico", "Drains 31 U.S. states and two Canadian provinces!"),
            new AtlasEntry(5, "Yenisei River", "5,539 km (3,445 mi)", "RUSSIA", "Asia · Mongolia & Siberia to Arctic Ocean", "The largest river system flowing into the icy Arctic Ocean!"),
            new AtlasEntry(6, "Yellow River (Huang He)", "5,464 km (3,395 mi)", "CHINA", "Asia · Northern China", "Named for the golden-yellow silt it carries across northern China!"),
            new AtlasEntry(7, "Parana River", "4,880 km (3,032 mi)", "ARGENTINA", "Americas · Brazil, Paraguay & Argentina", "Second-longest river in South America, feeding the Rio de la Plata!"),
            new AtlasEntry(8, "Congo River", "4,700 km (2,920 mi)", "DR CONGO", "Africa · Central African Rainforest", "The deepest river on Earth (over 220 m deep) and only major river to cross the Equator twice!"),
            new AtlasEntry(9, "Mekong River", "4,350 km (2,703 mi)", "VIETNAM", "Asia · China, Myanmar, Laos, Thailand, Cambodia & Vietnam", "Sustains 60 million people across six Southeast Asian countries!"),
            new AtlasEntry(10, "Danube River", "2,850 km (1,770 mi)", "HUNGARY", "Europe · Flows through 10 countries (Vienna, Bratislava, Budapest, Belgrade)", "Flows through more countries (10) than any other river in the world!")
        },
        // 2: Top 10 Largest Countries by Area
        {
            new AtlasEntry(1, "Russia", "17,098,242 km²", "RUSSIA", "Europe & Asia · Capital: Moscow", "Covers 11% of the world's landmass across 11 time zones!"),
            new AtlasEntry(2, "Canada", "9,984,670 km²", "CANADA", "Americas · Capital: Ottawa", "Has the longest coastline of any country on Earth (243,042 km)!"),
            new AtlasEntry(3, "United States", "9,833,520 km²", "UNITED STATES", "Americas · Capital: Washington, D.C.", "Spans from the Atlantic to the Pacific plus Alaska and Hawaii!"),
            new AtlasEntry(4, "China", "9,596,960 km²", "CHINA", "Asia · Capital: Beijing", "Borders 14 different countries on land—tied with Russia for the most!"),
            new AtlasEntry(5, "Brazil", "8,515,767 km²", "BRAZIL", "Americas · Capital: Brasilia", "Covers nearly half of the entire South American continent!"),
            new AtlasEntry(6, "Australia", "7,692,024 km²", "AUSTRALIA", "Oceania · Capital: Canberra", "The only country that covers an entire continent of its own!"),
            new AtlasEntry(7, "India", "3,287,263 km²", "INDIA", "Asia · Capital: New Delhi", "Framed by the Himalayas in the north and the Indian Ocean in the south!"),
            new AtlasEntry(8, "Argentina", "2,780,400 km²", "ARGENTINA", "Americas · Capital: Buenos Aires", "Stretches from subtropical waterfalls in the north to sub-Antarctic Patagonia!"),
            new AtlasEntry(9, "Kazakhstan", "2,724,900 km²", "KAZAKHSTAN", "Asia · Capital: Astana", "The largest landlocked country in the world!"),
            new AtlasEntry(10, "Algeria", "2,381,741 km²", "ALGERIA", "Africa · Capital: Algiers", "The largest country in Africa, with 80% covered by the Sahara Desert!")
        },
        // 3: Top 10 Most Populous Countries
        {
            new AtlasEntry(1, "India", "1.44 Billion", "INDIA", "Asia · Capital: New Delhi", "The most populous country on Earth, with 22 official languages!"),
            new AtlasEntry(2, "China", "1.41 Billion", "CHINA", "Asia · Capital: Beijing", "Home to giant megacities like Shanghai, Beijing, Chongqing, and Guangzhou!"),
            new AtlasEntry(3, "United States", "335 Million", "UNITED STATES", "Americas · Capital: Washington, D.C.", "The most populous country in the Americas!"),
            new AtlasEntry(4, "Indonesia", "279 Million", "INDONESIA", "Asia · Capital: Jakarta", "The world's most populous island nation, spread across 17,000+ islands!"),
            new AtlasEntry(5, "Pakistan", "241 Million", "PAKISTAN", "Asia · Capital: Islamabad", "Grows along the fertile Indus River Valley and northern mountains!"),
            new AtlasEntry(6, "Nigeria", "224 Million", "NIGERIA", "Africa · Capital: Abuja", "The most populous country in Africa, with over 500 spoken languages!"),
            new AtlasEntry(7, "Brazil", "216 Million", "BRAZIL", "Americas · Capital: Brasilia", "The most populous nation in South America and the Southern Hemisphere!"),
            new AtlasEntry(8, "Bangladesh", "173 Million", "BANGLADESH", "Asia · Capital: Dhaka", "Formed by the fertile Ganges-Brahmaputra river delta!"),
            new AtlasEntry(9, "Russia", "144 Million", "RUSSIA", "Europe & Asia · Capital: Moscow", "Moscow is the largest city entirely within Europe!"),
            new AtlasEntry(10, "Ethiopia", "126 Million", "ETHIOPIA", "Africa · Capital: Addis Ababa", "The most populous landlocked country in the world!")
        },
        // 4: World Wonders
        {
            new AtlasEntry(1, "Great Pyramid of Giza", "2560 BC · Ancient Wonder", "EGYPT", "Giza, Egypt · Africa", "The only surviving Wonder of the Ancient World!"),
            new AtlasEntry(2, "Great Wall of China", "700 BC – 1644 AD", "CHINA", "Northern China · Asia", "21,196 km of walls, watchtowers, and fortresses!"),
            new AtlasEntry(3, "Petra (Al-Khazneh)", "312 BC · Rose City", "JORDAN", "Ma'an Governorate, Jordan · Asia", "Carved directly into pink sandstone cliffs with an ingenious water conduit system!"),
            new AtlasEntry(4, "The Colosseum", "80 AD · Roman Empire", "ITALY", "Rome, Italy · Europe", "The largest ancient amphitheater ever built!"),
            new AtlasEntry(5, "Chichen Itza", "600–1200 AD · Mayan", "MEXICO", "Yucatan, Mexico · Americas", "During the spring and autumn equinoxes, sunlight casts a serpent shadow down its staircase!"),
            new AtlasEntry(6, "Machu Picchu", "1450 AD · Inca Empire", "PERU", "Cusco Region, Peru · Americas", "Sits 2,430 meters above sea level in the Peruvian Andes!"),
            new AtlasEntry(7, "Taj Mahal", "1648 AD · Mughal Architecture", "INDIA", "Agra, India · Asia", "Inlaid with 28 types of precious and semi-precious stones!"),
            new AtlasEntry(8, "Christ the Redeemer", "1931 AD · Art Deco", "BRAZIL", "Rio de Janeiro, Brazil · Americas", "Stands 30 meters tall atop the 700-meter Corcovado mountain peak!"),
            new AtlasEntry(9, "Angkor Wat", "1150 AD · Khmer Empire", "CAMBODIA", "Siem Reap, Cambodia · Asia", "Designed to represent Mount Meru, home of the gods in Hindu and Buddhist tradition!"),
            new AtlasEntry(10, "Eiffel Tower", "1889 AD · Iron Engineering", "FRANCE", "Paris, France · Europe", "Built from 18,038 wrought-iron parts joined by 2.5 million rivets!")
        },
        // 5: Top 10 Smallest Countries (Microstates)
        {
            new AtlasEntry(1, "Vatican City", "0.49 km²", "VATICAN CITY", "Europe · Enclave inside Rome, Italy", "The smallest country on Earth—you can walk across it in 20 minutes!"),
            new AtlasEntry(2, "Monaco", "2.02 km²", "MONACO", "Europe · French Riviera on the Mediterranean", "Second-smallest country on Earth, famous for its harbor and Grand Prix!"),
            new AtlasEntry(3, "Nauru", "21 km²", "NAURU", "Oceania · Micronesia in the Central Pacific", "The smallest island nation and smallest republic in the world!"),
            new AtlasEntry(4, "Tuvalu", "26 km²", "TUVALU", "Oceania · Polynesia in the South Pacific", "Made of nine low-lying ring-shaped coral atolls and reef islands!"),
            new AtlasEntry(5, "San Marino", "61 km²", "SAN MARINO", "Europe · Surrounded by Italy on Mount Titano", "The world's oldest sovereign republic, founded in 301 AD!"),
            new AtlasEntry(6, "Liechtenstein", "160 km²", "LIECHTENSTEIN", "Europe · Alpine valley between Switzerland & Austria", "One of only two doubly landlocked countries in the world!"),
            new AtlasEntry(7, "Marshall Islands", "181 km²", "MARSHALL ISLANDS", "Oceania · 29 Coral Atolls in the Pacific", "Over 98% of its territory is crystal-clear Pacific Ocean water!"),
            new AtlasEntry(8, "Saint Kitts and Nevis", "261 km²", "SAINT KITTS AND NEVIS", "Americas · Leeward Islands in the Caribbean", "The smallest sovereign nation in the Western Hemisphere!"),
            new AtlasEntry(9, "Maldives", "300 km²", "MALDIVES", "Asia · Indian Ocean Archipelago", "The smallest country in Asia by land area, spread across 26 coral atolls!"),
            new AtlasEntry(10, "Malta", "316 km²", "MALTA", "Europe · Central Mediterranean Sea", "The smallest country in the European Union!")
        },
        // 6: Extreme Earth Records (Oceans, Deserts & Lakes)
        {
            new AtlasEntry(1, "Pacific Ocean & Mariana Trench", "165.2 Million km² · 10,935 m deep", "GUAM", "Largest & Deepest Ocean on Earth", "Covers more area than all of Earth's continents combined!"),
            new AtlasEntry(2, "Sahara Desert", "9.2 Million km²", "ALGERIA", "Africa · Largest Hot Desert on Earth", "Stretches across 11 North African nations from the Atlantic to the Red Sea!"),
            new AtlasEntry(3, "Caspian Sea", "371,000 km²", "KAZAKHSTAN", "Asia & Europe · Largest Inland Body of Water", "Bordered by Kazakhstan, Russia, Turkmenistan, Iran, and Azerbaijan!"),
            new AtlasEntry(4, "Lake Baikal", "1,642 m (5,387 ft) deep", "RUSSIA", "Asia · Deepest & Oldest Lake on Earth", "Holds 22% of all unfrozen surface freshwater on the planet!"),
            new AtlasEntry(5, "Lake Superior", "82,100 km²", "CANADA", "Americas · Largest Freshwater Lake by Area", "Shared by Canada and the United States among the five Great Lakes!"),
            new AtlasEntry(6, "Angel Falls", "979 m (3,212 ft) drop", "VENEZUELA", "Americas · Highest Waterfall on Earth", "Plunges from the flat-topped Auyantepui mountain in Canaima National Park!"),
            new AtlasEntry(7, "Dead Sea", "-430 m (-1,412 ft)", "JORDAN", "Asia · Lowest Point on Dry Land", "Nearly 10 times saltier than the ocean, making swimmers float effortlessly!"),
            new AtlasEntry(8, "Atacama Desert", "105,000 km²", "CHILE", "Americas · Driest Non-Polar Desert on Earth", "Its crystal-clear night skies host the world's largest astronomical telescopes!"),
            new AtlasEntry(9, "Gobi Desert", "1.29 Million km²", "MONGOLIA", "Asia · Cold Winter Rain-Shadow Desert", "Famous for dinosaur fossils discovered in its Flaming Cliffs!"),
            new AtlasEntry(10, "Great Barrier Reef", "2,300 km long", "AUSTRALIA", "Oceania · Coral Sea off Queensland", "The world's largest coral reef system, visible even from orbit!")
        }
    };

    static Country findByName(String name) {
        if (name == null) return COUNTRIES[0];
        for (Country c : COUNTRIES) {
            if (c.name.equalsIgnoreCase(name)) return c;
        }
        return COUNTRIES[0];
    }

    static List<Country> getPoolForTierAndRegions(int tier, int regionMask) {
        int mask = (regionMask & REGION_ALL_MASK) == 0 ? REGION_ALL_MASK : (regionMask & REGION_ALL_MASK);
        List<Country> strictMatch = new ArrayList<>();
        List<Country> regionMatch = new ArrayList<>();
        for (Country c : COUNTRIES) {
            if ((mask & (1 << c.continent)) != 0) {
                regionMatch.add(c);
                // Strictly separate Novice (tier 1) vs Expert (tier 2)!
                if (c.tier == tier) {
                    strictMatch.add(c);
                }
            }
        }
        // If a single region (like Oceania) has < 14 countries in a single tier, include all countries of that region
        // so we ALWAYS have 18+ countries to generate 10 non-repeating questions and distractors!
        if (strictMatch.size() >= 14) {
            return strictMatch;
        }
        if (regionMatch.size() >= 10) {
            return regionMatch;
        }
        List<Country> fallback = new ArrayList<>();
        for (Country c : COUNTRIES) {
            if (c.tier == tier) fallback.add(c);
        }
        return fallback;
    }

    static List<Country> pickQuestionSet(int tier, int regionMask, int count, Random rng) {
        List<Country> pool = new ArrayList<>(getPoolForTierAndRegions(tier, regionMask));
        Collections.shuffle(pool, rng);
        if (pool.size() <= count) return pool;
        return new ArrayList<>(pool.subList(0, count));
    }

    static List<Wonder> pickWonderSet(int regionMask, int count, Random rng) {
        int mask = (regionMask & REGION_ALL_MASK) == 0 ? REGION_ALL_MASK : (regionMask & REGION_ALL_MASK);
        List<Wonder> pool = new ArrayList<>();
        for (Wonder w : WONDERS) {
            if ((mask & (1 << w.continent)) != 0) pool.add(w);
        }
        if (pool.size() < count) {
            pool.clear();
            Collections.addAll(pool, WONDERS);
        }
        Collections.shuffle(pool, rng);
        List<Wonder> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(pool.get(i % pool.size()));
        }
        return out;
    }

    /**
     * Smart distractor selector: in Expert mode (tier 2), prefers countries from the SAME continent
     * or similar flag pattern so choices are genuinely challenging!
     */
    static List<Country> buildSmartDistractors(Country target, List<Country> pool, int tier, int count, Random rng) {
        List<Country> sameContOrPat = new ArrayList<>();
        List<Country> others = new ArrayList<>();
        for (Country c : pool) {
            if (c.code.equals(target.code)) continue;
            if (c.continent == target.continent || c.pattern == target.pattern) {
                sameContOrPat.add(c);
            } else {
                others.add(c);
            }
        }
        if (sameContOrPat.size() + others.size() < count - 1) {
            for (Country c : COUNTRIES) {
                if (!c.code.equals(target.code) && !sameContOrPat.contains(c) && !others.contains(c)) {
                    others.add(c);
                }
            }
        }
        Collections.shuffle(sameContOrPat, rng);
        Collections.shuffle(others, rng);

        List<Country> out = new ArrayList<>();
        out.add(target);
        if (tier >= 2) {
            for (Country c : sameContOrPat) {
                if (out.size() >= count) break;
                out.add(c);
            }
        }
        for (Country c : others) {
            if (out.size() >= count) break;
            out.add(c);
        }
        for (Country c : sameContOrPat) {
            if (out.size() >= count) break;
            if (!out.contains(c)) out.add(c);
        }
        Collections.shuffle(out, rng);
        return out;
    }

    /**
     * Combinatorial Fact & Lie Generator (50,000+ unique combinations across 200 countries).
     * Generates either:
     * - Style A (Profile Inspector): Shows Flag + Country Name + 4 Attributes (3 True, 1 Impostor)
     * - Style B (4 Statements): Shows 4 Geography Statements (3 True, 1 Lie)
     */
    static OddQuestion generateOddQuestion(List<Country> pool, int questionIdx, boolean forceProfileStyle, boolean forceStatementStyle, Random rng) {
        List<Country> src = (pool != null && pool.size() >= 6) ? new ArrayList<>(pool) : new ArrayList<>(List.of(COUNTRIES));
        Collections.shuffle(src, rng);

        boolean useProfile = forceProfileStyle || (!forceStatementStyle && (questionIdx % 2 == 0));

        if (useProfile) {
            Country hero = src.get(0);
            Country imp = src.get(1);
            for (int i = 1; i < src.size(); i++) {
                Country cand = src.get(i);
                if (!cand.currency.equals(hero.currency) && !cand.language.equals(hero.language) && !cand.capital.equals(hero.capital)) {
                    imp = cand;
                    break;
                }
            }

            int wrongSlot = rng.nextInt(4);
            String[] opts = new String[4];
            String explanation;

            // Slot 0: Capital
            if (wrongSlot == 0) {
                boolean useTrap = rng.nextBoolean() && hero.trapCity != null && !hero.trapCity.equalsIgnoreCase(hero.capital);
                String fakeCap = useTrap ? hero.trapCity : imp.capital;
                opts[0] = "🏛️ Capital: " + fakeCap;
                explanation = "Caught the lie! The capital of " + hero.name + " is " + hero.capital + " (not " + fakeCap + ").";
            } else {
                opts[0] = "🏛️ Capital: " + hero.capital;
                explanation = "";
            }

            // Slot 1: Currency
            if (wrongSlot == 1) {
                opts[1] = "💴 Currency: " + imp.currency;
                explanation = "Caught the lie! " + hero.name + " uses the " + hero.currency + " (not " + imp.currency + ").";
            } else {
                opts[1] = "💴 Currency: " + hero.currency;
            }

            // Slot 2: Language or Continent
            if (wrongSlot == 2) {
                int wrongCont = (hero.continent + 1 + rng.nextInt(4)) % 5;
                if (rng.nextBoolean()) {
                    opts[2] = "🌍 Region: " + CONTINENT_NAMES[wrongCont];
                    explanation = "Caught the lie! " + hero.name + " is in " + CONTINENT_NAMES[hero.continent] + " (not " + CONTINENT_NAMES[wrongCont] + ").";
                } else {
                    opts[2] = "🗣️ Language: " + imp.language;
                    explanation = "Caught the lie! " + hero.name + " speaks " + hero.language + " (not " + imp.language + ").";
                }
            } else {
                opts[2] = rng.nextBoolean()
                        ? ("🌍 Region: " + CONTINENT_NAMES[hero.continent])
                        : ("🗣️ Language: " + hero.language);
            }

            // Slot 3: Landmark or Flag Colors
            if (wrongSlot == 3) {
                if (rng.nextBoolean()) {
                    opts[3] = "🎨 Flag has " + hero.absentColor + " color";
                    explanation = "Caught the lie! The flag of " + hero.name + " is " + hero.flagColors + " (no " + hero.absentColor + ").";
                } else {
                    opts[3] = "🗽 Famous for: " + imp.landmark;
                    explanation = "Caught the lie! " + hero.name + " is famous for " + hero.landmark + " (" + imp.landmark + " is in " + imp.name + ").";
                }
            } else {
                opts[3] = rng.nextBoolean()
                        ? ("🎨 Colors: " + hero.flagColors)
                        : ("🗽 Famous for: " + hero.landmark);
            }

            return new OddQuestion(
                    true,
                    hero,
                    hero.name,
                    "Spot the 1 WRONG attribute for " + hero.name,
                    opts,
                    wrongSlot,
                    explanation
            );
        } else {
            // Style B: 4 Independent Statements (3 TRUE, 1 FALSE) across 4 different countries
            Country c0 = src.get(0);
            Country c1 = src.get(1);
            Country c2 = src.get(2);
            Country c3 = src.get(3);
            Country c4 = src.get(4);

            int wrongSlot = rng.nextInt(4);
            String[] opts = new String[4];
            String explanation = "";

            Country[] countries = {c0, c1, c2, c3};
            for (int i = 0; i < 4; i++) {
                Country c = countries[i];
                int factType = (i + questionIdx) % 5;
                if (i == wrongSlot) {
                    // Generate a plausible FALSE statement!
                    switch (factType) {
                        case 0:
                            opts[i] = "Flag of " + c.name + " has " + c.absentColor.toLowerCase() + " color";
                            explanation = "Lie spotted! The flag of " + c.name + " has " + c.flagColors + " (no " + c.absentColor.toLowerCase() + ").";
                            break;
                        case 1:
                            String fakeCap = (rng.nextBoolean() && !c.trapCity.equalsIgnoreCase(c.capital)) ? c.trapCity : c4.capital;
                            opts[i] = fakeCap + " is the capital of " + c.name;
                            explanation = "Lie spotted! The capital of " + c.name + " is " + c.capital + " (not " + fakeCap + ").";
                            break;
                        case 2:
                            String fakeCurr = c4.currency.equals(c.currency) ? "Mexican Peso" : c4.currency;
                            opts[i] = "Currency of " + c.name + " is the " + fakeCurr;
                            explanation = "Lie spotted! " + c.name + " uses the " + c.currency + ".";
                            break;
                        case 3:
                            int fakeCont = (c.continent + 2) % 5;
                            opts[i] = c.name + " is located in " + CONTINENT_NAMES[fakeCont];
                            explanation = "Lie spotted! " + c.name + " is located in " + CONTINENT_NAMES[c.continent] + ".";
                            break;
                        default:
                            opts[i] = c4.landmark + " is located in " + c.name;
                            explanation = "Lie spotted! " + c4.landmark + " is in " + c4.name + " (" + c.name + " has " + c.landmark + ").";
                            break;
                    }
                } else {
                    // Generate a verified TRUE statement!
                    switch (factType) {
                        case 0:
                            opts[i] = "Flag of " + c.name + " features " + c.flagColors;
                            break;
                        case 1:
                            opts[i] = c.capital + " is the capital of " + c.name;
                            break;
                        case 2:
                            opts[i] = "Currency of " + c.name + " is the " + c.currency;
                            break;
                        case 3:
                            opts[i] = c.name + " is located in " + CONTINENT_NAMES[c.continent];
                            break;
                        default:
                            opts[i] = c.landmark + " is in " + c.name;
                            break;
                    }
                }
            }

            return new OddQuestion(
                    false,
                    null,
                    "SPOT THE WRONG STATEMENT",
                    "3 statements are true · Click the 1 statement that is FALSE",
                    opts,
                    wrongSlot,
                    explanation
            );
        }
    }

    static String getDailyChallengeHeadline() {
        return "⚡ Daily Quiz";
    }
}
