package com.codepdbh.cbfdrecomp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.TextView;

import java.io.File;

/**
 * Test activity: runs the headless game for a few seconds. Everything lives in
 * ConkerRecompiled/ at the root of the shared storage (/storage/emulated/0): rom.z64, and
 * the game's data there too (mods/*.nrm, mods.json, saves). Output goes to logcat (tag
 * ConkerRecomp).
 */
public class MainActivity extends Activity {
    private TextView status;
    private boolean started = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        status = new TextView(this);
        status.setPadding(48, 48, 48, 48);
        setContentView(status);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (started) {
            return;
        }
        if (!Environment.isExternalStorageManager()) {
            status.setText("Allow \"All files access\" so the game can use the ConkerRecompiled folder, then come back.");
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        started = true;
        startGame();
    }

    static File gameFolder() {
        return new File(Environment.getExternalStorageDirectory(), "ConkerRecompiled");
    }

    private void startGame() {
        File folder = gameFolder();
        folder.mkdirs();
        new File(folder, "mods").mkdirs();
        File rom = new File(folder, "rom.z64");
        if (!rom.isFile()) {
            status.setText("Put your US ROM at " + rom.getAbsolutePath() + " and reopen the app.");
            started = false;
            return;
        }
        status.setText("Running the game headless from " + folder.getAbsolutePath()
            + " (see logcat, tag ConkerRecomp)...");
        String[] args = {
            "--headless",
            "--rom", rom.getAbsolutePath(),
            "--data", folder.getAbsolutePath(),
            "--seconds", "20",
        };
        new Thread(() -> {
            int result = NativeBridge.run(args);
            runOnUiThread(() -> status.setText("Finished with code " + result));
        }, "ConkerMain").start();
    }
}
