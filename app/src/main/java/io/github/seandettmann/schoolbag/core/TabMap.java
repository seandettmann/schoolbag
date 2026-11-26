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

    static void draw(Hud h, Json.J s, Json.J player) {
        Painter p = h.p();
        Words w = h.words();
        p.sprite("paper_blank", 0, 0, 3, 1f);
        Json.J map = s.get("map");
        boolean dead = s.get("death").get("gameOver").bool();
        boolean loading = s.has("transition") && !dead;
        float[] box = null;
        if (dead) {
            dead(h, player);
        } else if (loading) {
            boolean a = (h.now() / 350) % 2 == 0;
            p.sprite(a ? "load_a" : "load_b", 368, 60, 2, 1f);
            h.centered(Face.TITLE, 64, 64, "Loading...", 0, 356, 960, Hud.INK);
            String leaving = Floors.name(w, s.get("floor"), false);
            h.centered(Face.SMALL, 50, 50, "Leaving " + leaving, 0, 430, 960, Hud.GREY);
        } else if (map.get("hidden").bool()) {
            p.sprite("map_question", 336, 36, 9, 1f);
            String why = map.get("hiddenReason").str().equals("lost")
                    ? "Curse of the Lost: the map returns next floor" : "The map is hidden for now";
            h.centered(Face.SMALL, 50, 50, why, 144, 352, 672, Hud.INK);
        } else {
            box = rooms(h, map);
        }
        if (box == null || !overlaps(box, 790, 44, 102, 58)) p.sprite("wm_spider", 790, 44, 2, 0.14f);
        if (box == null || !overlaps(box, 48, 372, 130, 85)) p.sprite("doodle_038", 48, 372, 1, 0.13f);
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
            if (cols * 9 * c <= 780 && rows * 8 * c <= 432) { ms = c; break; }
        }
        float mapW = cols * 9 * ms, mapH = rows * 8 * ms;
        float ox = (float) Math.floor(475 - mapW / 2), oy = (float) Math.floor(268 - mapH / 2);
        int ik = Math.max(1, ms / 3);
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
                float bw = sw * 9 * ms, bh = sh * 8 * ms;
                p.sprite("i-" + icon, rx + (float) Math.floor((bw - 16 * ik) / 2), ry + (float) Math.floor((bh - 16 * ik) / 2), ik, 1f);
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

    /** Trinkets and the pocket, on the strip under the map. */
    private static void strip(Hud h, Json.J player) {
        Painter p = h.p();
        Words w = h.words();
        h.text(Face.SMALL, 50, 50, "Trinkets", 72, 572, Hud.INK);
        float y = 628;
        for (Json.J t : player.get("trinkets").items()) {
            int id = t.get("id").integer();
            p.picture("trinkets", id, 72, y, 2, 1f, false);
            String name = w.text(t.get("name").str()) + (t.get("golden").bool() ? " (gold)" : "");
            h.text(Face.SMALL, 50, 50, h.fit(Face.SMALL, 50, name, 404 - 78), 72 + 64 + 14, y + 7, Hud.INK);
            y += 74;
        }
        h.text(Face.SMALL, 50, 50, "Pocket", 516, 572, Hud.INK);
        Json.J bag = player.get("bag");
        if (bag.exists()) {
            List<Json.J> its = bag.get("items").items();
            for (int i = 0; i < 8; i++) {
                String r = i < its.size() ? "cr-" + its.get(i).integer() : "cr-empty";
                p.sprite(r, 516 + (i % 4) * 48, 630 + (i / 4) * 48, 3, 1f);
            }
            return;
        }
        List<Json.J> pockets = player.get("pockets").items();
        if (pockets.isEmpty()) return;
        Json.J first = pockets.get(0);
        float px = 516, py = 628;
        String name, line = "";
        int color = Hud.INK;
        if (first.get("kind").str().equals("pill")) {
            int color0 = first.get("color").integer() & 2047;
            p.sprite("pill-" + color0, px, py + 8, 2, 1f);
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
            String region = Cards.region(id);
            p.sprite(region, px, py, 2, 1f);
            name = w.text(first.get("name").str());
            line = w.cardQuote(id);
        }
        h.text(Face.SMALL, 50, 50, h.fit(Face.SMALL, 50, name, 340 - 46), px + 32 + 14, py + 7, color);
        if (!line.isEmpty()) h.text(Face.SMALL, 50, 50, h.fit(Face.SMALL, 50, line, 340), px, py + 64 + 8, Hud.GREY);
    }

    private static String pillName(Hud h, Json.J player, int effect) {
        for (Json.J pl : player.get("pills").items())
            if (pl.get("effect").integer() == effect) return pl.get("name").str();
        return "";
    }
}
