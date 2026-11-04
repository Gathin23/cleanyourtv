package io.github.cleanyourtv.topicphotos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

/**
 * The app and the module card: the photos full screen. Left/Right change the photo, Up shows its details, OK opens
 * the settings, Back closes the details and then the app.
 */
public final class ViewerActivity extends Activity {

    private Carousel carousel;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        carousel = new Carousel(this, "    OK " + getString(R.string.settings_short));
        setContentView(carousel);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Settings may have changed the topics: show what is saved now, and fetch again if it no longer matches.
        carousel.setPhotos(Photos.load(this));
        Photos.refresh(this, () -> {
            if (!isDestroyed()) carousel.setPhotos(Photos.load(this));
        });
        carousel.start();
    }

    @Override
    protected void onPause() {
        carousel.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        carousel.release();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (carousel.handleKey(event)) return true;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (carousel.infoShown()) {
            carousel.setInfo(false);
            return;
        }
        finish();
    }
}
