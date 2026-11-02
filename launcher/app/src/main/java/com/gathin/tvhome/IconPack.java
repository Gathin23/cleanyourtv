package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;

/** An installed ADW/Nova icon pack (config.xml, icon_pack): its appfilter.xml mapped to the pack's drawables. */
final class IconPack {
    private final Resources res;
    private final String pkg;
    private final IconPackFilter filter;

    private IconPack(Resources res, String pkg, IconPackFilter filter) {
        this.res = res;
        this.pkg = pkg;
        this.filter = filter;
    }

    /** The pack, or null when the name is empty, the pack is not installed or it maps nothing. */
    static IconPack load(Context ctx, String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        try {
            Resources res = ctx.createPackageContext(pkg, 0).getResources();
            IconPackFilter filter = new IconPackFilter();
            int id = res.getIdentifier("appfilter", "xml", pkg);
            if (id != 0) {
                read(res.getXml(id), filter);
            } else {
                try (InputStream in = res.getAssets().open("appfilter.xml")) {
                    XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
                    p.setInput(in, null);
                    read(p, filter);
                }
            }
            return filter.isEmpty() ? null : new IconPack(res, pkg, filter);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    private static void read(XmlPullParser p, IconPackFilter filter) throws Exception {
        for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT; t = p.next()) {
            if (t == XmlPullParser.START_TAG && "item".equals(p.getName())) {
                filter.add(p.getAttributeValue(null, "component"), p.getAttributeValue(null, "drawable"));
            }
        }
    }

    /** The pack's drawable for this component, or null when it has none. */
    Drawable icon(ComponentName component) {
        String name = filter.lookup(component.getPackageName(), component.getClassName());
        if (name == null) return null;
        int id = res.getIdentifier(name, "drawable", pkg);
        if (id == 0) return null;
        try {
            return res.getDrawable(id, null);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }
}
