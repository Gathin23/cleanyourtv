package io.github.nickld.frigateglance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class Fixtures {
    private Fixtures() {}

    static String fixture(String name) {
        try (InputStream in = Fixtures.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new IllegalArgumentException("no fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A review payload as Frigate publishes it on frigate/reviews. */
    static String reviewJson(String type, String id, String camera, String severity, String... objects) {
        StringBuilder objs = new StringBuilder();
        for (String o : objects) objs.append(objs.length() == 0 ? "" : ",").append('"').append(o).append('"');
        String after = "{\"id\":\"" + id + "\",\"camera\":\"" + camera + "\",\"severity\":\"" + severity
                + "\",\"data\":{\"detections\":[],\"objects\":[" + objs + "],\"zones\":[]}}";
        return "{\"type\":\"" + type + "\",\"before\":" + after + ",\"after\":" + after + "}";
    }
}
