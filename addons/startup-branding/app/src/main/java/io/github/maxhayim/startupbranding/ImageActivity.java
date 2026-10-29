package io.github.maxhayim.startupbranding;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The picture: which one, how it's scaled and where it sits, with a live 16:9 preview beside the rows. Pictures
 * come from the TV's file picker, where it has a real one, or from a computer with push-image.sh.
 */
public class ImageActivity extends Activity {
    private static final int PICK = 1;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Page page;
    private WelcomeView preview;
    private String shownImage;
    private Bitmap shownBitmap;
    private int previewW = 640, previewH = 360;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        page = new Page(this, "Custom Image", true);
        page.subtitle.setText("PNG, JPEG or WebP, up to 15 MB.");
        TextView label = new TextView(this);
        label.setText("Preview");
        label.setTextSize(14);
        label.setTextColor(0xFF8B949E);
        label.setPadding(0, 0, 0, page.px(8));
        page.side.addView(label);
        FrameLayout box = new FrameLayout(this);
        preview = new WelcomeView(this);
        box.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.side.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, page.px(1)));
        // 16:9 at the side panel's width, once it is known.
        box.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l, h = w * 9 / 16;
            if (w > 0 && v.getLayoutParams().height != h) {
                previewW = w;
                previewH = h;
                v.getLayoutParams().height = h;
                v.post(v::requestLayout);
            }
        });
        TextView hint = new TextView(this);
        hint.setText("From a computer: TV_HOST=<tv ip> addons/startup-branding/push-image.sh picture.png");
        hint.setTextSize(13);
        hint.setTextColor(0xFF8B949E);
        hint.setPadding(0, page.px(14), 0, 0);
        page.side.addView(hint);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build(page.focused());
    }

    private void build(int focus) {
        page.rows.removeAllViews();
        Look l = Prefs.load(this);
        List<String> pictures = Images.list(this);
        if (!l.image.isEmpty() && !pictures.contains(l.image)) {
            // The file went away: say None rather than a name that no longer shows.
            l.image = "";
            Prefs.save(this, l);
        }

        page.add("Picture", pictures.isEmpty() ? "None on this TV yet." : "OK picks the next one ("
                        + pictures.size() + (pictures.size() == 1 ? " picture)." : " pictures)."),
                pictures.isEmpty() ? null : () -> change(x -> {
                    int i = pictures.indexOf(x.image);
                    x.image = i + 1 < pictures.size() ? pictures.get(i + 1) : "";
                }))
                .setValue(l.image.isEmpty() ? "None" : l.image);
        Intent pick = Images.picker(this);
        page.add("Choose a File", pick != null ? "Opens this TV's file picker; the picture is copied in."
                        : "This TV has no file picker. Use a computer (see right), or a file manager app.",
                pick == null ? null : () -> {
                    try {
                        startActivityForResult(pick, PICK);
                    } catch (ActivityNotFoundException | SecurityException e) {
                        page.subtitle.setText("The file picker didn't open.");
                    }
                })
                .setValue(pick != null ? "" : "Unavailable");
        page.add("Scaling", scaleDetail(l.scale), () -> change(x -> x.scale = Look.next(Look.Scale.values(), x.scale)))
                .setValue(MainActivity.cap(l.scale.name()));
        page.add("Position", "Where the picture sits when it doesn't fill the screen.",
                () -> change(x -> x.align = Look.next(Look.Align.values(), x.align)))
                .setValue(MainActivity.cap(l.align.name()));
        page.add("Full-Screen Preview", "Back or OK closes it.",
                () -> startActivity(new Intent(this, WelcomeActivity.class).putExtra(WelcomeActivity.EXTRA_PREVIEW, true)));
        page.focus(focus);
        showPreview(l);
    }

    private static String scaleDetail(Look.Scale s) {
        switch (s) {
            case FILL: return "Fill: covers the screen, cropping the edges.";
            case STRETCH: return "Stretch: covers the screen, ignoring the picture's shape.";
            case ORIGINAL: return "Original: its own size on a 1080p screen, shrunk only if bigger.";
            default: return "Fit: all of the picture, as big as it fits.";
        }
    }

    private void showPreview(Look l) {
        if (l.image.equals(shownImage)) {
            preview.show(l, shownBitmap);
            return;
        }
        shownImage = l.image;
        shownBitmap = null;
        preview.show(l, null);
        if (l.image.isEmpty()) return;
        String name = l.image;
        int w = previewW, h = previewH;
        new Thread(() -> {
            Bitmap b = Images.load(this, name, w * 2, h * 2);
            main.post(() -> {
                if (name.equals(shownImage)) {
                    shownBitmap = b;
                    preview.show(Prefs.load(this), b);
                }
            });
        }, "preview-image").start();
    }

    private interface Change {
        void apply(Look l);
    }

    private void change(Change c) {
        Look l = Prefs.load(this);
        c.apply(l);
        Prefs.save(this, l);
        build(page.focused());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri uri = data == null ? null : data.getData();
        if (requestCode != PICK || resultCode != RESULT_OK || uri == null) return;
        String name = Images.copyIn(this, uri);
        if (name == null) {
            page.subtitle.setText("That file isn't a PNG, JPEG or WebP picture under 15 MB.");
            return;
        }
        change(x -> x.image = name);
    }
}
