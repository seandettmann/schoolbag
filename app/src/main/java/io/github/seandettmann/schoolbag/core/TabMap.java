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
        h.sketches(SKETCHES, dead(s));
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

    // the Map's own pencil sketches (see Hud.sketches): {region, x, y, zoom, tilt}
    private static final Object[][] SKETCHES = {
            {"doodle_026", 96, 52, 1, -10}, {"doodle_023", 400, 34, 1, 4}, {"wm_spider", 770, 96, 2, 12},
            {"doodle_031", 236, 236, 1, -7}, {"wm_stick", 690, 286, 2, 8}, {"doodle_038", 120, 452, 1, 3},
            {"doodle_045", 520, 520, 1, -6}, {"doodle_019", 800, 450, 1, -4},
            // scribbles: a drip off the top edge, scratches, and on the strip
            {"sc_drip1", 300, 28, 3, 0}, {"sc_s1", 40, 566, 3, 0}, {"sc_s12", 866, 300, 3, 0}, {"sc_moon", 690, 590, 3, 0},
            {"sc_s3", 30, 700, 3, 0}, {"sc_s7", 440, 806, 3, 0}, {"sc_blot", 624, 694, 3, 0}};

    /** Whether a box on a sheet would sit under the death picture (the ghost, or who died). */
    static boolean underGhost(float x, float y, float w, float h) {
        boolean ghost = x < 588 && x + w > 381 && y < GHOST_TOP + 300 && y + h > GHOST_TOP - 22;
        boolean line = x < 660 && x + w > 300 && y < GHOST_TOP + 376 && y + h > GHOST_TOP + 296;
        return ghost || line;
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

    // The strip under the map, between its torn edges, and the middle of its height.
    // Each half holds at most half the strip: trinkets from the left, the pocket from the right.
    static final float STRIP_X0 = 66, STRIP_X1 = 855, STRIP_MID = (SHEET_BOTTOM + 826) / 2f;
    static final float STRIP_HALF = (STRIP_X0 + STRIP_X1) / 2f;

    /**
     * The strip under the map, where the game's HUD has these: the trinkets from
     * the left, the pocket mirrored from the right. One thing on a side shows at
     * 3x with its name beside it (towards the middle); two show side by side at
     * 3x with no names; on the pocket's side the one you'll use next stays on
     * the very right with a pencil line under it, and the next one is dimmed to
     * its left. When they switch (the drop button) they slide past each other
     * into their new places. A pill's good, bad or horse is under its name.
     * Tainted Cain's bag sits on the right, in two rows. No headings: the
     * pictures say which is which.
     */
    private static void strip(Hud h, Json.J player) {
        Painter p = h.p();
        Words w = h.words();
        float cy = STRIP_MID, gap = 24;

        // the trinkets, from the left
        List<Json.J> trinkets = player.get("trinkets").items();
        if (trinkets.isEmpty()) {
            h.text(Face.SMALL, 50, 54, "No trinkets", STRIP_X0, cy - 27, Hud.INK);
        } else if (trinkets.size() == 1) {
            Json.J t = trinkets.get(0);
            float pw = trinket(h, t.get("id").integer(), STRIP_X0, cy, false);
            String name = w.text(t.get("name").str()) + (t.get("golden").bool() ? " (gold)" : "");
            float tx = STRIP_X0 + pw + 16;
            nameLines(h, name, null, Hud.INK, tx, STRIP_HALF - 12 - tx, cy, false);
        } else {
            float pw = trinket(h, trinkets.get(0).get("id").integer(), STRIP_X0, cy, false);
            trinket(h, trinkets.get(1).get("id").integer(), STRIP_X0 + pw + gap, cy, false);
        }

        // the pocket (or Tainted Cain's bag), from the right
        Json.J bag = player.get("bag");
        List<Json.J> pockets = player.get("pockets").items();
        if (bag.exists()) {
            List<Json.J> its = bag.get("items").items();
            float step = 36, top = cy - (2 * step - 4) / 2f, left = STRIP_X1 - (4 * step - 4);
            for (int i = 0; i < 8; i++) {
                String r = i < its.size() ? "cr-" + its.get(i).integer() : "cr-empty";
                p.sprite(r, left + (i % 4) * step, top + (i / 4) * step, 2, 1f);
            }
        } else if (pockets.isEmpty()) {
            String t = "Empty pocket";
            h.text(Face.SMALL, 50, 54, t, STRIP_X1 - p.width(Face.SMALL, 50, t), cy - 27, Hud.INK);
        } else if (pockets.size() == 1) {
            Json.J it = pockets.get(0);
            pocket(h, it, STRIP_X1 - 48, cy, 1f);
            String name, note = "";
            int color = Hud.INK; // every word in ink (Sean, Nov 27 2026)
            if (it.get("kind").str().equals("pill")) {
                if (it.get("identified").bool()) {
                    int eff = it.get("effect").integer();
                    name = w.text(pillName(h, player, eff));
                    String cls = w.pillClass(eff);
                    note = cls.equals("+") ? "Good pill" : cls.equals("-") ? "Bad pill" : "";
                } else {
                    name = "???";
                }
                if ((it.get("color").integer() & 2048) != 0) note = note.isEmpty() ? "Horse pill" : note.replace(" pill", " horse pill"); // "Good horse pill"
            } else {
                // a tarot card without its number: "The Fool", not "0 - The Fool"
                name = w.text(it.get("name").str()).replaceFirst("^[0IVXLC]+ +- +", "");
            }
            float tr = STRIP_X1 - 48 - 16;
            nameLines(h, name, note, color, tr, tr - (STRIP_HALF + 12), cy, true);
        } else {
            twoPockets(h, pockets, cy, gap);
        }
    }

    static final long SWAP_MS = 280;   // how long two pocket items take to switch places
    static final float DIM = 0.45f;     // the next pocket item, waiting its turn

    private static String pocketKey(Json.J it) {
        return it.get("kind").str() + ":" + it.get("id").integer() + ":" + it.get("color").integer();
    }

    /**
     * Every frame, on any tab: notices the pocket's first item becoming what was
     * second (the drop button switching them, or the first used up), so the Map
     * strip can show them switching places. Only starts it while the Map tab is
     * open, so coming back to the tab later doesn't replay it.
     */
    static void trackPocket(Hud h, Json.J player) {
        Ui ui = h.ui();
        List<Json.J> pk = player.get("pockets").items();
        String a = pk.size() > 0 ? pocketKey(pk.get(0)) : "", b = pk.size() > 1 ? pocketKey(pk.get(1)) : "";
        if (!a.isEmpty() && !b.isEmpty() && a.equals(ui.pocket1) && !a.equals(ui.pocket0) && ui.tab.equals("map")) {
            ui.swapFrom0 = ui.pocket0;
            ui.swapFrom1 = ui.pocket1;
            ui.pocketSwapAt = h.now();
        }
        ui.pocket0 = a;
        ui.pocket1 = b;
    }

    /**
     * Two pocket items: the one in use on the very right with a pencil line under
     * it, the next dimmed to its left. Just after they switch, each slides from
     * where it was to where it is now (passing the other a little above or
     * below), brightening or dimming on the way; one that wasn't showing fades in.
     */
    private static void twoPockets(Hud h, List<Json.J> pockets, float cy, float gap) {
        Painter p = h.p();
        Ui ui = h.ui();
        float right = STRIP_X1 - 48, left = right - gap - 48;
        long since = h.now() - ui.pocketSwapAt;
        float t = since >= 0 && since < SWAP_MS ? since / (float) SWAP_MS : 1f;
        float e = 1 - (1 - t) * (1 - t) * (1 - t); // eases out
        if (t < 1) ui.wake(h.now() + 16);
        for (int i = 1; i >= 0; i--) { // the one in use drawn last, on top
            Json.J it = pockets.get(i);
            String k = pocketKey(it);
            float toX = i == 0 ? right : left, toA = i == 0 ? 1f : DIM;
            float fromX = k.equals(ui.swapFrom0) ? right : left;
            float fromA = k.equals(ui.swapFrom0) ? 1f : k.equals(ui.swapFrom1) ? DIM : 0f;
            if (t >= 1) { fromX = toX; fromA = toA; }
            float x = fromX + (toX - fromX) * e, a = fromA + (toA - fromA) * e;
            float arc = (float) Math.sin(Math.PI * e) * 14 * (toX > fromX ? -1 : 1); // moving right goes over
            pocket(h, it, x, cy + arc, a);
        }
        // the pencil line under the one in use, like the open tab's: it stays put
        float ly = cy + 36 + 10, lx = right - 2;
        Hud.pencil(p, lx, ly, 52);
    }

    /** A trinket at 3x (trimmed), its left at x (or its right, mirrored), centred on cy; returns its width. */
    private static float trinket(Hud h, int id, float x, float cy, boolean fromRight) {
        int[] b = h.p().pictureBounds("trinkets", id);
        float pw = b == null ? 96 : Math.min(96, b[2] * 3), ph = b == null ? 96 : Math.min(96, b[3] * 3);
        h.item("trinkets", id, fromRight ? x - pw : x, cy - ph / 2f, pw, ph, 3, 1f, false);
        return pw;
    }

    /** A pocket card or pill at 3x, in a card front's box (48 x 72) with its left at x, centred on cy. */
    private static void pocket(Hud h, Json.J it, float x, float cy, float alpha) {
        Painter p = h.p();
        float py = cy - 36;
        if (it.get("kind").str().equals("pill")) {
            String r = "pill-" + (it.get("color").integer() & 2047);
            int[] s = p.size(r);
            if (s != null) p.sprite(r, x + (48 - s[0] * 3) / 2f, py + (72 - s[1] * 3) / 2f, 3, alpha);
        } else {
            Cards.draw(p, it.get("id").integer(), x, py, 3, alpha);
        }
    }

    /**
     * A name beside a picture on the strip, on up to two lines, with an optional
     * note under it, centred on cy, all in ink. As big as fits: 54, or 48 or 40
     * for a long name with a note under it, so it stays inside the strip's
     * paper (136 of its 172 points). Left-aligned from x, or right-aligned to x
     * when mirrored (the pocket's side).
     */
    private static void nameLines(Hud h, String name, String note, int color, float x, float width, float cy, boolean mirrored) {
        boolean hasNote = note != null && !note.isEmpty();
        float size = 54, lead = 0, noteSize = 0, total = 0;
        List<String> lines = null;
        int n = 0;
        for (float s : new float[] {54, 48, 40}) {
            size = s;
            lead = Math.round(s * 1.1f);
            noteSize = Math.round(s * 0.8f);
            lines = h.wrap(Face.SMALL, s, name, width);
            n = Math.min(lines.size(), 2);
            total = n * lead + (hasNote ? noteSize + 4 : 0);
            if (lines.size() <= 2 && total <= 136) break;
        }
        if (lines.size() > 2) lines.set(1, h.fit(Face.SMALL, size, lines.get(1) + " " + lines.get(2), width));
        float y = cy - total / 2f;
        for (int i = 0; i < n; i++) {
            String l = lines.get(i);
            h.text(Face.SMALL, size, lead, l, mirrored ? x - h.p().width(Face.SMALL, size, l) : x, y, color);
            y += lead;
        }
        if (hasNote) {
            String l = h.fit(Face.SMALL, noteSize, note, width);
            h.text(Face.SMALL, noteSize, noteSize + 4, l, mirrored ? x - h.p().width(Face.SMALL, noteSize, l) : x, y, Hud.INK);
        }
    }

    private static String pillName(Hud h, Json.J player, int effect) {
        for (Json.J pl : player.get("pills").items())
            if (pl.get("effect").integer() == effect) return pl.get("name").str();
        return "";
    }
}
