package io.github.cleanyourtv.topicphotos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The photos full screen, changing on their own: Left and Right go back and forward, Up shows the photo's details
 * (description, place, author and licence, date, size), Down hides them. The credit stays in a corner, as the
 * licences ask. Shared by the viewer and the screensaver.
 */
final class Carousel extends FrameLayout {

    private final ImageView[] layers = new ImageView[2];
    private int front;
    private final TextView credit, message, infoTitle, infoBody;
    private final LinearLayout info;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final boolean still;
    private final String hint;
    private List<Photos.Photo> photos = new ArrayList<>();
    private int index = -1, generation;
    private boolean running;
    /** Released by its owner: nothing more is decoded, looked up or painted. */
    private boolean released;
    private final Runnable tick = () -> show(next(1), true);

    Carousel(Context c, String hint) {
        super(c);
        this.hint = hint;
        setBackgroundColor(0xFF000000);
        // Animations off in the TV's developer options: photos swap without a fade.
        still = android.provider.Settings.Global.getFloat(c.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 2; i++) {
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            v.setAlpha(0f);
            layers[i] = v;
            addView(v, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0x99000000, 0x00000000}));
        addView(shade, new LayoutParams(LayoutParams.MATCH_PARENT, (int) (220 * d), Gravity.BOTTOM));

        message = text(c, 18, 0xCCFFFFFF);
        message.setText(R.string.finding);
        addView(message, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        credit = text(c, 13, 0xB3FFFFFF);
        credit.setSingleLine(true);
        credit.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams cp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        cp.setMargins((int) (48 * d), 0, (int) (48 * d), (int) (32 * d));
        addView(credit, cp);

        info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setBackgroundResource(R.drawable.panel);
        info.setPadding((int) (28 * d), (int) (22 * d), (int) (28 * d), (int) (24 * d));
        info.setVisibility(GONE);
        infoTitle = text(c, 20, 0xFFFFFFFF);
        infoTitle.setMaxLines(3);
        infoTitle.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(infoTitle);
        infoBody = text(c, 15, 0xD9FFFFFF);
        infoBody.setLineSpacing(6 * d, 1f);
        infoBody.setPadding(0, (int) (12 * d), 0, 0);
        info.addView(infoBody);
        LayoutParams ip = new LayoutParams((int) (580 * d), LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        ip.setMargins((int) (48 * d), 0, 0, (int) (72 * d));
        addView(info, ip);
        for (View v : new View[]{shade, message, credit, info}) {
            v.setOutlineProvider(null);   // raised above the photos without a shadow
            v.setTranslationZ(10f);
        }
    }

    private static TextView text(Context c, int sp, int color) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setShadowLayer(6, 0, 1, 0x99000000);
        return t;
    }

    /** The photos to show; keeps the one on screen when it is still among them. */
    void setPhotos(List<Photos.Photo> list) {
        String current = index >= 0 && index < photos.size() ? photos.get(index).id : null;
        generation++;   // a decode queued for the old list must not paint into the new one
        photos = new ArrayList<>(list);
        message.setVisibility(photos.isEmpty() ? VISIBLE : GONE);
        if (photos.isEmpty()) return;
        for (int i = 0; current != null && i < photos.size(); i++) {
            if (photos.get(i).id.equals(current)) {
                index = i;
                schedule();
                return;
            }
        }
        show(random.nextInt(photos.size()), false);
    }

