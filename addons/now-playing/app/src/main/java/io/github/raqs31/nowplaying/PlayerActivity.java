package io.github.raqs31.nowplaying;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.session.MediaController;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/** The card's screen: what is playing, the controls, and the switch for the card on Home. */
public class PlayerActivity extends Activity implements SessionTracker.Listener {
    private final Handler main = new Handler(Looper.getMainLooper());
    private SessionTracker tracker;
    private boolean noAccess;

    private View player;
    private ImageView art;
    private TextView app;
    private TextView title;
    private TextView artist;
    private TextView album;
    private ProgressBar progress;
    private TextView time;
    private Button playPause;
    private TextView message;
    private Button accessButton;
    private Button cardToggle;
    private TextView cardStatus;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            showProgress();
            main.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(build());
        tracker = new SessionTracker(this, this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        noAccess = !tracker.start();
        if (noAccess) onChanged(null);
        showCardSetting();
    }

    @Override
    protected void onStop() {
        tracker.stop();
        main.removeCallbacks(tick);
        super.onStop();
    }

    @Override
    public void onChanged(MediaController c) {
        main.removeCallbacks(tick);
        boolean playing = c != null;
        player.setVisibility(playing ? View.VISIBLE : View.GONE);
        message.setVisibility(playing ? View.GONE : View.VISIBLE);
        accessButton.setVisibility(noAccess ? View.VISIBLE : View.GONE);
        message.setText(noAccess ? R.string.no_access : R.string.nothing_playing);
        if (!playing) {
            (noAccess ? accessButton : cardToggle).requestFocus();
            return;
        }
        Bitmap cover = SessionTracker.art(c);
        if (cover != null) art.setImageBitmap(cover);
        else art.setImageDrawable(SessionTracker.appIcon(this, c));
        app.setText(SessionTracker.appName(this, c));
        title.setText(SessionTracker.title(c));
        artist.setText(SessionTracker.artist(c));
        album.setText(SessionTracker.album(c));
        boolean active = SessionPicker.active(SessionTracker.state(c));
        playPause.setText(active ? R.string.pause : R.string.play);
        if (!player.hasFocus()) playPause.requestFocus();
        showProgress();
        if (active) main.postDelayed(tick, 1000);
    }

    private void showProgress() {
        MediaController c = tracker.current();
        if (c == null) return;
        long duration = SessionTracker.duration(c);
        long position = SessionTracker.position(c, SystemClock.elapsedRealtime());
        boolean known = duration > 0 && position >= 0;
        progress.setVisibility(known ? View.VISIBLE : View.INVISIBLE);
        time.setVisibility(known ? View.VISIBLE : View.INVISIBLE);
        if (!known) return;
        progress.setMax((int) (duration / 1000));
        progress.setProgress((int) (position / 1000));
        time.setText(Progress.format(position) + " / " + Progress.format(duration));
    }

    private void showCardSetting() {
        boolean on = CardService.cardOn(this);
        cardToggle.setText(on ? R.string.card_on : R.string.card_off);
        boolean helperOff = on && !CardService.isRunning();
        cardStatus.setVisibility(helperOff ? View.VISIBLE : View.GONE);
    }

    private void control(int action) {
        MediaController c = tracker.current();
        if (c != null) SessionTracker.act(c, action);
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101418);
        root.setPadding(px(64), px(48), px(64), px(48));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        top.addView(art, new LinearLayout.LayoutParams(px(280), px(280)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(px(40), 0, 0, 0);
        app = text(16, 0xFF8A939C, Typeface.NORMAL);
        title = text(34, Color.WHITE, Typeface.BOLD);
        artist = text(22, 0xFFB8C0C8, Typeface.NORMAL);
        album = text(16, 0xFF8A939C, Typeface.NORMAL);
        info.addView(app);
        info.addView(title);
        info.addView(artist);
        info.addView(album);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(8));
        bar.topMargin = px(24);
        info.addView(progress, bar);
        time = text(14, 0xFF8A939C, Typeface.NORMAL);
        info.addView(time);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, px(32), 0, 0);
        buttons.addView(button(R.string.prev, v -> control(KeyRules.PREV)));
        playPause = button(R.string.pause, v -> control(KeyRules.TOGGLE));
        buttons.addView(playPause);
        buttons.addView(button(R.string.next, v -> control(KeyRules.NEXT)));
        buttons.addView(button(R.string.stop, v -> control(KeyRules.STOP)));
        buttons.addView(button(R.string.open_app, v -> {
            MediaController c = tracker.current();
            if (c != null && !SessionTracker.openApp(this, c)) {
                Toast.makeText(this, SessionTracker.appName(this, c), Toast.LENGTH_SHORT).show();
            }
        }));
        top.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout playerBox = new LinearLayout(this);
        playerBox.setOrientation(LinearLayout.VERTICAL);
        playerBox.addView(top);
        playerBox.addView(buttons);
        player = playerBox;
        root.addView(playerBox);

        message = text(22, Color.WHITE, Typeface.NORMAL);
        message.setSingleLine(false);
        root.addView(message);
        accessButton = button(R.string.open_access_settings, v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (ActivityNotFoundException missing) {
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show();
            }
        });
        root.addView(accessButton, wrap());

        cardToggle = button(R.string.card_off, v -> {
            CardService.setCardOn(this, !CardService.cardOn(this));
            showCardSetting();
        });
        LinearLayout.LayoutParams toggle = wrap();
        toggle.topMargin = px(40);
        root.addView(cardToggle, toggle);
        cardStatus = text(14, 0xFFE8A33D, Typeface.NORMAL);
        cardStatus.setText(R.string.helper_off);
        cardStatus.setSingleLine(false);
        root.addView(cardStatus);
        return root;
    }

    private TextView text(int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private Button button(int label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(getColorStateList(R.color.button_text));
        b.setBackgroundResource(R.drawable.button_background);
        b.setPadding(px(20), px(8), px(20), px(8));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = wrap();
        lp.rightMargin = px(12);
        b.setLayoutParams(lp);
        return b;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int px(int dps) {
        return Math.round(dps * getResources().getDisplayMetrics().density);
    }
}
