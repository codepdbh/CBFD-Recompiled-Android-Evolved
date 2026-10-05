package com.codepdbh.cbfdrecomp;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The game's settings for a phone, in plain words. It edits the same files the game's own
 * menus do (general.json, graphics.json and sound.json in ConkerRecompiled/), which the game
 * reads when it starts.
 */
public class SettingsActivity extends Activity {
    private static final String TAG = "ConkerRecomp";
    private static final int ORANGE = 0xFFF08A24;

    private JSONObject general, graphics, sound;
    private float density;

    // Graphics presets: resolution and antialiasing (the frame rate is chosen on its own).
    private enum Preset {
        PERFORMANCE("Fluido", "Resolución media (480p): lo más fluido, menos calor y batería."),
        BALANCED("Equilibrado", "Resolución alta (hasta 960p): nítido y fluido. Recomendado."),
        QUALITY("Calidad", "La resolución de la pantalla, con bordes suaves (antialiasing). Muy exigente.");

        final String label, description;

        Preset(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        general = read("general.json");
        graphics = read("graphics.json");
        sound = read("sound.json");
        setContentView(build());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        write("general.json", general);
        write("graphics.json", graphics);
        write("sound.json", sound);
    }

    // Once, the Balanced preset rather than the desktop's defaults (full resolution, MSAA and the
    // display's refresh rate, too much for a phone). Kept from then on.
    static void writeDefaults() {
        File marker = new File(MainActivity.gameFolder(), ".android_graphics_defaults");
        if (marker.exists()) {
            return;
        }
        JSONObject json = read("graphics.json");
        try {
            applyPreset(json, Preset.BALANCED);
            applyFrameRate(json, 60);
            if (!json.has("ar_option")) {
                json.put("ar_option", "Expand");
                json.put("hr_option", "Clamp16x9");
                json.put("wm_option", "Windowed");
                json.put("api_option", "Auto");
                json.put("hpfb_option", "Off");
                json.put("ds_option", 0);
                json.put("developer_mode", false);
            }
            write("graphics.json", json);
            marker.createNewFile();
        } catch (Exception e) {
            Log.w(TAG, "Couldn't write the graphics defaults", e);
        }
    }

    /**
     * Balanced renders at up to 4x the N64's 240 lines (960p), not at the screen's resolution: as
     * sharp on a phone, and less than half the GPU work at 1440p, so high frame rates hold steady.
     * Quality keeps the screen's (this marker), which the game's own "Auto" means.
     */
    private static File nativeResolutionMarker() {
        return new File(MainActivity.gameFolder(), ".android_native_resolution");
    }

    /** The resolution scale the game should use instead of the screen's, or 0 for the screen's. */
    static int resolutionScale(int screenHeight) {
        JSONObject json = read("graphics.json");
        if (!json.optString("res_option", "Auto").equals("Auto") || nativeResolutionMarker().exists()) {
            return 0;
        }
        int screenScale = Math.max(1, screenHeight / 240);
        return screenScale > 4 ? 4 : 0;
    }

    private static void applyPreset(JSONObject json, Preset preset) throws Exception {
        try {
            if (preset == Preset.QUALITY) {
                nativeResolutionMarker().createNewFile();
            } else {
                nativeResolutionMarker().delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "Couldn't set the resolution marker", e);
        }
        switch (preset) {
            case PERFORMANCE:
                json.put("res_option", "Original2x");
                json.put("msaa_option", "None");
                break;
            case BALANCED:
                json.put("res_option", "Auto");
                json.put("msaa_option", "None");
                break;
            case QUALITY:
                json.put("res_option", "Auto");
                json.put("msaa_option", "MSAA2X");
                break;
        }
        if (!json.has("rr_option")) {
            applyFrameRate(json, 60);
        }
    }

    /** 30 is the game's own rate; others are RT64's manual rate, whatever the screen runs at. */
    private static void applyFrameRate(JSONObject json, int fps) throws Exception {
        if (fps <= 30) {
            json.put("rr_option", "Original");
        } else {
            json.put("rr_option", "Manual");
            json.put("rr_manual_value", fps);
        }
    }

    /** The frame rate set (the Display option counts as the screen's maximum). */
    static int currentFrameRate(JSONObject json, int displayMax) {
        switch (json.optString("rr_option", "Manual")) {
            case "Original": return 30;
            case "Display": return displayMax;
            default: return json.optInt("rr_manual_value", 60);
        }
    }

