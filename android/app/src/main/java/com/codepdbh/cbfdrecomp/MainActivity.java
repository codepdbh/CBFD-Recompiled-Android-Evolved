package com.codepdbh.cbfdrecomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The start screen: pick the ROM, turn mods on and off, and play (GameActivity).
 *
 * Everything lives in ConkerRecompiled/ at the root of the shared storage
 * (/storage/emulated/0): the ROM, mods, saves, settings, and the game's menus' assets.
 * Reading and writing it takes "All files access", which this asks for first.
 */
public class MainActivity extends Activity {
    private static final String TAG = "ConkerRecomp";
    private static final int PICK_ROM = 1, PICK_MODS = 2;
    // The ROM the game keeps once it has checked it (librecomp's stored_filename()).
    private static final String STORED_ROM = "conker.n64.us.1.0.z64";

    private TextView romStatus;
    private Button play, romButton, modsButton;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        romStatus = findViewById(R.id.rom_status);
        play = findViewById(R.id.play);
        romButton = findViewById(R.id.rom);
        modsButton = findViewById(R.id.mods);
        play.setOnClickListener(v -> startGame());
        romButton.setOnClickListener(v -> pickRom());
        modsButton.setOnClickListener(v -> showMods());
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (!Environment.isExternalStorageManager()) {
            askForStorage();
            return;
        }
        try {
            prepareFolder(gameFolder());
        } catch (IOException e) {
            Log.e(TAG, "Couldn't prepare " + gameFolder(), e);
            romStatus.setText("No se pudo preparar " + gameFolder() + ": " + e.getMessage());
        }
        updateRomStatus();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    private void hideSystemBars() {
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    static File gameFolder() {
        return new File(Environment.getExternalStorageDirectory(), "ConkerRecompiled");
    }

    // Storage permission.

    private void askForStorage() {
        romStatus.setText("Falta el permiso de archivos.");
        setButtonsEnabled(false);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Permiso de archivos")
            .setMessage("El juego guarda la ROM, los mods y las partidas en la carpeta ConkerRecompiled "
                + "de tu memoria interna. Activa \"Permitir acceso a todos los archivos\" en la siguiente pantalla.")
            .setCancelable(false)
            .setPositiveButton("Continuar", (d, w) -> startActivity(new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))))
            .show();
    }

    // The game's folder.

    /**
     * The game's menus (RecompFrontend) load assets/ from the folder the game runs in, and
     * keep their data there when it holds portable.txt. The assets are copied again whenever
     * the app is installed or updated.
     */
    private void prepareFolder(File folder) throws IOException {
        new File(folder, "mods").mkdirs();
        File portable = new File(folder, "portable.txt");
        if (!portable.exists() && !portable.createNewFile()) {
            throw new IOException("can't create " + portable);
        }

        // The ROM picked here is kept by the game as STORED_ROM the first time it runs:
        // once it has been, the copy picked here only takes space.
        File rom = new File(folder, "rom.z64");
        File stored = new File(folder, STORED_ROM);
        if (rom.isFile() && stored.isFile() && stored.lastModified() >= rom.lastModified()
                && stored.length() == rom.length()) {
            rom.delete();
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
                // The APK's own asset folders, not the menus'.
                if (path.isEmpty() && (child.equals("images") || child.equals("webkit") || child.equals("geoid_map"))) {
                    continue;
                }
                copyAssets(manager, path.isEmpty() ? child : path + "/" + child, new File(target, child));
            }
            return;
        }
        try (InputStream in = manager.open(path); OutputStream out = new FileOutputStream(target)) {
            copy(in, out);
        }
    }

    // The ROM.

    private boolean hasRom() {
        File folder = gameFolder();
        return new File(folder, STORED_ROM).isFile() || new File(folder, "rom.z64").isFile();
    }

    private void updateRomStatus() {
        boolean rom = hasRom();
        romStatus.setText(rom ? "ROM lista ✓" : "Elige tu ROM de Conker's Bad Fur Day (USA) para empezar.");
        romButton.setText(rom ? "Cambiar ROM" : "Elegir ROM");
        setButtonsEnabled(true);
        play.setEnabled(rom);
    }

    private void setButtonsEnabled(boolean enabled) {
        play.setEnabled(enabled && !busy);
        romButton.setEnabled(enabled && !busy);
        modsButton.setEnabled(enabled && !busy);
    }

    private void pickRom() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_ROM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == PICK_ROM && data.getData() != null) {
            importRom(data.getData());
        }
        else if (requestCode == PICK_MODS) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    uris.add(data.getClipData().getItemAt(i).getUri());
                }
            }
            else if (data.getData() != null) {
                uris.add(data.getData());
            }
            importMods(uris);
        }
    }

    /** Copies the picked ROM to the folder as rom.z64, after a look at its header. */
    private void importRom(Uri uri) {
        busy = true;
        setButtonsEnabled(false);
        romStatus.setText("Copiando la ROM…");
        new Thread(() -> {
            String error = null;
            File target = new File(gameFolder(), "rom.z64");
            File partial = new File(gameFolder(), "rom.z64.part");
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new IOException("no se pudo abrir");
                }
                byte[] header = new byte[4];
                if (in.read(header) != 4 || !isN64Header(header)) {
                    error = "Ese archivo no es una ROM de N64.";
                }
                else {
                    try (OutputStream out = new FileOutputStream(partial)) {
                        out.write(header);
                        copy(in, out);
                    }
                    target.delete();
                    if (!partial.renameTo(target)) {
                        throw new IOException("no se pudo guardar");
                    }
                    // The game checks it and keeps it on its next start.
                    new File(gameFolder(), STORED_ROM).delete();
                }
            } catch (IOException e) {
                error = "No se pudo copiar la ROM: " + e.getMessage();
            }
            partial.delete();
            String message = error;
            runOnUiThread(() -> {
                busy = false;
                updateRomStatus();
                if (message != null) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    /** The first word of a ROM in any of the N64's byte orders (.z64, .v64, .n64). */
    private static boolean isN64Header(byte[] b) {
        int word = ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
        return word == 0x80371240 || word == 0x37804012 || word == 0x40123780;
    }

    // Mods.

    private static final class ModInfo {
        String id, name, description;
        File file;
    }

    private List<ModInfo> listMods() {
        List<ModInfo> mods = new ArrayList<>();
        File[] files = new File(gameFolder(), "mods").listFiles((dir, name) -> name.endsWith(".nrm"));
        if (files == null) {
            return mods;
        }
        Arrays.sort(files);
        for (File file : files) {
            ModInfo mod = new ModInfo();
            mod.file = file;
            mod.id = file.getName().replace(".nrm", "");
            mod.name = mod.id;
            mod.description = "";
            try (ZipFile zip = new ZipFile(file)) {
                ZipEntry entry = zip.getEntry("mod.json");
                if (entry != null) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        JSONObject json = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                        mod.id = json.optString("id", mod.id);
                        mod.name = json.optString("display_name", mod.name);
                        mod.description = json.optString("short_description", json.optString("description", ""));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Couldn't read " + file, e);
            }
            mods.add(mod);
        }
        return mods;
    }

    private File modsConfig() {
        return new File(gameFolder(), "mods.json");
    }

    private Set<String> readEnabledMods() {
        Set<String> enabled = new LinkedHashSet<>();
        try {
            String text = readText(modsConfig());
            if (text != null) {
                JSONArray list = new JSONObject(text).optJSONArray("enabled_mods");
                for (int i = 0; list != null && i < list.length(); i++) {
                    enabled.add(list.getString(i));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Couldn't read mods.json", e);
        }
        return enabled;
    }

    /** Writes the enabled mods into mods.json, keeping the rest of what the game keeps there. */
    private void writeEnabledMods(Set<String> enabled) {
        try {
            String text = readText(modsConfig());
            JSONObject json = text != null ? new JSONObject(text) : new JSONObject();
            json.put("enabled_mods", new JSONArray(enabled));
            if (!json.has("mod_order")) {
                json.put("mod_order", new JSONArray());
            }
            try (OutputStream out = new FileOutputStream(modsConfig())) {
                out.write(json.toString(4).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo guardar mods.json: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showMods() {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad, pad / 2, pad, pad / 2);

        List<ModInfo> mods = listMods();
        Set<String> enabled = readEnabledMods();
        if (mods.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No hay mods todavía. Añade archivos .nrm con el botón de abajo, "
                + "o cópialos a ConkerRecompiled/mods.");
            empty.setTextColor(Color.LTGRAY);
            list.addView(empty);
        }
        for (ModInfo mod : mods) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, pad / 2, 0, pad / 2);
            Switch toggle = new Switch(this);
            toggle.setText(mod.name);
            toggle.setTextSize(17);
            toggle.setChecked(enabled.contains(mod.id));
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    enabled.add(mod.id);
                } else {
                    enabled.remove(mod.id);
                }
                writeEnabledMods(enabled);
            });
            row.addView(toggle);
            if (!mod.description.isEmpty()) {
                TextView description = new TextView(this);
                description.setText(mod.description);
                description.setTextColor(Color.GRAY);
                description.setTextSize(13);
                row.addView(description);
            }
            list.addView(row);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Mods")
            .setView(scroll)
            .setPositiveButton("Listo", null)
            .setNeutralButton("Añadir .nrm", (d, w) -> pickMods())
            .create();
        dialog.show();
    }

    private void pickMods() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_MODS);
    }

    /** Copies the picked .nrm files into mods/ and turns them on. */
    private void importMods(List<Uri> uris) {
        busy = true;
        setButtonsEnabled(false);
        new Thread(() -> {
            int added = 0;
            List<String> failed = new ArrayList<>();
            File modsFolder = new File(gameFolder(), "mods");
            for (Uri uri : uris) {
                String name = displayName(uri);
                if (name == null || !name.toLowerCase().endsWith(".nrm")) {
                    failed.add(name != null ? name : uri.toString());
                    continue;
                }
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(new File(modsFolder, name))) {
                    if (in == null) {
                        throw new IOException();
                    }
                    copy(in, out);
                    added++;
                } catch (IOException e) {
                    failed.add(name);
                }
            }
            int count = added;
            runOnUiThread(() -> {
                busy = false;
                updateRomStatus();
                // New mods start on.
                Set<String> enabled = readEnabledMods();
                for (ModInfo mod : listMods()) {
                    if (!enabled.contains(mod.id) && mod.file.lastModified() > System.currentTimeMillis() - 60000) {
                        enabled.add(mod.id);
                    }
                }
                writeEnabledMods(enabled);
                String message = count + (count == 1 ? " mod añadido" : " mods añadidos");
                if (!failed.isEmpty()) {
                    message += ". No son .nrm: " + String.join(", ", failed);
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                showMods();
            });
        }).start();
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri,
                new String[] { android.provider.OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            Log.w(TAG, "Couldn't name " + uri, e);
        }
        return null;
    }

    // Playing.

    private void startGame() {
        if (!hasRom()) {
            pickRom();
            return;
        }
        startActivity(new Intent(this, GameActivity.class));
    }

    // Helpers.

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1 << 16];
        int count;
        while ((count = in.read(buffer)) > 0) {
            out.write(buffer, 0, count);
        }
    }

    private static String readText(File file) {
        if (!file.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
