package com.codepdbh.cbfdrecomp;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** The game: SDL's activity, running main.cpp's main in libConkerRecomp.so. */
public class GameActivity extends SDLActivity {
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
