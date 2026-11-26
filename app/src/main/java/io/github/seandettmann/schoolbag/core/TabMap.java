package io.github.seandettmann.schoolbag.core;

import java.util.List;

/** The Map tab: the floor's map in the game's own minimap art, and trinkets and pocket below. */
final class TabMap {
    private TabMap() {}

    // the cells each RoomShape covers, from its top-left
    static final int[][][] CELLS = {
            {}, {{0, 0}}, {{0, 0}}, {{0, 0}}, {{0, 0}, {0, 1}}, {{0, 0}, {0, 1}}, {{0, 0}, {1, 0}}, {{0, 0}, {1, 0}},
            {{0, 0}, {1, 0}, {0, 1}, {1, 1}}, {{1, 0}, {0, 1}, {1, 1}}, {{0, 0}, {0, 1}, {1, 1}},
            {{0, 0}, {1, 0}, {1, 1}}, {{0, 0}, {1, 0}, {0, 1}}};

    // RoomType -> the game's minimap icon
    static String icon(int type) {
        switch (type) {
            case 2: return "shop";
            case 4: return "treasureroom";
            case 5: return "boss";
            case 6: return "miniboss";
            case 7: return "secretroom";
            case 8: return "supersecretroom";
            case 9: return "arcade";
            case 10: return "curseroom";
            case 11: return "ambushroom";
            case 12: return "library";
            case 13: return "sacrificeroom";
            case 14: return "devilroom";
            case 15: return "angelroom";
            case 16: return "ladder";
            case 17: return "bossambushroom";
            case 18: return "isaacsroom";
            case 19: return "barrenroom";
            case 20: return "chestroom";
            case 21: return "diceroom";
            case 24: return "planetarium";
            case 25: return "teleporterroom";
            case 29: return "ultrasecretroom";
            default: return null;
        }
    }

    // The Map tab's page gives the map more room than the other tabs: its big
    // sheet is GROW game pixels taller, and the strip under it only needs two
    // lines, so it gives back the difference (every page is Hud.EXTRA taller).
    static final int GROW = 40;
    static final float SHEET_BOTTOM = (178 + GROW) * 3;        // where the strip starts
    static final float MAP_CX = 475, MAP_CY = 268 + GROW * 1.5f, MAP_W = 780, MAP_H = 432 + GROW * 3;
    // the biggest zoom for the rooms (9 x 8 game pixels a cell): small floors fill the sheet
    static final int MAP_ZOOM = 14;
    // how far the sheet's centre moved down since the first layout, for the hidden-map picture
    static final float DOWN = (GROW - 22) * 1.5f;
    // the death picture's top: the ghost and its line centred in the Items and Room tabs'
    // sheets, and at the same spot on the Map's, so nothing moves when you switch tabs
    static final float GHOST_TOP = 119;

    static void page(Painter p) {
        Hud.page(p, "paper_blank", GROW, GROW - Hud.EXTRA);
    }

    static void draw(Hud h, Json.J s, Json.J player) {
        Painter p = h.p();
        page(p);
        // the Map's own pencil sketches, under the map
        h.sketch("wm_spider", 790, 40, 2);
        h.sketch("doodle_026", 60, 36, 1);
        h.sketch("doodle_023", 600, 24, 1);
        h.sketch("doodle_031", 60, 270, 1);
        h.sketch("wm_stick", 840, 250, 2);
        h.sketch("doodle_038", 48, SHEET_BOTTOM - 162, 1);
        h.sketch("doodle_045", 800, SHEET_BOTTOM - 154, 1);
        Json.J map = s.get("map");
        // between floors the map stays as it was until the next floor's arrives
        if (dead(s)) {
            ghost(h, player);
        } else if (map.get("hidden").bool()) {
            p.sprite("map_question", 336, 36 + DOWN, 9, 1f);
            String why = map.get("hiddenReason").str().equals("lost")
                    ? "Curse of the Lost: the map returns next floor" : "The map is hidden for now";
            // wrapped: at today's text size the Curse of the Lost line is wider than the page
            List<String> lines = h.wrap(Face.SMALL, Hud.BODY, why, 740);
            for (int i = 0; i < lines.size() && i < 2; i++)
                h.centered(Face.SMALL, Hud.BODY, Hud.BODY, lines.get(i), 64, 352 + DOWN + i * 64, 832, Hud.INK);
        } else {
            rooms(h, map);
        }
        if (!dead(s)) strip(h, player); // after a death the strip is left blank
    }

