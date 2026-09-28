package com.codepdbh.cbfdrecomp;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The game: SDL's activity, running main.cpp's main in libConkerRecomp.so, with the
 * on-screen controls over it.
 */
public class GameActivity extends SDLActivity {
    private TouchControlsView touchControls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Draw under the camera cutout too: the game fills the whole screen.
        getWindow().getAttributes().layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if (mLayout != null) {
            touchControls = new TouchControlsView(this);
            touchControls.setOnSaveCopies(this::showSaveCopies);
            touchControls.setOnMenu(() -> new PauseMenu(this, touchControls).show());
            touchControls.setOnDeviceLost(this::onDeviceLost);
            addContentView(touchControls, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        hideSystemBars();
        // The screen at least as fast as the game's frame rate.
        FrameRates.requestFor(this, SettingsActivity.currentFrameRate(
            SettingsActivity.read("graphics.json"), FrameRates.displayMax(this)));
    }

    // Save copies (SaveSlots): the overlay's save button.

    void showSaveCopies() {
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(16 * density);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad, pad / 2, pad, 0);

        TextView about = new TextView(this);
        about.setText("Una copia guarda tu partida tal como el juego la guardó en el último checkpoint "
            + "(no el momento exacto en que tocas Guardar). Al cargarla, el juego se reinicia: "
            + "elige tu partida en su menú para seguir desde ese checkpoint.");
        about.setTextColor(Color.LTGRAY);
        about.setTextSize(13);
        list.addView(about);

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Copias de partida")
            .setView(list)
            .setNegativeButton("Cerrar", null)
            .create();

        for (int slot = 1; slot <= SaveSlots.COUNT; slot++) {
            final int n = slot;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, pad, 0, 0);
            TextView label = new TextView(this);
            String when = SaveSlots.describe(n);
            String state = when == null ? "Vacío"
                : when + (SaveSlots.sameAsSave(n) ? "\nIgual a tu partida actual" : "");
            label.setText("Espacio " + n + "\n" + state);
            label.setTextColor(Color.WHITE);
            label.setTextSize(15);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            Button save = new Button(this);
            save.setText("Guardar");
            save.setAllCaps(false);
            save.setEnabled(SaveSlots.hasSave());
            save.setOnClickListener(v -> {
                try {
                    SaveSlots.save(n);
                    Toast.makeText(this, "Copia guardada en el espacio " + n, Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Toast.makeText(this, "No se pudo guardar la copia: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
                dialog.dismiss();
            });
            row.addView(save);

            Button load = new Button(this);
            load.setText("Cargar");
            load.setAllCaps(false);
            load.setEnabled(SaveSlots.hasSlot(n));
            load.setOnClickListener(v -> {
                dialog.dismiss();
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("¿Cargar el espacio " + n + "?")
                    .setMessage((SaveSlots.sameAsSave(n)
                            ? "Esta copia es igual a tu partida actual: el juego no ha guardado nada desde entonces.\n\n"
                            : "")
                        + "El juego se reiniciará con esa copia. Después, elige tu partida en el menú del juego. "
                        + "Lo que hayas avanzado desde tu último checkpoint se perderá.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Cargar", (d, w) -> restartWith(n))
                    .show();
            });
            row.addView(load);
            list.addView(row);
        }
        if (!SaveSlots.hasSave()) {
            TextView none = new TextView(this);
            none.setText("Todavía no hay partida guardada: el juego guarda al llegar a un checkpoint.");
            none.setTextColor(0xFFF08A24);
            none.setTextSize(13);
            none.setPadding(0, pad, 0, 0);
            list.addView(none);
        }
        dialog.show();
    }

    /**
     * The renderer lost the GPU (the driver reset it: a fault, or a job that took too long, more
     * likely with the screen being recorded). Nothing more can be drawn, so the game starts again
     * from its last save.
     */
    private void onDeviceLost() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Error de la GPU")
            .setMessage("La GPU del teléfono se reinició y el juego no puede seguir dibujando. "
                + "Se volverá a abrir desde tu último guardado.\n\n"
                + "Si pasa seguido, prueba la calidad gráfica \"Fluido\".")
            .setCancelable(false)
            .setPositiveButton("Reiniciar el juego", (d, w) -> restartWith(MainActivity.RESTART_ONLY))
            .setNegativeButton("Salir al inicio", (d, w) -> restartWith(0))
            .show();
    }

    /**
     * Ends the game, and the start screen restores the copy (if any, or plays again with
     * RESTART_ONLY). The game's process just ends: waiting for the game to wind down hangs once
     * the renderer has lost the GPU. Its save is already written (librecomp writes it at once).
     */
    /** Back to the start screen: the game's process ends at once (see restartWith). */
    void exitToStart() {
        restartWith(0);
    }

    private void restartWith(int slot) {
        Intent intent = new Intent(this, MainActivity.class);
        if (slot != 0) {
            intent.putExtra(MainActivity.EXTRA_RESTORE_SLOT, slot);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    // The phone's gyro only while the game shows.
    @Override
    protected void onPause() {
        if (touchControls != null) {
            touchControls.pauseSensors();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (touchControls != null && touchControls.isAttachedToWindow()) {
            touchControls.resumeSensors();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // The game runs in its own process (":game" in the manifest): end it with the game,
        // so the next one starts clean (SDL and the runtime only start once per process).
        if (isFinishing()) {
            System.exit(0);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    /** Immersive: no status or navigation bar; a swipe from an edge shows them for a moment. */
    private void hideSystemBars() {
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "ConkerRecomp" };
    }

    @Override
    protected String[] getArguments() {
        File folder = MainActivity.gameFolder();
        List<String> args = new ArrayList<>();
        args.add("--data");
        args.add(folder.getAbsolutePath());
        // MainActivity is the launcher: straight into the game.
        args.add("--start");
        // The ROM picked on the start screen is checked and kept the first time.
        File rom = new File(folder, "rom.z64");
        if (rom.isFile()) {
            args.add("--rom");
            args.add(rom.getAbsolutePath());
        }
        return args.toArray(new String[0]);
    }
}
