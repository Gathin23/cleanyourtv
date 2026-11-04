package io.github.cleanyourtv.topicphotos;

import android.service.dreams.DreamService;
import android.view.KeyEvent;

/**
 * The screensaver: the same photos. Interactive, so Left and Right change the photo and Up shows its details; Back
 * closes the details, and any other key wakes the TV.
 */
public class PhotoDream extends DreamService {

    private Carousel carousel;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);
        setFullscreen(true);
        carousel = new Carousel(this, "");
        setContentView(carousel);
        carousel.setPhotos(Photos.load(this));
        Photos.refresh(this, () -> {
            if (carousel != null) carousel.setPhotos(Photos.load(this));
        });
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        carousel.start();
    }

    @Override
    public void onDreamingStopped() {
        carousel.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (carousel != null) carousel.release();
        carousel = null;
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (carousel != null && carousel.handleKey(event)) return true;
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && carousel != null && carousel.infoShown()) {
            if (event.getAction() == KeyEvent.ACTION_UP) carousel.setInfo(false);
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }
}
