package com.codepdbh.cbfdrecomp;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * The in-game menu (the overlay's ☰ button): big buttons to go on, keep save copies, edit the
 * touch controls or leave, and the settings that matter while playing, applied at once through
 * the game's own settings (touch_controls.cpp). The desktop menu is still there for remapping a
 * controller or keyboard.
 */
final class PauseMenu {
    private static final int ORANGE = 0xFFF08A24, MUTED = 0xFFB8A898;

    static native void nativeSetOption(String config, String option, int kind, double value);
    static native double nativeGetOption(String config, String option);

    private static final int ENUM = 0, NUMBER = 1;

    private final GameActivity activity;
    private final TouchControlsView touchControls;
    private final float density;
    private Dialog dialog;

    PauseMenu(GameActivity activity, TouchControlsView touchControls) {
        this.activity = activity;
        this.touchControls = touchControls;
        this.density = activity.getResources().getDisplayMetrics().density;
    }

    void show() {
        dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.setContentView(build());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    private void close() {
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    private View build() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(0xD9000000);
        root.setPadding(dp(32), dp(20), dp(32), dp(20));
        root.setGravity(Gravity.CENTER_VERTICAL);

        // The actions.
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Pausa", 28, Color.WHITE, true);
        title.setPadding(0, 0, 0, dp(12));
        actions.addView(title);
        actions.addView(action("▶   Continuar", true, v -> close()));
        actions.addView(action("💾   Copias de partida", false, v -> {
            close();
            activity.showSaveCopies();
        }));
        actions.addView(action("✎   Mover los botones", false, v -> {
            close();
            touchControls.startEditing();
            touchControls.invalidate();
        }));
        actions.addView(action("🎮   Menú avanzado (mando y teclado)", false, v -> {
            close();
            touchControls.pressMenuButton();
        }));
        actions.addView(action("⏏   Salir al inicio", false, v -> {
            close();
            activity.finish();
        }));
        root.addView(actions, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // The settings.
        LinearLayout settings = new LinearLayout(activity);
        settings.setOrientation(LinearLayout.VERTICAL);
        settings.setBackgroundResource(R.drawable.panel);
        settings.setPadding(dp(20), dp(14), dp(20), dp(14));
        TextView heading = text("Ajustes rápidos", 18, ORANGE, true);
        heading.setPadding(0, 0, 0, dp(6));
        settings.addView(heading);
        settings.addView(qualityButtons());
        settings.addView(frameRateButtons());
        settings.addView(slider("Volumen", "sound", "main_volume", 0, 100));
        settings.addView(slider("Transparencia de los botones", "general", "android_touch_opacity", 10, 100));
        settings.addView(toggle("Apuntar moviendo el teléfono (con R)", "general", "android_phone_gyro"));
        settings.addView(toggle("Vibrar con el juego", "general", "android_phone_rumble"));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(settings);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f);
        params.setMargins(dp(28), 0, 0, 0);
        root.addView(scroll, params);
        return root;
    }

    // Graphics quality, as on the settings screen: resolution, antialiasing and frame rate.
    // The indices are ultramodern's enums (Resolution, Antialiasing, RefreshRate).
    private View qualityButtons() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(6));
        box.addView(text("Calidad gráfica", 16, Color.WHITE, false));
        TextView description = text("", 13, MUTED, false);
        LinearLayout row = new LinearLayout(activity);
        String[] labels = { "Fluido", "Equilibrado", "Calidad" };
        String[] descriptions = {
            "Resolución media: más fluido, menos calor y batería.",
            "Resolución completa. Recomendado.",
            "Resolución completa con bordes suaves (antialiasing). Exigente.",
        };
        Button[] buttons = new Button[3];
        int current = currentQuality();
        for (int i = 0; i < 3; i++) {
            final int preset = i;
            Button button = new Button(activity);
            button.setText(labels[i]);
            button.setAllCaps(false);
            button.setTextColor(Color.WHITE);
            button.setOnClickListener(v -> {
                applyQuality(preset);
                description.setText(descriptions[preset]);
                highlight(buttons, preset);
            });
            buttons[i] = button;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
            params.setMargins(dp(3), dp(6), dp(3), dp(4));
            row.addView(button, params);
        }
        highlight(buttons, current);
        description.setText(descriptions[current]);
        box.addView(row);
        box.addView(description);
        return box;
    }

    private int currentQuality() {
        double res = nativeGetOption("graphics", "res_option");
        double msaa = nativeGetOption("graphics", "msaa_option");
        if (res == 0 || res == 1) {
            return 0;
        }
        if (msaa > 0) {
            return 2;
        }
        return 1;
    }

    private static void applyQuality(int preset) {
        switch (preset) {
            case 0:
                nativeSetOption("graphics", "res_option", ENUM, 1);  // Original2x
                nativeSetOption("graphics", "msaa_option", ENUM, 0); // None
                break;
            case 1:
                nativeSetOption("graphics", "res_option", ENUM, 2);  // Auto
                nativeSetOption("graphics", "msaa_option", ENUM, 0);
                break;
            case 2:
                nativeSetOption("graphics", "res_option", ENUM, 2);
                nativeSetOption("graphics", "msaa_option", ENUM, 1); // MSAA2X
                break;
        }
    }

    // The frame rate: 30 (Original), or RT64's manual rate, independent of the screen's.
    private View frameRateButtons() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(6));
        box.addView(text("Cuadros por segundo", 16, Color.WHITE, false));
        TextView description = text("", 13, MUTED, false);
        int max = FrameRates.displayMax(activity);
        java.util.List<Integer> rates = FrameRates.choices(activity);
        double rr = nativeGetOption("graphics", "rr_option");
        int current = rr == 0 ? 30 : rr == 1 ? max : (int) Math.round(nativeGetOption("graphics", "rr_manual_value"));
        LinearLayout row = new LinearLayout(activity);
        Button[] buttons = new Button[rates.size()];
        int selected = 0;
        for (int i = 0; i < rates.size(); i++) {
            final int fps = rates.get(i);
            final int index = i;
            Button button = new Button(activity);
            button.setText(fps == max && fps > 60 ? fps + " (máx)" : String.valueOf(fps));
            button.setAllCaps(false);
            button.setTextColor(Color.WHITE);
            button.setOnClickListener(v -> {
                if (fps <= 30) {
                    nativeSetOption("graphics", "rr_option", ENUM, 0);       // Original
                } else {
                    nativeSetOption("graphics", "rr_manual_value", NUMBER, fps);
                    nativeSetOption("graphics", "rr_option", ENUM, 2);       // Manual
                }
                FrameRates.requestFor(activity, fps);
                description.setText(SettingsActivity.frameRateDescription(fps));
                highlight(buttons, index);
            });
            buttons[i] = button;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
            params.setMargins(dp(3), dp(6), dp(3), dp(4));
            row.addView(button, params);
            if (Math.abs(fps - current) < Math.abs(rates.get(selected) - current)) {
                selected = i;
            }
        }
        highlight(buttons, selected);
        description.setText(SettingsActivity.frameRateDescription(rates.get(selected)));
        box.addView(row);
        box.addView(description);
        return box;
    }

    private void highlight(Button[] buttons, int selected) {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setBackgroundResource(i == selected ? R.drawable.btn_primary : R.drawable.btn_secondary);
        }
    }

    // Building blocks.

    private Button action(String label, boolean primary, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(primary ? 20 : 17);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(20), 0, dp(20), 0);
        button.setBackgroundResource(primary ? R.drawable.btn_primary : R.drawable.btn_secondary);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(primary ? 58 : 50));
        params.setMargins(0, dp(5), 0, dp(5));
        button.setLayoutParams(params);
        return button;
    }

    private View toggle(String label, String config, String option) {
        Switch toggle = new Switch(activity);
        toggle.setText(label);
        toggle.setTextSize(16);
        toggle.setTextColor(Color.WHITE);
        toggle.setThumbTintList(ColorStateList.valueOf(ORANGE));
        toggle.setPadding(0, dp(10), 0, dp(10));
        toggle.setChecked(nativeGetOption(config, option) == 1); // On
        toggle.setOnCheckedChangeListener((b, on) -> nativeSetOption(config, option, ENUM, on ? 1 : 0));
        return toggle;
    }

    private View slider(String label, String config, String option, int min, int max) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(4));
        int value = (int) Math.round(nativeGetOption(config, option));
        if (value < min) {
            value = max;
        }
        TextView title = text(label + ": " + value + "%", 16, Color.WHITE, false);
        row.addView(title);
        SeekBar bar = new SeekBar(activity);
        bar.setMin(min);
        bar.setMax(max);
        bar.setProgress(value);
        bar.setProgressTintList(ColorStateList.valueOf(ORANGE));
        bar.setThumbTintList(ColorStateList.valueOf(ORANGE));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                title.setText(label + ": " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                nativeSetOption(config, option, NUMBER, seekBar.getProgress());
            }
        });
        row.addView(bar);
        return row;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private int dp(float value) {
        return Math.round(value * density);
    }
}
