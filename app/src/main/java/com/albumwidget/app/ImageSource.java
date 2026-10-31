package com.albumwidget.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lista las imágenes de las carpetas elegidas (incluye subcarpetas). */
final class ImageSource {
    private static final int MAX_IMAGES = 5000;
    private static final int MAX_DEPTH = 5;
    private static final long CACHE_MS = 10 * 60 * 1000L;

    private static final class Entry {
        final String name;
        final Uri uri;

        Entry(String name, Uri uri) {
            this.name = name == null ? "" : name;
            this.uri = uri;
        }
    }

    private static final class Cached {
        final long time;
        final List<Uri> list;

        Cached(long time, List<Uri> list) {
            this.time = time;
            this.list = list;
        }
    }

    private static final Map<Integer, Cached> CACHE = new HashMap<>();

    private ImageSource() {}

    static synchronized void invalidate(int widgetId) {
        CACHE.remove(widgetId);
    }

    /** Lista (con caché de 10 min) de imágenes del widget, ordenadas por nombre. */
    static synchronized List<Uri> get(Context c, int widgetId) {
        Cached cached = CACHE.get(widgetId);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.time < CACHE_MS) return cached.list;
        List<Uri> fresh = list(c, Prefs.getFolders(c, widgetId));
        CACHE.put(widgetId, new Cached(now, fresh));
        return fresh;
    }

    static List<Uri> list(Context c, List<String> trees) {
        List<Entry> all = new ArrayList<>();
        for (String t : trees) {
            try {
                Uri tree = Uri.parse(t);
                collect(c, tree, DocumentsContract.getTreeDocumentId(tree), all, 0);
            } catch (Exception ignored) {
                // Carpeta borrada o permiso perdido: se omite.
            }
        }
        Collections.sort(all, (a, b) -> a.name.compareToIgnoreCase(b.name));
        List<Uri> out = new ArrayList<>(all.size());
        for (Entry e : all) out.add(e.uri);
        return out;
    }

    private static void collect(Context c, Uri tree, String parentId, List<Entry> out, int depth) {
        if (depth > MAX_DEPTH || out.size() >= MAX_IMAGES) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] proj = {
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE
        };
        List<String> subdirs = new ArrayList<>();
        try (Cursor cur = c.getContentResolver().query(children, proj, null, null, null)) {
            if (cur == null) return;
            while (cur.moveToNext() && out.size() < MAX_IMAGES) {
                String id = cur.getString(0);
                String name = cur.getString(1);
                String mime = cur.getString(2);
                if (Document.MIME_TYPE_DIR.equals(mime)) {
                    subdirs.add(id);
                } else if (mime != null && mime.startsWith("image/") && !mime.contains("svg")) {
                    out.add(new Entry(name, DocumentsContract.buildDocumentUriUsingTree(tree, id)));
                }
            }
        }
        for (String d : subdirs) collect(c, tree, d, out, depth + 1);
    }
}
