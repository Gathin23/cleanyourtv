package io.github.anhost77.salon;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Map;

/**
 * Réglages > Depuis le téléphone: a QR code to the {@link PhoneServer} page, which lives only while this screen is
 * open (and ten minutes at most). Back closes the page on the phone too.
 */
public final class PhoneActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private PhoneServer server;
    private TextView status;
    private float unit;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        unit = getResources().getDisplayMetrics().heightPixels / 1080f;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setGravity(Gravity.CENTER);
        page.setBackgroundColor(0xFF0B0F16);
        page.setPadding(px(120), px(80), px(120), px(80));

        ImageView qr = new ImageView(this);
        page.addView(qr, new LinearLayout.LayoutParams(px(520), px(520)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(px(80), 0, 0, 0);
        words.addView(text(getString(R.string.phone_title), 44, Typeface.BOLD, Color.WHITE));
        TextView how = text(getString(R.string.phone_how), 26, Typeface.NORMAL, 0xCCFFFFFF);
        how.setPadding(0, px(24), 0, px(24));
        words.addView(how);
        TextView address = text("", 26, Typeface.BOLD, 0xFFFFD27A);
        words.addView(address);
        status = text("", 26, Typeface.NORMAL, 0xFF7CE38B);
        status.setPadding(0, px(24), 0, 0);
        words.addView(status);
        page.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        setContentView(page);

        server = new PhoneServer(this, what -> main.post(() -> status.setText("✓ " + what)));
        new Thread(() -> {
            String url;
            try {
                url = server.start();
            } catch (Exception e) {
                url = null;
            }
            String found = url;
            main.post(() -> {
                if (isFinishing()) {
                    server.stop();
                    return;
                }
                if (found == null) {
                    how.setText(R.string.phone_no_network);
                    return;
                }
                qr.setImageBitmap(qr(found, px(520)));
                address.setText(found);
                main.postDelayed(() -> {
                    status.setTextColor(0x99FFFFFF);
                    status.setText(R.string.phone_expired);
                }, PhoneServer.LIFETIME_MS);
            });
        }, "phone-start").start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Leaving the screen closes the page: nothing listens on the network once Réglages is closed.
        if (server != null) server.stop();
        finish();
    }

    private static Bitmap qr(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) px[y * size + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
            }
            return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            return null;
        }
    }

    private TextView text(String s, float size, int style, int colour) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * unit);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, style));
        t.setTextColor(colour);
        return t;
    }

    private int px(float at1080) {
        return Math.round(at1080 * unit);
    }
}
