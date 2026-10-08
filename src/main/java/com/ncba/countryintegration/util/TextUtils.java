package com.ncba.countryintegration.util;

import java.util.Locale;

public final class TextUtils {
    private TextUtils() { }

    /** "  kENYA " -> "Kenya"; collapses inner whitespace. */
    public static String toSentenceCase(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String t = input.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
    }

    /** Escape text for safe inclusion in an XML element. */
    public static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
