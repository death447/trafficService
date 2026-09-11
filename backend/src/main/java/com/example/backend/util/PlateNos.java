package com.example.backend.util;

import java.util.Locale;

public final class PlateNos {

    private PlateNos() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || cp == 0x00B7 || cp == '.') {
                continue;
            }
            sb.appendCodePoint(cp);
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
