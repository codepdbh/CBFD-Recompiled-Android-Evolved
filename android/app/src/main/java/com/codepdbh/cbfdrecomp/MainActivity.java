package com.codepdbh.cbfdrecomp;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Prepares the game's folder, then starts the game (GameActivity). Everything lives in
 * ConkerRecompiled/ at the root of the shared storage (/storage/emulated/0): the ROM, mods,
 * saves, settings, and the launcher's assets. Reading and writing it takes "All files
 * access", which this asks for first.
 */
public class MainActivity extends Activity {
    private static final String TAG = "ConkerRecomp";
    private TextView status;

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
        if (!Environment.isExternalStorageManager()) {
            status.setText("Allow \"All files access\" so the game can use the ConkerRecompiled folder, then come back.");
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName())));
            return;
        }
        File folder = gameFolder();
        try {
            prepareFolder(folder);
        } catch (IOException e) {
            Log.e(TAG, "Couldn't prepare " + folder, e);
            status.setText("Couldn't prepare " + folder + ": " + e.getMessage());
            return;
        }
        startActivity(new Intent(this, GameActivity.class));
        finish();
    }

    static File gameFolder() {
        return new File(Environment.getExternalStorageDirectory(), "ConkerRecompiled");
    }

    /**
     * The launcher (RecompFrontend) loads assets/ from the folder it runs in, and keeps its
     * data there when it holds portable.txt. The assets are copied again whenever the app
     * is installed or updated.
     */
    private void prepareFolder(File folder) throws IOException {
        new File(folder, "mods").mkdirs();
        File portable = new File(folder, "portable.txt");
        if (!portable.exists() && !portable.createNewFile()) {
            throw new IOException("can't create " + portable);
        }

        String version;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = info.versionName + " " + info.lastUpdateTime;
        } catch (Exception e) {
            version = "";
        }
        File assets = new File(folder, "assets");
        File stamp = new File(assets, ".version");
        if (stamp.isFile() && version.equals(readText(stamp))) {
            return;
        }
        copyAssets(getAssets(), "", assets);
        try (OutputStream out = new FileOutputStream(stamp)) {
            out.write(version.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copyAssets(AssetManager manager, String path, File target) throws IOException {
        String[] children = manager.list(path);
        if (children != null && children.length > 0) {
            target.mkdirs();
            for (String child : children) {
                // The APK's own asset folders, not the launcher's.
                if (path.isEmpty() && (child.equals("images") || child.equals("webkit") || child.equals("geoid_map"))) {
                    continue;
                }
                copyAssets(manager, path.isEmpty() ? child : path + "/" + child, new File(target, child));
            }
            return;
        }
        try (InputStream in = manager.open(path); OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
        }
    }

    private static String readText(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
