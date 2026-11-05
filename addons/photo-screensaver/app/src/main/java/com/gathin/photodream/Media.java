package com.gathin.photodream;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Which files the screensaver shows and FilesProvider accepts, and how big a picture is decoded. */
final class Media {
    static final String PICTURES = "pictures";
    static final String SECONDS = "seconds.txt";
    static final int DEFAULT_SECONDS = 20;
    static final int MAX_FILES = 100;
    static final long IMAGE_MAX = 10L * 1024 * 1024;
    static final long VIDEO_MAX = 1024L * 1024 * 1024;
    /** 4K UHD and a little more; a bigger picture is skipped rather than decoded on a 1 GB TV. */
    static final long MAX_PIXELS = 8_400_000L;

    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO = Pattern.compile("[A-Za-z0-9_.-]+\\.mp4", Pattern.CASE_INSENSITIVE);

    private Media() {}

    static boolean image(String name) {
        return name != null && !name.startsWith(".") && IMAGE.matcher(name).matches();
    }

    static boolean video(String name) {
        return name != null && !name.startsWith(".") && VIDEO.matcher(name).matches();
    }

    static boolean file(String name) {
        return image(name) || video(name);
    }

    /** The path under the files dir that a provider URI may write or delete, or null. */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (segments.size() == 1 && SECONDS.equals(segments.get(0))) return SECONDS;
        if (segments.size() == 2 && PICTURES.equals(segments.get(0)) && file(segments.get(1))) {
            return PICTURES + "/" + segments.get(1);
        }
        return null;
    }

    /** True for "pictures" alone: a delete of it clears every picture and clip. */
    static boolean isFolder(List<String> segments) {
        return segments != null && segments.size() == 1 && PICTURES.equals(segments.get(0));
    }

    /** The folder's pictures and clips by name, at most 100; empty and oversized files are left out. */
    static List<File> list(File folder) {
        List<File> result = new ArrayList<>();
        File[] entries = folder == null ? null : folder.listFiles();
        if (entries == null) return result;
        Arrays.sort(entries, Comparator.comparing(File::getName));
        for (File entry : entries) {
            if (!entry.isFile() || entry.length() <= 0) continue;
            String name = entry.getName();
            boolean ok = video(name) ? entry.length() <= VIDEO_MAX : image(name) && entry.length() <= IMAGE_MAX;
            if (ok) result.add(entry);
            if (result.size() == MAX_FILES) break;
        }
        return result;
    }

    /** The file after {@code previous} by name, wrapping around; the first one when previous is gone. */
    static File next(List<File> files, String previous) {
        if (files.isEmpty()) return null;
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).getName().equals(previous)) return files.get((i + 1) % files.size());
        }
        return files.get(0);
    }

    /** seconds.txt's number, 5 to 3600; 20 when it is missing or not a number. */
    static int seconds(String text) {
        if (text == null) return DEFAULT_SECONDS;
        try {
            int s = Integer.parseInt(text.trim());
            return s < 5 || s > 3600 ? DEFAULT_SECONDS : s;
        } catch (NumberFormatException e) {
            return DEFAULT_SECONDS;
        }
    }

    private static final Pattern NAME_DATE = Pattern.compile("-(\\d{4})(\\d{2})(\\d{2})?\\.[A-Za-z0-9]+$");

    /** {year, month} from a name ending in its date, photo-20231202.jpg or photo-202312.jpg; else null. */
    static int[] dateFromName(String name) {
        Matcher m = name == null ? null : NAME_DATE.matcher(name);
        if (m == null || !m.find()) return null;
        int year = Integer.parseInt(m.group(1)), month = Integer.parseInt(m.group(2));
        return year >= 1900 && month >= 1 && month <= 12 ? new int[]{year, month} : null;
    }

    static boolean accepted(int width, int height) {
        return width > 0 && height > 0 && (long) width * height <= MAX_PIXELS;
    }

    /** The largest power-of-two sample size that still covers the screen in both directions. */
    static int sampleSize(int width, int height, int screenW, int screenH) {
        int sample = 1;
        while (width / (sample * 2) >= screenW && height / (sample * 2) >= screenH) sample *= 2;
        return sample;
    }

    /** {inDensity, inTargetDensity} that scale a sampled picture down to what CENTER_CROP shows, or null. */
    static int[] coverScale(int width, int height, int screenW, int screenH) {
        if ((long) screenW * height >= (long) screenH * width) {
            return width > screenW ? new int[]{width, screenW} : null;
        }
        return height > screenH ? new int[]{height, screenH} : null;
    }
}
