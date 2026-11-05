package io.github.savioronezero.worldclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Bundled public-domain land silhouette, parsed once; no bitmap, map SDK, tiles or frame allocation. */
final class WorldMap {
    private final Path land = new Path();
    private final Path ocean = new Path();
    WorldMap(Context context) {
        land.setFillType(Path.FillType.EVEN_ODD);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getResources().openRawResource(R.raw.world_map), StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Path shape = line.startsWith("o ") ? ocean : land;
                if (line.startsWith("o ")) line = line.substring(2);
                boolean first = true;
                for (String point : line.split(" ")) {
                    int comma = point.indexOf(',');
                    float x = Float.parseFloat(point.substring(0, comma));
                    float y = Float.parseFloat(point.substring(comma + 1));
                    if (first) { shape.moveTo(x, y); first = false; }
                    else shape.lineTo(x, y);
                }
                shape.close();
            }
        } catch (IOException | NumberFormatException | IndexOutOfBoundsException unavailable) {
            ocean.reset();
            land.reset(); // A damaged optional background must never take the clocks down.
        }
    }
    void draw(Canvas canvas, Paint paint, int width, int height) {
        float scale = Math.min(width * 0.94f / 360f, height * 0.90f / 175.22f);
        canvas.save();
        canvas.translate((width - 360 * scale) / 2f, (height - 175.22f * scale) / 2f);
        canvas.scale(scale, scale);
        paint.setColor(Color.rgb(8, 16, 25));
        canvas.drawPath(ocean, paint);
        paint.setColor(Color.rgb(25, 44, 59));
        canvas.drawPath(land, paint);
        canvas.restore();
    }
}
