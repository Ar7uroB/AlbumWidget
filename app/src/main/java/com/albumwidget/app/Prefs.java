package com.albumwidget.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Ajustes guardados por widget (cada widget tiene su propio álbum). */
final class Prefs {
    static final long DEFAULT_INTERVAL_SEC = 30;
    static final long MIN_INTERVAL_SEC = 5;
    static final long MAX_INTERVAL_SEC = 7L * 24 * 3600;

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("album_prefs", Context.MODE_PRIVATE);
    }

    static List<String> getFolders(Context c, int id) {
        String raw = sp(c).getString("folders_" + id, "");
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\n")) {
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    static void setFolders(Context c, int id, List<String> folders) {
        StringBuilder sb = new StringBuilder();
        for (String f : folders) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(f);
        }
        sp(c).edit().putString("folders_" + id, sb.toString()).apply();
    }

    static long getIntervalSec(Context c, int id) {
        return sp(c).getLong("interval_" + id, DEFAULT_INTERVAL_SEC);
    }

    static void setIntervalSec(Context c, int id, long sec) {
        sp(c).edit().putLong("interval_" + id, sec).apply();
    }

    static boolean getShuffle(Context c, int id) {
        return sp(c).getBoolean("shuffle_" + id, false);
    }

    static void setShuffle(Context c, int id, boolean v) {
        sp(c).edit().putBoolean("shuffle_" + id, v).apply();
    }

    /** true = mostrar la imagen completa (sin recortar). */
    static boolean getFit(Context c, int id) {
        return sp(c).getBoolean("fit_" + id, false);
    }

    static void setFit(Context c, int id, boolean v) {
        sp(c).edit().putBoolean("fit_" + id, v).apply();
    }

    static boolean getRounded(Context c, int id) {
        return sp(c).getBoolean("rounded_" + id, true);
    }

    static void setRounded(Context c, int id, boolean v) {
        sp(c).edit().putBoolean("rounded_" + id, v).apply();
    }

    static int getIndex(Context c, int id) {
        return sp(c).getInt("index_" + id, 0);
    }

    static void setIndex(Context c, int id, int v) {
        sp(c).edit().putInt("index_" + id, v).apply();
    }

    static void clear(Context c, int id) {
        SharedPreferences p = sp(c);
        SharedPreferences.Editor e = p.edit();
        String suffix = "_" + id;
        for (Map.Entry<String, ?> en : p.getAll().entrySet()) {
            if (en.getKey().endsWith(suffix)) e.remove(en.getKey());
        }
        e.apply();
    }
}
