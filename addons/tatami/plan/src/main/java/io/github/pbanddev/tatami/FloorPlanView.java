package io.github.pbanddev.tatami;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Choreographer;
import android.view.SurfaceHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * The animated floor plan on an OpenGL surface. The host calls {@link #onResume()} and
 * {@link #onPause()}. Time comes from the wall clock, so every view of it shows the same frame.
 *
 * <p>One frame per display refresh, asked for by the Choreographer, never as fast as the surface takes them.
 * Free-running (RENDERMODE_CONTINUOUSLY), the GL thread queued frames until the surface's buffers were full, and
 * after Home sat still for a while it could settle one frame further ahead (44 ms to the screen became 61 ms)
 * and stay there. Its GPU work then landed on the start of every refresh, where SurfaceFlinger composes this
 * layer on the GPU, and the launcher's own frames waited behind both: 98 % janky over the dock on the test
 * TV, while the plan itself still showed 60 fps. A request made while a frame is still being drawn
 * simply waits for it, so the queue cannot grow.
 */
public final class FloorPlanView extends GLSurfaceView {
    private final FloorPlanGlRenderer renderer;
    private final Choreographer choreographer = Choreographer.getInstance();
    private boolean ticking;
    private final Choreographer.FrameCallback tick = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!ticking) return;
            requestRender();
            choreographer.postFrameCallback(this);
        }
    };

    public FloorPlanView(Context context) {
        this(context, null);
    }

    public FloorPlanView(Context context, AttributeSet attrs) {
        super(context, attrs);
        recordsGlFailure(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(new SmoothEdges());
        renderer = new FloorPlanGlRenderer(Effects.load(context));
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ticking) return;
        ticking = true;
        choreographer.postFrameCallback(tick);
    }

    @Override
    public void onPause() {
        ticking = false;
        choreographer.removeFrameCallback(tick);
        super.onPause();
    }

    private static final String FAILURE_FILE = "floor_plan";
    private static final String FAILED_INSTALL = "gl_failed_install";
    private static boolean recording;
    /** A GL thread in this process died; set and read on the main thread. */
    private static boolean glDead;
    /** The plans on screen now, so a dead GL thread can let their windows draw. Main thread only. */
    private static final List<FloorPlanView> attached = new ArrayList<>();
    /** The window's wait for this surface's next frame, handed to the GL thread; null once it ran. */
    private Runnable pendingDraw;

    /**
     * True when the plan's OpenGL setup failed in GLSurfaceView itself (no config, a context the driver refuses) since
     * this version of the app was installed. Home then leaves the plan out, as with floor_plan false. Installing an
     * update tries again.
     */
    public static boolean failedOnThisInstall(Context context) {
        return context.getSharedPreferences(FAILURE_FILE, Context.MODE_PRIVATE).getLong(FAILED_INSTALL, -1) == installed(context);
    }

    /**
     * The renderer survives its own GL failures (FloorPlanGlRenderer#broken); these come from GLSurfaceView's own EGL
     * setup, which throws on its GL thread. The app must not crash: on Android 10 any crash of a Home that is not a
     * system app makes Android forget it as the default Home, and the next Home press shows the chooser (seen on the
     * test TV). So only that thread ends. Without it the window would wait forever for the frame it asked the thread
     * for, and Home would stay black and not responding, so that wait is let go here; the plan stays black and the
     * UI over it works. The failure is written down, so Home's next start leaves the plan out.
     * GLSurfaceView names its thread "GLThread &lt;id&gt;". Other threads keep the handler they had.
     */
    private static synchronized void recordsGlFailure(Context context) {
        if (recording) return;
        recording = true;
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        Thread.UncaughtExceptionHandler next = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            if (!thread.getName().startsWith("GLThread")) {
                if (next != null) next.uncaughtException(thread, error);
                return;
            }
            Log.e("Tatami", "the floor plan cannot draw on this TV", error);
            app.getSharedPreferences(FAILURE_FILE, Context.MODE_PRIVATE).edit()
                    .putLong(FAILED_INSTALL, installed(app)).apply();
            main.post(() -> {
                glDead = true;
                for (FloorPlanView view : new ArrayList<>(attached)) view.releasePendingDraw();
            });
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached.add(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        attached.remove(this);
        releasePendingDraw();
        super.onDetachedFromWindow();
    }

    /** The window waits for this surface's next frame; the GL thread runs it after drawing, unless it is dead. */
    @Override
    public void surfaceRedrawNeededAsync(SurfaceHolder holder, Runnable finishDrawing) {
        if (glDead) {
            finishDrawing.run();
            return;
        }
        AtomicBoolean done = new AtomicBoolean();
        Runnable once = () -> {
            if (done.compareAndSet(false, true)) finishDrawing.run();
        };
        pendingDraw = once;
        super.surfaceRedrawNeededAsync(holder, once);
    }

    private void releasePendingDraw() {
        Runnable draw = pendingDraw;
        pendingDraw = null;
        if (draw != null && glDead) draw.run();
    }

    private static long installed(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException impossible) {
            return 0;
        }
    }

    static double now() {
        return System.currentTimeMillis() / 1000.0;
    }

    void setEffects(Effects effects) {
        renderer.setEffects(effects);
    }

    /** RGB 888 with 4x multisampling where the TV has it, plain RGB 888 where it does not. */
    private static final class SmoothEdges implements EGLConfigChooser {
        private static final int EGL_OPENGL_ES2_BIT = 4;

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            EGLConfig smooth = pick(egl, display, true);
            if (smooth != null) return smooth;
            EGLConfig plain = pick(egl, display, false);
            if (plain != null) return plain;
            throw new IllegalStateException("no OpenGL ES 2 window config");
        }

        private static EGLConfig pick(EGL10 egl, EGLDisplay display, boolean multisample) {
            int[] spec = multisample
                    ? new int[] {EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                            EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, 4, EGL10.EGL_NONE}
                    : new int[] {EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_NONE};
            int[] count = new int[1];
            if (!egl.eglChooseConfig(display, spec, null, 0, count) || count[0] == 0) return null;
            EGLConfig[] configs = new EGLConfig[count[0]];
            if (!egl.eglChooseConfig(display, spec, configs, configs.length, count)) return null;
            int[] value = new int[1];
            for (EGLConfig config : configs) {
                if (size(egl, display, config, EGL10.EGL_RED_SIZE, value) == 8
                        && size(egl, display, config, EGL10.EGL_GREEN_SIZE, value) == 8
                        && size(egl, display, config, EGL10.EGL_BLUE_SIZE, value) == 8
                        && size(egl, display, config, EGL10.EGL_DEPTH_SIZE, value) == 0) {
                    return config;
                }
            }
            return configs[0];
        }

        private static int size(EGL10 egl, EGLDisplay display, EGLConfig config, int attribute, int[] value) {
            return egl.eglGetConfigAttrib(display, config, attribute, value) ? value[0] : -1;
        }
    }
}
