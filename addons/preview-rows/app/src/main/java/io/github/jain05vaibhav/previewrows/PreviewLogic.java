package io.github.jain05vaibhav.previewrows;

import io.github.jain05vaibhav.previewrows.PreviewModels.CardItem;
import io.github.jain05vaibhav.previewrows.PreviewModels.ChannelRecord;
import io.github.jain05vaibhav.previewrows.PreviewModels.ChannelRow;
import io.github.jain05vaibhav.previewrows.PreviewModels.FocusTarget;
import io.github.jain05vaibhav.previewrows.PreviewModels.IntentChoice;
import io.github.jain05vaibhav.previewrows.PreviewModels.PosterSource;
import io.github.jain05vaibhav.previewrows.PreviewModels.ProgramRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure-Java grouping, filtering, poster selection and intent selection for preview channels.
 * No Android framework imports so all rules run in plain JVM unit tests.
 */
final class PreviewLogic {

    private static final Set<String> BLOCKED_URI_SCHEMES = new HashSet<>(Arrays.asList(
            "javascript", "vbscript", "data", "file", "about", "blob"
    ));

    private PreviewLogic() {
    }

    /**
     * Groups preview programs by channel, filters out non-searchable or empty entries,
     * sorts programs within each channel by weight (desc) then cursor order (asc),
     * drops empty channels, and sorts channels (browsable first, then by app/channel label).
     */
    static List<ChannelRow> groupAndFilter(Collection<ChannelRecord> channels,
                                           Collection<ProgramRecord> programs,
                                           Map<String, String> appLabels) {
        if (programs == null || programs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, ChannelRecord> channelById = new HashMap<>();
        if (channels != null) {
            for (ChannelRecord ch : channels) {
                if (ch != null && ch.id > 0) {
                    channelById.put(ch.id, ch);
                }
            }
        }

        Map<Long, List<CardItem>> grouped = new LinkedHashMap<>();
        for (ProgramRecord program : programs) {
            if (program == null || !program.searchable || program.channelId <= 0) {
                continue;
            }
            ChannelRecord channel = channelById.get(program.channelId);
            String pkg = firstNonEmpty(program.packageName, channel != null ? channel.packageName : "");
            String appLabel = lookupLabel(appLabels, pkg);
            String rawTitle = chooseProgramTitle(program);
            if (rawTitle.isEmpty() && !isAllowedLaunchUri(program.intentUri)) {
                continue;
            }
            IntentChoice launch = chooseIntent(program, channel, pkg);
            String title = !rawTitle.isEmpty() ? rawTitle : (!appLabel.isEmpty() ? appLabel : "Untitled");
            String subtitle = buildSubtitle(program, title);
            PosterSource poster = choosePoster(program);
            CardItem card = new CardItem(
                    program.id, pkg, title, subtitle, poster, launch, program.weight, program.order);
            grouped.computeIfAbsent(program.channelId, k -> new ArrayList<>()).add(card);
        }

        List<ChannelRow> rows = new ArrayList<>();
        for (Map.Entry<Long, List<CardItem>> entry : grouped.entrySet()) {
            List<CardItem> items = entry.getValue();
            if (items.isEmpty()) {
                continue;
            }
            items.sort((a, b) -> {
                int w = Integer.compare(b.weight, a.weight);
                if (w != 0) return w;
                int o = Integer.compare(a.order, b.order);
                if (o != 0) return o;
                return Long.compare(a.id, b.id);
            });

            long channelId = entry.getKey();
            ChannelRecord channel = channelById.get(channelId);
            String pkg = firstNonEmpty(
                    channel != null ? channel.packageName : "",
                    items.get(0).packageName);
            String appLabel = lookupLabel(appLabels, pkg);
            String channelTitle = channel != null ? channel.displayName : "";
            String header = formatHeader(appLabel, channelTitle, pkg, channelId);
            boolean browsable = channel != null && channel.browsable;

            rows.add(new ChannelRow(channelId, pkg, appLabel, channelTitle, header, browsable, items));
        }

        rows.sort((a, b) -> {
            if (a.browsable != b.browsable) return a.browsable ? -1 : 1;
            String aApp = a.appLabel.isEmpty() ? a.headerLabel : a.appLabel;
            String bApp = b.appLabel.isEmpty() ? b.headerLabel : b.appLabel;
            int byApp = aApp.compareToIgnoreCase(bApp);
            if (byApp != 0) return byApp;
            int byHeader = a.headerLabel.compareToIgnoreCase(b.headerLabel);
            if (byHeader != 0) return byHeader;
            return Long.compare(a.channelId, b.channelId);
        });

        return rows;
    }

    /**
     * Picks the best launch target for a card:
     * 1) the program's own intent_uri if valid,
     * 2) the channel's app_link_intent_uri if valid,
     * 3) launching the publishing package directly,
     * 4) NONE when nothing is available.
     */
    static IntentChoice chooseIntent(ProgramRecord program, ChannelRecord channel, String fallbackPackage) {
        String pkg = firstNonEmpty(
                program != null ? program.packageName : "",
                channel != null ? channel.packageName : "",
                fallbackPackage);
        if (program != null && isAllowedLaunchUri(program.intentUri)) {
            return new IntentChoice(IntentChoice.Kind.INTENT_URI, program.intentUri.trim(), pkg);
        }
        if (channel != null && isAllowedLaunchUri(channel.appLinkIntentUri)) {
            return new IntentChoice(IntentChoice.Kind.CHANNEL_URI, channel.appLinkIntentUri.trim(), pkg);
        }
        if (!pkg.isEmpty()) {
            return new IntentChoice(IntentChoice.Kind.PACKAGE_LAUNCH, "", pkg);
        }
        return new IntentChoice(IntentChoice.Kind.NONE, "", "");
    }

    /**
     * Verifies whether a resolved activity is safe to launch for a preview card:
     * - rejects unresolved intents (empty resolvedPkg),
     * - rejects intents that resolve back into this add-on (selfPkg),
     * - when the publishing package is known, rejects intents that resolve to a different package.
     */
    static boolean canLaunch(String resolvedPkg, String publisherPkg, String selfPkg) {
        String resolved = resolvedPkg == null ? "" : resolvedPkg.trim();
        if (resolved.isEmpty()) {
            return false;
        }
        String self = selfPkg == null ? "" : selfPkg.trim();
        if (!self.isEmpty() && resolved.equals(self)) {
            return false;
        }
        String publisher = publisherPkg == null ? "" : publisherPkg.trim();
        if (!publisher.isEmpty() && !resolved.equals(publisher)) {
            return false;
        }
        return true;
    }

    /**
     * Caps each channel row to at most maxItemsPerRow cards for bounded TV memory.
     */
    static List<ChannelRow> capRows(List<ChannelRow> rows, int maxItemsPerRow) {
        if (rows == null || rows.isEmpty() || maxItemsPerRow <= 0) {
            return Collections.emptyList();
        }
        List<ChannelRow> out = new ArrayList<>(rows.size());
        for (ChannelRow row : rows) {
            if (row == null || row.items.isEmpty()) continue;
            List<CardItem> items = row.items.size() <= maxItemsPerRow
                    ? row.items
                    : row.items.subList(0, maxItemsPerRow);
            out.add(new ChannelRow(row.channelId, row.packageName, row.appLabel,
                    row.channelTitle, row.headerLabel, row.browsable, items));
        }
        return out;
    }

    /**
     * Picks the card to restore focus to after a refresh:
     * 1) exact (channelId, programId) match within maxItemsPerRow,
     * 2) first item in the same channelId,
     * 3) first item in the first non-empty row.
     */
    static FocusTarget pickFocusTarget(List<ChannelRow> rows, long wantedChannelId, long wantedProgramId) {
        return pickFocusTarget(rows, wantedChannelId, wantedProgramId, Integer.MAX_VALUE);
    }

    static FocusTarget pickFocusTarget(List<ChannelRow> rows, long wantedChannelId,
                                       long wantedProgramId, int maxItemsPerRow) {
        if (rows == null || rows.isEmpty() || maxItemsPerRow <= 0) {
            return null;
        }
        int firstNonEmptyRow = -1;
        int sameChannelRow = -1;
        for (int r = 0; r < rows.size(); r++) {
            ChannelRow row = rows.get(r);
            if (row == null || row.items.isEmpty()) continue;
            if (firstNonEmptyRow < 0) firstNonEmptyRow = r;
            if (wantedChannelId > 0 && row.channelId == wantedChannelId) {
                if (sameChannelRow < 0) sameChannelRow = r;
                if (wantedProgramId > 0) {
                    int limit = Math.min(row.items.size(), maxItemsPerRow);
                    for (int i = 0; i < limit; i++) {
                        if (row.items.get(i).id == wantedProgramId) {
                            return new FocusTarget(r, i);
                        }
                    }
                }
            }
        }
        if (sameChannelRow >= 0) {
            return new FocusTarget(sameChannelRow, 0);
        }
        return firstNonEmptyRow >= 0 ? new FocusTarget(firstNonEmptyRow, 0) : null;
    }

    /**
     * Computes a power-of-two subsampling factor bounded by both target dimensions and a total
     * pixel budget, so extreme aspect-ratio images (e.g. 20000x100) still subsample down safely.
     */
    static int computeSampleSize(int width, int height, int reqWidth, int reqHeight) {
        if (width <= 0 || height <= 0 || reqWidth <= 0 || reqHeight <= 0) {
            return 1;
        }
        int inSampleSize = 1;
        long maxPixels = (long) reqWidth * reqHeight * 4L;
        int maxW = reqWidth * 4;
        int maxH = reqHeight * 4;
        while (true) {
            int next = inSampleSize * 2;
            int w = width / inSampleSize;
            int h = height / inSampleSize;
            boolean bothAboveDouble = (h / 2) >= reqHeight && (w / 2) >= reqWidth;
            boolean exceedsDimOrPixelBudget = w > maxW || h > maxH || ((long) w * h) > maxPixels;
            if ((!bothAboveDouble && !exceedsDimOrPixelBudget) || next > Math.max(width, height)) {
                break;
            }
            inSampleSize = next;
        }
        return inSampleSize;
    }

    /**
     * Validates that a URI string has a safe, non-empty scheme suitable for startActivity.
     */
    static boolean isAllowedLaunchUri(String rawUri) {
        if (rawUri == null) return false;
        String uri = rawUri.trim();
        if (uri.isEmpty() || uri.indexOf('\n') >= 0 || uri.indexOf('\r') >= 0) {
            return false;
        }
        int colon = uri.indexOf(':');
        if (colon <= 0 || colon >= uri.length() - 1) {
            return false;
        }
        String scheme = uri.substring(0, colon);
        if (!isValidScheme(scheme)) {
            return false;
        }
        String lowerScheme = scheme.toLowerCase(Locale.ROOT);
        if (BLOCKED_URI_SCHEMES.contains(lowerScheme)) {
            return false;
        }
        String rest = uri.substring(colon + 1);
        return !rest.equals("//");
    }

    /**
     * Picks a still image source without requiring network access:
     * local content:// or android.resource:// URIs load offline; http(s):// URIs fall back
     * to the app's banner or icon plus title text because v1 has no INTERNET permission.
     */
    static PosterSource choosePoster(ProgramRecord program) {
        if (program == null) {
            return new PosterSource(PosterSource.Kind.NONE, "");
        }
        String primary = program.posterArtUri;
        String secondary = program.thumbnailUri;

        if (isLocalImageUri(primary)) {
            return new PosterSource(PosterSource.Kind.LOCAL_URI, primary);
        }
        if (isLocalImageUri(secondary)) {
            return new PosterSource(PosterSource.Kind.LOCAL_URI, secondary);
        }
        if (isRemoteUri(primary)) {
            return new PosterSource(PosterSource.Kind.REMOTE_FALLBACK, primary);
        }
        if (isRemoteUri(secondary)) {
            return new PosterSource(PosterSource.Kind.REMOTE_FALLBACK, secondary);
        }
        return new PosterSource(PosterSource.Kind.NONE, "");
    }

    static boolean isLocalImageUri(String uri) {
        if (uri == null) return false;
        String lower = uri.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("content://") || lower.startsWith("android.resource://");
    }

    static boolean isRemoteUri(String uri) {
        if (uri == null) return false;
        String lower = uri.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    static String buildSubtitle(ProgramRecord program, String cardTitle) {
        if (program == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean hasSeason = !program.seasonNumber.isEmpty();
        boolean hasEpisode = !program.episodeNumber.isEmpty();
        if (hasSeason && hasEpisode) {
            sb.append('S').append(program.seasonNumber).append(" · E").append(program.episodeNumber);
        } else if (hasEpisode) {
            sb.append("Ep ").append(program.episodeNumber);
        } else if (hasSeason) {
            sb.append("Season ").append(program.seasonNumber);
        }

        if (!program.episodeTitle.isEmpty() && !program.episodeTitle.equalsIgnoreCase(cardTitle)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(program.episodeTitle);
        } else if (sb.length() == 0 && !program.shortDescription.isEmpty()
                && !program.shortDescription.equalsIgnoreCase(cardTitle)) {
            sb.append(program.shortDescription);
        }

        if (program.durationMillis > 0 && program.lastPlaybackPositionMillis > 0) {
            int pct = (int) Math.round((program.lastPlaybackPositionMillis * 100.0) / program.durationMillis);
            if (pct < 1) pct = 1;
            if (pct > 100) pct = 100;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(pct).append('%');
        }
        return sb.toString();
    }

    static String formatHeader(String appLabel, String channelTitle, String pkg, long channelId) {
        if (!appLabel.isEmpty() && !channelTitle.isEmpty()) {
            return appLabel.equalsIgnoreCase(channelTitle)
                    ? appLabel
                    : appLabel + " · " + channelTitle;
        }
        if (!appLabel.isEmpty()) return appLabel;
        if (!channelTitle.isEmpty()) return channelTitle;
        if (!pkg.isEmpty()) return pkg;
        return "Channel " + channelId;
    }

    private static String chooseProgramTitle(ProgramRecord program) {
        return firstNonEmpty(
                program.title,
                program.episodeTitle,
                program.shortDescription);
    }

    private static String lookupLabel(Map<String, String> appLabels, String pkg) {
        if (appLabels == null || pkg == null || pkg.isEmpty()) return "";
        String label = appLabels.get(pkg);
        return label == null ? "" : label.trim();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null) {
                String trimmed = v.trim();
                if (!trimmed.isEmpty()) return trimmed;
            }
        }
        return "";
    }

    private static boolean isValidScheme(String scheme) {
        if (scheme.isEmpty()) return false;
        char first = scheme.charAt(0);
        if (!((first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z'))) {
            return false;
        }
        for (int i = 1; i < scheme.length(); i++) {
            char c = scheme.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.';
            if (!ok) return false;
        }
        return true;
    }
}

