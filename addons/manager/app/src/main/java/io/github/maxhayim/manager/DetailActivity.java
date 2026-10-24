package io.github.maxhayim.manager;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * One add-on, theme or switch: its screenshot from the repository, what it does, who made it and where it was
 * tested. An installed add-on can be opened or uninstalled (the system asks first). Anything else is installed
 * from a computer, the creator's way: the command is shown with this TV's address filled in.
 */
public class DetailActivity extends Activity {
    private Catalog.Item item;
    private LinearLayout rows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        item = Repo.find(Catalog.Kind.valueOf(getIntent().getStringExtra("kind")), getIntent().getStringExtra("id"));
        if (item == null) {
            finish();
            return;
        }
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setBackgroundColor(0xFF0D1117);
        page.setPadding(px(48), px(32), px(48), px(24));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(item.kind == Catalog.Kind.SWITCH ? item.id : item.name);
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        left.addView(title);
        ImageView shot = new ImageView(this);
        shot.setAdjustViewBounds(true);
        shot.setScaleType(ImageView.ScaleType.FIT_START);
        shot.setBackgroundColor(0xFF161B22);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(px(520), px(293));
        slp.topMargin = px(16);
        left.addView(shot, slp);
        TextView note = new TextView(this);
        note.setTextSize(12);
        note.setTextColor(0xFF8B949E);
        note.setPadding(0, px(8), 0, 0);
        note.setText(item.screenshot == null ? "No screenshot yet." : "Screenshot from the project's repository.");
        left.addView(note);
        if (item.kind == Catalog.Kind.SWITCH) {
            // A switch is a line in a theme's config.json: there is nothing to picture.
            shot.setVisibility(View.GONE);
            note.setVisibility(View.GONE);
        }
        page.addView(left, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(px(32), 0, 0, 0);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        setContentView(page);

        if (item.screenshot != null) {
            new Thread(() -> {
                Bitmap b = Repo.picture(item.screenshot);
                runOnUiThread(() -> {
                    if (b != null) shot.setImageBitmap(b);
                    else note.setText("The screenshot couldn't be read.");
                });
            }).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (item != null) build();
    }

    private void build() {
        rows.removeAllViews();
        text(item.what, 17, 0xFFFFFFFF);
        if (item.type != null && !item.type.isEmpty()) text("Value: " + item.type, 14, 0xFFC9D1D9);
        if (item.author != null && !item.author.isEmpty()) text("By " + item.author, 14, 0xFFC9D1D9);
        if (item.tested != null && !item.tested.isEmpty()) text("Tested on " + item.tested, 14, 0xFFC9D1D9);
        if (item.permissions != null && !item.permissions.isEmpty()) text("Permissions: " + item.permissions, 14, 0xFFC9D1D9);

        Row first = null;
        if (item.kind == Catalog.Kind.ADDON && Repo.installed(this, item)) {
            String pkg = Repo.packages.get(item.id);
            text("Installed on this TV.", 15, 0xFF3DDC84);
            first = add(new Row(this, "Open", null, () -> {
                Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
                open(i);
            }));
            add(new Row(this, "Uninstall", "The TV asks before anything is removed.",
                    () -> open(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)))));
        }
        String how = item.kind == Catalog.Kind.THEME ? "To use this theme"
                : item.kind == Catalog.Kind.SWITCH ? "To change it" : Repo.installed(this, item) ? "To update it" : "To install it";
        text(how + ", run this on a computer in a copy of the project, as its creator describes:", 14, 0xFF8B949E);
        TextView cmd = text(Catalog.installCommand(item, Repo.ip(this)), 15, 0xFF8AB4F8);
        cmd.setTypeface(android.graphics.Typeface.MONOSPACE);
        cmd.setBackgroundColor(0xFF161B22);
        cmd.setPadding(px(12), px(10), px(12), px(10));
        text("Nothing is installed from the TV: add-ons are built from their source and installed over ADB.", 12, 0xFF8B949E);
        Row back = add(new Row(this, "Back", null, this::finish));
        (first != null ? first : back).requestFocus();
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(0, px(6), 0, px(6));
        rows.addView(t);
        return t;
    }

    private Row add(Row r) {
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void open(Intent i) {
        if (i == null) return;
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // Nothing to open.
        }
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
