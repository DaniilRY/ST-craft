package com.murong.stcraft.tavern;

import java.util.ArrayList;
import java.util.List;

/** Вырезает служебные метки из текста, в том числе когда они приходят кусками потока. */
public final class MarkerFilter {
    private static final List<String> MARKERS = List.of("[FOLLOW]", "[STAY]", "[FREE]", "[HOME]");

    private final StringBuilder pending = new StringBuilder();
    private final List<String> found = new ArrayList<>();

    /** Возвращает текст, который уже безопасно показывать. */
    public String accept(String chunk) {
        pending.append(chunk);
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < pending.length()) {
            char c = pending.charAt(i);
            if (c != '[') {
                out.append(c);
                i++;
                continue;
            }
            String rest = pending.substring(i);
            String full = fullMarker(rest);
            if (full != null) {
                found.add(full);
                i += full.length();
            } else if (couldBePrefix(rest)) {
                break; // возможно, метка ещё не дописана
            } else {
                out.append(c);
                i++;
            }
        }
        pending.delete(0, i);
        return out.toString();
    }

    /** Конец потока: недописанный хвост это обычный текст. */
    public String finish() {
        String s = pending.toString();
        pending.setLength(0);
        return s;
    }

    /** Последняя найденная метка ("[FOLLOW]" или "[STAY]") или null. */
    public String marker() { return found.isEmpty() ? null : found.get(found.size() - 1); }
    public List<String> markers() { return found; }

    private static String fullMarker(String rest) {
        for (String m : MARKERS) {
            if (rest.length() >= m.length() && rest.regionMatches(true, 0, m, 0, m.length())) return m;
        }
        return null;
    }

    private static boolean couldBePrefix(String rest) {
        for (String m : MARKERS) {
            if (rest.length() < m.length() && m.regionMatches(true, 0, rest, 0, rest.length())) return true;
        }
        return false;
    }
}