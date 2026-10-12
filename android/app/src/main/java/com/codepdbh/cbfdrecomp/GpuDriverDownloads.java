package com.codepdbh.cbfdrecomp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Drivers to download from GitHub releases (adrenotools ZIPs, imported by GpuDrivers): known
 * repositories by GPU family, or any other one the player types.
 */
final class GpuDriverDownloads {
    enum Family { ADRENO, MALI, OTHER }

    static final class Source {
        final String repository, description;
        final Family family;

        Source(String repository, Family family, String description) {
            this.repository = repository;
            this.family = family;
            this.description = description;
        }
    }

    static final class Asset {
        final String release, name, url;
        final long size;

        Asset(String release, String name, String url, long size) {
            this.release = release;
            this.name = name;
            this.url = url;
            this.size = size;
        }
    }

    // Repositories seen publishing adrenotools packages (a ZIP with meta.json).
    static final Source[] SOURCES = {
        new Source("K11MCH1/AdrenoToolsDrivers", Family.ADRENO, "Turnip (Mesa) y drivers de Qualcomm para Adreno"),
        new Source("StevenMXZ/Adreno-Tools-Drivers", Family.ADRENO, "Turnip (Mesa) para Adreno, compilado cada dos semanas"),
        new Source("FristOneRR/FristOneRR-Panvk-Driver", Family.MALI, "PanVK experimental para Mali-G57"),
        new Source("LukeValen/panvk-mali-g52", Family.MALI, "PanVK experimental para Mali-G52"),
        new Source("wonderkast02/panvk-g720-kbase-csf", Family.MALI, "PanVK experimental para Mali-G720"),
    };

    private GpuDriverDownloads() {}

    static Family family(String renderer) {
        String name = renderer.toLowerCase(Locale.ROOT);
        if (name.contains("adreno")) {
            return Family.ADRENO;
        }
        if (name.contains("mali") || name.contains("immortalis")) {
            return Family.MALI;
        }
        return Family.OTHER;
    }

    /** What to tell the player about drivers for their GPU. */
    static String advice(String renderer) {
        switch (family(renderer)) {
            case ADRENO:
                return "Tu GPU es " + renderer + ": prueba Turnip (Mesa), el driver alternativo más maduro para Adreno.";
            case MALI:
                return "Tu GPU es " + renderer + ": hay drivers PanVK experimentales para algunos Mali (G52, G57, G720). "
                    + "Elige el de tu modelo; si no lo reconoce, el juego usará el del sistema.";
            default:
                return "Tu GPU es " + (renderer.isEmpty() ? "desconocida" : renderer) + ": no conocemos drivers alternativos "
                    + "para ella (Xclipse, PowerVR...). Puedes probar uno de otro repositorio.";
        }
    }

    /** The known sources, those for the player's GPU first. */
    static List<Source> sources(String renderer) {
        Family family = family(renderer);
        List<Source> result = new ArrayList<>();
        for (Source source : SOURCES) {
            if (source.family == family) {
                result.add(source);
            }
        }
        for (Source source : SOURCES) {
            if (source.family != family) {
                result.add(source);
            }
        }
        return result;
    }

    static boolean validRepository(String repository) {
        return repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    }

    /** The ZIPs in the repository's latest releases. */
    static List<Asset> assets(String repository) throws Exception {
        if (!validRepository(repository)) {
            throw new IOException("Escribe el repositorio como usuario/nombre.");
        }
        HttpURLConnection connection = open("https://api.github.com/repos/" + repository + "/releases?per_page=15");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        int status = connection.getResponseCode();
        if (status == 404) {
            throw new IOException("No existe el repositorio " + repository + ".");
        }
        if (status != 200) {
            throw new IOException("GitHub respondió " + status + (status == 403 ? " (límite de consultas, prueba más tarde)." : "."));
        }
        String body;
        try (InputStream in = connection.getInputStream()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<Asset> result = new ArrayList<>();
        JSONArray releases = new JSONArray(body);
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            String tag = release.optString("name", "");
            if (tag.trim().isEmpty()) {
                tag = release.optString("tag_name", "");
            }
            JSONArray files = release.optJSONArray("assets");
            for (int j = 0; files != null && j < files.length(); j++) {
                JSONObject file = files.getJSONObject(j);
                String name = file.optString("name", "");
                if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    result.add(new Asset(tag, name, file.optString("browser_download_url", ""), file.optLong("size", 0)));
                }
            }
        }
        return result;
    }

    /** Downloads the ZIP to the app's cache. */
    static File download(Context context, Asset asset) throws Exception {
        if (!asset.url.startsWith("https://github.com/") && !asset.url.startsWith("https://objects.githubusercontent.com/")) {
            throw new IOException("Dirección de descarga no válida.");
        }
        File file = new File(context.getCacheDir(), "driver-download.zip");
        HttpURLConnection connection = open(asset.url);
        if (connection.getResponseCode() != 200) {
            throw new IOException("La descarga falló (" + connection.getResponseCode() + ").");
        }
        long total = 0;
        try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            int count;
            while ((count = in.read(buffer)) > 0) {
                total += count;
                if (total > 256L * 1024 * 1024) {
                    throw new IOException("El archivo es demasiado grande para ser un driver.");
                }
                out.write(buffer, 0, count);
            }
        }
        return file;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "ConkerRecompiled-Android");
        return connection;
    }
}
