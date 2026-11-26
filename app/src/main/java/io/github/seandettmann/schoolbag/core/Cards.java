package io.github.seandettmann.schoolbag.core;

/**
 * A pocket card's picture, as the game's HUD shows it: every card by its ID
 * (card-&lt;id&gt;, from ui_cardspills.anm2), soul stones as their pickups, and
 * runes as the game's own rune icon (the minimap's), since the game's card
 * sheet has none.
 */
final class Cards {
    private Cards() {}

    /** Hagalaz to Black Rune, and the Rune Shard. */
    static boolean rune(int id) {
        return (id >= 32 && id <= 41) || id == 55;
    }

    /**
     * Draws the card's picture in a box the size of a card front at a zoom (16 x
     * 24 game pixels a zoom: 48 x 72 points at 3x), trimmed of its empty border
     * and centred, at the largest whole zoom up to that one that fits.
     */
    static void draw(Painter p, int id, float x, float y, int zoom) {
        draw(p, id, x, y, zoom, 1f);
    }

    /** The same, faded to `alpha`. */
    static void draw(Painter p, int id, float x, float y, int zoom, float alpha) {
        String region = "card-" + id;
        int max = zoom;
        float boxW = 16 * zoom, boxH = 24 * zoom;
        if (rune(id)) { region = "i-rune"; max = zoom * 2; }
        else if (id >= 81 && id <= 97) { boxW = 22 * zoom; x -= 3 * zoom; } // soul stones: pickups, a little wider than a card
        else if (p.size(region) == null) region = "cardfront-Outline";
        int[] b = p.regionBounds(region);
        if (b == null) return;
        int z = max;
        while (z > 1 && (b[2] * z > boxW || b[3] * z > boxH)) z--;
        p.spritePart(region, b[0], b[1], b[2], b[3],
                x + (float) Math.floor((boxW - b[2] * z) / 2), y + (float) Math.floor((boxH - b[3] * z) / 2), z, alpha);
    }
}
