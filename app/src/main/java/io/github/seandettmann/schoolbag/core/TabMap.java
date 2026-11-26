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

    // The Map tab's page gives the map more room than the other tabs: the big
    // sheet is GROW game pixels taller (a band of its middle repeated) and the
    // strip under it as much shorter (a band of its middle left out).
    static final int GROW = 22;
    static final float SHEET_BOTTOM = (178 + GROW) * 3;        // where the strip starts
    static final float MAP_CX = 475, MAP_CY = 268 + GROW * 1.5f, MAP_W = 780, MAP_H = 432 + GROW * 3;

    static void page(Painter p) {
        p.spritePart("paper_blank", 0, 0, 320, 100, 0, 0, 3, 1f);
        for (int done = 0; done < GROW; done += 40)
            p.spritePart("paper_blank", 0, 60, 320, Math.min(40, GROW - done), 0, (100 + done) * 3, 3, 1f);
        p.spritePart("paper_blank", 0, 100, 320, 78, 0, (100 + GROW) * 3, 3, 1f);
        p.spritePart("paper_blank", 0, 178, 320, 30, 0, (178 + GROW) * 3, 3, 1f);
        p.spritePart("paper_blank", 0, 208 + GROW, 320, 272 - 208 - GROW, 0, (208 + GROW) * 3, 3, 1f);
    }

    static void draw(Hud h, Json.J s, Json.J player) {
        Painter p = h.p();
        Words w = h.words();
        page(p);
        Json.J map = s.get("map");
        boolean dead = s.get("death").get("gameOver").bool();
        boolean loading = s.has("transition") && !dead;
        float[] box = null;
        if (dead) {
            dead(h, player);
        } else if (loading) {
            boolean a = (h.now() / 350) % 2 == 0;
            p.sprite(a ? "load_a" : "load_b", 368, 60, 2, 1f);
            h.centered(Face.TITLE, Hud.NAME, Hud.NAME, "Loading...", 0, 356, 960, Hud.INK);
            String leaving = Floors.name(w, s.get("floor"), false);
            h.centered(Face.SMALL, Hud.BODY, Hud.BODY, "Leaving " + leaving, 0, 436, 960, Hud.GREY);
        } else if (map.get("hidden").bool()) {
            p.sprite("map_question", 336, 36, 9, 1f);
            String why = map.get("hiddenReason").str().equals("lost")
                    ? "Curse of the Lost: the map returns next floor" : "The map is hidden for now";
            h.centered(Face.SMALL, Hud.BODY, Hud.BODY, why, 64, 352, 832, Hud.INK);
        } else {
            box = rooms(h, map);
        }
        if (box == null || !overlaps(box, 790, 44, 102, 58)) p.sprite("wm_spider", 790, 44, 2, 0.14f);
        if (box == null || !overlaps(box, 48, 438, 130, 85)) p.sprite("doodle_038", 48, 438, 1, 0.13f);
        strip(h, player);
    }

    private static boolean overlaps(float[] m, float bx, float by, float bw, float bh) {
        return !(bx + bw + 16 < m[0] || bx > m[0] + m[2] + 16 || by + bh + 16 < m[1] || by > m[1] + m[3] + 16);
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
        for (int c = 9; c >= 3; c--) {
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

    private static void dead(Hud h, Json.J player) {
        Painter p = h.p();
        double t = (h.now() % 2400) / 2400.0;
        float bob = (float) (-12 * (0.5 - 0.5 * Math.cos(2 * Math.PI * t)));
        boolean a = (h.now() / 350) % 2 == 0;
        p.sprite(a ? "ghost_a" : "ghost_b", 391, 64 + bob, 3, 1f);
        String who = h.words().text(player.get("name").str());
        if (who.isEmpty()) who = "Isaac";
        h.centered(Face.TITLE, 64, 64, who + " died...", 0, 368, 960, Hud.INK);
    }

    /**
     * Trinkets (left) and the pocket (right), on the strip under the map: one
     * line each, no headings (the pictures say which is which).
     */
    private static void strip(Hud h, Json.J player) {
        Painter p = h.p();
        Words w = h.words();
        float top = SHEET_BOTTOM + 26, rowH = 64, gap = 10;
        float lx = 60, rx = 540, lw = 456, rw = 360;
        List<Json.J> trinkets = player.get("trinkets").items();
        if (trinkets.isEmpty()) h.text(Face.SMALL, Hud.BODY, Hud.BODY, "No trinkets", lx, top + 2, Hud.NUMGREY);
        for (int i = 0; i < trinkets.size() && i < 2; i++) {
            Json.J t = trinkets.get(i);
            float y = top + i * (rowH + gap);
            p.picture("trinkets", t.get("id").integer(), lx, y, 2, 1f, false);
            String name = w.text(t.get("name").str()) + (t.get("golden").bool() ? " (gold)" : "");
            h.text(Face.SMALL, Hud.BODY, Hud.BODY, h.fit(Face.SMALL, Hud.BODY, name, lw - 78), lx + 64 + 14, y + 2, Hud.INK);
        }
        Json.J bag = player.get("bag");
        if (bag.exists()) {
            List<Json.J> its = bag.get("items").items();
            for (int i = 0; i < 8; i++) {
                String r = i < its.size() ? "cr-" + its.get(i).integer() : "cr-empty";
                p.sprite(r, rx + (i % 4) * 48, top + 6 + (i / 4) * 48, 3, 1f);
            }
            return;
        }
        List<Json.J> pockets = player.get("pockets").items();
        if (pockets.isEmpty()) {
            h.text(Face.SMALL, Hud.BODY, Hud.BODY, "Empty pocket", rx, top + 2, Hud.NUMGREY);
            return;
        }
        Json.J first = pockets.get(0);
        String name, line = "";
        int color = Hud.INK;
        if (first.get("kind").str().equals("pill")) {
            int color0 = first.get("color").integer() & 2047;
            p.sprite("pill-" + color0, rx, top + 8, 2, 1f);
            if (first.get("identified").bool()) {
                int eff = first.get("effect").integer();
                name = w.text(pillName(h, player, eff));
                String cls = w.pillClass(eff);
                line = cls.equals("+") ? "Good pill" : cls.equals("-") ? "Bad pill" : "";
            } else {
                name = "???";
                color = Hud.NUMGREY;
            }
            if ((first.get("color").integer() & 2048) != 0) line = line.isEmpty() ? "Horse pill" : line + ", horse pill";
        } else {
            int id = first.get("id").integer();
            p.sprite(Cards.region(id), rx, top + 8, 2, 1f);
            name = w.text(first.get("name").str());
            line = w.cardQuote(id);
        }
        h.text(Face.SMALL, Hud.BODY, Hud.BODY, h.fit(Face.SMALL, Hud.BODY, name, rw - 46), rx + 32 + 14, top + 2, color);
        if (!line.isEmpty())
            h.text(Face.SMALL, Hud.BODY, Hud.BODY, h.fit(Face.SMALL, Hud.BODY, line, rw), rx, top + rowH + gap + 2, Hud.GREY);
    }

    private static String pillName(Hud h, Json.J player, int effect) {
        for (Json.J pl : player.get("pills").items())
            if (pl.get("effect").integer() == effect) return pl.get("name").str();
        return "";
    }
}
