package com.gathin.geogenius;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.speech.tts.TextToSpeech;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Zero-asset synthesized arcade sound effects (ascending streak marimba chimes,
 * gentle wrong thud, High-Score trumpet fanfare) + offline Android TextToSpeech
 * voice reader for younger readers.
 */
final class GeoSoundEngine {

    private final Context context;
    private final ExecutorService audioExec = Executors.newSingleThreadExecutor();
    private TextToSpeech tts;
    private boolean ttsReady = false;

    GeoSoundEngine(Context context) {
        this.context = context;
        try {
            tts = new TextToSpeech(context.getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    int res = tts.setLanguage(Locale.US);
                    ttsReady = (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED);
                    tts.setSpeechRate(0.95f);
                }
            });
        } catch (Throwable ignored) {
            ttsReady = false;
        }
    }

    void speak(String text) {
        if (!GeoStore.isSoundEnabled(context) || !ttsReady || tts == null || text == null) return;
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "geo_utt");
        } catch (Throwable ignored) {
        }
    }

    void playCorrect(int streak) {
        if (!GeoStore.isSoundEnabled(context)) return;
        // Ascending marimba chime based on streak
        final double baseFreq = 523.25 * Math.pow(1.06, Math.min(8, Math.max(0, streak - 1)));
        audioExec.execute(() -> {
            playTonePair(baseFreq, baseFreq * 1.25, 90, 120);
        });
    }

    void playWrong() {
        if (!GeoStore.isSoundEnabled(context)) return;
        audioExec.execute(() -> {
            playTonePair(220.0, 174.6, 110, 140);
        });
    }

    void playHighScoreFanfare() {
        if (!GeoStore.isSoundEnabled(context)) return;
        audioExec.execute(() -> {
            playTonePair(523.25, 659.25, 90, 90);
            playTonePair(783.99, 1046.50, 100, 220);
        });
    }

    void playPop() {
        if (!GeoStore.isSoundEnabled(context)) return;
        audioExec.execute(() -> {
            playTonePair(440.0, 587.33, 45, 55);
        });
    }

    private void playTonePair(double f1, double f2, int ms1, int ms2) {
        try {
            int sr = 22050;
            int n1 = (sr * ms1) / 1000;
            int n2 = (sr * ms2) / 1000;
            short[] pcm = new short[n1 + n2];
            for (int i = 0; i < n1; i++) {
                double env = Math.sin(Math.PI * i / (double) n1);
                pcm[i] = (short) (Math.sin(2.0 * Math.PI * f1 * i / sr) * 11000 * env);
            }
            for (int i = 0; i < n2; i++) {
                double env = Math.pow(1.0 - (i / (double) n2), 1.4);
                pcm[n1 + i] = (short) (Math.sin(2.0 * Math.PI * f2 * i / sr) * 12000 * env);
            }
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            track.write(pcm, 0, pcm.length);
            track.play();
            Thread.sleep(ms1 + ms2 + 20L);
            track.release();
        } catch (Throwable ignored) {
        }
    }

    void shutdown() {
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
            audioExec.shutdownNow();
        } catch (Throwable ignored) {
        }
    }
}

