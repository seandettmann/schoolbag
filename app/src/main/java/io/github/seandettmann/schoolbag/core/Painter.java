package io.github.seandettmann.schoolbag.core;

/**
 * What the HUD needs to draw, in the 1240 x 1080 points of the Thor's bottom
 * screen. The app draws onto Android's Canvas; the tests draw the same calls
 * into an image off the device.
 *
 * Sprites are the game's pixel art: drawn at whole-number zooms with no
 * smoothing. Regions are the names in the art bundle's regions.json.
 */
public interface Painter {
    /** A named region, top-left at x, y, at a zoom. */
    void sprite(String region, float x, float y, float zoom, float alpha);

    /** Part of a named region (in the region's own pixels), at a zoom. */
    void spritePart(String region, int sx, int sy, int sw, int sh, float x, float y, float zoom, float alpha);

    /** A region repeated over a box, at a zoom (the floor behind everything). */
    void tile(String region, float x, float y, float w, float h, float zoom);

    /** Width and height of a region in its own pixels, or null if the bundle doesn't have it. */
    int[] size(String region);

    /**
     * An item picture from the bundle ("items" or "trinkets", by ID): its whole
     * 32 x 32 frame, or (trim) just its drawn pixels, top-left at x, y, at a
     * zoom. False if there's none.
     */
    boolean picture(String kind, int id, float x, float y, float zoom, float alpha, boolean trim);

    /** The drawn part of an item picture: {x, y, w, h} in its own pixels, or null. */
    int[] pictureBounds(String kind, int id);

    void rect(float x, float y, float w, float h, int argb);

    /** Text with its baseline at y. */
    void text(Face face, float size, String s, float x, float baseline, int argb);

    float width(Face face, float size, String s);

    void save();
    void restore();
    void translate(float dx, float dy);
    /** Rotates about cx, cy. */
    void rotate(float degrees, float cx, float cy);
    void clip(float x, float y, float w, float h);
    /** Darkens everything drawn until the matching restore (CSS brightness()). */
    void brightness(float factor);
}