    void start() {
        running = true;
        schedule();
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    void release() {
        stop();
        released = true;
        generation++;
        handler.removeCallbacksAndMessages(null);
        decoder.shutdownNow();
        network.shutdownNow();
    }

    private void schedule() {
        handler.removeCallbacks(tick);
        if (running && photos.size() > 1) handler.postDelayed(tick, Settings.interval(getContext()) * 1000L);
    }

    /** Forward picks any other photo at random; back steps to the previous one in the list. */
    private int next(int step) {
        int n = photos.size();
        if (n < 2) return Math.max(0, index);
        if (step > 0) {
            int pick = random.nextInt(n - 1);
            return pick >= index ? pick + 1 : pick;   // never the same photo twice in a row
        }
        return ((index + step) % n + n) % n;
    }

    /** Left/Right change the photo, Up shows its details, Down hides them. Other keys are left to the caller. */
    boolean handleKey(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down && photos.size() > 1) show(next(e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1), true);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down && e.getRepeatCount() == 0 && !photos.isEmpty()) setInfo(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) setInfo(false);
                return true;
            default:
                return false;
        }
    }

    boolean infoShown() {
        return info.getVisibility() == VISIBLE;
    }

    void setInfo(boolean on) {
        info.setVisibility(on ? VISIBLE : GONE);
        if (on) fillInfo();
    }

    private void show(int chosen, boolean animate) {
        if (released || photos.isEmpty()) return;
        int ticket = ++generation;
        Photos.Photo p = photos.get(chosen);
        int[] screen = Photos.screen(getContext());
        decoder.execute(() -> {
            Bitmap b = decode(Photos.file(getContext(), p).getPath(), screen[0], screen[1]);
            handler.post(() -> {
                if (released || ticket != generation) return;   // released, a new list, or a later press won
                index = chosen;
                if (b != null) {
                    ImageView fresh = layers[1 - front], old = layers[front];
                    fresh.setImageBitmap(b);
                    fresh.setTranslationZ(1f);   // over the old one; the text sits at 10
                    old.setTranslationZ(0f);
                    fresh.setAlpha(animate && !still ? 0f : 1f);
                    fresh.animate().alpha(1f).setDuration(animate && !still ? 900 : 0)
                            .withEndAction(() -> old.setAlpha(0f)).start();
                    front = 1 - front;
                }
                credit.setText(creditLine(p));
                if (infoShown()) fillInfo();
                askPlace(p);
                schedule();
            });
        });
    }

    /** "Photo: who · Wikimedia Commons · licence · where   ▲ Details". */
    private String creditLine(Photos.Photo p) {
        Context c = getContext();
        StringBuilder s = new StringBuilder(c.getString(R.string.credit, p.author.isEmpty() ? "?" : p.author))
                .append(" · Wikimedia Commons");
        if (!p.license.isEmpty()) s.append(" · ").append(p.license);
        if (!p.place.isEmpty()) s.append(" · ").append(p.place);
        return s.append("    ▲ ").append(c.getString(R.string.details)).append(hint).toString();
    }

    /**
     * The place of the photo now on screen, asked once, off the main thread. The reply counts only while that same
     * photo (by id, not by position, which a new list reuses) is still on screen.
     */
    private void askPlace(Photos.Photo p) {
        if (released || p.placed) return;
        network.execute(() -> {
            Photos.place(getContext(), p);
            handler.post(() -> {
                if (released || !p.placed || index < 0 || index >= photos.size()) return;
                Photos.Photo now = photos.get(index);
                if (!now.id.equals(p.id)) return;
                now.place = p.place;   // a refresh may have put a new copy of the same photo in the list
                now.placed = true;
                credit.setText(creditLine(now));
                if (infoShown()) fillInfo();
            });
        });
    }

    /** What is known about the photo on screen; what its uploader left out says so. */
    private void fillInfo() {
        if (index < 0 || index >= photos.size()) return;
        Context c = getContext();
        Photos.Photo p = photos.get(index);
        infoTitle.setText(p.description.isEmpty() ? p.title : p.description);
        String missing = p.placed ? c.getString(R.string.not_given) : "…";
        List<String> lines = new ArrayList<>();
        lines.add(c.getString(R.string.place) + ": " + (p.place.isEmpty() ? missing : p.place));
        lines.add(c.getString(R.string.by) + ": " + (p.author.isEmpty() ? c.getString(R.string.not_given) : p.author)
                + " · Wikimedia Commons" + (p.license.isEmpty() ? "" : " · " + p.license));
        if (!p.taken.isEmpty()) lines.add(c.getString(R.string.taken) + ": " + p.taken);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(Photos.file(c, p).getPath(), o);
        if (o.outWidth > 0) lines.add(c.getString(R.string.size) + ": " + o.outWidth + " × " + o.outHeight
                + (p.width > 0 ? " (" + c.getString(R.string.original, p.width + " × " + p.height) + ")" : ""));
        infoBody.setText(TextUtils.join("\n", lines));
    }

    private static Bitmap decode(String path, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(path, d);
    }
}
