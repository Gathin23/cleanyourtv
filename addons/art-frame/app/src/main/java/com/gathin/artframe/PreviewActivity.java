package com.gathin.artframe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/** A search hit, large, with Save (the 4K picture into the library) and Save and show. */
public class PreviewActivity extends Activity {
    static final String EXTRA_RESULT = "result";
    private static final long MAX_DOWNLOAD = 80L << 20;

    private Sources.Result result;
    private TextView status;
    private TextView save;
    private TextView saveShow;
    private Artwork saved;
    private boolean busy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            result = Sources.Result.fromJson(new JSONObject(getIntent().getStringExtra(EXTRA_RESULT)));
        } catch (JSONException | NullPointerException e) {
            finish();
            return;
        }
        int pad = Ui.dp(this, 36);
        LinearLayout root = Ui.row(this);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.TOP);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.6f);
        lp.setMargins(0, 0, Ui.dp(this, 32), 0);
        root.addView(image, lp);

        LinearLayout side = Ui.column(this);
        TextView title = Ui.heading(this, result.title.isEmpty() ? getString(R.string.untitled) : result.title);
        title.setTextSize(26);
        side.addView(title, Ui.gap(this, 0, 4));
        String by = result.artist.isEmpty() ? result.date : result.date.isEmpty() ? result.artist : result.artist + ", " + result.date;
        side.addView(Ui.text(this, by, 17, Ui.MUTED), Ui.gap(this, 0, 4));
        side.addView(Ui.text(this, result.credit, 14, Ui.MUTED), Ui.gap(this, 0, 20));
        save = Ui.button(this, getString(R.string.save), v -> save(false));
        side.addView(save, Ui.gap(this, 0, 10));
        saveShow = Ui.button(this, getString(R.string.save_show), v -> save(true));
        side.addView(saveShow, Ui.gap(this, 0, 10));
        status = Ui.text(this, getString(R.string.save_hint), 15, Ui.MUTED);
        side.addView(status);
        root.addView(side, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Ui.screen(this, root);
        save.requestFocus();

        new Thread(() -> {
            try {
                byte[] data = Http.bytes(result.preview, 16L << 20);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                o.inSampleSize = Images.sampleSize(o.outWidth, o.outHeight, 1600, 900);
                o.inJustDecodeBounds = false;
                Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                runOnUiThread(() -> image.setImageBitmap(bm));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText(getString(R.string.preview_failed)));
            }
        }, "preview").start();
    }

    private void save(boolean show) {
        if (saved != null) {
            if (show) show();
            return;
        }
        if (busy) return;
        busy = true;
        status.setText(R.string.downloading);
        new Thread(() -> {
            File tmp = null;
            try {
                tmp = File.createTempFile("art-dl", ".img", getCacheDir());
                String full = Sources.byId(result.source).resolveFull(result);
                Http.download(full, tmp, MAX_DOWNLOAD);
                Artwork a = new Artwork();
                a.title = result.title;
                a.artist = result.artist;
                a.date = result.date;
                a.source = result.source;
                a.url = result.url;
                a.credit = result.credit;
                a.collections.add(Library.MUSEUM);
                // A picture shaped like the screen fills it; any other sits in a matte.
                a.fill = result.width > 0 && Sources.suits(result.width, result.height, Images.screenRatio(this));
                Artwork done = Library.get(this).addFile(tmp, a);
                runOnUiThread(() -> {
                    busy = false;
                    saved = done;
                    status.setText(getString(R.string.saved, done.width, done.height));
                    save.setText(R.string.saved_short);
                    if (show) show();
                });
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> {
                    busy = false;
                    status.setText(getString(R.string.save_failed, String.valueOf(e.getMessage())));
                });
            } finally {
                if (tmp != null) tmp.delete();
            }
        }, "save").start();
    }

    private void show() {
        Intent i = new Intent(this, FrameActivity.class);
        i.putExtra(FrameActivity.EXTRA_PIECE, saved.id);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