    /** Draws the rooms; returns the map's box {x, y, w, h}. */
    private static float[] rooms(Hud h, Json.J map) {
        Painter p = h.p();
        int width = Math.max(1, map.get("width").integer(13));
        int current = map.get("current").integer(-1);
        boolean mirrored = map.get("mirrored").bool();
        List<Json.J> rooms = map.get("rooms").items();
        if (rooms.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Json.J r : rooms) {
            int idx = r.get("index").integer(), shape = shape(r);
            for (int[] c : CELLS[shape]) {
                int x = idx % width + c[0], y = idx / width + c[1];
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
        }
        int cols = maxX - minX + 1, rows = maxY - minY + 1;
        int ms = 3;
        for (int c = MAP_ZOOM; c >= 3; c--) {
            if (cols * 9 * c <= MAP_W && rows * 8 * c <= MAP_H) { ms = c; break; }
        }
        float mapW = cols * 9 * ms, mapH = rows * 8 * ms;
        float ox = (float) Math.floor(MAP_CX - mapW / 2), oy = (float) Math.floor(MAP_CY - mapH / 2);
        // a dark block under every cell a room covers (the game's outline fills the
        // pixel its big-room art stops short of)
        for (Json.J r : rooms) {
            int idx = r.get("index").integer(), shape = shape(r);
            for (int[] c : CELLS[shape]) {
                int x = idx % width + c[0] - minX, y = idx / width + c[1] - minY;
                if (mirrored) x = cols - 1 - x;
                p.rect(ox + x * 9 * ms, oy + y * 8 * ms, 9 * ms, 8 * ms, 0xFF050504);
            }
        }
        for (Json.J r : rooms) {
            int idx = r.get("index").integer(), shape = shape(r);
            int sw = (shape >= 6 ? 2 : 1), sh = (shape == 4 || shape == 5 || shape >= 8) ? 2 : 1;
            boolean isCurrent = false;
            for (int[] c : CELLS[shape]) if (idx + c[0] + c[1] * width == current) isCurrent = true;
            int x = idx % width - minX, y = idx / width - minY;
            int drawShape = shape;
            if (mirrored) {
                x = cols - x - sw;
                if (shape == 9) drawShape = 10; else if (shape == 10) drawShape = 9;
                else if (shape == 11) drawShape = 12; else if (shape == 12) drawShape = 11;
            }
            String look = isCurrent ? "c" : r.get("visited").bool() ? "v" : "u";
            float rx = ox + x * 9 * ms, ry = oy + y * 8 * ms;
            p.sprite("m-" + look + drawShape, rx, ry, ms, 1f);
            String icon = icon(r.get("type").integer());
            if (icon != null && (r.get("display").integer() & 4) != 0) {
                // the icon's own pixels (the game's frames leave space round them),
                // as big as fits well inside one cell, centred in the whole room
                int[] b = p.regionBounds("i-" + icon);
                if (b != null) {
                    int iz = Math.max(1, (int) Math.floor(Math.min(9 * ms, 8 * ms) * 0.62f / Math.max(b[2], b[3])));
                    float bw = sw * 9 * ms, bh = sh * 8 * ms;
                    p.spritePart("i-" + icon, b[0], b[1], b[2], b[3], rx + (float) Math.floor((bw - b[2] * iz) / 2),
                            ry + (float) Math.floor((bh - b[3] * iz) / 2), iz, 1f);
                }
            }
        }
        return new float[]{ox, oy, mapW, mapH};
    }

    private static int shape(Json.J r) {
        int s = r.get("shape").integer(1);
        return s >= 1 && s <= 12 ? s : 1;
    }

    /** The run is over: the game's death screen is up. */
    static boolean dead(Json.J s) {
        return s.get("death").get("gameOver").bool();
    }

    /**
     * The death picture, on every tab's sheet while the game's death screen is
     * up, at the same spot on each: the ghost bobbing, and who died.
     */
    static void ghost(Hud h, Json.J player) {
        Painter p = h.p();
        double t = (h.now() % 2400) / 2400.0;
        float bob = (float) (-12 * (0.5 - 0.5 * Math.cos(2 * Math.PI * t)));
        boolean a = (h.now() / 350) % 2 == 0;
        h.ui().wake(h.now() + 66); // the ghost bobs
        p.sprite(a ? "ghost_a" : "ghost_b", 391, GHOST_TOP + bob, 3, 1f);
        String who = h.words().text(player.get("name").str());
        if (who.isEmpty()) who = "Isaac";
        h.centered(Face.TITLE, 64, 64, who + " died...", 0, GHOST_TOP + 304, 960, Hud.INK);
    }

    // The strip under the map, between its torn edges, and where its two halves meet.
    static final float STRIP_X0 = 66, STRIP_X1 = 855, STRIP_MID = (SHEET_BOTTOM + 826) / 2f;
    static final float STRIP_SPLIT = STRIP_X0 + 0.45f * (STRIP_X1 - STRIP_X0);

    /**
     * The strip under the map, as two small cards side by side, where the game's
     * HUD has them. The trinkets on the left, a line each. The pocket on the
     * right: its picture, its name and, in grey under it, a pill's good, bad or
     * horse and what's next in the pocket (or Tainted Cain's bag, in two rows).
     * No headings: the pictures say which is which.
     */
    private static void strip(Hud h, Json.J player) {
        Painter p = h.p();
        Words w = h.words();
        float cy = STRIP_MID, x0 = STRIP_SPLIT + 16, right = STRIP_X1;

        // the trinkets, one a line
        List<Json.J> trinkets = player.get("trinkets").items();
        if (trinkets.isEmpty()) {
            h.text(Face.SMALL, 44, 48, "No trinkets", STRIP_X0, cy - 24, Hud.NUMGREY);
        } else {
            int n = Math.min(trinkets.size(), 2);
            float rowH = 68, y = cy - n * rowH / 2f;
            for (int i = 0; i < n; i++) {
                Json.J t = trinkets.get(i);
                String name = w.text(t.get("name").str()) + (t.get("golden").bool() ? " (gold)" : "");
                p.picture("trinkets", t.get("id").integer(), STRIP_X0, y + 2, 2, 1f, false);
                h.text(Face.SMALL, 40, 48, h.fit(Face.SMALL, 40, name, STRIP_SPLIT - 16 - STRIP_X0 - 72), STRIP_X0 + 72, y + 12, Hud.INK);
                y += rowH;
            }
        }

        // the pocket (or Tainted Cain's bag)
        Json.J bag = player.get("bag");
        List<Json.J> pockets = player.get("pockets").items();
        if (bag.exists()) {
            List<Json.J> its = bag.get("items").items();
            float step = 36, top = cy - (2 * step - 4) / 2f;
            for (int i = 0; i < 8; i++) {
                String r = i < its.size() ? "cr-" + its.get(i).integer() : "cr-empty";
                p.sprite(r, x0 + (i % 4) * step, top + (i / 4) * step, 2, 1f);
            }
        } else if (pockets.isEmpty()) {
            h.text(Face.SMALL, 44, 48, "Empty pocket", x0, cy - 24, Hud.NUMGREY);
        } else {
            Json.J first = pockets.get(0);
            String name, note = "";
            int color = Hud.INK;
            float px = x0, py = cy - 36;                 // a 48 x 72 box: a card front at 3x
            if (first.get("kind").str().equals("pill")) {
                String r = "pill-" + (first.get("color").integer() & 2047);
                int[] s = p.size(r);
                if (s != null) p.sprite(r, px + (48 - s[0] * 3) / 2f, py + (72 - s[1] * 3) / 2f, 3, 1f);
                if (first.get("identified").bool()) {
                    int eff = first.get("effect").integer();
                    name = w.text(pillName(h, player, eff));
                    String cls = w.pillClass(eff);
                    note = cls.equals("+") ? "Good pill" : cls.equals("-") ? "Bad pill" : "";
                } else {
                    name = "???";
                    color = Hud.NUMGREY;
                }
                if ((first.get("color").integer() & 2048) != 0) note = note.isEmpty() ? "Horse pill" : note + ", horse pill";
            } else {
                Cards.draw(p, first.get("id").integer(), px, py, 3);
                name = w.text(first.get("name").str());
            }
            if (pockets.size() > 1) {
                String next = nameOf(h, player, pockets.get(1));
                note = note.isEmpty() ? "Then " + next : note + ", then " + next;
            }
            float tx = x0 + 48 + 20, tw = right - tx, th = note.isEmpty() ? 54 : 54 + 44;
            h.text(Face.TITLE, 48, 54, h.fit(Face.TITLE, 48, name, tw), tx, cy - th / 2f, color);
            if (!note.isEmpty()) h.text(Face.SMALL, 38, 44, h.fit(Face.SMALL, 38, note, tw), tx, cy - th / 2f + 54, Hud.GREY);
        }
    }

    /** A pocket item's name: a card's, an identified pill's, or ??? for a pill not yet known. */
    private static String nameOf(Hud h, Json.J player, Json.J it) {
        if (!it.get("kind").str().equals("pill")) return h.words().text(it.get("name").str());
        if (!it.get("identified").bool()) return "???";
        return h.words().text(pillName(h, player, it.get("effect").integer()));
    }

    private static String pillName(Hud h, Json.J player, int effect) {
        for (Json.J pl : player.get("pills").items())
            if (pl.get("effect").integer() == effect) return pl.get("name").str();
        return "";
    }
}
