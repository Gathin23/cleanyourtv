package com.gathin.tvhome;

import android.media.MediaPlayer;
import android.net.Uri;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.VideoView;

import java.io.File;

/**
 * Screensaver: loops a muted video pushed to the launcher's external files folder
 * (/sdcard/Android/data/com.gathin.tvhome/files/screensaver.webm or .mp4).
 * Falls back to black if no video is there.
 */
public class VideoDream extends DreamService {

    private VideoView video;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        video = new VideoView(this);
        root.addView(video, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        File file = findVideo();
        if (file == null) return;
        video.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            video.start();
        });
        video.setOnErrorListener((mp, what, extra) -> true);
        video.setVideoURI(Uri.fromFile(file));
    }

    @Override
    public void onDreamingStopped() {
        video.stopPlayback();
        super.onDreamingStopped();
    }

    private File findVideo() {
        File dir = getExternalFilesDir(null);
        if (dir == null) return null;
        for (String name : new String[]{"screensaver.webm", "screensaver.mp4", "screensaver.mkv"}) {
            File f = new File(dir, name);
            if (f.isFile() && f.length() > 0) return f;
        }
        return null;
    }
}
