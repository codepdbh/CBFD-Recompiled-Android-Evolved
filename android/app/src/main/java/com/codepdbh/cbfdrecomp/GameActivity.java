package com.codepdbh.cbfdrecomp;

import android.os.Bundle;
import android.view.ViewGroup;
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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Draw under the camera cutout too: the game fills the whole screen.
        getWindow().getAttributes().layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if (mLayout != null) {
            addContentView(new TouchControlsView(this), new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        hideSystemBars();
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
        // A ROM put in the folder is loaded (and kept) the first time: the launcher's
        // Load ROM has no file picker on Android yet.
        File rom = new File(folder, "rom.z64");
        if (rom.isFile()) {
            args.add("--rom");
            args.add(rom.getAbsolutePath());
        }
        return args.toArray(new String[0]);
    }
}
