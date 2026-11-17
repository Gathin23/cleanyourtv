package io.github.kunalsharma.tvremote;

/**
 * Bluetooth HID reports for a TV remote: one keyboard (report 1) and one consumer-control key (report 2).
 *
 * <p>The TV sees the phone as an ordinary Bluetooth keyboard. Arrow keys and Enter move and select in any
 * Android TV screen; the consumer keys (Home, Back, volume, play/pause) are the ones a remote has. No part of
 * this class touches Android, so it is tested on the JVM.
 */
final class HidKeys {
    private HidKeys() {}

    static final int REPORT_KEYBOARD = 1;
    static final int REPORT_CONSUMER = 2;

    // Keyboard usages (HID usage page 0x07).
    static final int KEY_ENTER = 0x28;
    static final int KEY_BACKSPACE = 0x2A;
    static final int KEY_SPACE = 0x2C;
    static final int KEY_RIGHT = 0x4F;
    static final int KEY_LEFT = 0x50;
    static final int KEY_DOWN = 0x51;
    static final int KEY_UP = 0x52;

    static final int MOD_SHIFT = 0x02;

    // Consumer usages (HID usage page 0x0C).
    static final int CONSUMER_PLAY_PAUSE = 0xCD;
    static final int CONSUMER_MUTE = 0xE2;
    static final int CONSUMER_VOLUME_UP = 0xE9;
    static final int CONSUMER_VOLUME_DOWN = 0xEA;
    static final int CONSUMER_HOME = 0x0223;
    static final int CONSUMER_BACK = 0x0224;

    /** The HID report descriptor: boot-style keyboard with LEDs (id 1), 16-bit consumer control (id 2). */
    static final byte[] DESCRIPTOR = bytes(
            // Keyboard
            0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, REPORT_KEYBOARD,
            0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
            0x95, 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91, 0x02,
            0x95, 0x01, 0x75, 0x03, 0x91, 0x01,
            0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
            0xC0,
            // Consumer control
            0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x85, REPORT_CONSUMER,
            0x15, 0x00, 0x26, 0xFF, 0x03, 0x19, 0x00, 0x2A, 0xFF, 0x03, 0x75, 0x10, 0x95, 0x01, 0x81, 0x00,
            0xC0);

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    /** Keyboard report body (without the report id): modifiers, reserved, six key slots. 0 releases. */
    static byte[] keyboardReport(int modifiers, int key) {
        byte[] report = new byte[8];
        report[0] = (byte) modifiers;
        report[2] = (byte) key;
        return report;
    }

    /** Consumer report body: the 16-bit usage, little endian. 0 releases. */
    static byte[] consumerReport(int usage) {
        return new byte[] {(byte) (usage & 0xFF), (byte) ((usage >> 8) & 0xFF)};
    }

    /**
     * The usage and modifiers that type {@code c} on a US keyboard, as {modifiers, key}, or null when the
     * character is not supported.
     */
    static int[] forChar(char c) {
        if (c >= 'a' && c <= 'z') return new int[] {0, 0x04 + (c - 'a')};
        if (c >= 'A' && c <= 'Z') return new int[] {MOD_SHIFT, 0x04 + (c - 'A')};
        if (c >= '1' && c <= '9') return new int[] {0, 0x1E + (c - '1')};
        switch (c) {
            case '0': return new int[] {0, 0x27};
            case ' ': return new int[] {0, KEY_SPACE};
            case '\n': return new int[] {0, KEY_ENTER};
            case '.': return new int[] {0, 0x37};
            case ',': return new int[] {0, 0x36};
            case '-': return new int[] {0, 0x2D};
            case '_': return new int[] {MOD_SHIFT, 0x2D};
            case '=': return new int[] {0, 0x2E};
            case '+': return new int[] {MOD_SHIFT, 0x2E};
            case '/': return new int[] {0, 0x38};
            case '?': return new int[] {MOD_SHIFT, 0x38};
            case ';': return new int[] {0, 0x33};
            case ':': return new int[] {MOD_SHIFT, 0x33};
            case '\'': return new int[] {0, 0x34};
            case '!': return new int[] {MOD_SHIFT, 0x1E};
            case '@': return new int[] {MOD_SHIFT, 0x1F};
            case '#': return new int[] {MOD_SHIFT, 0x20};
            default: return null;
        }
    }
}
