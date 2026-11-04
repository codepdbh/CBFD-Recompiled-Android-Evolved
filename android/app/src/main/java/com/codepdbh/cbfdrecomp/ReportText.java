package com.codepdbh.cbfdrecomp;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * A game log made to fit a GitHub issue's link, whose length GitHub limits, so the issue
 * opens already holding the log's useful part, with nothing for the player to paste. No Android
 * dependencies, so it can be tried on a PC.
 */
final class ReportText {
    // Encoded, the body's share of the link: GitHub answers 500 when sending a longer one to its
    // login page (measured: 4500 works, 5000 doesn't, for players without a session there).
    static final int MAX_ENCODED_BODY = 4000;

    private ReportText() {}

    /** The log without what's the same in every game, and with repeated lines counted. */
    static List<String> usefulLines(String log) {
        List<String> lines = new ArrayList<>();
        String previous = null;
        int repeats = 0;
        for (String line : log.split("\n")) {
            if (line.startsWith("Loaded font face") || line.startsWith("INFO: ") || line.startsWith("SVG plugin")
                    || line.startsWith("  Instance ID") || line.startsWith("  Path:")) {
                continue;
            }
            if (line.equals(previous)) {
                repeats++;
                continue;
            }
            if (repeats > 0) {
                lines.add("(la línea anterior se repite " + repeats + " veces más)");
            }
            repeats = 0;
            previous = line;
            lines.add(line);
        }
        if (repeats > 0) {
            lines.add("(la línea anterior se repite " + repeats + " veces más)");
        }
        return lines;
    }

    /** The first lines (the phone, its GPU) and the last (how the game ended), within maxChars. */
    static String compact(String log, int maxChars) {
        List<String> lines = usefulLines(log);
        int total = 0;
        for (String line : lines) {
            total += line.length() + 1;
        }
        if (total <= maxChars) {
            return String.join("\n", lines);
        }

        // The same for each end, what one doesn't use going to the other when it's shorter.
        List<String> head = new ArrayList<>();
        int used = 0;
        int index = 0;
        while (index < lines.size() && used + lines.get(index).length() + 1 <= maxChars / 2) {
            used += lines.get(index).length() + 1;
            head.add(lines.get(index));
            index++;
        }
        List<String> tail = new ArrayList<>();
        int end = lines.size() - 1;
        while (end >= index && used + lines.get(end).length() + 1 <= maxChars) {
            used += lines.get(end).length() + 1;
            tail.add(0, lines.get(end));
            end--;
        }
        head.add("[...]");
        head.addAll(tail);
        return String.join("\n", head);
    }

    /** The issue's text, holding as much of the log as the link allows. */
    static String issueBody(String log) {
        int budget = MAX_ENCODED_BODY;
        while (true) {
            String body = "**Qué pasó:** (cuéntalo aquí)\n\n\n**Informe:**\n\n```\n" + compact(log, budget) + "\n```\n";
            if (encodedLength(body) <= MAX_ENCODED_BODY || budget < 200) {
                return body;
            }
            budget = budget * 4 / 5;
        }
    }

    // As Uri.Builder encodes it: %20 for a space.
    static int encodedLength(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8").replace("+", "%20").length();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
