package io.github.jain05vaibhav.previewrows;

import java.util.Collections;
import java.util.List;

/**
 * Plain-Java data types for preview channels and still cards. No Android types, so JVM unit tests cover the logic.
 */
final class PreviewModels {

    private PreviewModels() {
    }

    static final class ChannelRecord {
        final long id;
        final String packageName;
        final String displayName;
        final String appLinkIntentUri;
        final boolean browsable;
        final String type;
        final int order;

        ChannelRecord(long id, String packageName, String displayName, String appLinkIntentUri,
                      boolean browsable, String type, int order) {
            this.id = id;
            this.packageName = packageName == null ? "" : packageName.trim();
            this.displayName = displayName == null ? "" : displayName.trim();
            this.appLinkIntentUri = appLinkIntentUri == null ? "" : appLinkIntentUri.trim();
            this.browsable = browsable;
            this.type = type == null ? "" : type.trim();
            this.order = order;
        }
    }

    static final class ProgramRecord {
        final long id;
        final long channelId;
        final String packageName;
        final String title;
        final String shortDescription;
        final String episodeTitle;
        final String seasonNumber;
        final String episodeNumber;
        final String posterArtUri;
        final String thumbnailUri;
        final String intentUri;
        final int weight;
        final long lastPlaybackPositionMillis;
        final long durationMillis;
        final boolean searchable;
        final int order;

        ProgramRecord(long id, long channelId, String packageName, String title, String shortDescription,
                      String episodeTitle, String seasonNumber, String episodeNumber,
                      String posterArtUri, String thumbnailUri, String intentUri,
                      int weight, long lastPlaybackPositionMillis, long durationMillis,
                      boolean searchable, int order) {
            this.id = id;
            this.channelId = channelId;
            this.packageName = packageName == null ? "" : packageName.trim();
            this.title = title == null ? "" : title.trim();
            this.shortDescription = shortDescription == null ? "" : shortDescription.trim();
            this.episodeTitle = episodeTitle == null ? "" : episodeTitle.trim();
            this.seasonNumber = seasonNumber == null ? "" : seasonNumber.trim();
            this.episodeNumber = episodeNumber == null ? "" : episodeNumber.trim();
            this.posterArtUri = posterArtUri == null ? "" : posterArtUri.trim();
            this.thumbnailUri = thumbnailUri == null ? "" : thumbnailUri.trim();
            this.intentUri = intentUri == null ? "" : intentUri.trim();
            this.weight = weight;
            this.lastPlaybackPositionMillis = lastPlaybackPositionMillis;
            this.durationMillis = durationMillis;
            this.searchable = searchable;
            this.order = order;
        }
    }

    static final class IntentChoice {
        enum Kind {
            INTENT_URI,
            CHANNEL_URI,
            PACKAGE_LAUNCH,
            NONE
        }

        final Kind kind;
        final String uri;
        final String packageName;

        IntentChoice(Kind kind, String uri, String packageName) {
            this.kind = kind;
            this.uri = uri == null ? "" : uri;
            this.packageName = packageName == null ? "" : packageName;
        }
    }

    static final class PosterSource {
        enum Kind {
            LOCAL_URI,
            REMOTE_FALLBACK,
            NONE
        }

        final Kind kind;
        final String uri;

        PosterSource(Kind kind, String uri) {
            this.kind = kind;
            this.uri = uri == null ? "" : uri;
        }
    }

    static final class CardItem {
        final long id;
        final String packageName;
        final String title;
        final String subtitle;
        final PosterSource poster;
        final IntentChoice launch;
        final int weight;
        final int order;

        CardItem(long id, String packageName, String title, String subtitle,
                 PosterSource poster, IntentChoice launch, int weight, int order) {
            this.id = id;
            this.packageName = packageName;
            this.title = title;
            this.subtitle = subtitle;
            this.poster = poster;
            this.launch = launch;
            this.weight = weight;
            this.order = order;
        }
    }

    static final class ChannelRow {
        final long channelId;
        final String packageName;
        final String appLabel;
        final String channelTitle;
        final String headerLabel;
        final boolean browsable;
        final List<CardItem> items;

        ChannelRow(long channelId, String packageName, String appLabel, String channelTitle,
                   String headerLabel, boolean browsable, List<CardItem> items) {
            this.channelId = channelId;
            this.packageName = packageName;
            this.appLabel = appLabel;
            this.channelTitle = channelTitle;
            this.headerLabel = headerLabel;
            this.browsable = browsable;
            this.items = Collections.unmodifiableList(items);
        }
    }

    static final class FocusTarget {
        final int rowIndex;
        final int itemIndex;

        FocusTarget(int rowIndex, int itemIndex) {
            this.rowIndex = rowIndex;
            this.itemIndex = itemIndex;
        }
    }
}

