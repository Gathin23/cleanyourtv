package com.gathin.artframe;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;

/**
 * Shows still pictures at 4K through the TV's H.264 decoder: each picture is written as one I_PCM frame
 * ({@link PcmStill}) and decoded onto the surface, so it travels the video layer to the panel. Used on TVs whose
 * app layer is 1080p. Every call blocks; run it off the main thread.
 */
final class VideoStill {
    static final int W = 3840;
    static final int H = 2160;
    private static final long WAIT_US = 2_000_000;

    private final MediaCodec codec;
    private final byte[] config;
    private final int[] pixels = new int[W * H];
    private long pts;
    private int idr;
    private boolean configured;
    private boolean ended;

    /** True when a decoder says it handles H.264 at 3840x2160. */
    static boolean supported() {
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (!MediaFormat.MIMETYPE_VIDEO_AVC.equalsIgnoreCase(type)) continue;
                    MediaCodecInfo.VideoCapabilities v = info.getCapabilitiesForType(type).getVideoCapabilities();
                    if (v != null && v.isSizeSupported(W, H)) return true;
                }
            }
        } catch (RuntimeException e) {
            Log.w(Library.TAG, "codec list: " + e);
        }
        return false;
    }

    VideoStill(Surface surface) throws Exception {
        config = PcmStill.config(W, H);
        MediaFormat f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H);
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, W * H * 3 / 2 + (W / 16) * (H / 16) * 4 + 4096);
        f.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709);
        f.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED);
        f.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        f.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        // Without this the TV's decoder holds back up to 12 frames, and a still is a single frame.
        if (android.os.Build.VERSION.SDK_INT >= 30) f.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        try {
            codec.configure(f, surface, null, 0);
            codec.start();
        } catch (Exception e) {
            codec.release();
            throw e;
        }
    }

    /** Decodes the 3840x2160 picture onto the surface; throws when the decoder will not show it. */
    /**
     * Decodes the 3840x2160 picture onto the surface; throws when the decoder will not show it. The TV's video layer
     * shows a picture only once the one after it arrives, so each picture is sent twice: the second copy pushes the
     * first onto the screen at once instead of when the next change comes.
     */
    synchronized void show(Bitmap picture) throws Exception {
        setPicture(picture);
        show((PcmStill.Adjust) null);
    }

    /** Keeps the drawn picture's pixels, so brightness and contrast changes need not draw it again. */
    synchronized void setPicture(Bitmap picture) {
        picture.getPixels(pixels, 0, W, 0, 0, W, H);
    }

    /** Shows the kept picture with brightness and contrast applied inside the piece's rectangle. */
    synchronized void show(PcmStill.Adjust adjust) throws Exception {
        long start = System.nanoTime();
        byte[] frame = PcmStill.frame(pixels, W, H, idr++, adjust);
        long built = System.nanoTime();
        decode(frame);
        decode(frame);
        Log.i(Library.TAG, "4K picture: built in " + (built - start) / 1_000_000 + " ms, shown after "
                + (System.nanoTime() - start) / 1_000_000 + " ms");
    }

    private void decode(byte[] frame) {
        if (ended) {
            // The last picture was pushed out with end-of-stream: start a new stream.
            codec.flush();
            configured = false;
            ended = false;
        }
        if (!configured) {
            queue(config, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
            configured = true;
        }
        queue(frame, MediaCodec.BUFFER_FLAG_KEY_FRAME);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long start = System.nanoTime();
        long deadline = start + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            // A decoder that still holds the frame after a moment is told the stream has ended.
            if (!ended && System.nanoTime() - start > 300_000_000L) {
                queue(new byte[0], MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                ended = true;
            }
            int out = codec.dequeueOutputBuffer(info, 100_000);
            if (out >= 0) {
                boolean hasPicture = info.size > 0 || (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0;
                if (hasPicture) {
                    // Shown now, not at the frame's timestamp.
                    codec.releaseOutputBuffer(out, System.nanoTime());
                    return;
                }
                codec.releaseOutputBuffer(out, false);
                throw new IllegalStateException("the decoder ended the stream without a picture");
            }
        }
        throw new IllegalStateException("the decoder returned no picture");
    }


    private void queue(byte[] data, int flags) {
        int in = codec.dequeueInputBuffer(WAIT_US);
        if (in < 0) throw new IllegalStateException("no decoder input buffer");
        ByteBuffer b = codec.getInputBuffer(in);
        if (data.length > 0 && (b == null || b.capacity() < data.length)) {
            codec.queueInputBuffer(in, 0, 0, pts, 0);
            throw new IllegalStateException("decoder input buffer too small: "
                    + (b == null ? 0 : b.capacity()) + " < " + data.length);
        }
        if (b != null) {
            b.clear();
            b.put(data);
        }
        codec.queueInputBuffer(in, 0, data.length, pts, flags);
        pts += 1_000_000;
    }

    synchronized void release() {
        try {
            codec.stop();
        } catch (RuntimeException ignored) {
            // Releasing anyway.
        }
        codec.release();
    }
}
