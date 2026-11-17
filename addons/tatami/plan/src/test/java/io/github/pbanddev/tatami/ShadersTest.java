package io.github.pbanddev.tatami;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/** Yojōhan's shader text never changes, except the one reviewed edit: the fade to dark, applied last. */
public class ShadersTest {
    private static final Look.Post POST = Look.YOJOHAN.post;

    @Test
    public void yojohanSourcesAreAsFirstShipped() {
        assertEquals(SCENE_VERTEX, Shaders.SCENE_VERTEX);
        assertEquals(QUAD_VERTEX, Shaders.QUAD_VERTEX);
        assertEquals(PLAIN_FRAGMENT, Shaders.PLAIN_FRAGMENT);
        assertEquals(DOWNSAMPLE_FRAGMENT, Shaders.DOWNSAMPLE_FRAGMENT);
        assertEquals(BLUR_FRAGMENT, Shaders.blurFragment(POST));
    }

    @Test
    public void sceneAndBlendOnlyGainTheFade() {
        assertEquals(fade(SCENE_FRAGMENT, "uniform float uGrainAmp;\n",
                        "gl_FragColor = vec4(c, 1.0);", "gl_FragColor = vec4(c * uFade, 1.0);"),
                Shaders.sceneFragment(POST));
        assertEquals(fade(BLEND_FRAGMENT, "uniform float uVig;\n",
                        "gl_FragColor = vec4(blurred * mix(vec3(1.0), lens.rgb * 2.0, uVig), lens.a);",
                        "gl_FragColor = vec4(blurred * mix(vec3(1.0), lens.rgb * 2.0, uVig) * uFade, lens.a);"),
                Shaders.blendFragment(POST));
    }

    @Test
    public void nagayaBlursAsYojohan() {
        Look.Post nagaya = Look.NAGAYA.post;
        assertSame(POST.blurOval, nagaya.blurOval);
        assertEquals(POST.blurDivisor, nagaya.blurDivisor);
        assertArrayEquals(POST.blurWeights, nagaya.blurWeights, 0f);
        assertArrayEquals(POST.blurOffsets, nagaya.blurOffsets, 0f);
        assertEquals(Shaders.blurFragment(POST), Shaders.blurFragment(nagaya));
    }

    /** Its fills come washed from the CPU: no gain from the lens map, whose alpha still shapes the blur's oval. */
    @Test
    public void nagayaTakesNoLensGainAndHasItsOwnGrain() {
        assertEquals(NAGAYA_SCENE_FRAGMENT, Shaders.sceneFragment(Look.NAGAYA.post));
        assertEquals(NAGAYA_BLEND_FRAGMENT, Shaders.blendFragment(Look.NAGAYA.post));
    }

    @Test
    public void grainStepIsWrittenOnlyWhenItIsThere() {
        String nagaya = Shaders.sceneFragment(Look.NAGAYA.post);
        assertEquals(1, count(nagaya, "smoothstep("));
        assertEquals(0, count(Shaders.sceneFragment(POST), "smoothstep("));
    }

    /** Declares uFade after {@code after} and swaps the final line for its faded one. */
    private static String fade(String source, String after, String last, String faded) {
        assertEquals(1, count(source, after));
        assertEquals(1, count(source, last));
        return source.replace(after, after + "uniform float uFade;\n").replace(last, faded);
    }

    private static int count(String s, String part) {
        int n = 0;
        for (int i = s.indexOf(part); i >= 0; i = s.indexOf(part, i + 1)) n++;
        return n;
    }

    // Yojōhan's sources as first shipped.

    private static final String SCENE_VERTEX = """
            uniform mat4 uMatrix;
            attribute vec2 aPos;
            attribute vec4 aColor;
            varying vec4 vColor;
            void main() { vColor = aColor; gl_Position = uMatrix * vec4(aPos, 0.0, 1.0); }
            """;

    private static final String QUAD_VERTEX = """
            attribute vec2 aPos;
            varying vec2 vUv;
            void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }
            """;

    private static final String PLAIN_FRAGMENT = """
            precision mediump float;
            varying vec4 vColor;
            void main() { gl_FragColor = vColor; }
            """;

