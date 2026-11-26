import io.github.seandettmann.schoolbag.core.Face;
import io.github.seandettmann.schoolbag.core.Json;
import io.github.seandettmann.schoolbag.core.Painter;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the HUD into an image off the device (Java2D), from the same art
 * bundle the app uses. For checking screens against the mock-ups.
 */
public final class DesktopPainter implements Painter {
    private final File art;
    private final Graphics2D g;
    private final Map<String, BufferedImage> sheets = new HashMap<>();
    private final Map<String, int[]> regions = new HashMap<>();
    private final Map<String, String> regionSheet = new HashMap<>();
    private final Map<Face, Font> fonts = new HashMap<>();
    private final Map<String, int[]> bounds = new HashMap<>();
    private final Deque<Object[]> stack = new ArrayDeque<>();
    private float bright = 1f;

    public DesktopPainter(File art, Graphics2D g) throws Exception {
        this.art = art;
        this.g = g;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        Json.J r = Json.parse(new String(Files.readAllBytes(new File(art, "regions.json").toPath()), "UTF-8"));
        for (Map.Entry<String, Object> e : r.map().entrySet()) {
            Json.J v = new Json.J(e.getValue());
            regionSheet.put(e.getKey(), v.at(0).str());
            regions.put(e.getKey(), new int[]{v.at(1).integer(), v.at(2).integer(), v.at(3).integer(), v.at(4).integer()});
        }
        for (Face f : Face.values()) {
            Font font = f.file == null ? new Font("SansSerif", Font.PLAIN, 10)
                    : Font.createFont(Font.TRUETYPE_FONT, new File(art, f.file));
            fonts.put(f, font);
        }
    }

    private BufferedImage sheet(String name) {
        return sheets.computeIfAbsent(name, n -> {
            try { return ImageIO.read(new File(art, n.contains("/") ? n : "sheets/" + n)); }
            catch (Exception e) { return null; }
        });
    }

