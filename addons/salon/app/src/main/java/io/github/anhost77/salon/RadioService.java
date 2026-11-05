package io.github.anhost77.salon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.IBinder;

/**
 * Plays one radio of {@link Radios} in the background, so it keeps going on Home and over other screens.
 * Started by the launcher's Radios tab: action PLAY with extra "index" (the same index again stops it), or STOP.
 * Stops by itself when another app takes the sound (a video, a programme).
 */
public final class RadioService extends Service {
    static final String PLAY = "io.github.anhost77.salon.RADIO_PLAY";
    static final String STOP = "io.github.anhost77.salon.RADIO_STOP";
    private static final String CHANNEL = "radio";
    private androidx.media3.exoplayer.ExoPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;

    static int playing(Context ctx) {
        return ctx.getSharedPreferences("radio", MODE_PRIVATE).getInt("playing", -1);
    }

    private void setPlaying(int index) {
        getSharedPreferences("radio", MODE_PRIVATE).edit().putInt("playing", index).apply();
        getContentResolver().notifyChange(Radios.URI, null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The launcher counts the rows it shows: map them back to the stations Réglages left on.
        int row = intent == null ? -1 : intent.getIntExtra("index", -1);
        int[] shown = Radios.shown();
        int index = row >= 0 && row < shown.length ? shown[row] : -1;
        boolean play = intent != null && PLAY.equals(intent.getAction()) && index >= 0
                && index < Radios.STATIONS.length && index != playing(this);
        foreground(play ? Radios.STATIONS[index][0] : "");
        release();
        if (!play) {
            setPlaying(-1);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        start(index);
        return START_NOT_STICKY;
    }

    private void start(int index) {
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS) stopAll();
                }).build();
        audio.requestAudioFocus(focus);
        player = new androidx.media3.exoplayer.ExoPlayer.Builder(this).build();
        player.addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                stopAll();
            }
        });
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(Radios.STATIONS[index][1]));
        player.setPlayWhenReady(true);
        player.prepare();
        setPlaying(index);
    }

    private void stopAll() {
        release();
        setPlaying(-1);
        stopForeground(true);
        stopSelf();
    }

    private void release() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
    }

    private void foreground(String name) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Radio", NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.icon)
                .setContentTitle(name.isEmpty() ? "Radio" : name).setContentText("En écoute").setOngoing(true).build();
        startForeground(1, n);
    }

    @Override
    public void onDestroy() {
        release();
        if (playing(this) >= 0) setPlaying(-1);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
