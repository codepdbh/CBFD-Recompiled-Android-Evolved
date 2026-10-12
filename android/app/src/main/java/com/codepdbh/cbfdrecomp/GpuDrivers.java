// Adapted from codepdbh/nfsmw-android a7e6e4c; SPDX-License-Identifier: GPL-3.0-only
package com.codepdbh.cbfdrecomp;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.system.Os;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Custom Vulkan drivers the player imports (Turnip and other Android ARM64 drivers, in
 * adrenotools' ZIP format with a meta.json), and the one chosen for the game. They live in the
 * app's own storage, not ConkerRecompiled/: Android only loads libraries from there. The game opens
 * the chosen one through libadrenotools (host/src/android/gpu_driver.cpp).
 */
final class GpuDrivers {
    static final class Driver {
        final String id, name, library;
        final File directory;

        Driver(String id, String name, File directory, String library) {
            this.id = id;
            this.name = name;
            this.directory = directory;
            this.library = library;
        }
    }

    private GpuDrivers() {}

    /** Only the 64-bit build has the loader (libadrenotools is ARM64 only). */
    static boolean supported() {
        return android.os.Process.is64Bit();
    }

    private static File root(Context context) {
        File root = new File(context.getFilesDir(), "gpu_drivers");
        root.mkdirs();
        return root;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("gpu_drivers", Context.MODE_PRIVATE);
    }

    static List<Driver> list(Context context) {
        List<Driver> result = new ArrayList<>();
        File[] dirs = root(context).listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                try {
                    if (dir.getName().startsWith(".")) {
                        continue;
                    }
                    JSONObject meta = new JSONObject(new String(Files.readAllBytes(new File(dir, "driver.json").toPath()), StandardCharsets.UTF_8));
                    String library = meta.getString("libraryName");
                    if (!library.matches("[A-Za-z0-9_.+-]+\\.so")) {
                        continue;
                    }
                    File loadDir = new File(dir, meta.getString("folder"));
                    if (!loadDir.getCanonicalPath().startsWith(dir.getCanonicalPath() + "/")) {
                        continue;
                    }
                    if (new File(loadDir, library).isFile()) {
                        result.add(new Driver(dir.getName(), meta.getString("name"), loadDir, library));
                    }
                } catch (Exception ignored) {
                    // Not a driver.
                }
            }
        }
        result.sort(Comparator.comparing(d -> d.name.toLowerCase(Locale.ROOT)));
        return result;
    }

    static String selectedId(Context context) {
        return prefs(context).getString("selected", "");
    }

    static Driver selected(Context context) {
        String id = selectedId(context);
        for (Driver driver : list(context)) {
            if (driver.id.equals(id)) {
                return driver;
            }
        }
        return null;
    }

    static void select(Context context, String id) {
        prefs(context).edit().putString("selected", id).apply();
    }

    static Driver importZip(Context context, Uri uri) throws Exception {
        File stage = new File(root(context), ".import-" + UUID.randomUUID());
        if (!stage.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta del driver.");
        }
        try {
            InputStream input = context.getContentResolver().openInputStream(uri);
            if (input == null) {
                throw new IOException("No se pudo abrir el ZIP.");
            }
            DriverArchive.extract(input, stage);
            File packageRoot = findMeta(stage);
            if (packageRoot == null) {
                throw new IOException("Falta meta.json en el ZIP: no es un driver en formato adrenotools.");
            }
            JSONObject meta = new JSONObject(new String(Files.readAllBytes(new File(packageRoot, "meta.json").toPath()), StandardCharsets.UTF_8));
            String library = meta.getString("libraryName");
            if (!library.matches("[A-Za-z0-9_.+-]+\\.so")) {
                throw new IOException("libraryName no es válido.");
            }
            if (meta.optInt("minApi", 26) > Build.VERSION.SDK_INT) {
                throw new IOException("Este driver necesita una versión más reciente de Android.");
            }
            DriverArchive.validateLibrary(new File(packageRoot, library));
            String name = meta.optString("name", library);
            if (name.trim().isEmpty() || name.length() > 120) {
                throw new IOException("Nombre de driver no válido.");
            }
            String relative = "payload";
            File installed = new File(root(context), UUID.randomUUID().toString());
            if (!installed.mkdirs()) {
                throw new IOException("No se pudo instalar el driver.");
            }
            try {
                if (!packageRoot.renameTo(new File(installed, relative))) {
                    throw new IOException("No se pudo instalar el paquete.");
                }
                JSONObject record = new JSONObject().put("name", name).put("libraryName", library).put("folder", relative);
                Files.write(new File(installed, "driver.json").toPath(), record.toString().getBytes(StandardCharsets.UTF_8));
                readOnly(new File(installed, relative));
            } catch (Exception error) {
                DriverArchive.delete(installed);
                throw error;
            }
            return new Driver(installed.getName(), name, new File(installed, relative), library);
        } finally {
            if (stage.exists()) {
                DriverArchive.delete(stage);
            }
        }
    }

    private static File findMeta(File root) throws IOException {
        if (new File(root, "meta.json").isFile()) {
            return root;
        }
        File found = null;
        File[] children = root.listFiles(File::isDirectory);
        if (children != null) {
            for (File child : children) {
                File candidate = findMeta(child);
                if (candidate != null) {
                    if (found != null) {
                        throw new IOException("Hay varios meta.json; el paquete es ambiguo.");
                    }
                    found = candidate;
                }
            }
        }
        return found;
    }

    // Android won't load a library others could write.
    private static void readOnly(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                readOnly(child);
            }
        } else if (!file.setReadOnly()) {
            throw new IOException("No se pudo proteger la biblioteca del driver.");
        }
    }

    static void remove(Context context, Driver driver) throws IOException {
        File target = new File(root(context), driver.id);
        if (!target.getCanonicalFile().getParentFile().equals(root(context).getCanonicalFile())) {
            throw new IOException("Ruta de driver no válida.");
        }
        DriverArchive.delete(target);
        if (selectedId(context).equals(driver.id)) {
            select(context, "");
        }
    }

    /** For the game's process, before its library starts: which driver to open and where from. */
    static void exportForGame(Context context) throws Exception {
        Driver driver = supported() ? selected(context) : null;
        Os.setenv("CONKER_NATIVE_LIB_DIR", context.getApplicationInfo().nativeLibraryDir, true);
        Os.setenv("CONKER_TMP_DIR", context.getCacheDir().getAbsolutePath(), true);
        Os.setenv("CONKER_GPU_DRIVER_DIR", driver == null ? "" : driver.directory.getAbsolutePath() + "/", true);
        Os.setenv("CONKER_GPU_DRIVER_NAME", driver == null ? "" : driver.library, true);
        GameLog.append("[android] Vulkan driver: " + (driver == null ? "the system's" : driver.name + " (" + driver.library + ")") + "\n");
    }
}