    private void blit(BufferedImage img, int sx, int sy, int sw, int sh, float x, float y, float zoom, float alpha) {
        if (img == null) return;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, Math.min(1, alpha))));
        BufferedImage src = img;
        if (bright < 1f) src = darken(img.getSubimage(sx, sy, sw, sh));
        else src = img.getSubimage(sx, sy, sw, sh);
        AffineTransform t = g.getTransform();
        g.translate(x, y);
        g.scale(zoom, zoom);
        g.drawImage(src, 0, 0, null);
        g.setTransform(t);
        g.setComposite(old);
    }

    private BufferedImage darken(BufferedImage in) {
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < in.getHeight(); y++)
            for (int x = 0; x < in.getWidth(); x++) {
                int c = in.getRGB(x, y);
                int a = c >>> 24, r = (int) (((c >> 16) & 255) * bright), gg = (int) (((c >> 8) & 255) * bright), b = (int) ((c & 255) * bright);
                out.setRGB(x, y, (a << 24) | (r << 16) | (gg << 8) | b);
            }
        return out;
    }

    @Override public void sprite(String region, float x, float y, float zoom, float alpha) {
        int[] q = regions.get(region);
        if (q == null) { System.err.println("missing region " + region); return; }
        blit(sheet(regionSheet.get(region)), q[0], q[1], q[2], q[3], x, y, zoom, alpha);
    }

    @Override public void spritePart(String region, int sx, int sy, int sw, int sh, float x, float y, float zoom, float alpha) {
        int[] q = regions.get(region);
        if (q == null || sw <= 0 || sh <= 0) return;
        blit(sheet(regionSheet.get(region)), q[0] + sx, q[1] + sy, sw, sh, x, y, zoom, alpha);
    }

    @Override public void tile(String region, float x, float y, float w, float h, float zoom) {
        int[] q = regions.get(region);
        if (q == null) return;
        Shape clip = g.getClip();
        g.clipRect((int) x, (int) y, (int) w, (int) h);
        for (float ty = y; ty < y + h; ty += q[3] * zoom)
            for (float tx = x; tx < x + w; tx += q[2] * zoom) sprite(region, tx, ty, zoom, 1f);
        g.setClip(clip);
    }

    @Override public int[] size(String region) {
        int[] q = regions.get(region);
        return q == null ? null : new int[]{q[2], q[3]};
    }

    @Override public int[] regionBounds(String region) {
        String k = "region:" + region;
        if (bounds.containsKey(k)) return bounds.get(k);
        int[] q = regions.get(region);
        BufferedImage img = q == null ? null : sheet(regionSheet.get(region));
        int[] r = null;
        if (img != null) {
            int minX = q[2], minY = q[3], maxX = -1, maxY = -1;
            for (int y = 0; y < q[3]; y++)
                for (int x = 0; x < q[2]; x++)
                    if ((img.getRGB(q[0] + x, q[1] + y) >>> 24) > 0) {
                        minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                    }
            r = maxX < 0 ? new int[]{0, 0, q[2], q[3]} : new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
        }
        bounds.put(k, r);
        return r;
    }

    private BufferedImage picture(String kind, int id) { return sheet(kind + "/" + id + ".png"); }

    @Override public boolean picture(String kind, int id, float x, float y, float zoom, float alpha, boolean trim) {
        BufferedImage img = picture(kind, id);
        if (img == null) return false;
        int[] b = trim ? pictureBounds(kind, id) : new int[]{0, 0, Math.min(32, img.getWidth()), Math.min(32, img.getHeight())};
        blit(img, b[0], b[1], b[2], b[3], x, y, zoom, alpha);
        return true;
    }

    @Override public int[] pictureBounds(String kind, int id) {
        String k = kind + "/" + id;
        if (bounds.containsKey(k)) return bounds.get(k);
        BufferedImage img = picture(kind, id);
        int[] b = null;
        if (img != null) {
            int minX = img.getWidth(), minY = img.getHeight(), maxX = -1, maxY = -1;
            // the item's own 32 x 32 frame (some sheets hold more than one)
            int fw = Math.min(32, img.getWidth()), fh = Math.min(32, img.getHeight());
            for (int y = 0; y < fh; y++)
                for (int x = 0; x < fw; x++)
                    if ((img.getRGB(x, y) >>> 24) > 0) {
                        minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                    }
            b = maxX < 0 ? new int[]{0, 0, fw, fh} : new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
        }
        bounds.put(k, b);
        return b;
    }

    @Override public void rect(float x, float y, float w, float h, int argb) {
        g.setColor(color(argb));
        g.fill(new java.awt.geom.Rectangle2D.Float(x, y, w, h));
    }

    private Color color(int argb) {
        int a = argb >>> 24, r = (int) (((argb >> 16) & 255) * bright), gg = (int) (((argb >> 8) & 255) * bright), b = (int) ((argb & 255) * bright);
        return new Color(r, gg, b, a);
    }

    private Font font(Face f, float size) { return fonts.get(f).deriveFont(size); }

    @Override public void text(Face face, float size, String s, float x, float baseline, int argb) {
        if (s == null || s.isEmpty()) return;
        g.setFont(font(face, size));
        g.setColor(color(argb));
        g.drawString(s, x, baseline);
    }

    @Override public float width(Face face, float size, String s) {
        return (float) font(face, size).getStringBounds(s, g.getFontRenderContext()).getWidth();
    }

    @Override public void save() { stack.push(new Object[]{g.getTransform(), g.getClip(), bright}); }

    @Override public void restore() {
        Object[] o = stack.pop();
        g.setTransform((AffineTransform) o[0]);
        g.setClip((Shape) o[1]);
        bright = (Float) o[2];
    }

    @Override public void translate(float dx, float dy) { g.translate(dx, dy); }
    @Override public void rotate(float degrees, float cx, float cy) { g.rotate(Math.toRadians(degrees), cx, cy); }
    @Override public void clip(float x, float y, float w, float h) { g.clip(new java.awt.geom.Rectangle2D.Float(x, y, w, h)); }
    @Override public void brightness(float factor) { bright *= factor; }
}
