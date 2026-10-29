package com.gathin.newsdream;

import java.nio.charset.StandardCharsets;

/**
 * A QR code for a short text (the headline's link): byte mode, error correction level M, versions 1 to 15 (up to
 * about 400 bytes). The smallest version that fits is used, and the mask with the lowest penalty. Follows
 * ISO/IEC 18004 the way Project Nayuki's MIT-licensed generator lays it out; no library, so no new dependency.
 */
final class Qr {
    static final int MAX_VERSION = 15;

    // Indexed by version; level M.
    private static final int[] ECC_PER_BLOCK = {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24};
    private static final int[] BLOCKS = {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10};
    private static final int FORMAT_M = 0;

    private final int size;
    private final boolean[][] dark;       // [y][x]
    private final boolean[][] function;   // finder, timing, alignment, format and version modules

    private Qr(int version) {
        size = version * 4 + 17;
        dark = new boolean[size][size];
        function = new boolean[size][size];
    }

    /** The modules, [y][x], true for dark; null when the text is too long for version 15. */
    static boolean[][] encode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (int version = 1; version <= MAX_VERSION; version++) {
            int capacityBits = dataCodewords(version) * 8;
            int countBits = version < 10 ? 8 : 16;
            if (4 + countBits + bytes.length * 8 <= capacityBits) {
                Qr qr = new Qr(version);
                qr.draw(version, data(bytes, countBits, capacityBits));
                return qr.dark;
            }
        }
        return null;
    }

    private static byte[] data(byte[] bytes, int countBits, int capacityBits) {
        Bits bits = new Bits(capacityBits);
        bits.add(0b0100, 4);                                 // byte mode
        bits.add(bytes.length, countBits);
        for (byte b : bytes) bits.add(b & 0xFF, 8);
        bits.add(0, Math.min(4, capacityBits - bits.length));  // terminator
        bits.add(0, (8 - bits.length % 8) % 8);
        for (int pad = 0xEC; bits.length < capacityBits; pad ^= 0xEC ^ 0x11) bits.add(pad, 8);
        return bits.bytes;
    }

    private void draw(int version, byte[] data) {
        drawFunctionPatterns(version);
        drawCodewords(withEcc(version, data));
        int best = 0;
        long bestPenalty = Long.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            applyMask(mask);
            drawFormat(mask);
            long p = penalty();
            if (p < bestPenalty) {
                best = mask;
                bestPenalty = p;
            }
            applyMask(mask);   // XOR again: undone
        }
        applyMask(best);
        drawFormat(best);
    }

    // ---- function patterns ----

    private void drawFunctionPatterns(int version) {
        for (int i = 0; i < size; i++) {
            set(6, i, i % 2 == 0);
            set(i, 6, i % 2 == 0);
        }
        finder(3, 3);
        finder(size - 4, 3);
        finder(3, size - 4);
        int[] align = alignmentPositions(version);
        int n = align.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == 0 && j == 0 || i == 0 && j == n - 1 || i == n - 1 && j == 0) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        set(align[i] + dx, align[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                    }
                }
            }
        }
        drawFormat(0);   // reserves the modules; drawn for real once the mask is chosen
        if (version >= 7) {
            int rem = version;
            for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
            int bits = version << 12 | rem;
            for (int i = 0; i < 18; i++) {
                boolean bit = (bits >>> i & 1) != 0;
                int a = size - 11 + i % 3;
                int b = i / 3;
                set(a, b, bit);
                set(b, a, bit);
            }
        }
    }

    private void finder(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = x + dx;
                int yy = y + dy;
                if (xx >= 0 && xx < size && yy >= 0 && yy < size) set(xx, yy, dist != 2 && dist != 4);
            }
        }
    }

    private static int[] alignmentPositions(int version) {
        if (version == 1) return new int[0];
        int n = version / 7 + 2;
        int step = (version * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
        int[] result = new int[n];
        result[0] = 6;
        for (int i = n - 1, pos = version * 4 + 10; i >= 1; i--, pos -= step) result[i] = pos;
        return result;
    }

    private void drawFormat(int mask) {
        int data = FORMAT_M << 3 | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6));
        set(8, 8, bit(bits, 7));
        set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, size - 15 + i, bit(bits, i));
        set(8, size - 8, true);   // always dark
    }

    private void set(int x, int y, boolean isDark) {
        dark[y][x] = isDark;
        function[y][x] = true;
    }

    private static boolean bit(int value, int i) {
        return (value >>> i & 1) != 0;
    }

    // ---- data ----

    static int rawDataModules(int version) {
        int result = (16 * version + 128) * version + 64;
        if (version >= 2) {
            int n = version / 7 + 2;
            result -= (25 * n - 10) * n - 55;
            if (version >= 7) result -= 36;
        }
        return result;
    }

    static int dataCodewords(int version) {
        return rawDataModules(version) / 8 - ECC_PER_BLOCK[version] * BLOCKS[version];
    }

    private static byte[] withEcc(int version, byte[] data) {
        int blocks = BLOCKS[version];
        int eccLen = ECC_PER_BLOCK[version];
        int raw = rawDataModules(version) / 8;
        int shortBlocks = blocks - raw % blocks;
        int shortLen = raw / blocks;
        byte[] divisor = rsDivisor(eccLen);
        byte[][] all = new byte[blocks][];
        for (int i = 0, k = 0; i < blocks; i++) {
            int datLen = shortLen - eccLen + (i < shortBlocks ? 0 : 1);
            byte[] dat = new byte[datLen];
            System.arraycopy(data, k, dat, 0, datLen);
            k += datLen;
            byte[] block = new byte[shortLen + 1];
            System.arraycopy(dat, 0, block, 0, datLen);
            byte[] ecc = rsRemainder(dat, divisor);
            System.arraycopy(ecc, 0, block, block.length - eccLen, eccLen);
            all[i] = block;
        }
        byte[] result = new byte[raw];
        int n = 0;
        for (int i = 0; i < shortLen + 1; i++) {
            for (int j = 0; j < blocks; j++) {
                // Short blocks have a gap where the long ones carry one more data byte.
                if (i != shortLen - eccLen || j >= shortBlocks) result[n++] = all[j][i];
            }
        }
        return result;
    }

    private static byte[] rsDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) mul(result[j] & 0xFF, root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = mul(root, 0x02);
        }
        return result;
    }

    private static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= (byte) mul(divisor[i] & 0xFF, factor);
        }
        return result;
    }

    /** Multiplication in GF(2^8) modulo x^8 + x^4 + x^3 + x^2 + 1. */
    private static int mul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xFF;
    }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;   // skip the vertical timing column
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!function[y][x] && i < data.length * 8) {
                        dark[y][x] = ((data[i >>> 3] >>> (7 - (i & 7))) & 1) != 0;
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert;
                switch (mask) {
                    case 0: invert = (x + y) % 2 == 0; break;
                    case 1: invert = y % 2 == 0; break;
                    case 2: invert = x % 3 == 0; break;
                    case 3: invert = (x + y) % 3 == 0; break;
                    case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                if (invert && !function[y][x]) dark[y][x] = !dark[y][x];
            }
        }
    }

    /** The spec's rules 1, 2 and 4: long runs, 2x2 blocks and the dark/light balance. Lower is easier to read. */
    private long penalty() {
        long p = 0;
        for (int a = 0; a < size; a++) {
            int rowRun = 1;
            int colRun = 1;
            for (int b = 1; b < size; b++) {
                if (dark[a][b] == dark[a][b - 1]) {
                    if (++rowRun == 5) p += 3; else if (rowRun > 5) p++;
                } else rowRun = 1;
                if (dark[b][a] == dark[b - 1][a]) {
                    if (++colRun == 5) p += 3; else if (colRun > 5) p++;
                } else colRun = 1;
            }
        }
        int darkCount = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (dark[y][x]) darkCount++;
                if (x + 1 < size && y + 1 < size && dark[y][x] == dark[y][x + 1]
                        && dark[y][x] == dark[y + 1][x] && dark[y][x] == dark[y + 1][x + 1]) p += 3;
            }
        }
        int total = size * size;
        int k = (Math.abs(darkCount * 20 - total * 10) + total - 1) / total - 1;
        return p + Math.max(k, 0) * 10L;
    }

    private static final class Bits {
        final byte[] bytes;
        int length;

        Bits(int capacityBits) {
            bytes = new byte[capacityBits / 8];
        }

        void add(int value, int count) {
            for (int i = count - 1; i >= 0; i--, length++) {
                if ((value >>> i & 1) != 0) bytes[length >>> 3] |= (byte) (0x80 >>> (length & 7));
            }
        }
    }
}
