package io.github.pbanddev.tatami;

/**
 * The GLSL ES 1.00 sources of the plan's passes, made from a look's lens values ({@link Look.Post}). Pure Java,
 * so a test can hold Yojōhan's text still. {@link FloorPlanGlRenderer} compiles them.
 *
 * <p>uFade, applied last in the 2 passes that reach the window, darkens the whole picture: 1 as drawn, 0 black.
 */
final class Shaders {
    private static final String PRECISION =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n";
    /**
     * Mediump maths, about twice as fast as highp on many TV GPUs, with HIGHP for the texture coordinates that must
     * land on the right pixel: the grain tile's (in mediump, gl_FragCoord / 256 cannot hit texel centres across
     * a 1080p screen) and the blurred copy's (mediump is about 1 px off near the right edge).
     */
    private static final String LENS_PRECISION = "precision mediump float;\n"
            + "#ifdef GL_FRAGMENT_PRECISION_HIGH\n#define HIGHP highp\n#else\n#define HIGHP mediump\n#endif\n";
    /** The scene's colours as they are, for the copy to blur. */
    static final String PLAIN_FRAGMENT = "precision mediump float;\nvarying vec4 vColor;\n"
            + "void main() { gl_FragColor = vColor; }\n";
    static final String SCENE_VERTEX =
            "uniform mat4 uMatrix;\nattribute vec2 aPos;\nattribute vec4 aColor;\nvarying vec4 vColor;\n"
                    + "void main() { vColor = aColor; gl_Position = uMatrix * vec4(aPos, 0.0, 1.0); }\n";
    static final String QUAD_VERTEX =
            "attribute vec2 aPos;\nvarying vec2 vUv;\n"
                    + "void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n";
    /**
     * Full size down to 1/4 as a 4x4 box: 4 bilinear taps, each the average of 2x2 texels. A wall
     * thinner than one small pixel then fades in and out smoothly instead of popping, which is
     * what made the blurred edges flicker when the scene was drawn straight at 1/4 size.
     */
    static final String DOWNSAMPLE_FRAGMENT = PRECISION
            + "uniform sampler2D uTex;\nuniform vec2 uTexel;\nvarying vec2 vUv;\n"
            + "void main() {\n"
            + "  gl_FragColor = 0.25 * (texture2D(uTex, vUv + uTexel * vec2(-1.0, -1.0))\n"
            + "      + texture2D(uTex, vUv + uTexel * vec2(1.0, -1.0))\n"
            + "      + texture2D(uTex, vUv + uTexel * vec2(-1.0, 1.0))\n"
            + "      + texture2D(uTex, vUv + uTexel * vec2(1.0, 1.0)));\n}\n";

    private Shaders() {}

    /**
     * The plan's triangles through the lens, into the window. The vignette and colour ramp (uVig 1 or 0)
     * come from the lens map. Grain is zero-mean and added after them (uGrainAmp 1 or 0); only this sharp layer
     * has it, so it fades where the blurred copy covers it. A look washed on the CPU ({@link Look.Post#wash}) comes
     * with its colours done, so it reads no lens map here: grain only.
     */
    static String sceneFragment(Look.Post post) {
        StringBuilder amp = new StringBuilder(glsl(post.grainBase / 255f));
        if (post.grainSlope != 0f) amp.append(" + ").append(glsl(post.grainSlope / 255f)).append(" * luma * luma");
        if (post.grainStep != 0f) {
            amp.append(" + ").append(glsl(post.grainStep / 255f)).append(" * smoothstep(")
                    .append(glsl(post.grainStepFrom)).append(", ").append(glsl(post.grainStepTo)).append(", luma)");
        }
        String lensUniforms = post.wash ? "" : "uniform vec2 uInvSize;\nuniform float uVig;\n";
        String lensSampler = post.wash ? "" : "uniform sampler2D uLens;\n";
        String gain = post.wash ? "" : " * mix(vec3(1.0), texture2D(uLens, gl_FragCoord.xy * uInvSize).rgb * 2.0, uVig)";
        return LENS_PRECISION
                + lensUniforms + "uniform float uGrainAmp;\nuniform float uFade;\n"
                + "uniform HIGHP vec2 uGrainOffset;\n"
                + "uniform sampler2D uGrain;\n" + lensSampler + "varying vec4 vColor;\n"
                + "void main() {\n"
                + "  vec3 c = vColor.rgb" + gain + ";\n"
                + "  float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));\n"
                + "  HIGHP vec2 g = gl_FragCoord.xy / " + glsl(Grain.SIZE) + " + uGrainOffset;\n"
                + "  float n = (texture2D(uGrain, g).r - 0.5) * 8.0;\n"
                + "  c += n * (" + amp + ") * uGrainAmp;\n"
                + "  gl_FragColor = vec4(c * uFade, 1.0);\n}\n";
    }

    /** One separable Gaussian round, along uStep (one texel across or down). */
    static String blurFragment(Look.Post post) {
        StringBuilder s = new StringBuilder(PRECISION)
                .append("uniform sampler2D uTex;\nuniform vec2 uStep;\nvarying vec2 vUv;\n")
                .append("void main() {\n  vec4 c = vec4(0.0);\n");
        for (int i = 0; i < post.blurWeights.length; i++) {
            String w = glsl(post.blurWeights[i]), o = glsl(post.blurOffsets[i]);
            if (post.blurOffsets[i] == 0f) {
                s.append("  c += texture2D(uTex, vUv) * ").append(w).append(";\n");
            } else {
                s.append("  c += (texture2D(uTex, vUv + uStep * ").append(o).append(") + texture2D(uTex, vUv - uStep * ")
                        .append(o).append(")) * ").append(w).append(";\n");
            }
        }
        return s.append("  gl_FragColor = c;\n}\n").toString();
    }

    /**
     * The blurred copy through the same lens, see-through in the sharp oval and opaque outside it. A look washed on
     * the CPU takes no gain here either; the lens map's alpha still shapes the oval.
     */
    static String blendFragment(Look.Post post) {
        return LENS_PRECISION
                + "uniform sampler2D uTex;\nuniform sampler2D uLens;\n" + (post.wash ? "" : "uniform float uVig;\n")
                + "uniform float uFade;\n"
                + "uniform HIGHP vec4 uBlurArea;\n"
                + "varying HIGHP vec2 vUv;\n"
                + "void main() {\n"
                + "  vec4 lens = texture2D(uLens, vUv);\n"
                + "  HIGHP vec2 uv = vUv * uBlurArea.xy + uBlurArea.zw;\n"
                + "  vec3 blurred = texture2D(uTex, uv).rgb;\n"
                + "  gl_FragColor = vec4(blurred" + (post.wash ? "" : " * mix(vec3(1.0), lens.rgb * 2.0, uVig)")
                + " * uFade, lens.a);\n}\n";
    }

    /** A GLSL ES 1.00 float literal. Float.toString always writes a decimal point ("2.0", "0.42", "1.0E-5"). */
    static String glsl(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) throw new IllegalArgumentException("not a number: " + v);
        return Float.toString(v);
    }
}
