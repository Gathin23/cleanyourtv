package com.gathin.artframe;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * One picture as an H.264 frame made only of I_PCM macroblocks: the raw 8-bit samples of each 16x16 block, with no
 * prediction or transform, so it needs no encoder and loses nothing but the 4:2:0 chroma subsampling. A TV's video
 * decoder shows it on the video layer, which on TVs whose apps run at 1080p is the only layer that reaches the
 * panel at 4K. BT.709 limited range, signalled in the VUI.
 *
 * Speed: after the slice header and the first macroblock every macroblock starts byte-aligned and is exactly
 * {0x0D, 0x00} (mb_type I_PCM and its alignment bits) plus 384 samples, so the rows are converted on all cores
 * straight into their place in the output. Samples are kept within 16..240, so no start code can appear among them
 * and emulation prevention only has to look at the header.
 */
final class PcmStill {
    private static final int MB_BYTES = 2 + 256 + 64 + 64;
    private static final ExecutorService POOL =
            Executors.newFixedThreadPool(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));

    /** Brightness and contrast, applied while converting, only inside the picture's rectangle. */
    static final class Adjust {
        final float contrast;
        final float lumaShift;
        final int x0, y0, x1, y1;

        /** brightness and contrast -50..50, as on the piece; the rectangle in pixels, right and bottom exclusive. */
        Adjust(int brightness, int contrast, int x0, int y0, int x1, int y1) {
            this.contrast = 1f + contrast / 100f;
            // RGB' = c*RGB + t, with t as in Images.filter; in limited-range luma that shift is t * 219/255.
            float t = brightness * 1.275f + 128f * (1f - this.contrast);
            this.lumaShift = t * 219f / 255f + 16f * (1f - this.contrast);
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        boolean none() {
            return contrast == 1f && lumaShift == 0f;
        }
    }

    private PcmStill() {}

    /** Annex B: SPS and PPS, each after a start code; the codec configuration. */
    static byte[] config(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        nal(out, 0x67, sps(width, height));
        nal(out, 0x68, pps());
        return out.toByteArray();
    }

    /** Annex B: one IDR slice holding the whole picture. argb is width*height pixels, row by row. */
    static byte[] frame(int[] argb, int width, int height, int idrId) {
        return frame(argb, width, height, idrId, null);
    }

    static byte[] frame(int[] argb, int width, int height, int idrId, Adjust adjust) {
        int mbW = width / 16;
        int mbH = height / 16;
        Adjust adj = adjust == null || adjust.none() ? null : adjust;
        // Slice header, then the first macroblock through the bit writer (the header leaves it unaligned).
        Bits b = new Bits(512);
        b.ue(0);            // first_mb_in_slice
        b.ue(7);            // slice_type I
        b.ue(0);            // pic_parameter_set_id
        b.u(4, 0);          // frame_num
        b.ue(idrId & 0xffff);
        b.u(1, 0);          // no_output_of_prior_pics_flag
        b.u(1, 0);          // long_term_reference_flag
        b.se(0);            // slice_qp_delta
        b.ue(1);            // disable_deblocking_filter_idc: off, the samples are exact
        byte[] y = new byte[256];
        byte[] cb = new byte[64];
        byte[] cr = new byte[64];
        block(argb, width, 0, 0, y, cb, cr, adj);
        b.ue(25);
        b.align();
        b.bytes(y);
        b.bytes(cb);
        b.bytes(cr);
        byte[] head = escape(b.toArray());

        int count = mbW * mbH;
        int total = 4 + 1 + head.length + (count - 1) * MB_BYTES + 1;
        byte[] out = new byte[total];
        out[3] = 1;
        out[4] = 0x65;      // IDR slice, nal_ref_idc 3
        System.arraycopy(head, 0, out, 5, head.length);
        int base = 5 + head.length;

        List<Future<?>> jobs = new ArrayList<>();
        int threads = 4;
        for (int t = 0; t < threads; t++) {
            int part = t;
            jobs.add(POOL.submit(() -> {
                byte[] ty = new byte[256];
                byte[] tcb = new byte[64];
                byte[] tcr = new byte[64];
                int[] sumB = new int[64];
                int[] sumR = new int[64];
                for (int my = part; my < mbH; my += threads) {
                    for (int mx = 0; mx < mbW; mx++) {
                        int k = my * mbW + mx;
                        if (k == 0) continue;
                        blockInto(argb, width, mx * 16, my * 16, ty, tcb, tcr, sumB, sumR, adj);
                        int at = base + (k - 1) * MB_BYTES;
                        out[at] = 0x0D;
                        out[at + 1] = 0;
                        System.arraycopy(ty, 0, out, at + 2, 256);
                        System.arraycopy(tcb, 0, out, at + 258, 64);
                        System.arraycopy(tcr, 0, out, at + 322, 64);
                    }
                }
            }));
        }
        for (Future<?> f : jobs) {
            try {
                f.get();
            } catch (Exception e) {
                throw new IllegalStateException("conversion failed", e);
            }
        }
        out[total - 1] = (byte) 0x80;   // rbsp_slice_trailing_bits: stop bit, already aligned
        return out;
    }

    /** One macroblock's samples: 16x16 luma, 8x8 of each chroma averaged over 2x2, within 16..235/240. */
    static void block(int[] argb, int width, int x0, int y0, byte[] y, byte[] cb, byte[] cr) {
        blockInto(argb, width, x0, y0, y, cb, cr, new int[64], new int[64], null);
    }

    static void block(int[] argb, int width, int x0, int y0, byte[] y, byte[] cb, byte[] cr, Adjust adj) {
        blockInto(argb, width, x0, y0, y, cb, cr, new int[64], new int[64], adj);
    }

    private static void blockInto(int[] argb, int width, int x0, int y0, byte[] y, byte[] cb, byte[] cr,
                                  int[] sumB, int[] sumR, Adjust adj) {
        java.util.Arrays.fill(sumB, 0);
        java.util.Arrays.fill(sumR, 0);
        boolean any = adj != null && x0 + 16 > adj.x0 && x0 < adj.x1 && y0 + 16 > adj.y0 && y0 < adj.y1;
        for (int dy = 0; dy < 16; dy++) {
            int row = (y0 + dy) * width + x0;
            for (int dx = 0; dx < 16; dx++) {
                int p = argb[row + dx];
                int r = (p >> 16) & 0xff;
                int g = (p >> 8) & 0xff;
                int bl = p & 0xff;
                // BT.709, limited range, 16.16 fixed point.
                int luma = (11966 * r + 40254 * g + 4064 * bl + (16 << 16) + 32768) >> 16;
                if (any && inside(adj, x0 + dx, y0 + dy)) {
                    luma = Math.round(adj.contrast * luma + adj.lumaShift);
                }
                y[dy * 16 + dx] = (byte) clamp(luma, 16, 235);
                int c = (dy >> 1) * 8 + (dx >> 1);
                sumB[c] += (-6596 * r - 22189 * g + 28785 * bl);
                sumR[c] += (28785 * r - 26145 * g - 2640 * bl);
            }
        }
        for (int c = 0; c < 64; c++) {
            int vb = ((sumB[c] >> 2) + 32768) >> 16;
            int vr = ((sumR[c] >> 2) + 32768) >> 16;
            if (any && inside(adj, x0 + (c & 7) * 2, y0 + (c >> 3) * 2)) {
                vb = Math.round(vb * adj.contrast);
                vr = Math.round(vr * adj.contrast);
            }
            cb[c] = (byte) clamp(vb + 128, 16, 240);
            cr[c] = (byte) clamp(vr + 128, 16, 240);
        }
    }

    private static boolean inside(Adjust a, int x, int y) {
        return x >= a.x0 && x < a.x1 && y >= a.y0 && y < a.y1;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(hi, v);
    }

    static byte[] sps(int width, int height) {
        Bits b = new Bits(64);
        b.u(8, 100);    // profile_idc: High, which every 4K decoder supports
        b.u(8, 0);      // constraint flags
        b.u(8, 51);     // level 5.1
        b.ue(0);        // seq_parameter_set_id
        b.ue(1);        // chroma_format_idc 4:2:0
        b.ue(0);        // bit_depth_luma_minus8
        b.ue(0);        // bit_depth_chroma_minus8
        b.u(1, 0);      // qpprime_y_zero_transform_bypass_flag
        b.u(1, 0);      // seq_scaling_matrix_present_flag
        b.ue(0);        // log2_max_frame_num_minus4
        b.ue(2);        // pic_order_cnt_type 2: output order is decode order, no delay
        b.ue(1);        // max_num_ref_frames
        b.u(1, 0);      // gaps_in_frame_num_value_allowed_flag
        b.ue(width / 16 - 1);
        b.ue(height / 16 - 1);
        b.u(1, 1);      // frame_mbs_only_flag
        b.u(1, 1);      // direct_8x8_inference_flag
        b.u(1, 0);      // frame_cropping_flag
        b.u(1, 1);      // vui_parameters_present_flag
        b.u(1, 0);      // aspect_ratio_info_present_flag
        b.u(1, 0);      // overscan_info_present_flag
        b.u(1, 1);      // video_signal_type_present_flag
        b.u(3, 5);      // video_format: unspecified
        b.u(1, 0);      // video_full_range_flag: limited
        b.u(1, 1);      // colour_description_present_flag
        b.u(8, 1);      // colour_primaries BT.709
        b.u(8, 1);      // transfer_characteristics BT.709
        b.u(8, 1);      // matrix_coefficients BT.709
        b.u(1, 0);      // chroma_loc_info_present_flag
        b.u(1, 0);      // timing_info_present_flag
        b.u(1, 0);      // nal_hrd_parameters_present_flag
        b.u(1, 0);      // vcl_hrd_parameters_present_flag
        b.u(1, 0);      // pic_struct_present_flag
        b.u(1, 1);      // bitstream_restriction_flag: no reordering, so the frame comes out at once
        b.u(1, 1);      // motion_vectors_over_pic_boundaries_flag
        b.ue(0);        // max_bytes_per_pic_denom
        b.ue(0);        // max_bits_per_mb_denom
        b.ue(15);       // log2_max_mv_length_horizontal
        b.ue(15);       // log2_max_mv_length_vertical
        b.ue(0);        // max_num_reorder_frames
        b.ue(1);        // max_dec_frame_buffering
        b.trailing();
        return b.toArray();
    }

    static byte[] pps() {
        Bits b = new Bits(16);
        b.ue(0);        // pic_parameter_set_id
        b.ue(0);        // seq_parameter_set_id
        b.u(1, 0);      // entropy_coding_mode_flag: CAVLC
        b.u(1, 0);      // bottom_field_pic_order_in_frame_present_flag
        b.ue(0);        // num_slice_groups_minus1
        b.ue(0);        // num_ref_idx_l0_default_active_minus1
        b.ue(0);        // num_ref_idx_l1_default_active_minus1
        b.u(1, 0);      // weighted_pred_flag
        b.u(2, 0);      // weighted_bipred_idc
        b.se(0);        // pic_init_qp_minus26
        b.se(0);        // pic_init_qs_minus26
        b.se(0);        // chroma_qp_index_offset
        b.u(1, 1);      // deblocking_filter_control_present_flag
        b.u(1, 0);      // constrained_intra_pred_flag
        b.u(1, 0);      // redundant_pic_cnt_present_flag
        b.trailing();
        return b.toArray();
    }

    /** Writes a NAL unit after a 4-byte start code, inserting emulation-prevention bytes. */
    static void nal(ByteArrayOutputStream out, int header, byte[] rbsp) {
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(1);
        out.write(header);
        byte[] e = escape(rbsp);
        out.write(e, 0, e.length);
    }

    /** Emulation prevention: a 0x03 after any two zero bytes that are followed by a byte of 0..3. */
    static byte[] escape(byte[] rbsp) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(rbsp.length + 16);
        int zeros = 0;
        for (byte x : rbsp) {
            int v = x & 0xff;
            if (zeros >= 2 && v <= 3) {
                out.write(3);
                zeros = 0;
            }
            out.write(v);
            zeros = v == 0 ? zeros + 1 : 0;
        }
        return out.toByteArray();
    }

    /** A big-endian bit writer for Exp-Golomb and fixed-width fields. */
    static final class Bits {
        private byte[] buf;
        private int pos;
        private int acc;
        private int nbits;

        Bits(int capacity) {
            buf = new byte[Math.max(16, capacity)];
        }

        void u(int n, int value) {
            for (int i = n - 1; i >= 0; i--) bit((value >>> i) & 1);
        }

        void ue(int value) {
            int v = value + 1;
            int len = 32 - Integer.numberOfLeadingZeros(v);
            u(len - 1, 0);
            u(len, v);
        }

        void se(int value) {
            ue(value <= 0 ? -2 * value : 2 * value - 1);
        }

        void bit(int b) {
            acc = (acc << 1) | b;
            if (++nbits == 8) {
                put((byte) acc);
                acc = 0;
                nbits = 0;
            }
        }

        void align() {
            while (nbits != 0) bit(0);
        }

        void bytes(byte[] data) {
            if (nbits != 0) throw new IllegalStateException("not aligned");
            ensure(data.length);
            System.arraycopy(data, 0, buf, pos, data.length);
            pos += data.length;
        }

        void trailing() {
            bit(1);
            align();
        }

        private void put(byte b) {
            ensure(1);
            buf[pos++] = b;
        }

        private void ensure(int n) {
            if (pos + n > buf.length) buf = java.util.Arrays.copyOf(buf, Math.max(buf.length * 2, pos + n));
        }

        int length() {
            return pos;
        }

        byte[] toArray() {
            return java.util.Arrays.copyOf(buf, pos);
        }
    }
}