    private Preset currentPreset() {
        String res = graphics.optString("res_option", "Auto");
        String msaa = graphics.optString("msaa_option", "None");
        if (res.equals("Original2x") || res.equals("Original")) {
            return Preset.PERFORMANCE;
        }
        if (!msaa.equals("None") || nativeResolutionMarker().exists()) {
            return Preset.QUALITY;
        }
        return Preset.BALANCED;
    }

    // The screen.

    private View build() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundResource(R.drawable.bg_launcher);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(24), dp(14), dp(24), dp(6));
        Button back = new Button(this);
        back.setText("‹  Volver");
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setBackgroundResource(R.drawable.btn_secondary);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        TextView title = text("Ajustes", 24, Color.WHITE, true);
        title.setPadding(dp(18), 0, 0, 0);
        bar.addView(title);
        page.addView(bar);

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setPadding(dp(16), 0, dp(16), dp(16));
        LinearLayout left = column(), right = column();
        columns.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        columns.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(columns);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // Graphics.
        LinearLayout card = card(left, "Gráficos");
        TextView presetDescription = text(currentPreset().description, 13, 0xFFB8A898, false);
        LinearLayout presets = new LinearLayout(this);
        Button[] presetButtons = new Button[Preset.values().length];
        for (Preset preset : Preset.values()) {
            Button button = new Button(this);
            button.setText(preset.label);
            button.setAllCaps(false);
            button.setTextColor(Color.WHITE);
            presetButtons[preset.ordinal()] = button;
            button.setOnClickListener(v -> {
                try {
                    applyPreset(graphics, preset);
                } catch (Exception e) {
                    Log.w(TAG, "preset", e);
                }
                presetDescription.setText(preset.description);
                highlight(presetButtons, preset.ordinal());
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
            params.setMargins(dp(3), 0, dp(3), 0);
            presets.addView(button, params);
        }
        highlight(presetButtons, currentPreset().ordinal());
        card.addView(presets);
        card.addView(presetDescription);
        card.addView(frameRateRow());
        card.addView(toggle("Pantalla ancha", "Usa todo el ancho del teléfono en vez del 4:3 original.",
            !graphics.optString("ar_option", "Expand").equals("Original"),
            on -> put(graphics, "ar_option", on ? "Expand" : "Original")));

        // Sound.
        card = card(left, "Sonido");
        card.addView(slider("Volumen", sound.optInt("main_volume", 100), 0, 100, "%",
            value -> put(sound, "main_volume", value)));

        // Touch controls.
        card = card(right, "Controles táctiles");
        String visibility = general.optString("android_touch_visibility", "Auto");
        card.addView(toggle("Ocultar con un mando conectado",
            "Si conectas un mando Bluetooth o USB, los botones en pantalla desaparecen solos.",
            !visibility.equals("Always"),
            on -> put(general, "android_touch_visibility", on ? "Auto" : "Always")));
        card.addView(slider("Transparencia de los botones", general.optInt("android_touch_opacity", 60), 10, 100, "%",
            value -> put(general, "android_touch_opacity", value)));
        card.addView(toggle("Vibrar al tocar un botón", null,
            general.optString("android_touch_haptics", "On").equals("On"),
            on -> put(general, "android_touch_haptics", on ? "On" : "Off")));
        card.addView(text("Para mover o cambiar el tamaño de los botones, toca ✎ arriba en el juego.",
            13, 0xFFB8A898, false));

        // Motion and vibration.
        card = card(right, "Movimiento y vibración");
        card.addView(toggle("Apuntar moviendo el teléfono",
            "Al mantener R para mirar, gira e inclina el teléfono para apuntar.",
            general.optString("android_phone_gyro", "On").equals("On"),
            on -> put(general, "android_phone_gyro", on ? "On" : "Off")));
        card.addView(slider("Sensibilidad al mover el teléfono", general.optInt("gyro_sensitivity", 25), 5, 100, "%",
            value -> put(general, "gyro_sensitivity", value)));
        card.addView(toggle("Invertir arriba/abajo al apuntar con el teléfono", null,
            general.optString("look_gyro_invert", "None").equals("InvertY"),
            on -> put(general, "look_gyro_invert", on ? "InvertY" : "None")));
        card.addView(toggle("Vibrar con el juego",
            "El teléfono vibra con los golpes y explosiones, como el Rumble Pak.",
            general.optString("android_phone_rumble", "On").equals("On"),
            on -> put(general, "android_phone_rumble", on ? "On" : "Off")));
        card.addView(slider("Fuerza de la vibración", general.optInt("rumble_strength", 25), 0, 100, "%",
            value -> put(general, "rumble_strength", value)));
        card.addView(toggle("Cámara con R: arriba mira abajo",
            "Como en el juego original: al mirar con R, empujar el stick arriba mira hacia abajo.",
            general.optString("look_stick_invert", "InvertY").equals("InvertY"),
            on -> put(general, "look_stick_invert", on ? "InvertY" : "None")));

        return page;
    }

    // The frame rate: 30, 60 and the screen's faster rates, up to its maximum.
    private View frameRateRow() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(12), 0, dp(4));
        int max = FrameRates.displayMax(this);
        box.addView(text("Cuadros por segundo", 16, Color.WHITE, false));
        TextView description = text("", 13, 0xFFB8A898, false);
        java.util.List<Integer> rates = FrameRates.choices(this);
        LinearLayout row = new LinearLayout(this);
        Button[] buttons = new Button[rates.size()];
        int current = currentFrameRate(graphics, max);
        int selected = 0;
        for (int i = 0; i < rates.size(); i++) {
            final int fps = rates.get(i);
            final int index = i;
            Button button = new Button(this);
            button.setText(fps == max && fps > 60 ? fps + " (máx)" : String.valueOf(fps));
            button.setAllCaps(false);
            button.setTextColor(Color.WHITE);
            button.setOnClickListener(v -> {
                try {
                    applyFrameRate(graphics, fps);
                } catch (Exception e) {
                    Log.w(TAG, "fps", e);
                }
                description.setText(frameRateDescription(fps));
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
        description.setText(frameRateDescription(rates.get(selected)));
        box.addView(row);
        box.addView(description);
        return box;
    }

    static String frameRateDescription(int fps) {
        if (fps <= 30) {
            return "Como en la consola. Lo más ligero.";
        }
        if (fps <= 60) {
            return "Movimiento suave. Recomendado.";
        }
        return "Lo más suave que tu pantalla puede mostrar. Exigente: calienta más y gasta más batería.";
    }

    private void highlight(Button[] buttons, int selected) {
        for (int i = 0; i < buttons.length; i++) {
            if (i == selected) {
                buttons[i].setBackgroundResource(R.drawable.btn_primary);
            } else {
                buttons[i].setBackgroundResource(R.drawable.btn_secondary);
            }
        }
    }

    // Building blocks.

    private interface OnToggle { void changed(boolean on); }
    private interface OnValue { void changed(int value); }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(8), 0, dp(8), 0);
        return column;
    }

    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.panel);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        TextView heading = text(title, 18, ORANGE, true);
        heading.setPadding(0, 0, 0, dp(8));
        card.addView(heading);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, dp(8));
        parent.addView(card, params);
        return card;
    }

    private View toggle(String label, String description, boolean checked, OnToggle listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextSize(16);
        toggle.setTextColor(Color.WHITE);
        toggle.setChecked(checked);
        toggle.setThumbTintList(ColorStateList.valueOf(ORANGE));
        toggle.setOnCheckedChangeListener((b, on) -> listener.changed(on));
        row.addView(toggle);
        if (description != null) {
            row.addView(text(description, 13, 0xFFB8A898, false));
        }
        return row;
    }

    private View slider(String label, int value, int min, int max, String unit, OnValue listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView title = text(label + ": " + value + unit, 16, Color.WHITE, false);
        row.addView(title);
        SeekBar bar = new SeekBar(this);
        bar.setMin(min);
        bar.setMax(max);
        bar.setProgress(value);
        bar.setProgressTintList(ColorStateList.valueOf(ORANGE));
        bar.setThumbTintList(ColorStateList.valueOf(ORANGE));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                title.setText(label + ": " + progress + unit);
                listener.changed(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        row.addView(bar);
        return row;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
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

    // The files.

    private static void put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (Exception e) {
            Log.w(TAG, "Couldn't set " + key, e);
        }
    }

    static JSONObject read(String name) {
        File file = new File(MainActivity.gameFolder(), name);
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                Log.w(TAG, "Couldn't read " + file, e);
            }
        }
        return new JSONObject();
    }

    private static void write(String name, JSONObject json) {
        writeFile(new File(MainActivity.gameFolder(), name), json);
    }

    private static void writeFile(File file, JSONObject json) {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(json.toString(4).getBytes(StandardCharsets.UTF_8));
        } catch (IOException | org.json.JSONException e) {
            Log.w(TAG, "Couldn't write " + file, e);
        }
    }
}
