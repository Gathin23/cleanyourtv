package com.gathin.homedashboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Recent distinct stories and a three-row carousel with each incoming story at the top. */
final class NewsSelection {
    static List<Headline> recent(List<Headline> input, long now) {
        List<Headline> sorted = new ArrayList<>();
        for (Headline h : input) {
            if (!h.title.trim().isEmpty() && (h.time == 0 || now - h.time <= 48 * 3600000L)) sorted.add(h);
        }
        sorted.sort(Comparator.comparingLong((Headline h) -> h.time).reversed());
        List<Headline> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Headline h : sorted) {
            if (seen.add(h.title.trim().toLowerCase(Locale.ROOT))) out.add(h);
            if (out.size() == 60) break;
        }
        return out;
    }

    static final class Rotation {
        private final List<Headline> stories;
        private final List<Headline> shown;
        private int next;

        Rotation(List<Headline> stories) {
            this.stories=new ArrayList<>(stories);
            shown=new ArrayList<>(stories.subList(0,Math.min(3,stories.size())));
            next=shown.size();
        }
        List<Headline> visible() { return new ArrayList<>(shown); }
        boolean advance() {
            if(stories.size()<=3)return false;
            for(int tries=0;tries<stories.size();tries++){
                Headline incoming=stories.get(next%stories.size());next=(next+1)%stories.size();
                if(shown.contains(incoming))continue;
                shown.add(0,incoming);shown.remove(shown.size()-1);return true;
            }
            return false;
        }
    }
}
