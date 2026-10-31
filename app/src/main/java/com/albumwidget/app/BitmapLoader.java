package com.albumwidget.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.InputStream;

/** Decodifica una imagen y la dibuja ya al tamaño exacto del widget. */
final class BitmapLoader {
    private BitmapLoader() {}

    /** Devuelve null si la imagen no se puede leer. */
    static Bitmap load(Context c, Uri uri, int w, int h, boolean fit, boolean rounded) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int target = Math.max(w, h);
            int sample = 1;
            while (Math.min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) {
                sample *= 2;
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap src;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                src = BitmapFactory.decodeStream(in, null, opts);
            }
            if (src == null) return null;

            src = applyExifRotation(c, uri, src);
            return compose(src, w, h, fit, rounded, c.getResources().getDisplayMetrics().density);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private static Bitmap applyExifRotation(Context c, Uri uri, Bitmap src) {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return src;
            int o = new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix m = new Matrix();
            switch (o) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    m.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    m.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    m.postRotate(270);
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    m.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    m.postScale(1, -1);
                    break;
                default:
                    return src;
            }
            Bitmap rotated = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
            if (rotated != src) src.recycle();
            return rotated;
        } catch (Exception e) {
            return src;
        }
    }

    private static Bitmap compose(Bitmap src, int w, int h, boolean fit, boolean rounded, float density) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);

        int sw = src.getWidth();
        int sh = src.getHeight();
        float scale = fit
                ? Math.min(w / (float) sw, h / (float) sh)
                : Math.max(w / (float) sw, h / (float) sh);
        float dx = (w - sw * scale) / 2f;
        float dy = (h - sh * scale) / 2f;

        BitmapShader shader = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(dx, dy);
        shader.setLocalMatrix(m);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(shader);

        RectF dest = fit ? new RectF(dx, dy, w - dx, h - dy) : new RectF(0, 0, w, h);
        float r = rounded ? 16f * density : 0f;
        canvas.drawRoundRect(dest, r, r, paint);

        src.recycle();
        return out;
    }
}
