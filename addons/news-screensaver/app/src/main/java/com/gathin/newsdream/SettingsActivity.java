package com.gathin.newsdream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The settings, for the remote's D-pad: seconds per headline, and the feeds (OK on one removes it, the field
 * below adds one). Reached from the TV's screensaver settings.
 * When the screensaver starts is the TV's own setting (or setup.sh's IDLE_MINUTES).
 */
public class SettingsActivity extends Activity {

    private File dir;
    private Button secondsButton;
    private LinearLayout feedList;
    private EditText address;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dir = getFilesDir();
        int pad = dp(48);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad * 2, pad, pad * 2, pad);

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setPadding(0, 0, 0, dp(16));
        list.addView(title);

        secondsButton = new Button(this);
        secondsButton.setOnClickListener(v -> {
            int next = Config.nextSeconds(Config.seconds(dir));
            Config.write(new File(dir, Config.SECONDS), String.valueOf(next));
            updateSeconds();
        });
        list.addView(secondsButton, wrap(0));
        updateSeconds();

        list.addView(heading(R.string.feeds_heading));
        feedList = new LinearLayout(this);
        feedList.setOrientation(LinearLayout.VERTICAL);
        list.addView(feedList);

        address = new EditText(this);
        address.setHint(R.string.feed_hint);
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_DONE);
        address.setOnEditorActionListener((v, action, event) -> {
            add();
            return true;
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(8);
        list.addView(address, alp);

        Button add = new Button(this);
        add.setText(R.string.feed_add);
        add.setOnClickListener(v -> add());
        list.addView(add, wrap(0));

        TextView info = new TextView(this);
        info.setText(R.string.settings_info);
        info.setPadding(dp(8), dp(24), 0, dp(8));
        list.addView(info);

        Button preview = new Button(this);
        preview.setText(R.string.preview);
        preview.setOnClickListener(v -> startActivity(new Intent(this, NewsActivity.class)));
        list.addView(preview, wrap(dp(16)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        setContentView(scroll);
        secondsButton.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateFeeds();   // setup.sh may have changed them meanwhile
        updateSeconds();
    }

    private void updateSeconds() {
        secondsButton.setText(getString(R.string.seconds_per_headline, Config.seconds(dir)));
    }

    private void updateFeeds() {
        feedList.removeAllViews();
        List<String> feeds = Config.feeds(dir);
        if (feeds.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(R.string.no_feeds_body);
            none.setPadding(dp(8), dp(8), 0, dp(8));
            feedList.addView(none);
        }
        for (String url : feeds) {
            Button b = new Button(this);
            b.setText(url);
            b.setAllCaps(false);
            b.setSingleLine(true);
            b.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(R.string.feed_remove)
                    .setMessage(url)
                    .setPositiveButton(R.string.feed_remove_yes, (d, w) -> remove(url))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
            feedList.addView(b, wrap(0));
        }
    }

    private void add() {
        String text = address.getText().toString().trim();
        if (text.isEmpty()) return;
        // "Name | address" or just the address; https:// is added when it is left out.
        int bar = text.lastIndexOf('|');
        String head = bar < 0 ? "" : text.substring(0, bar + 1);
        String tail = text.substring(bar + 1).trim();
        if (!tail.contains("://")) tail = "https://" + tail;
        String feed = Config.feed(head + tail);
        List<String> feeds = new ArrayList<>(Config.feeds(dir));
        if (feed == null) {
            Toast.makeText(this, R.string.feed_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        for (int i = 0; i < feeds.size(); i++) {
            if (Config.url(feeds.get(i)).equals(Config.url(feed))) {
                feeds.set(i, feed);   // same address again: the new name wins, in the same place
                save(feeds);
                address.setText("");
                return;
            }
        }
        if (feeds.size() >= Config.MAX_FEEDS) {
            Toast.makeText(this, getString(R.string.feed_too_many, Config.MAX_FEEDS), Toast.LENGTH_LONG).show();
            return;
        }
        feeds.add(feed);
        save(feeds);
        address.setText("");
    }

    private void remove(String url) {
        List<String> feeds = new ArrayList<>(Config.feeds(dir));
        feeds.remove(url);
        save(feeds);
    }

    private void save(List<String> feeds) {
        Config.write(new File(dir, Config.FEEDS), Config.feedsText(feeds));
        News.clearCache(dir);
        updateFeeds();
    }

    private TextView heading(int text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setPadding(dp(8), dp(24), 0, dp(4));
        return t;
    }

    private LinearLayout.LayoutParams wrap(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = top;
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
