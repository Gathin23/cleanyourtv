package io.github.sumyabazar1.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/** The hourly temperature curve or the daily high and low curves. */
public final class TemperatureChart extends View {
    private static final int HOURLY_COLOR = 0xFF8BEBDB;
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;
    private final float scaledDensity;
    private final int highColor;
    private final int lowColor;

    private String[] labels = new String[0];
    private String[] symbols = new String[0];
    private String[] conditions;
    private double[] high = new double[0];
    private double[] low;
    private String[] highLabels;
    private String[] lowLabels;
    private double minimum;
    private double span;
    private float plotTop;
    private float plotBottom;
    private boolean daily;
    private boolean iconsEnabled;

    public TemperatureChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        scaledDensity = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1, getResources().getDisplayMetrics());
        highColor = context.getColor(R.color.forecast_high);
        lowColor = context.getColor(R.color.forecast_low);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void enableIcons() {
        iconsEnabled = true;
    }

    void setHourly(List<Forecast.Hour> hours, DateTimeFormatter time, boolean fahrenheit) {
        daily = false;
        labels = new String[hours.size()];
        symbols = new String[hours.size()];
        high = new double[hours.size()];
        low = null;
        conditions = new String[hours.size()];
        for (int i = 0; i < hours.size(); i++) {
            Forecast.Hour hour = hours.get(i);
            labels[i] = time.format(hour.time);
            symbols[i] = WeatherIcons.symbol(hour.code, hour.day);
            if (symbols[i] != null) conditions[i] = getResources().getString(Forecast.sky(hour.code, hour.day));
            high[i] = hour.tempC;
        }
        update(fahrenheit);
    }

    void setDaily(List<Forecast.Day> days, boolean fahrenheit) {
        daily = true;
        labels = new String[days.size()];
        symbols = new String[days.size()];
        high = new double[days.size()];
        low = new double[days.size()];
        conditions = new String[days.size()];
        for (int i = 0; i < days.size(); i++) {
            Forecast.Day day = days.get(i);
            labels[i] = day.date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
            conditions[i] = getResources().getString(Forecast.sky(day.code, true));
            symbols[i] = WeatherIcons.symbol(day.code, true);
            high[i] = day.maxC;
            low[i] = day.minC;
        }
        update(fahrenheit);
    }

    private void update(boolean fahrenheit) {
        highLabels = degrees(high, fahrenheit);
        lowLabels = daily ? degrees(low, fahrenheit) : null;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        StringBuilder description = new StringBuilder(getResources().getString(
                daily ? R.string.daily_forecast : R.string.hourly_forecast));
        for (int i = 0; i < labels.length; i++) {
            if (highLabels[i] == null) continue;
            min = Math.min(min, daily ? low[i] : high[i]);
            max = Math.max(max, high[i]);
            description.append(". ").append(labels[i]).append(' ');
            if (daily) {
                description.append(conditions[i]).append(", ").append(getResources().getString(
                        R.string.high_low, Forecast.degrees(high[i], fahrenheit),
                        Forecast.degrees(low[i], fahrenheit)));
            } else {
                if (conditions[i] != null) description.append(conditions[i]).append(", ");
                description.append(highLabels[i]);
            }
        }
        double padding = Math.max(1, (max - min) * 0.08);
        minimum = min - padding;
        span = max - min + padding * 2;
        if (labels.length == 0) {
            description.append(". ").append(getResources().getString(R.string.forecast_unavailable));
        }
        setContentDescription(description);
        invalidate();
    }

    private String[] degrees(double[] temperatures, boolean fahrenheit) {
        String[] result = new String[temperatures.length];
        for (int i = 0; i < temperatures.length; i++) {
            if (Double.isFinite(temperatures[i])) {
                result[i] = getResources().getString(R.string.temperature_degrees,
                        Forecast.degrees(temperatures[i], fahrenheit));
            }
        }
        return result;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (labels.length == 0) {
            drawText(canvas, getResources().getString(R.string.forecast_unavailable),
                    getWidth() / 2f, getHeight() / 2f, 15, 0xCCFFFFFF);
            return;
        }

        plotTop = 20 * scaledDensity;
        plotBottom = getHeight() - (daily ? 57 : 31) * scaledDensity;
        if (plotBottom <= plotTop) return;
        paint.setColor(0x26FFFFFF);
        paint.setStrokeWidth(density);
        for (int row = 0; row < 3; row++) {
            float y = plotTop + (plotBottom - plotTop) * row / 2;
            canvas.drawLine(x(0), y, x(labels.length - 1), y, paint);
        }

        drawSeries(canvas, high, highLabels, daily ? highColor : HOURLY_COLOR, true);
        if (daily) drawSeries(canvas, low, lowLabels, lowColor, false);

        for (int i = 0; i < labels.length; i++) {
            if (!marked(i)) continue;
            float x = x(i);
            float labelX = x + (showIcons() && symbols[i] != null ? 15 * density : 0);
            if (daily) {
                drawText(canvas, labels[i], labelX, getHeight() - 21 * scaledDensity, 15, 0xFFFFFFFF);
                paint.setTextSize(13 * scaledDensity);
                CharSequence condition = TextUtils.ellipsize(conditions[i], paint,
                        Math.max(1, (float) getWidth() / labels.length - 12 * density), TextUtils.TruncateAt.END);
                drawText(canvas, condition.toString(), x, getHeight() - 4 * scaledDensity, 13, 0xCCFFFFFF);
            } else {
                drawText(canvas, labels[i], labelX, getHeight() - 7 * scaledDensity, 14, 0xDCFFFFFF);
            }
        }
    }

    private void drawSeries(Canvas canvas, double[] values, String[] temperatures, int color, boolean above) {
        path.rewind();
        for (int i = 0; i < values.length; i++) {
            if (temperatures[i] == null) continue;
            float endX = x(i);
            float endY = y(values[i]);
            if (i == 0 || temperatures[i - 1] == null) {
                path.moveTo(endX, endY);
                continue;
            }
            float startX = x(i - 1);
            float third = (endX - startX) / 3;
            // Use the same easing for both daily series so the high and low curves can't cross.
            // For hourly data, match the slopes across samples without overshooting them.
            double startSlope = daily ? 0 : tangent(values, i - 1);
            double endSlope = daily ? 0 : tangent(values, i);
            path.cubicTo(startX + third, y(values[i - 1] + startSlope / 3),
                    endX - third, y(values[i] - endSlope / 3), endX, endY);
        }
        paint.setColor(color);
        paint.setStrokeWidth(2.5f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < values.length; i++) {
            if (temperatures[i] == null) continue;
            if (!marked(i)) {
                // A solitary reading still needs a visible point.
                if ((i == 0 || temperatures[i - 1] == null)
                        && (i + 1 == values.length || temperatures[i + 1] == null)) {
                    canvas.drawCircle(x(i), y(values[i]), 2 * density, paint);
                }
                continue;
            }
            float x = x(i);
            float y = y(values[i]);
            canvas.drawCircle(x, y, 2.8f * density, paint);
            drawText(canvas, temperatures[i], x, y + (above ? -8 : 19) * scaledDensity, 15, color);
        }
    }

    private static double tangent(double[] values, int index) {
        double before = index > 0 ? values[index] - values[index - 1] : Double.NaN;
        double after = index + 1 < values.length ? values[index + 1] - values[index] : Double.NaN;
        if (Double.isNaN(before)) return Double.isNaN(after) ? 0 : after;
        if (Double.isNaN(after)) return before;
        return Math.signum(before) == Math.signum(after)
                ? Math.copySign(Math.min(Math.abs(before), Math.abs(after)), before) : 0;
    }

    void appendIcons(WeatherIcons icons, StringBuilder html, int left, int top) {
        if (!showIcons()) return;
        float centerY = top + getHeight() - (daily ? 26 : 12) * scaledDensity;
        for (int i = 0; i < symbols.length; i++) {
            if (symbols[i] == null || !marked(i)) continue;
            float centerX = left + x(i) - (daily ? 20 : 23) * density;
            icons.append(html, symbols[i], false, centerX, centerY, 42 * density, 31.5f * density);
        }
    }

    private boolean showIcons() {
        return iconsEnabled && getHeight() >= (daily ? 78 : 70) * scaledDensity;
    }

    private float x(int index) {
        if (daily) return getWidth() * (index + 0.5f) / labels.length;
        float edge = 51 * scaledDensity;
        return edge + (getWidth() - 2 * edge) * index / (labels.length - 1);
    }

    private float y(double value) {
        return plotBottom - (float) ((value - minimum) / span) * (plotBottom - plotTop);
    }

    private boolean marked(int index) {
        return daily || index % 4 == 0 || index == labels.length - 1;
    }

    private void drawText(Canvas canvas, String text, float x, float baseline, int size, int color) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(size * scaledDensity);
        paint.setColor(color);
        canvas.drawText(text, x, baseline, paint);
    }
}