    private static final String DOWNSAMPLE_FRAGMENT = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uTex;
            uniform vec2 uTexel;
            varying vec2 vUv;
            void main() {
              gl_FragColor = 0.25 * (texture2D(uTex, vUv + uTexel * vec2(-1.0, -1.0))
                  + texture2D(uTex, vUv + uTexel * vec2(1.0, -1.0))
                  + texture2D(uTex, vUv + uTexel * vec2(-1.0, 1.0))
                  + texture2D(uTex, vUv + uTexel * vec2(1.0, 1.0)));
            }
            """;

    private static final String BLEND_FRAGMENT = """
            precision mediump float;
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            #define HIGHP highp
            #else
            #define HIGHP mediump
            #endif
            uniform sampler2D uTex;
            uniform sampler2D uLens;
            uniform float uVig;
            uniform HIGHP vec4 uBlurArea;
            varying HIGHP vec2 vUv;
            void main() {
              vec4 lens = texture2D(uLens, vUv);
              HIGHP vec2 uv = vUv * uBlurArea.xy + uBlurArea.zw;
              vec3 blurred = texture2D(uTex, uv).rgb;
              gl_FragColor = vec4(blurred * mix(vec3(1.0), lens.rgb * 2.0, uVig), lens.a);
            }
            """;

    private static final String SCENE_FRAGMENT = """
            precision mediump float;
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            #define HIGHP highp
            #else
            #define HIGHP mediump
            #endif
            uniform vec2 uInvSize;
            uniform float uVig;
            uniform float uGrainAmp;
            uniform HIGHP vec2 uGrainOffset;
            uniform sampler2D uGrain;
            uniform sampler2D uLens;
            varying vec4 vColor;
            void main() {
              vec3 c = vColor.rgb * mix(vec3(1.0), texture2D(uLens, gl_FragCoord.xy * uInvSize).rgb * 2.0, uVig);
              float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
              HIGHP vec2 g = gl_FragCoord.xy / 256.0 + uGrainOffset;
              float n = (texture2D(uGrain, g).r - 0.5) * 8.0;
              c += n * (0.005098039 + 0.006862745 * luma * luma) * uGrainAmp;
              gl_FragColor = vec4(c, 1.0);
            }
            """;

    private static final String BLUR_FRAGMENT = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uTex;
            uniform vec2 uStep;
            varying vec2 vUv;
            void main() {
              vec4 c = vec4(0.0);
              c += texture2D(uTex, vUv) * 0.2719;
              c += (texture2D(uTex, vUv + uStep * 1.3333) + texture2D(uTex, vUv - uStep * 1.3333)) * 0.3235;
              c += (texture2D(uTex, vUv + uStep * 3.165) + texture2D(uTex, vUv - uStep * 3.165)) * 0.0406;
              gl_FragColor = c;
            }
            """;

    // Nagaya's: Yojōhan's without the lens map's gain. Grain (1.4 + 2.35 * smoothstep(0.25, 0.30, luma)) * 2/3 levels.

    private static final String NAGAYA_SCENE_FRAGMENT = """
            precision mediump float;
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            #define HIGHP highp
            #else
            #define HIGHP mediump
            #endif
            uniform float uGrainAmp;
            uniform float uFade;
            uniform HIGHP vec2 uGrainOffset;
            uniform sampler2D uGrain;
            varying vec4 vColor;
            void main() {
              vec3 c = vColor.rgb;
              float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
              HIGHP vec2 g = gl_FragCoord.xy / 256.0 + uGrainOffset;
              float n = (texture2D(uGrain, g).r - 0.5) * 8.0;
              c += n * (0.0036601308 + 0.0061437907 * smoothstep(0.25, 0.3, luma)) * uGrainAmp;
              gl_FragColor = vec4(c * uFade, 1.0);
            }
            """;

    private static final String NAGAYA_BLEND_FRAGMENT = """
            precision mediump float;
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            #define HIGHP highp
            #else
            #define HIGHP mediump
            #endif
            uniform sampler2D uTex;
            uniform sampler2D uLens;
            uniform float uFade;
            uniform HIGHP vec4 uBlurArea;
            varying HIGHP vec2 vUv;
            void main() {
              vec4 lens = texture2D(uLens, vUv);
              HIGHP vec2 uv = vUv * uBlurArea.xy + uBlurArea.zw;
              vec3 blurred = texture2D(uTex, uv).rgb;
              gl_FragColor = vec4(blurred * uFade, lens.a);
            }
            """;
}
