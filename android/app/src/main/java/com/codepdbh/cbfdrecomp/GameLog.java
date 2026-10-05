package com.codepdbh.cbfdrecomp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.system.Os;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;

/**
 * The last game's log, ConkerRecompiled/logs/game.log, for players to send when something goes
 * wrong: the phone, then everything the game prints (the GPU and its driver among it), and a
 * crash's report at the end (android_main.cpp writes those through CONKER_LOG_FILE).
 */
final class GameLog {
    private GameLog() {}

    static File file() {
        return new File(new File(MainActivity.gameFolder(), "logs"), "game.log");
    }

    /** A new log for the game about to start: the phone, and where the game writes the rest. */
    static void begin(Context context) {
        File file = file();
        file.getParentFile().mkdirs();
        String header = "Conker Recompiled " + version(context) + " — " + DateFormat.getDateTimeInstance().format(new Date()) + "\n"
            + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")\n"
            + "SoC: " + Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL + ", hardware " + Build.HARDWARE + "\n"
            + "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), "
            + (android.os.Process.is64Bit() ? "64-bit" : "32-bit") + " (" + String.join(", ", Build.SUPPORTED_ABIS) + ")\n"
            + "Build: " + Build.FINGERPRINT + "\n\n";
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(header.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        try {
            Os.setenv("CONKER_LOG_FILE", file.getAbsolutePath(), true);
        } catch (Exception e) {
            // No native log then.
        }

        // A Java exception ends up in the log too.
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            append("\n*** Java exception in thread \"" + thread.getName() + "\":\n" + trace);
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    /** The installed app's version, as logs give it: "0.2.8 (102)". */
    static String version(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (Exception e) {
            return "?";
        }
    }

    /** The version of the app that wrote a log (its first line), or null if it doesn't say. */
    static String versionOf(String log) {
        String prefix = "Conker Recompiled ";
        int end = log.indexOf(" — ");
        if (!log.startsWith(prefix) || end < 0 || end > 80) {
            return null;
        }
        return log.substring(prefix.length(), end);
    }

    static void append(String text) {
        try (OutputStream out = new FileOutputStream(file(), true)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Nothing to do.
        }
    }

    /** The log's end (the part that matters), or null if there's none. */
    static String read(int maxBytes) {
        File file = file();
        if (!file.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] all = in.readAllBytes();
            if (all.length <= maxBytes) {
                return new String(all, StandardCharsets.UTF_8);
            }
            // The header, then the end.
            String text = new String(all, StandardCharsets.UTF_8);
            int headerEnd = Math.min(text.indexOf("\n\n") + 2, 2000);
            String header = headerEnd > 1 ? text.substring(0, headerEnd) : "";
            return header + "[...]\n" + new String(all, all.length - maxBytes, maxBytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
