package io.github.seandettmann.schoolbag;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import io.github.seandettmann.schoolbag.core.Face;
import io.github.seandettmann.schoolbag.core.Json;
import io.github.seandettmann.schoolbag.core.Painter;

/** Draws the HUD onto Android's Canvas from the art bundle in the app's assets. */
final class CanvasPainter implements Painter {
    private final AssetManager assets;
    private final Map<String, Bitmap> bitmaps = new HashMap<>();
    private final Map<String, int[]> regions = new HashMap<>();
    private final Map<String, String> regionSheet = new HashMap<>();
    private final Map<Face, Typeface> faces = new HashMap<>();
    private final Map<String, int[]> bounds = new HashMap<>();
    private final Paint sprite = new Paint();
    private final Paint fill = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final Deque<Float> brightStack = new ArrayDeque<>();
    private float bright = 1f;
    private Canvas canvas;

    CanvasPainter(AssetManager assets) {
        this.assets = assets;
        sprite.setFilterBitmap(false);
        sprite.setAntiAlias(false);
        try {
            Json.J r = Json.parse(App.read(assets, "art/regions.json"));
            for (Map.Entry<String, Object> e : r.map().entrySet()) {
                Json.J v = new Json.J(e.getValue());
                regionSheet.put(e.getKey(), v.at(0).str());
                regions.put(e.getKey(), new int[]{v.at(1).integer(), v.at(2).integer(), v.at(3).integer(), v.at(4).integer()});
            }
        } catch (Exception e) {
            // no art bundle: the HUD draws its text and nothing else
        }
        for (Face f : Face.values()) {
            Typeface t = Typeface.DEFAULT;
            if (f.file != null) {
                try { t = Typeface.createFromAsset(assets, "art/" + f.file); } catch (Exception ignored) { }
            }
            faces.put(f, t);
        }
    }

    void begin(Canvas c) {
        canvas = c;
        bright = 1f;
        brightStack.clear();
        sprite.setColorFilter(null);
    }

    private Bitmap bitmap(String path) {
        if (bitmaps.containsKey(path)) return bitmaps.get(path);
        Bitmap b = null;
        try (InputStream in = assets.open("art/" + path)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            b = BitmapFactory.decodeStream(in, null, o);
        } catch (Exception ignored) { }
        bitmaps.put(path, b);
        return b;
    }

    private void blit(Bitmap b, int sx, int sy, int sw, int sh, float x, float y, float zoom, float alpha) {
        if (b == null || sw <= 0 || sh <= 0) return;
        src.set(sx, sy, sx + sw, sy + sh);
        dst.set(x, y, x + sw * zoom, y + sh * zoom);
        sprite.setAlpha(Math.round(Math.max(0, Math.min(1, alpha)) * 255));
        canvas.drawBitmap(b, src, dst, sprite);
    }

    @Override public void sprite(String region, float x, float y, float zoom, float alpha) {
        int[] q = regions.get(region);
        if (q == null) return;
        blit(bitmap("sheets/" + regionSheet.get(region)), q[0], q[1], q[2], q[3], x, y, zoom, alpha);
    }

    @Override public void spritePart(String region, int sx, int sy, int sw, int sh, float x, float y, float zoom, float alpha) {
        int[] q = regions.get(region);
        if (q == null) return;
        blit(bitmap("sheets/" + regionSheet.get(region)), q[0] + sx, q[1] + sy, sw, sh, x, y, zoom, alpha);
    }

    @Override public void tile(String region, float x, float y, float w, float h, float zoom) {
        int[] q = regions.get(region);
        if (q == null) return;
        canvas.save();
        canvas.clipRect(x, y, x + w, y + h);
        for (float ty = y; ty < y + h; ty += q[3] * zoom)
            for (float tx = x; tx < x + w; tx += q[2] * zoom) sprite(region, tx, ty, zoom, 1f);
        canvas.restore();
    }

    @Override public int[] size(String region) {
        int[] q = regions.get(region);
        return q == null ? null : new int[]{q[2], q[3]};
    }

    private Bitmap picture(String kind, int id) { return bitmap(kind + "/" + id + ".png"); }

    @Override public boolean picture(String kind, int id, float x, float y, float zoom, float alpha, boolean trim) {
        Bitmap b = picture(kind, id);
        if (b == null) return false;
        int[] q = trim ? pictureBounds(kind, id) : new int[]{0, 0, Math.min(32, b.getWidth()), Math.min(32, b.getHeight())};
        blit(b, q[0], q[1], q[2], q[3], x, y, zoom, alpha);
        return true;
    }

    @Override public int[] pictureBounds(String kind, int id) {
        String k = kind + "/" + id;
        if (bounds.containsKey(k)) return bounds.get(k);
        Bitmap b = picture(kind, id);
        int[] r = null;
        if (b != null) {
            int fw = Math.min(32, b.getWidth()), fh = Math.min(32, b.getHeight());
            int minX = fw, minY = fh, maxX = -1, maxY = -1;
            for (int y = 0; y < fh; y++)
                for (int x = 0; x < fw; x++)
                    if ((b.getPixel(x, y) >>> 24) > 0) {
                        minX = Math.min(minX, x); minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                    }
            r = maxX < 0 ? new int[]{0, 0, fw, fh} : new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
        }
        bounds.put(k, r);
        return r;
    }

    private int shade(int argb) {
        if (bright >= 1f) return argb;
        int a = argb >>> 24;
        int r = (int) (((argb >> 16) & 255) * bright), g = (int) (((argb >> 8) & 255) * bright), b = (int) ((argb & 255) * bright);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override public void rect(float x, float y, float w, float h, int argb) {
        fill.setColor(shade(argb));
        canvas.drawRect(x, y, x + w, y + h, fill);
    }

    @Override public void text(Face face, float size, String s, float x, float baseline, int argb) {
        if (s == null || s.isEmpty()) return;
        text.setTypeface(faces.get(face));
        text.setTextSize(size);
        text.setColor(shade(argb));
        canvas.drawText(s, x, baseline, text);
    }

    @Override public float width(Face face, float size, String s) {
        text.setTypeface(faces.get(face));
        text.setTextSize(size);
        return text.measureText(s);
    }

    @Override public void save() {
        canvas.save();
        brightStack.push(bright);
    }

    @Override public void restore() {
        canvas.restore();
        bright = brightStack.isEmpty() ? 1f : brightStack.pop();
        applyBright();
    }

    @Override public void translate(float dx, float dy) { canvas.translate(dx, dy); }
    @Override public void rotate(float degrees, float cx, float cy) { canvas.rotate(degrees, cx, cy); }
    @Override public void clip(float x, float y, float w, float h) { canvas.clipRect(x, y, x + w, y + h); }

    @Override public void brightness(float factor) {
        bright *= factor;
        applyBright();
    }

    private void applyBright() {
        if (bright >= 1f) { sprite.setColorFilter(null); return; }
        ColorMatrix m = new ColorMatrix();
        m.setScale(bright, bright, bright, 1f);
        sprite.setColorFilter(new ColorMatrixColorFilter(m));
    }
}
