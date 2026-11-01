package io.github.cleanyourtv.flock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** Canvas renderer and frame-clock for the flock; the simulation runs only while this screen is visible. */
final class FlockView extends View {
    private static final int MIN_BOIDS = 24;
    private static final int MAX_BOIDS = 180;
    private static final int[] PALETTE = {0xFF70E1CF, 0xFF9BBEFF, 0xFFFFD28A, 0xFFE5F3F1};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundPaint = new Paint();
    private final Path bird = new Path();
    private final FlockModel model = new FlockModel(84);
    private final float density;
    private final String runningLabel;
    private final String pausedLabel;
    private final String controls;
    private boolean running;
    private boolean paused;
    private long previousFrame;

    FlockView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        runningLabel = context.getString(R.string.running);
        pausedLabel = context.getString(R.string.paused);
        controls = context.getString(R.string.controls);
        setFocusable(false);
        setContentDescription(context.getString(R.string.app_name) + ". " + controls);
    }

    void resume() {
        running = true;
        previousFrame = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    void pause() {
        running = false;
    }

    void togglePaused() {
        paused = !paused;
        invalidate();
    }

    void changeCount(int amount) {
        model.setCount(Math.max(MIN_BOIDS, Math.min(MAX_BOIDS, model.size() + amount)));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        model.resize(w, h);
        backgroundPaint.setShader(new LinearGradient(0, 0, w, h, 0xFF071820, 0xFF10232C,
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth(), height = getHeight();
        canvas.drawRect(0, 0, width, height, backgroundPaint);
        drawAmbientDots(canvas, width, height);

        if (running && !paused) {
            long now = SystemClock.uptimeMillis();
            float dt = (now - previousFrame) / 1000f;
            previousFrame = now;
            model.step(dt);
        }
        drawBoids(canvas);
        drawHud(canvas, width, height);
        if (running && !paused) postInvalidateOnAnimation();
    }

    private void drawAmbientDots(Canvas canvas, int width, int height) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 28; i++) {
            float x = ((i * 97 + 31) % 997) / 997f * width;
            float y = ((i * 193 + 71) % 991) / 991f * height;
            paint.setColor(i % 4 == 0 ? 0x1E8CC8C0 : 0x16FFFFFF);
            canvas.drawCircle(x, y, (i % 3 + 1) * density, paint);
        }
    }

    private void drawBoids(Canvas canvas) {
        for (int i = 0; i < model.size(); i++) {
            FlockModel.Boid boid = model.boidAt(i);
            float heading = (float) Math.atan2(boid.vy, boid.vx);
            float c = (float) Math.cos(heading), s = (float) Math.sin(heading);
            float noseX = boid.x + c * 11f * density;
            float noseY = boid.y + s * 11f * density;
            float rearX = boid.x - c * 8f * density;
            float rearY = boid.y - s * 8f * density;
            float sideX = -s * 5f * density;
            float sideY = c * 5f * density;

            paint.setColor(0x426EDBCB);
            paint.setStrokeWidth(1.5f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(rearX - c * 8f * density, rearY - s * 8f * density,
                    rearX, rearY, paint);

            bird.reset();
            bird.moveTo(noseX, noseY);
            bird.lineTo(rearX + sideX, rearY + sideY);
            bird.lineTo(boid.x - c * 1f * density, boid.y - s * 1f * density);
            bird.lineTo(rearX - sideX, rearY - sideY);
            bird.close();
            paint.setColor(PALETTE[i % PALETTE.length]);
            canvas.drawPath(bird, paint);
        }
    }

    private void drawHud(Canvas canvas, int width, int height) {
        float margin = 36f * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(15f * density);
        String status = paused ? pausedLabel : runningLabel;
        float statusWidth = paint.measureText(status);
        paint.setColor(0xE617303A);
        canvas.drawRoundRect(margin - 12f * density, margin - 8f * density,
                margin + statusWidth + 12f * density, margin + 24f * density,
                12f * density, 12f * density, paint);
        paint.setColor(0xBFE9F5F2);
        canvas.drawText(status, margin, margin + 15f * density, paint);

        String count = getResources().getQuantityString(R.plurals.birds, model.size(), model.size());
        paint.setTextAlign(Paint.Align.RIGHT);
        float countWidth = paint.measureText(count);
        paint.setColor(0xE617303A);
        canvas.drawRoundRect(width - margin - countWidth - 12f * density, margin - 8f * density,
                width - margin + 12f * density, margin + 24f * density,
                12f * density, 12f * density, paint);
        paint.setColor(0xAFC8D9D5);
        canvas.drawText(count, width - margin, margin + 15f * density, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(14f * density);
        float controlsWidth = paint.measureText(controls);
        paint.setColor(0xD9142932);
        canvas.drawRoundRect(width / 2f - controlsWidth / 2f - 16f * density,
                height - margin - 18f * density, width / 2f + controlsWidth / 2f + 16f * density,
                height - margin + 7f * density, 12f * density, 12f * density, paint);
        paint.setColor(0x80E9F5F2);
        canvas.drawText(controls, width / 2f, height - margin, paint);

        if (paused) {
            paint.setTextSize(22f * density);
            paint.setColor(0xFFF1F8F6);
            canvas.drawText(pausedLabel, width / 2f, height / 2f, paint);
        }
    }
}
