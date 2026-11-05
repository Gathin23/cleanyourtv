package io.github.shaharukhs.tvinspector;

import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.view.Display;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Names for the numbers Android hands back. Only constants, no Android calls, so the JVM tests can run it. */
final class Labels {
    private Labels() { }

    /** Standard picture sizes, largest first; a decoder's rank is the index of the largest one it accepts. */
    static final int[][] SIZES = {{7680, 4320}, {3840, 2160}, {1920, 1080}, {1280, 720}};
    private static final String[] SIZE_NAMES = {"8K", "4K", "1080p", "720p"};
    /** No decoder of that kind; any rank 0..SIZES.length is a decoder (SIZES.length: below 720p). */
    static final int MISSING = -1;

    static int better(int a, int b) { return a < 0 ? b : b < 0 ? a : Math.min(a, b); }

    static String decoder(String label, int hardware, int software) {
        if (hardware >= 0) return label + ": hardware, " + size(hardware);
        if (software >= 0) return label + ": software only, " + size(software);
        return label + ": no decoder";
    }

    private static String size(int rank) { return rank < SIZE_NAMES.length ? "up to " + SIZE_NAMES[rank] : "below 720p"; }

    /** Codec names Android ships as software decoders, for TVs older than API 29's isHardwareAccelerated. */
    static boolean softwareCodec(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("omx.google.") || lower.startsWith("c2.android.");
    }

    static String refresh(float hz) {
        String text = String.format(Locale.US, "%.2f", hz).replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    /** One line per resolution, largest first, with its refresh rates fastest first: "3840 × 2160: 60, 50 Hz". */
    static List<String> modes(int[] widths, int[] heights, float[] rates) {
        Integer[] order = new Integer[widths.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            long area = (long) widths[b] * heights[b] - (long) widths[a] * heights[a];
            if (area != 0) return area > 0 ? 1 : -1;
            if (widths[a] != widths[b]) return widths[b] - widths[a];
            return Float.compare(rates[b], rates[a]);
        });
        Map<String, Set<String>> lines = new LinkedHashMap<>();
        for (int i : order) {
            lines.computeIfAbsent(widths[i] + " × " + heights[i], k -> new LinkedHashSet<>()).add(refresh(rates[i]));
        }
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> line : lines.entrySet()) {
            result.add(line.getKey() + ": " + String.join(", ", line.getValue()) + " Hz");
        }
        return result;
    }

    static String hdr(int type) {
        switch (type) {
            case Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION: return "Dolby Vision";
            case Display.HdrCapabilities.HDR_TYPE_HDR10: return "HDR10";
            case Display.HdrCapabilities.HDR_TYPE_HLG: return "HLG";
            case Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS: return "HDR10+";
            default: return "HDR type " + type;
        }
    }

    static String encoding(int encoding) {
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_8BIT: return "PCM 8-bit";
            case AudioFormat.ENCODING_PCM_16BIT: return "PCM 16-bit";
            case AudioFormat.ENCODING_PCM_24BIT_PACKED: return "PCM 24-bit";
            case AudioFormat.ENCODING_PCM_32BIT: return "PCM 32-bit";
            case AudioFormat.ENCODING_PCM_FLOAT: return "PCM float";
            case AudioFormat.ENCODING_AC3: return "Dolby Digital";
            case AudioFormat.ENCODING_E_AC3: return "Dolby Digital Plus";
            case AudioFormat.ENCODING_E_AC3_JOC: return "Dolby Atmos (DD+ JOC)";
            case AudioFormat.ENCODING_DOLBY_TRUEHD: return "Dolby TrueHD";
            case AudioFormat.ENCODING_DOLBY_MAT: return "Dolby MAT";
            case AudioFormat.ENCODING_AC4: return "Dolby AC-4";
            case AudioFormat.ENCODING_DTS: return "DTS";
            case AudioFormat.ENCODING_DTS_HD: return "DTS-HD";
            case AudioFormat.ENCODING_DTS_HD_MA: return "DTS-HD MA";
            case AudioFormat.ENCODING_DTS_UHD_P1: return "DTS:X";
            case AudioFormat.ENCODING_IEC61937: return "IEC 61937 passthrough";
            case AudioFormat.ENCODING_MP3: return "MP3";
            case AudioFormat.ENCODING_AAC_LC: return "AAC";
            case AudioFormat.ENCODING_OPUS: return "Opus";
            default: return "encoding " + encoding;
        }
    }

    static String output(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "TV speakers";
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            case AudioDeviceInfo.TYPE_HDMI_ARC: return "HDMI ARC";
            case AudioDeviceInfo.TYPE_HDMI_EARC: return "HDMI eARC";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL: return "Optical (S/PDIF)";
            case AudioDeviceInfo.TYPE_LINE_ANALOG: return "Analogue line out";
            case AudioDeviceInfo.TYPE_AUX_LINE: return "Aux line out";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "Headphone jack";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth";
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER: return "Bluetooth LE";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB audio";
            default: return "output type " + type;
        }
    }

    /** "KEYCODE_G (35), scan code 34, from Remote": a null device is one the event names no real hardware for. */
    static String key(String name, int code, int scan, String device) {
        return name + " (" + code + "), scan code " + scan + ", "
                + (device == null ? "virtual device: injected or sent by the system" : "from " + device);
    }
}
