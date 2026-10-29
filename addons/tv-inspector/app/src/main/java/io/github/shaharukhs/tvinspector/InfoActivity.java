package io.github.shaharukhs.tvinspector;

import android.app.ActivityManager;
import android.hardware.display.DisplayManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** What the TV reports about itself, its screen, its decoders, its audio outputs and its input devices. */
public final class InfoActivity extends Screen {
    private static final String[][] VIDEO = {
            {"video/av01", "AV1"}, {"video/x-vnd.on2.vp9", "VP9"}, {"video/hevc", "HEVC (H.265)"},
            {"video/avc", "H.264"}, {"video/dolby-vision", "Dolby Vision"}, {"video/mpeg2", "MPEG-2"}};
    private static final String[][] AUDIO = {
            {"audio/ac3", "Dolby Digital"}, {"audio/eac3", "Dolby Digital Plus"}, {"audio/eac3-joc", "Dolby Atmos (DD+ JOC)"},
            {"audio/ac4", "Dolby AC-4"}, {"audio/true-hd", "Dolby TrueHD"}, {"audio/vnd.dts", "DTS"},
            {"audio/vnd.dts.hd", "DTS-HD"}};
    private TextView loading;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        layout();
        text(getString(R.string.info), 30);
        loading = text(getString(R.string.loading), 20);
        // The codec list can take a second on a slow TV, so it is read off the main thread, once.
        new Thread(() -> {
            String[][] sections = {
                    {getString(R.string.section_tv), device()},
                    {getString(R.string.section_screen), screen()},
                    {getString(R.string.section_video), String.join("\n", decoders())},
                    {getString(R.string.section_audio), audio()},
                    {getString(R.string.section_inputs), inputs()}};
            for (String[] s : sections) for (String line : s[1].split("\n")) Log.i("TvInspector", s[0] + ": " + line);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                column.removeView(loading);
                TextView first = null;
                for (String[] s : sections) {
                    TextView view = section(s[0], s[1]);
                    if (first == null) first = view;
                }
                text(getString(R.string.logcat), 16);
                button(getString(R.string.back), this::finish);
                first.requestFocus();
            });
        }, "tv-inspector").start();
    }

    private String device() {
        StringBuilder b = new StringBuilder();
        b.append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("\nDevice ").append(Build.DEVICE).append(", board ").append(Build.BOARD)
                .append(", hardware ").append(Build.HARDWARE);
        if (Build.VERSION.SDK_INT >= 31) b.append("\nSoC ").append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL);
        b.append("\nAndroid ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(')')
                .append("\nFirmware ").append(Build.DISPLAY)
                .append("\nSecurity patch ").append(Build.VERSION.SECURITY_PATCH);
        ActivityManager am = getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(memory);
        b.append("\nRAM ").append(memory.totalMem / (1024 * 1024)).append(" MB");
        if (am.isLowRamDevice()) b.append(", low-RAM device");
        return b.toString();
    }

    @SuppressWarnings("deprecation")
    private String screen() {
        Display display = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        Display.Mode now = display.getMode();
        StringBuilder b = new StringBuilder("Now ").append(now.getPhysicalWidth()).append(" × ").append(now.getPhysicalHeight())
                .append(" at ").append(Labels.refresh(now.getRefreshRate())).append(" Hz");
        Display.Mode[] modes = display.getSupportedModes();
        int[] widths = new int[modes.length], heights = new int[modes.length];
        float[] rates = new float[modes.length];
        for (int i = 0; i < modes.length; i++) {
            widths[i] = modes[i].getPhysicalWidth(); heights[i] = modes[i].getPhysicalHeight(); rates[i] = modes[i].getRefreshRate();
        }
        for (String line : Labels.modes(widths, heights, rates)) b.append('\n').append(line);
        Display.HdrCapabilities hdr = display.getHdrCapabilities();
        List<String> types = new ArrayList<>();
        if (hdr != null) for (int type : hdr.getSupportedHdrTypes()) types.add(Labels.hdr(type));
        b.append("\nHDR: ").append(types.isEmpty() ? "none reported" : String.join(", ", types));
        return b.toString();
    }

    private List<String> decoders() {
        Map<String, int[]> best = new LinkedHashMap<>(); // mime -> {hardware rank, software rank}
        for (String[] v : VIDEO) best.put(v[0], new int[]{Labels.MISSING, Labels.MISSING});
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder()) continue;
            boolean hardware = Build.VERSION.SDK_INT >= 29 ? info.isHardwareAccelerated() : !Labels.softwareCodec(info.getName());
            for (String type : info.getSupportedTypes()) {
                int[] ranks = best.get(type.toLowerCase(Locale.ROOT));
                if (ranks == null) continue;
                int rank = Labels.SIZES.length;
                try {
                    MediaCodecInfo.VideoCapabilities caps = info.getCapabilitiesForType(type).getVideoCapabilities();
                    for (int i = 0; i < Labels.SIZES.length && caps != null; i++) {
                        if (caps.isSizeSupported(Labels.SIZES[i][0], Labels.SIZES[i][1])) { rank = i; break; }
                    }
                } catch (IllegalArgumentException ignored) { }
                int slot = hardware ? 0 : 1;
                ranks[slot] = Labels.better(ranks[slot], rank);
            }
        }
        List<String> lines = new ArrayList<>();
        for (String[] v : VIDEO) { int[] r = best.get(v[0]); lines.add(Labels.decoder(v[1], r[0], r[1])); }
        return lines;
    }

    private String audio() {
        StringBuilder b = new StringBuilder();
        for (AudioDeviceInfo out : getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            Set<String> formats = new LinkedHashSet<>();
            for (int encoding : out.getEncodings()) formats.add(Labels.encoding(encoding));
            // Product names are left out: a Bluetooth output carries whatever its owner named it.
            if (b.length() > 0) b.append('\n');
            b.append(Labels.output(out.getType())).append(": ")
                    .append(formats.isEmpty() ? "formats not reported" : String.join(", ", formats));
        }
        Set<String> found = new LinkedHashSet<>();
        Set<String> wanted = new LinkedHashSet<>();
        for (String[] a : AUDIO) wanted.add(a[0]);
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) if (wanted.contains(type.toLowerCase(Locale.ROOT))) found.add(type.toLowerCase(Locale.ROOT));
        }
        List<String> names = new ArrayList<>();
        for (String[] a : AUDIO) if (found.contains(a[0])) names.add(a[1]);
        if (b.length() > 0) b.append('\n');
        b.append("Decoders on the TV: ").append(names.isEmpty() ? "no Dolby or DTS decoder" : String.join(", ", names));
        return b.toString();
    }

    private String inputs() {
        List<String> lines = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || d.isVirtual()) continue;
            List<String> kinds = new ArrayList<>();
            if (d.supportsSource(InputDevice.SOURCE_DPAD)) kinds.add("D-pad");
            if (d.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)) kinds.add("touch");
            else if (d.supportsSource(InputDevice.SOURCE_KEYBOARD)) kinds.add("keys");
            if (d.supportsSource(InputDevice.SOURCE_MOUSE)) kinds.add("pointer");
            if (d.supportsSource(InputDevice.SOURCE_JOYSTICK)) kinds.add("gamepad");
            lines.add(d.getName() + (kinds.isEmpty() ? "" : " (" + String.join(", ", kinds) + ")"));
        }
        return lines.isEmpty() ? "none" : String.join("\n", lines);
    }
}
