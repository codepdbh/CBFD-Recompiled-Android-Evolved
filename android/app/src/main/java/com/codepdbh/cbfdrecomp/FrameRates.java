package com.codepdbh.cbfdrecomp;

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The frame rates offered: 30 (the original), 60, and each faster rate the screen can show,
 * up to its maximum. The game draws at the chosen rate on its own (RT64's manual rate,
 * rr_option Manual with rr_manual_value), whatever rate the screen happens to run at: another
 * app such as the screen recorder can change that without upsetting the game.
 */
final class FrameRates {
    private FrameRates() {}

    /** The screen's fastest refresh rate, rounded (60 if it can't be told). */
    static int displayMax(Context context) {
        Display display = display(context);
        if (display == null) {
            return 60;
        }
        float max = 0;
        for (Display.Mode mode : display.getSupportedModes()) {
            max = Math.max(max, mode.getRefreshRate());
        }
        return max >= 59 ? Math.round(max) : 60;
    }

    /** 30, 60 and the screen's rates above 60. */
    static List<Integer> choices(Context context) {
        TreeSet<Integer> rates = new TreeSet<>();
        rates.add(30);
        rates.add(60);
        Display display = display(context);
        if (display != null) {
            for (Display.Mode mode : display.getSupportedModes()) {
                int rate = Math.round(mode.getRefreshRate());
                if (rate > 60) {
                    rates.add(rate);
                }
            }
        }
        return new ArrayList<>(rates);
    }

    /**
     * Asks the system to run the screen at least as fast as the game draws (Samsung's adaptive
     * rate would otherwise often keep it at 60 Hz).
     */
    static void requestFor(Activity activity, int fps) {
        Display display = display(activity);
        if (display == null) {
            return;
        }
        Display.Mode current = display.getMode();
        Display.Mode best = null;
        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() != current.getPhysicalWidth() || mode.getPhysicalHeight() != current.getPhysicalHeight()) {
                continue;
            }
            float rate = mode.getRefreshRate();
            // The slowest mode that still shows every frame; else the fastest there is.
            if (best == null
                || (rate >= fps - 1 && (best.getRefreshRate() < fps - 1 || rate < best.getRefreshRate()))
                || (best.getRefreshRate() < fps - 1 && rate > best.getRefreshRate())) {
                best = mode;
            }
        }
        if (best != null) {
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            params.preferredDisplayModeId = best.getModeId();
            activity.getWindow().setAttributes(params);
        }
    }

    private static Display display(Context context) {
        DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        return manager != null ? manager.getDisplay(Display.DEFAULT_DISPLAY) : null;
    }
}
