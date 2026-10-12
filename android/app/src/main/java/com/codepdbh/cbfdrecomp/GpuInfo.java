package com.codepdbh.cbfdrecomp;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The phone's GPU, by name (OpenGL's renderer string, read once with a throwaway context), and
 * whether it's fast enough for the Balanced preset's higher resolution.
 */
final class GpuInfo {
    private static final String TAG = "ConkerRecomp";
    private static String renderer;

    private GpuInfo() {}

    static synchronized String renderer() {
        if (renderer != null) {
            return renderer;
        }
        renderer = "";
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display, null, 0, null, 0)) {
            return renderer;
        }
        try {
            int[] configAttributes = { EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE };
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            if (!EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, count, 0) || count[0] == 0) {
                return renderer;
            }
            EGLContext context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[] { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE }, 0);
            EGLSurface surface = EGL14.eglCreatePbufferSurface(display, configs[0],
                new int[] { EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE }, 0);
            if (EGL14.eglMakeCurrent(display, surface, surface, context)) {
                String name = GLES20.glGetString(GLES20.GL_RENDERER);
                renderer = name != null ? name : "";
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            }
            EGL14.eglDestroySurface(display, surface);
            EGL14.eglDestroyContext(display, context);
        } catch (Exception e) {
            Log.w(TAG, "Couldn't read the GPU's name", e);
        } finally {
            EGL14.eglTerminate(display);
        }
        return renderer;
    }

    /**
     * Fast enough for 960p (the Balanced preset's 4x): Adreno 640 and up, Mali-G6xx/G7xx and
     * Immortalis, Samsung's Xclipse. Measured: an Adreno 610 takes 50-65 ms a frame at 960p and
     * 6 ms at 480p.
     */
    static boolean isHighEnd() {
        String name = renderer();
        Matcher adreno = Pattern.compile("Adreno[^0-9]*([0-9]{3})").matcher(name);
        if (adreno.find()) {
            int model = Integer.parseInt(adreno.group(1));
            return model >= 640;
        }
        Matcher mali = Pattern.compile("Mali-G([0-9]+)").matcher(name);
        if (mali.find()) {
            int model = Integer.parseInt(mali.group(1));
            return model >= 600 || (model >= 76 && model < 100);
        }
        return name.contains("Immortalis") || name.contains("Xclipse");
    }
}
