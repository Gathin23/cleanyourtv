package io.github.vitorcamillo.wolfgames;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Validated local launch targets. Never accepts arbitrary URIs, commands or components. */
final class MoonlightCatalog {
    static final int MAX_GAMES = 64;
    static final long MAX_CATALOG_BYTES = 262144;
    final String hostName;
    final String hostUuid;
    final List<Game> games;

    static final class Game {
        final String name;
        final String appId;
        final String image;

        Game(String name, String appId, String image) {
            this.name = label(name);
            if (appId == null || appId.isEmpty()) this.appId = null;
            else {
                if (!appId.matches("[1-9][0-9]{0,9}")) throw new IllegalArgumentException("Invalid game id");
                try {
                    if (Long.parseLong(appId) > Integer.MAX_VALUE) throw new IllegalArgumentException("Game id overflow");
                } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid game id", e); }
                this.appId = appId;
            }
            if (image != null && !image.isEmpty() && !image.matches("[A-Za-z0-9_-]{1,100}\\.(png|jpg|jpeg)")) {
                throw new IllegalArgumentException("Invalid cover filename");
            }
            this.image = image == null || image.isEmpty() ? null : image;
        }
    }

    MoonlightCatalog(String name, String uuid, List<Game> entries) {
        hostName = label(name);
        if (uuid == null || !uuid.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("Invalid host UUID");
        }
        hostUuid = UUID.fromString(uuid).toString();
        if (entries == null || entries.isEmpty() || entries.size() > MAX_GAMES) throw new IllegalArgumentException("Invalid game count");
        Set<String> names = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (Game game : entries) {
            if (game == null || !names.add(game.name) || (game.appId != null && !ids.add(game.appId))) {
                throw new IllegalArgumentException("Duplicate or invalid game");
            }
        }
        games = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** Display indices: the running game first, then the saved order, then catalog order. */
    static List<Integer> displayOrder(List<Game> games, List<String> savedNames, String activeAppId) {
        if (games == null) throw new IllegalArgumentException("Invalid games");
        List<Integer> order = new ArrayList<>(games.size());
        boolean[] used = new boolean[games.size()];
        if (activeAppId != null) {
            for (int i = 0; i < games.size(); i++) {
                Game game = games.get(i);
                if (game != null && activeAppId.equals(game.appId)) {
                    used[i] = true;
                    order.add(i);
                    break;
                }
            }
        }
        if (savedNames != null) {
            for (String name : savedNames) {
                if (name == null) continue;
                for (int i = 0; i < games.size(); i++) {
                    Game game = games.get(i);
                    if (!used[i] && game != null && name.equals(game.name)) {
                        used[i] = true;
                        order.add(i);
                        break;
                    }
                }
            }
        }
        for (int i = 0; i < games.size(); i++) if (!used[i]) order.add(i);
        return order;
    }

    /** Copies the names with the entries at from and to swapped; both must be valid positions. */
    static List<String> moved(List<String> names, int from, int to) {
        if (names == null || from < 0 || to < 0 || from >= names.size() || to >= names.size()) {
            throw new IllegalArgumentException("Invalid move");
        }
        List<String> order = new ArrayList<>(names);
        String value = order.get(from);
        order.set(from, order.get(to));
        order.set(to, value);
        return order;
    }

    static String label(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 120 || !value.equals(value.trim())) {
            throw new IllegalArgumentException("Invalid label");
        }
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) throw new IllegalArgumentException("Control character");
        return value;
    }

    static File localFile(File directory, String name) throws IOException {
        if (name == null || (!name.equals("catalog.json") && !name.matches("[A-Za-z0-9_-]{1,100}\\.(png|jpg|jpeg)"))) {
            throw new IOException("Invalid filename");
        }
        File file = new File(directory, name);
        if (!directory.getCanonicalFile().equals(file.getCanonicalFile().getParentFile())) throw new IOException("Cover outside catalog");
        return file;
    }
}
