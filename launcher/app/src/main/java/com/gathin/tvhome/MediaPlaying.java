package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;

import java.util.List;

/**
 * Whether music or video is actually started. This TV does not tell a normal app which package
 * owns the player, but the player is listed only while it is playing and drops out as soon as it
 * pauses. The Off row is shown from that.
 */
final class MediaPlaying {
    private MediaPlaying() {}

    static boolean started(Context ctx) {
        AudioManager audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        List<AudioPlaybackConfiguration> configs = audio.getActivePlaybackConfigurations();
        if (configs == null) return false;
        for (AudioPlaybackConfiguration c : configs) {
            if (playerState(c) != 2) continue; // PLAYER_STATE_STARTED
            AudioAttributes a = c.getAudioAttributes();
            if (a != null && a.getUsage() == AudioAttributes.USAGE_MEDIA) return true;
        }
        return false;
    }

    private static int playerState(AudioPlaybackConfiguration c) {
        try {
            return (Integer) AudioPlaybackConfiguration.class.getMethod("getPlayerState").invoke(c);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }
}
