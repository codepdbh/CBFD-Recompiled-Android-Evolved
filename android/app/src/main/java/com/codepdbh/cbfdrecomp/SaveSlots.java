package com.codepdbh.cbfdrecomp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;

/**
 * Copies of the game's save (its EEPROM, which the game writes at its checkpoints), kept in
 * slots: ConkerRecompiled/saves/copias/slotN.bin. Restoring one replaces the save, and the game
 * then starts again from it (a static recompilation can't snapshot the running game itself).
 */
final class SaveSlots {
    static final int COUNT = 3;
    // librecomp's save file: saves/<game id>.bin (main.cpp's game_id).
    private static final String SAVE_NAME = "conker.n64.us.1.0.bin";

    private SaveSlots() {}

    static File saveFile() {
        return new File(new File(MainActivity.gameFolder(), "saves"), SAVE_NAME);
    }

    static File slotFile(int slot) {
        return new File(new File(new File(MainActivity.gameFolder(), "saves"), "copias"), "slot" + slot + ".bin");
    }

    static boolean hasSave() {
        return saveFile().isFile();
    }

    static boolean hasSlot(int slot) {
        return slotFile(slot).isFile();
    }

    /** When the slot was saved, or null if it's empty. */
    static String describe(int slot) {
        File file = slotFile(slot);
        if (!file.isFile()) {
            return null;
        }
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(file.lastModified()));
    }

    /** Whether the slot holds the same save as the game's now (nothing saved in between). */
    static boolean sameAsSave(int slot) {
        File a = saveFile(), b = slotFile(slot);
        if (!a.isFile() || !b.isFile() || a.length() != b.length()) {
            return false;
        }
        try (InputStream inA = new FileInputStream(a); InputStream inB = new FileInputStream(b)) {
            return java.util.Arrays.equals(inA.readAllBytes(), inB.readAllBytes());
        } catch (IOException e) {
            return false;
        }
    }

    static void save(int slot) throws IOException {
        File target = slotFile(slot);
        target.getParentFile().mkdirs();
        copy(saveFile(), target);
    }

    /** Only while the game isn't running: it would write its own save over this one. */
    static void restore(int slot) throws IOException {
        File save = saveFile();
        save.getParentFile().mkdirs();
        copy(slotFile(slot), save);
        // librecomp falls back on the backup if the save can't be read: keep it the same.
        File backup = new File(save.getPath() + ".bak");
        if (backup.exists()) {
            copy(slotFile(slot), backup);
        }
    }

    private static void copy(File from, File to) throws IOException {
        File partial = new File(to.getPath() + ".part");
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(partial)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
        }
        if (to.exists() && !to.delete()) {
            throw new IOException("can't replace " + to);
        }
        if (!partial.renameTo(to)) {
            throw new IOException("can't write " + to);
        }
    }
}
