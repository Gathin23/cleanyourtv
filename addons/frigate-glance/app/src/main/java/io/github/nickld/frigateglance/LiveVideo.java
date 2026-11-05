package io.github.nickld.frigateglance;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.TextureView;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Live picture for the card: go2rtc's RTSP stream through Media3, decoded by the TV's hardware decoder.
 * Muted, small buffers, and given up on after four seconds without a frame, so the card falls back to
 * refreshing snapshots instead of showing a frozen box. Main thread only.
 */
@OptIn(markerClass = UnstableApi.class)
final class LiveVideo {
    interface Listener {
        void onPlaying();

        void onGaveUp();
    }

    static final long GIVE_UP_MS = 4000;
    static final int RTSP_PORT = 8554;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private boolean playing;
    private long startedAt;
    private final Runnable giveUp = this::fail;

    LiveVideo(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    static String rtspUrl(String host, String user, String password, String stream) {
        String auth = user.isEmpty() ? "" : enc(user) + (password.isEmpty() ? "" : ":" + enc(password)) + "@";
        return "rtsp://" + auth + host + ":" + RTSP_PORT + "/" + enc(stream);
    }

    void start(String rtspUrl, TextureView surface) {
        release();
        playing = false;
        startedAt = android.os.SystemClock.elapsedRealtime();
        DefaultLoadControl load = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(1000, 3000, 500, 500)
                .build();
        player = new ExoPlayer.Builder(context).setLoadControl(load).build();
        // The card is silent: skip the camera's audio track entirely, which also spares the decoder its timestamps.
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build());
        player.setVideoTextureView(surface);
        player.addListener(new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
                if (playing) return;
                playing = true;
                main.removeCallbacks(giveUp);
                Log.i(ConfigStore.TAG, "live video playing after " + (android.os.SystemClock.elapsedRealtime() - startedAt) + " ms");
                listener.onPlaying();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.w(ConfigStore.TAG, "live video: " + error.getErrorCodeName());
                fail();
            }
        });
        player.setMediaSource(new RtspMediaSource.Factory().setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(rtspUrl)));
        player.setPlayWhenReady(true);
        player.prepare();
        main.postDelayed(giveUp, GIVE_UP_MS);
    }

    void release() {
        main.removeCallbacks(giveUp);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void fail() {
        boolean wasActive = player != null;
        if (wasActive) Log.i(ConfigStore.TAG, "live video gave up; refreshing snapshots");
        release();
        if (wasActive) listener.onGaveUp();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }
}
