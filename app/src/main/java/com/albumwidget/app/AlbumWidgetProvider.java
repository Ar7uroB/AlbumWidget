package com.albumwidget.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlbumWidgetProvider extends AppWidgetProvider {
    static final String ACTION_NEXT = "com.albumwidget.app.NEXT";
    static final String ACTION_TICK = "com.albumwidget.app.TICK";

    /** Tope de píxeles del bitmap enviado al widget (límite de memoria de RemoteViews). */
    private static final long MAX_PIXELS = 1_000_000L;
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Random RANDOM = new Random();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        final Context app = context.getApplicationContext();

        if (ACTION_NEXT.equals(action) || ACTION_TICK.equals(action)) {
            final int id = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return;
            final PendingResult pr = goAsync();
            EXEC.execute(() -> {
                try {
                    update(app, id, true);
                } finally {
                    pr.finish();
                }
            });
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // Las alarmas se pierden al reiniciar: se vuelven a programar.
            AppWidgetManager mgr = AppWidgetManager.getInstance(app);
            for (int id : mgr.getAppWidgetIds(new ComponentName(app, AlbumWidgetProvider.class))) {
                schedule(app, id);
            }
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        refreshAsync(context, ids, goAsync());
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager mgr, int id, Bundle newOptions) {
        refreshAsync(context, new int[]{id}, goAsync());
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        for (int id : ids) {
            cancel(context, id);
            ImageSource.invalidate(id);
            Prefs.clear(context, id);
        }
    }

    // ---------------------------------------------------------------- actualización

    private static void refreshAsync(Context context, final int[] ids, final PendingResult pr) {
        final Context app = context.getApplicationContext();
        EXEC.execute(() -> {
            try {
                for (int id : ids) update(app, id, false);
            } finally {
                if (pr != null) pr.finish();
            }
        });
    }

    /** Para llamarlo desde la pantalla de configuración. */
    static void refreshInBackground(Context context, int id) {
        refreshAsync(context, new int[]{id}, null);
    }

    static void update(Context ctx, int id, boolean advance) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_album);

        List<Uri> images = ImageSource.get(ctx, id);
        Bitmap bmp = null;

        if (!images.isEmpty()) {
            int n = images.size();
            boolean shuffle = Prefs.getShuffle(ctx, id);
            boolean fit = Prefs.getFit(ctx, id);
            boolean rounded = Prefs.getRounded(ctx, id);
            int[] size = targetSize(ctx, mgr, id);
            int idx = Prefs.getIndex(ctx, id);
            if (idx < 0 || idx >= n) idx = 0;

            int attempts = Math.min(n, 5);
            for (int a = 0; a < attempts && bmp == null; a++) {
                if (advance || a > 0) idx = nextIndex(idx, n, shuffle);
                bmp = BitmapLoader.load(ctx, images.get(idx), size[0], size[1], fit, rounded);
            }
            Prefs.setIndex(ctx, id, idx);
        }

        if (bmp != null) {
            rv.setImageViewBitmap(R.id.img, bmp);
            rv.setViewVisibility(R.id.img, View.VISIBLE);
            rv.setViewVisibility(R.id.empty, View.GONE);
            rv.setOnClickPendingIntent(R.id.root, nextPendingIntent(ctx, id));
        } else {
            rv.setViewVisibility(R.id.img, View.GONE);
            rv.setViewVisibility(R.id.empty, View.VISIBLE);
            rv.setTextViewText(R.id.empty, images.isEmpty()
                    ? ctx.getString(R.string.tap_to_configure)
                    : "No se pudieron cargar las imágenes.\nToca para revisar las carpetas.");
            rv.setOnClickPendingIntent(R.id.root, configPendingIntent(ctx, id));
        }
        mgr.updateAppWidget(id, rv);

        if (bmp != null && images.size() > 1) {
            schedule(ctx, id);
        } else {
            cancel(ctx, id);
        }
    }

    private static int nextIndex(int current, int n, boolean shuffle) {
        if (n <= 1) return 0;
        if (!shuffle) return (current + 1) % n;
        int next;
        do {
            next = RANDOM.nextInt(n);
        } while (next == current);
        return next;
    }

    private static int[] targetSize(Context ctx, AppWidgetManager mgr, int id) {
        Bundle o = mgr.getAppWidgetOptions(id);
        float d = ctx.getResources().getDisplayMetrics().density;
        boolean land = ctx.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int wDp = o.getInt(land
                ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int hDp = o.getInt(land
                ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (wDp <= 0 || hDp <= 0) {
            wDp = 180;
            hDp = 180;
        }
        double w = wDp * d;
        double h = hDp * d;
        double px = w * h;
        if (px > MAX_PIXELS) {
            double s = Math.sqrt(MAX_PIXELS / px);
            w *= s;
            h *= s;
        }
        return new int[]{Math.max(1, (int) w), Math.max(1, (int) h)};
    }

    // ---------------------------------------------------------------- alarmas

    static void schedule(Context ctx, int id) {
        long sec = Prefs.getIntervalSec(ctx, id);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = tickPendingIntent(ctx, id);
        long at = SystemClock.elapsedRealtime() + sec * 1000L;
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, at, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, at, pi);
        }
    }

    static void cancel(Context ctx, int id) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tickPendingIntent(ctx, id));
    }

    private static PendingIntent tickPendingIntent(Context ctx, int id) {
        Intent i = new Intent(ctx, AlbumWidgetProvider.class)
                .setAction(ACTION_TICK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        return PendingIntent.getBroadcast(ctx, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent nextPendingIntent(Context ctx, int id) {
        Intent i = new Intent(ctx, AlbumWidgetProvider.class)
                .setAction(ACTION_NEXT)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        return PendingIntent.getBroadcast(ctx, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent configPendingIntent(Context ctx, int id) {
        Intent i = new Intent(ctx, ConfigActivity.class)
                .setData(Uri.parse("album://config/" + id))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
