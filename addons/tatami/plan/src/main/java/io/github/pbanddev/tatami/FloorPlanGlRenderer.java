package io.github.pbanddev.tatami;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Draws the floor plan with OpenGL ES 2, all on the GPU, through a film-like lens ({@link Look.Post}):
 * <ol>
 *   <li>the copy to blur: the bare scene (the cheapest shader there is) into a texture {@link Lens#margin} bigger
 *       than the screen on every side, so walls just off screen blur in at the edges as they should;</li>
 *   <li>that copy averaged down to a quarter size as a 4x4 box;</li>
 *   <li>one separable Gaussian round there, across and then down (2 passes);</li>
 *   <li>the scene into the window (the window asks for 4x MSAA, so tilted walls stay smooth), with the
 *       vignette, the colour ramp and the grain;</li>
 *   <li>the blurred copy, given the same vignette and colour ramp, blended over it everywhere but a sharp
 *       oval in the middle. Both are smooth over hundreds of pixels, so giving them after the blur looks the
 *       same as before it, and the blurred copy's scene pass stays cheap.</li>
 * </ol>
 * The vignette, the colour ramp and the blur's oval come from a small picture of the screen made once per
 * size ({@link Lens}), so each pixel reads them instead of working them out. Nagaya's fills come washed from the
 * CPU ({@link Wash}), so its 2 window passes take no vignette or colour ramp; its blend reads the map's oval only.
 * Six passes; with tilt-shift off, only the scene into the window. Nothing is copied from the CPU to the GPU
 * each frame except the scene's triangles. The grain tile goes up once per GL context.
 *
 * <p>Each look has its own {@link Slot}; the {@link LookSchedule} picks the one to draw each frame and how bright.
 * Yojōhan's scene is built here, at once, as it always was. Nagaya's is built once per process on a background
 * thread ({@link NagayaScene}), after this view's first frame, and only once the Look setting asks for it. Anything
 * Nagaya throws marks it failed for the process ({@link Scenes#APP}) and Yojōhan is drawn instead; only a failure
 * of Yojōhan's frame, or of what both share, sets {@link #broken}.
 */
final class FloorPlanGlRenderer implements GLSurfaceView.Renderer {

    /** Attribute slots, bound before every program links. */
    private static final int POS = 0, COLOR = 1;
    private static final long GRAIN_SEED = 1L;
    /** Its lens values size the render targets and make the lens map, which Nagaya reads only for the blur's oval. */
    private final Look look = Look.YOJOHAN;
    private final Slot yojohan = new Slot(Look.YOJOHAN);
    private final Slot nagaya = new Slot(Look.NAGAYA);
    private final LookSchedule schedule = new LookSchedule();
    /** The wall-clock time of this view's first frame, for the schedule; NaN before it. */
    private double startedAt = Double.NaN;
    private final float floorR, floorG, floorB;
    private final FloatBuffer quadBuffer;
    /** The grain tile, made once on the GL thread, off Home's main thread. Its GPU copy dies with each GL context. */
    private ByteBuffer grainPixels;
    private final float[] grainOffset = new float[2];
    /**
     * Index 0 is the full-size copy of the scene, a margin bigger than the screen on every side; 1 and 2
     * are the 1/4-size blur targets of the same area; 3 is the lens map.
     */
    private final int[] texture = new int[4];
    private final int[] framebuffer = new int[3];
    /**
     * The copy to blur reaches this far past the screen's left and right, and top and bottom, edges, so walls
     * just off screen blur in too: {@link Lens#margin}, padded so the copy is whole GPU tiles.
     */
    private int marginX, marginY;
    /** The camera matrix for the copy to blur: the screen's, shrunk to fit the wider target. */
    private final float[] wideMatrix = new float[16];
    private volatile Effects effects;
    private int width, height, wideWidth, wideHeight, blurWidth, blurHeight;
    /** The passes both looks share: their shaders are the same for both. */
    private int plainProgram, downsample, blur, meshVbo, quadVbo, grainTexture;
    /** Uniform locations, named as in the shaders. */
    private int uPlainMatrix, uTexel, uStep;

    /**
     * One look's part of the renderer: its scene, its own copy of each frame's triangles and matrix (Nagaya's scene is
     * shared by the process, so a frame is copied out under its lock), and its programs, made in every GL context.
     */
    private static final class Slot {
        final Look look;
        /** Yojōhan's from the start; Nagaya's once its shared build is ready. Read on the GL thread only. */
        Scene scene;
        /** Sized from the scene's own mesh. */
        FloatBuffer mesh;
        int floats;
        final float[] matrix = new float[16];
        /** True once the programs exist in the current GL context. */
        boolean made;
        int sceneProgram, blend;
        /** Uniform locations, named as in the shaders. uLens reads texture unit 1; every other sampler unit 0. */
        int uMatrix, uInvSize, uVig, uGrainAmp, uGrainOffset, uFade, uBlendVig, uBlendFade, uBlurArea;

        Slot(Look look) {
            this.look = look;
        }

        /** Takes the scene and a buffer for its triangles. */
        void hold(Scene scene) {
            mesh = floats(scene.mesh.data.length);
            this.scene = scene;
        }
    }

    FloorPlanGlRenderer(Effects effects) {
        this.effects = effects;
        floorR = ((look.palette.floor >> 16) & 0xFF) / 255f;
        floorG = ((look.palette.floor >> 8) & 0xFF) / 255f;
        floorB = (look.palette.floor & 0xFF) / 255f;
        yojohan.hold(Scenes.APP.make(Look.YOJOHAN, NagayaScene::report));
        quadBuffer = floats(8);
        quadBuffer.put(new float[] {-1, -1, 1, -1, -1, 1, 1, 1}).position(0);
    }

    void setEffects(Effects effects) {
        this.effects = effects;
    }

    /**
     * A GL call failed (a shader or a render target the driver refused, or memory ran out, say right after a 4K app):
     * clear to the floor colour instead. The GL thread must keep drawing, because the window that holds this view
     * waits for its first frame before Android shows it; a dead GL thread left Home black and not responding.
     * A new context (the next time the surface is created) tries again.
     */
    private boolean broken;

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        broken = false;
        try {
            setUp();
        } catch (RuntimeException | OutOfMemoryError failure) {
            fail(failure);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        width = w;
        height = h;
        if (broken) return;
        try {
            size(w, h);
        } catch (RuntimeException | OutOfMemoryError failure) {
            fail(failure);
        }
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        if (!broken) {
            try {
                draw();
                return;
            } catch (RuntimeException | OutOfMemoryError failure) {
                fail(failure);
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClearColor(floorR, floorG, floorB, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    private void fail(Throwable failure) {
        broken = true;
        Log.e("Tatami", "the floor plan cannot draw on this context", failure);
    }

    /** A new context: everything from the old one is gone, its render targets and grain tile too. */
    private void setUp() {
        texture[0] = 0;   // so size() does not delete the old names, which may be live ones in this context
        nagaya.made = false;   // made when Nagaya is first ready in this context
        make(yojohan);
        plainProgram = program(Shaders.SCENE_VERTEX, Shaders.PLAIN_FRAGMENT);
        downsample = program(Shaders.QUAD_VERTEX, Shaders.DOWNSAMPLE_FRAGMENT);
        blur = program(Shaders.QUAD_VERTEX, Shaders.blurFragment(look.post));
        uPlainMatrix = GLES20.glGetUniformLocation(plainProgram, "uMatrix");
        uTexel = GLES20.glGetUniformLocation(downsample, "uTexel");
        uStep = GLES20.glGetUniformLocation(blur, "uStep");
        int[] buffers = new int[2];
        GLES20.glGenBuffers(2, buffers, 0);
        meshVbo = buffers[0];
        quadVbo = buffers[1];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 8 * 4, quadBuffer, GLES20.GL_STATIC_DRAW);
        if (grainPixels == null) grainPixels = ByteBuffer.wrap(Grain.texture(Grain.field(GRAIN_SEED)));
        int[] grain = new int[1];
        GLES20.glGenTextures(1, grain, 0);
        grainTexture = grain[0];
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, grainTexture);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, Grain.SIZE, Grain.SIZE, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, grainPixels);
        sampling(GLES20.GL_NEAREST, GLES20.GL_REPEAT);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearColor(floorR, floorG, floorB, 1f);
    }

    private void size(int w, int h) {
        int least = Lens.margin(look.post);
        marginX = Lens.padded(w, least, look.post.blurDivisor);
        marginY = Lens.padded(h, least, look.post.blurDivisor);
        wideWidth = w + 2 * marginX;
        wideHeight = h + 2 * marginY;
        blurWidth = Math.max(1, wideWidth / look.post.blurDivisor);
        blurHeight = Math.max(1, wideHeight / look.post.blurDivisor);
        if (texture[0] != 0) {
            GLES20.glDeleteTextures(4, texture, 0);
            GLES20.glDeleteFramebuffers(3, framebuffer, 0);
        }
        GLES20.glGenTextures(4, texture, 0);
        GLES20.glGenFramebuffers(3, framebuffer, 0);
        for (int i = 0; i < 3; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[i]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, i == 0 ? wideWidth : blurWidth, i == 0 ? wideHeight : blurHeight, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            sampling(GLES20.GL_LINEAR, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[i]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, texture[i], 0);
            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("render target " + i + " incomplete: " + status);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[3]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, Lens.width(w), Lens.height(h), 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, ByteBuffer.wrap(Lens.map(w, h, look.post)));
        sampling(GLES20.GL_LINEAR, GLES20.GL_CLAMP_TO_EDGE);
    }

    /** The slot's look's own passes: the scene into the window, and the blurred copy over it. */
    private static void make(Slot s) {
        Look.Post post = s.look.post;
        s.sceneProgram = program(Shaders.SCENE_VERTEX, Shaders.sceneFragment(post));
        s.blend = program(Shaders.QUAD_VERTEX, Shaders.blendFragment(post));
        s.uMatrix = GLES20.glGetUniformLocation(s.sceneProgram, "uMatrix");
        s.uInvSize = GLES20.glGetUniformLocation(s.sceneProgram, "uInvSize");
        s.uVig = GLES20.glGetUniformLocation(s.sceneProgram, "uVig");
        s.uGrainAmp = GLES20.glGetUniformLocation(s.sceneProgram, "uGrainAmp");
        s.uGrainOffset = GLES20.glGetUniformLocation(s.sceneProgram, "uGrainOffset");
        s.uFade = GLES20.glGetUniformLocation(s.sceneProgram, "uFade");
        s.uBlendVig = GLES20.glGetUniformLocation(s.blend, "uVig");
        s.uBlurArea = GLES20.glGetUniformLocation(s.blend, "uBlurArea");
        s.uBlendFade = GLES20.glGetUniformLocation(s.blend, "uFade");
        lensOnUnit1(s.sceneProgram);
        lensOnUnit1(s.blend);
        s.made = true;
    }

    /** Points the program's uLens sampler at texture unit 1. */
    private static void lensOnUnit1(int program) {
        GLES20.glUseProgram(program);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uLens"), 1);
    }

    private void draw() {
        Effects fx = effects;
        double t = FloorPlanView.now();
        boolean wantsNagaya = fx.look != LookSchedule.Mode.YOJOHAN;
        if (Double.isNaN(startedAt)) startedAt = t;
        schedule.at(t, fx.look, fx.swapMinutes * 60.0, wantsNagaya ? nagayaSince() : Double.POSITIVE_INFINITY, startedAt);
        if (schedule.look == Look.NAGAYA) {
            try {
                draw(nagaya, fx, t, schedule.fade);
            } catch (Throwable failure) {
                Scenes.APP.failed(Look.NAGAYA, failure, NagayaScene::report);
                draw(yojohan, fx, t, 1f);
            }
        } else {
            draw(yojohan, fx, t, schedule.fade);
        }
        if (wantsNagaya) NagayaScene.request();   // after the first frame, so the plan shows at once
    }

    /**
     * When Nagaya became ready to draw, for the schedule: positive infinity until its shared scene is built and,
     * in this context, its programs are made. A failure here marks it failed and keeps Yojōhan.
     */
    private double nagayaSince() {
        Scene shared = NagayaScene.scene();
        if (shared == null) return Double.POSITIVE_INFINITY;
        try {
            if (nagaya.scene != shared) nagaya.hold(shared);
            if (!nagaya.made) make(nagaya);
        } catch (Throwable failure) {
            Scenes.APP.failed(Look.NAGAYA, failure, NagayaScene::report);
            return Double.POSITIVE_INFINITY;
        }
        return NagayaScene.readySince();
    }

    /** One frame of the slot's look; {@code fade} darkens all of it, 1 as drawn, 0 black. */
    private void draw(Slot s, Effects fx, double t, float fade) {
        Scene scene = s.scene;
        synchronized (scene) {
            scene.build(t, width, height, fx.doors, fx.fixtures, fx.camera, fx.vignette);
            s.floats = scene.mesh.floats;
            s.mesh.clear();
            s.mesh.put(scene.mesh.data, 0, s.floats).position(0);
            System.arraycopy(scene.matrix, 0, s.matrix, 0, 16);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, s.floats * 4, s.mesh, GLES20.GL_STREAM_DRAW);
        Grain.offset(t, s.look.post.grainFps, grainOffset);
        // a look washed on the CPU has no uVig: the vignette switch turned its wash off in the build above
        float vignette = fx.vignette ? 1f : 0f;
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[3]);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        if (fx.tiltShift) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0]);
            GLES20.glViewport(0, 0, wideWidth, wideHeight);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            drawPlainScene(s);   // no lens yet, and no grain: grain belongs to the sharp layer only
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[1]);
            GLES20.glViewport(0, 0, blurWidth, blurHeight);
            GLES20.glUseProgram(downsample);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
            GLES20.glUniform2f(uTexel, 1f / wideWidth, 1f / wideHeight);
            drawQuad();
            blurInto(2, texture[1], 1f / blurWidth, 0f);
            blurInto(1, texture[2], 0f, 1f / blurHeight);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        drawScene(s, vignette, fx.grain ? 1f : 0f, fade);

        if (fx.tiltShift) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glUseProgram(s.blend);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[1]);
            GLES20.glUniform1f(s.uBlendVig, vignette);
            GLES20.glUniform1f(s.uBlendFade, fade);
            // where the screen sits inside the wider blurred copy: scale x, y, then offset x, y
            GLES20.glUniform4f(s.uBlurArea, (float) width / wideWidth, (float) height / wideHeight,
                    (float) marginX / wideWidth, (float) marginY / wideHeight);
            drawQuad();
            GLES20.glDisable(GLES20.GL_BLEND);
        }
    }

    /**
     * The scene's triangles through the lens into the bound target. {@code vignette} and {@code grain} are 1 (on) or
     * 0 (off); {@code fade} darkens the result.
     */
    private void drawScene(Slot s, float vignette, float grain, float fade) {
        GLES20.glUseProgram(s.sceneProgram);
        GLES20.glUniformMatrix4fv(s.uMatrix, 1, false, s.matrix, 0);
        GLES20.glUniform2f(s.uInvSize, 1f / width, 1f / height);
        GLES20.glUniform1f(s.uVig, vignette);
        GLES20.glUniform1f(s.uGrainAmp, grain);
        GLES20.glUniform1f(s.uFade, fade);
        GLES20.glUniform2f(s.uGrainOffset, grainOffset[0], grainOffset[1]);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, grainTexture);
        drawTriangles(s);
    }

    /** The scene's triangles as they are into the wider copy to blur: the screen's view, shrunk to fit it. */
    private void drawPlainScene(Slot s) {
        float[] m = s.matrix, wide = wideMatrix;
        float sx = (float) width / wideWidth, sy = (float) height / wideHeight;
        System.arraycopy(m, 0, wide, 0, 16);
        wide[0] = m[0] * sx;
        wide[4] = m[4] * sx;
        wide[12] = m[12] * sx;
        wide[1] = m[1] * sy;
        wide[5] = m[5] * sy;
        wide[13] = m[13] * sy;
        GLES20.glUseProgram(plainProgram);
        GLES20.glUniformMatrix4fv(uPlainMatrix, 1, false, wide, 0);
        drawTriangles(s);
    }

    private void drawTriangles(Slot s) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshVbo);
        int stride = SceneMesh.FLOATS_PER_VERTEX * 4;
        GLES20.glEnableVertexAttribArray(POS);
        GLES20.glVertexAttribPointer(POS, 2, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(COLOR);
        GLES20.glVertexAttribPointer(COLOR, 4, GLES20.GL_FLOAT, false, stride, 8);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, s.floats / SceneMesh.FLOATS_PER_VERTEX);
        GLES20.glDisableVertexAttribArray(COLOR);
    }

    private void blurInto(int target, int source, float stepX, float stepY) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[target]);
        GLES20.glUseProgram(blur);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, source);
        GLES20.glUniform2f(uStep, stepX, stepY);
        drawQuad();
    }

    private void drawQuad() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo);
        GLES20.glEnableVertexAttribArray(POS);
        GLES20.glVertexAttribPointer(POS, 2, GLES20.GL_FLOAT, false, 8, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    /** Filtering and wrapping of the bound texture. */
    private static void sampling(int filter, int wrap) {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap);
    }

    private static int program(String vertex, String fragment) {
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER, vertex));
        GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER, fragment));
        GLES20.glBindAttribLocation(program, POS, "aPos");
        GLES20.glBindAttribLocation(program, COLOR, "aColor");
        GLES20.glLinkProgram(program);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("link: " + GLES20.glGetProgramInfoLog(program));
        return program;
    }

    private static int shader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("compile: " + GLES20.glGetShaderInfoLog(shader));
        return shader;
    }

    private static FloatBuffer floats(int count) {
        return ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }
}
