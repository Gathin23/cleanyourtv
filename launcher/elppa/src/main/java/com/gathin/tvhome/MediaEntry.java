package com.gathin.tvhome;

/** Immutable provider data, also used by the pure-Java filtering tests. */
final class MediaEntry {
  final String key, source, title, subtitle, poster, backdrop, intentUri;
  final int watchType, contentType;
  final long engaged, position, duration;
  final boolean preview;

  MediaEntry(
      String key,
      String source,
      String title,
      String subtitle,
      String poster,
      String backdrop,
      String intentUri,
      int watchType,
      int contentType,
      long engaged,
      long position,
      long duration,
      boolean preview) {
    this.key = text(key);
    this.source = text(source);
    this.title = text(title);
    this.subtitle = text(subtitle);
    this.poster = text(poster);
    this.backdrop = text(backdrop);
    this.intentUri = text(intentUri);
    this.watchType = watchType;
    this.contentType = contentType;
    this.engaged = Math.max(0, engaged);
    this.position = Math.max(0, position);
    this.duration = Math.max(0, duration);
    this.preview = preview;
  }

  static String text(String value) {
    return value == null
        ? ""
        : value.replaceAll("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]", " ").trim();
  }

  boolean hasProgress() {
    return watchType == 0 && duration > 0 && position > 0;
  }

  int progress() {
    return duration <= 0 ? 0 : (int) Math.min(100, 100d * position / duration);
  }

  String heroArt() {
    return backdrop.isEmpty() ? poster : backdrop;
  }

  String identity() {
    return source + '\n' + (intentUri.isEmpty() ? title + '\n' + subtitle : intentUri);
  }
}
