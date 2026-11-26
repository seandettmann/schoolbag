package io.github.seandettmann.schoolbag.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws the bottom screen from the mod's state: the header (actives, hearts,
 * and one line of floor or mode, curses and clock), the pickups scrap, the
 * stats, the two tabs (Map and Items) and their pages, and the waiting and
 * settings pages.
 * Positions start from the approved mock-ups (1240 x 1080 points); since the
 * Thor tests the header is one line, the tabs sit beside the hearts and the
 * pages are taller.
 */
public final class Hud {
    static final int W = 1240, H = 1080;
    static final int INK = 0xFF362F2D, GREY = 0xFF6A5C52, NUMGREY = 0xFF7E6F62, HUDTEXT = 0xFFF5F0EA,
            FLOORTEXT = 0xFFBDB0A2, CURSETEXT = 0xFFE6CCF7, BLACK = 0xFF000000, PAPER = 0xFFC7B29A;
    // the main page's box: its top-left, and how many game pixels taller every
    // page is than its art (the room the one-line header gave back)
    static final int MX = 272, MY = 190, EXTRA = 18;
    // the tabs: beside the hearts, under the header's line
    static final float TABS_X = 626, TABS_Y = 84;
    // text sizes, a step up from the mock-ups after the first look on the Thor
    static final float LINE = 54;      // the header's line
    static final float NAME = 72;      // an item's name on its card
    static final float BODY = 60;      // the small hand lettering on cards and strips
    static final float DETAIL = 46;    // an item's full description

    private final Painter p;
    private final Ui ui;
    private final Words w;
    private final long now;

    private Hud(Painter p, Ui ui, Words w, long now) {
        this.p = p; this.ui = ui; this.w = w; this.now = now;
    }

    /** Draws one frame. state is the last answer from the mod, or null if there's none. */
    public static void draw(Painter p, Json.J state, Ui ui, Words words, long nowMs) {
        ui.hits.clear();
        new Hud(p, ui, words, nowMs).frame(state);
    }

    private void frame(Json.J s) {
        p.rect(0, 0, W, H, 0xFF1D1410);
        p.tile("floor_basement", 0, 0, W, H, 3);
        if (!"none".equals(ui.settings)) { Pages.settings(this); return; }
        boolean live = s != null && s.get("inRun").bool() && ui.outdated == null && s.get("players").size() > 0;
        if (!live) { Pages.waiting(this); return; }
        Json.J player = s.get("players").at(0);
        header(s, player);
        pickups(player);
        stats(s, player);
        tabs();
        p.save();
        p.translate(MX, MY);
        switch (ui.tab) {
            case "map": TabMap.draw(this, s, player); break;
            default: TabItems.draw(this, s, player);
        }
        p.restore();
    }

    // ---- shared helpers ----

    Painter p() { return p; }
    Ui ui() { return ui; }
    Words words() { return w; }
    long now() { return now; }

    /** Text in a line box: top-left x, top, as CSS lays out a line of the given height. */
    void text(Face f, float size, float lineHeight, String s, float x, float top, int color) {
        p.text(f, size, s, x, f.baseline(top, size, lineHeight), color);
    }

    /** The HUD's white-on-black lettering: the text with a black outline of o points. */
    void outlined(Face f, float size, float lineHeight, String s, float x, float top, int color, float o) {
        float b = f.baseline(top, size, lineHeight);
        float[][] offs = {{o, o}, {-o, 0}, {o, 0}, {0, -o}, {0, o}};
        for (float[] d : offs) p.text(f, size, s, x + d[0], b + d[1], BLACK);
        p.text(f, size, s, x, b, color);
    }

    /** Cuts text to fit a width, with "…" (CSS text-overflow: ellipsis). */
    String fit(Face f, float size, String s, float maxW) {
        if (p.width(f, size, s) <= maxW) return s;
        String dots = "…";
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (p.width(f, size, s.substring(0, mid) + dots) <= maxW) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo).trim() + dots;
    }

    /** Breaks text into lines no wider than maxW, at spaces. */
    List<String> wrap(Face f, float size, String s, float maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && p.width(f, size, next) > maxW) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /** Text in a box with text-align: center. */
    void centered(Face f, float size, float lineHeight, String s, float x, float top, float width, int color) {
        text(f, size, lineHeight, s, x + (width - p.width(f, size, s)) / 2f, top, color);
    }

    /** Draws an item picture trimmed of its empty border, at the largest whole zoom (up to max) that fits a box. */
    boolean item(String kind, int id, float x, float y, float boxW, float boxH, int maxZoom, float alpha, boolean center) {
        int[] b = p.pictureBounds(kind, id);
        if (b == null) {
            p.sprite("c_question", x, y, Math.max(1, Math.min(maxZoom, (int) (boxW / 32))), alpha);
            return false;
        }
        int z = maxZoom;
        while (z > 1 && (b[2] * z > boxW || b[3] * z > boxH)) z--;
        float dx = center ? (boxW - b[2] * z) / 2f : 0, dy = center ? (boxH - b[3] * z) / 2f : 0;
        return p.picture(kind, id, x + dx, y + dy, z, alpha, true);
    }

    /** An item's full 32 x 32 picture (untrimmed), as in the item grids. */
    void itemCell(String kind, int id, float x, float y, int zoom, float alpha) {
        if (!p.picture(kind, id, x, y, zoom, alpha, false))
            p.sprite("c_question", x, y, zoom, alpha);
    }

    /**
     * The card at the bottom of the Items tab: the item's picture, name
     * and pickup line, and the first of the game's info lines. Tapping it opens
     * (or closes) the item's full description.
     */
    void itemCard(int id, String nameCode, boolean blind, float stripW, boolean open, String back, Runnable toggle) {
        float cx = 72, cy = 580 + EXTRA * 3;
        p.save();
        p.clip(cx, cy, 790, 216);
        if (blind) {
            p.sprite("c_question", cx, cy + 2, 3, 1f);
            text(Face.TITLE, NAME, NAME, "???", cx + 120, cy, INK);
            text(Face.SMALL, BODY, BODY, fit(Face.SMALL, BODY, "Curse of the Blind hides this one", 670), cx + 120, cy + 76, GREY);
        } else {
            itemCell("items", id, cx, cy + 2, 3, 1f);
            text(Face.TITLE, NAME, NAME, fit(Face.TITLE, NAME, w.text(nameCode), 670), cx + 120, cy, INK);
            text(Face.SMALL, BODY, BODY, fit(Face.SMALL, BODY, w.quote("collectibles", id), 670), cx + 120, cy + 76, GREY);
            if (open) text(Face.SMALL, BODY, BODY, fit(Face.SMALL, BODY, back, stripW), cx, cy + 146, GREY);
            else text(Face.SMALL, BODY, BODY, fit(Face.SMALL, BODY, w.firstLine(id), stripW), cx, cy + 146, INK);
        }
        p.restore();
        if (!blind) ui.hit(MX + cx, MY + cy, 790, 216, toggle);
    }

    /** An item's full description, in the space above the card: the game's info lines, or its pickup line. */
    void itemDetail(int id) {
        List<String> lines = new ArrayList<>();
        for (Json.J l : w.lines(id)) lines.add(l.str());
        if (lines.isEmpty()) lines.add(w.quote("collectibles", id));
        p.save();
        p.clip(60, 96, 830, 400 + EXTRA * 3);
        float y = 108;
        for (String line : lines) {
            for (String l : wrap(Face.SMALL, DETAIL, line, 800)) {
                text(Face.SMALL, DETAIL, 54, l, 72, y, INK);
                y += 54;
            }
            y += 10;
        }
        p.restore();
    }

    /**
     * A page's paper at 3x: the big sheet grow game pixels taller (a plain band
     * of its middle repeated), the strip under it cut game pixels shorter (a
     * band of its middle left out).
     */
    static void page(Painter p, String region, int grow, int cut) {
        p.spritePart(region, 0, 0, 320, 100, 0, 0, 3, 1f);
        for (int done = 0; done < grow; done += 40)
            p.spritePart(region, 0, 60, 320, Math.min(40, grow - done), 0, (100 + done) * 3, 3, 1f);
        p.spritePart(region, 0, 100, 320, 78, 0, (100 + grow) * 3, 3, 1f);
        p.spritePart(region, 0, 178, 320, 30, 0, (178 + grow) * 3, 3, 1f);
        p.spritePart(region, 0, 208 + cut, 320, 272 - 208 - cut, 0, (208 + grow) * 3, 3, 1f);
    }

    float spriteW(String region, float zoom) { int[] s = p.size(region); return s == null ? 0 : s[0] * zoom; }
    float spriteH(String region, float zoom) { int[] s = p.size(region); return s == null ? 0 : s[1] * zoom; }

    static String two(int n) { return n < 10 && n >= 0 ? "0" + n : String.valueOf(n); }

    // ---- header: actives, hearts, lives, floor, clock, curse ----

    private void header(Json.J s, Json.J player) {
        Json.J primary = Json.J.NONE, bag = Json.J.NONE;
        for (Json.J a : player.get("actives").items()) {
            if (a.get("slot").integer() == 0) primary = a;
            if (a.get("slot").integer() == 1) bag = a;
        }
        boolean hasActive = primary.exists() && primary.get("id").integer() > 0;
        if (bag.exists() && bag.get("id").integer() > 0)
            item("items", bag.get("id").integer(), 10, 10, 72, 60, 3, 1f, false);
        if (hasActive) {
            item("items", primary.get("id").integer(), 40, 32, 132, 120, 6, 1f, false);
            charge(primary, 174, 18, 4);
        }
        float heartsX = hasActive ? 244 : 24;
        boolean unknown = (s.get("floor").get("curses").integer() & 8) != 0 || player.get("health").get("hidden").bool();
        boolean dead = s.get("death").get("gameOver").bool();
        List<Hearts.Heart> slots = new ArrayList<>();
        if (dead) {
            for (int i = 0; i < 6; i++) slots.add(heart("EmptyHeart"));
        } else if (unknown) {
            slots.add(heart("CurseHeart"));
        } else {
            slots.addAll(Hearts.of(player));
            for (Json.J sh : player.get("shields").items()) slots.add(heart(Hearts.shield(sh.str())));
        }
        for (int i = 0; i < slots.size() && i < 12; i++)
            drawHeart(slots.get(i), heartsX + (i % 6) * 60, 16 + (i / 6) * 50, 5);
        for (int i = 12; i < slots.size(); i++)
            drawHeart(slots.get(i), heartsX + 360 + (i - 12) * 60, 66, 5);
        // the header's line stops short of the hearts' first row and the lives
        int row1 = Math.min(Math.min(slots.size(), 12), 6);
        float left = hasActive ? 244 : 24;
        if (row1 > 0) left = heartsX + (row1 - 1) * 60 + 75;
        int lives = player.get("lives").integer();
        if (lives > 0 && !dead) {
            float lx = heartsX + row1 * 60 + 22;
            String t = "x" + lives + (player.get("livesMaybe").bool() ? "?" : "");
            outlined(Face.NUMBER, 52, 52, t, lx, 30, HUDTEXT, 4);
            left = Math.max(left, lx + p.width(Face.NUMBER, 52, t));
        }
        headerRight(s, left + 24);
    }

    private static Hearts.Heart heart(String name) { return new Hearts.Heart(name); }

    private void drawHeart(Hearts.Heart h, float x, float y, float zoom) {
        if (h.gap) return;
        float a = h.alpha;
        if (h.pulse) a = (float) (0.525 - 0.175 * Math.cos(2 * Math.PI * (now % 2400) / 2400.0)); // 35-70%, 2.4 s
        p.sprite("h-" + h.sprite, x, y, zoom, a);
        if (h.overlay != null) p.sprite("h-" + h.overlay, x, y, zoom, a);
    }

    /** The game's charge bar (ui_chargebar), filled to charge / maxCharge, with its notches. */
    void charge(Json.J active, float x, float y, float zoom) {
        int max = active.get("maxCharge").integer();
        if (max <= 0) return;
        int cur = Math.min(max, active.get("charge").integer());
        p.sprite("bar-BarEmpty", x, y, zoom, 1f);
        int top = 3, bot = 29;
        int cut = bot - Math.round((bot - top) * (float) cur / max);
        if (cur > 0) p.spritePart("bar-BarFull", 0, cut, 16, 32 - cut, x, y + cut * zoom, zoom, 1f);
        if (p.size("bar-BarOverlay" + max) != null) p.sprite("bar-BarOverlay" + max, x, y, zoom, 1f);
    }

    /**
     * The header's one line, right-aligned: the curses (the game's icons) and the
     * mirror world, then the floor's name (or Greed's mode in its place), then
     * the clock (or Greed's wave). The name is cut short before it reaches left.
     */
    private void headerRight(Json.J s, float left) {
        Json.J run = s.get("run"), floor = s.get("floor");
        int difficulty = run.get("difficulty").integer();
        boolean greed = difficulty >= 2;
        List<Object[]> items = new ArrayList<>();
        int curses = floor.get("curses").integer();
        for (int i = 0; i < 8; i++)
            if ((curses & (1 << i)) != 0 && p.size("curse_" + (i + 1)) != null) items.add(new Object[]{"icon", "curse_" + (i + 1)});
        if (s.get("map").get("mirrored").bool()) items.add(new Object[]{"icon3", "i-mirrorroom"});
        if (greed) {
            int wave = floor.get("greedWave").integer(), waves = floor.get("greedWaves").integer();
            items.add(new Object[]{"text", difficulty == 3 ? "Greedier!" : "Greed Mode", 0xFFF2C94C});
            items.add(new Object[]{"sprite", "lvl_greed", 4f, -12f});
            items.add(new Object[]{"num", "Wave " + wave + "/" + waves});
        } else {
            items.add(new Object[]{"text", Floors.name(w, floor, s.get("map").get("mirrored").bool())});
            items.add(new Object[]{"sprite", "lvl_clock", 4f, -16f});
            items.add(new Object[]{"num", clock(run.get("timeFrames").lng())});
        }
        flexRight(W - 24, 18, left, items.toArray(new Object[0][]), 14);
    }

    /**
     * A right-aligned row of the header's pieces, vertically centred in the line,
     * like the mock-up's flex rows. Kinds: text (hand lettering), num (numbers),
     * sprite (with left and right margins), icon (2x), icon3 (3x). If the row
     * would reach past left, its text is cut short to fit.
     */
    private void flexRight(float right, float top, float left, Object[][] items, float gap) {
        float[] widths = new float[items.length];
        float total = 0;
        for (int i = 0; i < items.length; i++) {
            Object[] it = items[i];
            switch ((String) it[0]) {
                case "text": widths[i] = p.width(Face.SMALL, LINE, (String) it[1]); break;
                case "num": widths[i] = p.width(Face.NUMBER, 39, (String) it[1]); break;
                case "sprite": widths[i] = spriteW((String) it[1], 2) + (Float) it[2] + (Float) it[3]; break;
                case "icon": widths[i] = spriteW((String) it[1], 2); break;
                case "icon3": widths[i] = spriteW((String) it[1], 3) - 14; break;
            }
            total += widths[i] + (i > 0 ? gap : 0);
        }
        for (int i = 0; i < items.length && total > right - left; i++) {
            if (!"text".equals(items[i][0])) continue;
            float room = widths[i] - (total - (right - left));
            String cut = room > 40 ? fit(Face.SMALL, LINE, (String) items[i][1], room) : "";
            items[i] = items[i].clone();
            items[i][1] = cut;
            total -= widths[i] - p.width(Face.SMALL, LINE, cut);
            widths[i] = p.width(Face.SMALL, LINE, cut);
        }
        float x = right - total;
        for (int i = 0; i < items.length; i++) {
            Object[] it = items[i];
            switch ((String) it[0]) {
                case "text":
                    outlined(Face.SMALL, LINE, LINE, (String) it[1], x, top, it.length > 2 ? (Integer) it[2] : FLOORTEXT, 3);
                    break;
                case "num":
                    outlined(Face.NUMBER, 39, 39, (String) it[1], x, top + (LINE - 39) / 2f, FLOORTEXT, 3);
                    break;
                case "sprite":
                    p.sprite((String) it[1], x + (Float) it[2], top + (LINE - spriteH((String) it[1], 2)) / 2f, 2, 1f);
                    break;
                case "icon":
                    p.sprite((String) it[1], x, top + (LINE - spriteH((String) it[1], 2)) / 2f, 2, 1f);
                    break;
                case "icon3":
                    p.sprite((String) it[1], x - 8, top + (LINE - spriteH((String) it[1], 3)) / 2f, 3, 1f);
                    break;
            }
            x += widths[i] + gap;
        }
    }

    static String clock(long frames) {
        long secs = frames / 30;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", secs / 3600, (secs / 60) % 60, secs % 60);
    }

    // ---- pickups ----

    private void pickups(Json.J player) {
        Json.J pk = player.get("pickups");
        int type = player.get("type").integer();
        List<String[]> rows = new ArrayList<>(); // {sprite, text}
        String coins = two(pk.get("coins").integer());
        rows.add(new String[]{"p-coin", coins});
        if (type == 25) rows.add(new String[]{"p-poop", two(pk.get("poopMana").integer())});   // Tainted ???
        else rows.add(new String[]{"p-bomb", two(pk.get("bombs").integer())});
        rows.add(new String[]{"p-key", two(pk.get("keys").integer())});
        // the game sends both charges for everyone; only the Bethanys use them
        if (type == 18) rows.add(new String[]{"p-soul", two(pk.get("soulCharge").integer())});
        if (type == 36) rows.add(new String[]{"p-blood", two(pk.get("bloodCharge").integer())});
        boolean poops = type == 25 && player.has("poops");
        boolean tall = rows.size() > 3 || poops;
        float x0 = 22, y0 = 176;
        if (tall) p.sprite("scrap-res4", x0, y0, 2, 1f);
        else p.sprite("scrap_res", x0, y0, 2, 1f);
        for (int i = 0; i < rows.size(); i++) {
            float ry = y0 + 30 + i * 62;
            p.sprite(rows.get(i)[0], x0 + 34, ry + 4, 3, 1f);
            String t = rows.get(i)[1];
            float fs = t.length() > 2 ? 52 : 65;
            text(Face.NUMBER, fs, fs, t, x0 + 34 + 48 + 16, ry + (56 - fs) / 2f, INK);
        }
        if (poops) {
            Json.J q = player.get("poops").get("queue");
            int have = pk.get("poopMana").integer();
            String[] names = {"", "poop", "corny", "burning", "stone", "stinky", "black", "holy", "liquid", "fart", "bomb", "diarrhea"};
            float qx = x0 + 26, qy = y0 + 220;
            p.sprite("pb-frame-ink", qx, qy, 2, 1f);
            int first = q.at(0).integer();
            if (first > 0 && first < names.length) p.sprite("pb-" + names[first], qx, qy, 2, have >= 1 ? 1f : 0.4f);
            for (int i = 1; i < 3; i++) {
                int k = q.at(i).integer();
                if (k > 0 && k < names.length) p.sprite("pb-" + names[k], qx + 48 + (i - 1) * 32, qy + 8, 2, i + 1 > have ? 0.4f : 1f);
            }
        }
        statsTall = tall;
    }

    private boolean statsTall;

    // ---- stats ----

    private void stats(Json.J s, Json.J player) {
        Json.J st = player.get("stats"), ch = s.get("chances");
        String[][] rows = {
                {"stat_speed", "speed"}, {"stat_tears", "tears"}, {"stat_damage", "damage"}, {"stat_range", "range"},
                {"stat_shotspeed", "shotSpeed"}, {"stat_luck", "luck"}, {"stat_devil", "devil"}, {"stat_angel", "angel"},
                {"stat-planet", "planetarium"}};
        float top = statsTall ? 494 : 444, rowH = statsTall ? 60 : 64, gap = statsTall ? 0 : 4;
        String who = player.get("type").integer() + ":" + s.get("run").get("seed").str();
        boolean fresh = !who.equals(ui.statsOf);
        ui.statsOf = who;
        for (int i = 0; i < rows.length; i++) {
            float y = top + i * (rowH + gap);
            String key = rows[i][1];
            boolean chance = i >= 6;
            Json.J v = chance ? ch.get(key) : st.get(key);
            String value = !v.exists() ? "-" : chance ? Math.round(v.num() * 100) + "%" : String.format(Locale.ROOT, "%.2f", v.num());
            p.sprite(rows[i][0], 30, y + (rowH - 64) / 2f, 4, 1f);
            float tx = 30 + 64 + 20;
            outlined(Face.NUMBER, 52, 52, value, tx, y + (rowH - 52) / 2f, HUDTEXT, 4);
            // the difference beside a stat that just changed, green up, red down
            if (v.exists()) {
                double now = chance ? Math.round(v.num() * 100) : Math.round(v.num() * 100) / 100.0;
                Double was = ui.lastStats.get(key);
                if (!fresh && was != null && Math.abs(now - was) > 1e-9) ui.changes.put(key, new double[]{now - was, this.now});
                ui.lastStats.put(key, now);
                double[] c = ui.changes.get(key);
                if (fresh) ui.changes.remove(key);
                else if (c != null && this.now - c[1] < 2500) {
                    String d = chance ? (c[0] > 0 ? "+" : "") + Math.round(c[0]) + "%" : String.format(Locale.ROOT, "%+.2f", c[0]);
                    float dx = tx + p.width(Face.NUMBER, 52, value) + 20 - 8;
                    float a = (float) Math.min(1, (2500 - (this.now - c[1])) / 500.0);
                    int col = c[0] > 0 ? 0xFF63D64A : 0xFFF0504A;
                    int alpha = Math.round(a * 255);
                    outlined(Face.NUMBER, 30, 30, d, dx, y + (rowH - 30) / 2f, (alpha << 24) | (col & 0xFFFFFF), 3);
                }
            }
        }
    }

    // ---- tabs ----

    /**
     * The two tabs, beside the hearts. Each is the game's paper scrap widened
     * to fill the space up to the page's edge: its first columns, then the
     * art again from an earlier column (a plain band of it drawn twice).
     */
    private void tabs() {
        // key, label, art, tilt, label nudge, art height, first columns, then from (141 game pixels wide)
        Object[][] t = {
                {"map", "Map", "tab_map", -1.5f, 2f, 112f, 93, 67},
                {"items", "Items", "tab_items", 0.8f, -3f, 114f, 62, 57}};
        float x = TABS_X;
        for (Object[] tab : t) {
            final String key = (String) tab[0];
            String art = (String) tab[2];
            int[] size = p.size(art);
            if (size == null) continue;
            int keep = (Integer) tab[6], from = (Integer) tab[7];
            float w = (keep + size[0] - from) * 2, h = (Float) tab[5], dy = (Float) tab[4];
            boolean active = key.equals(ui.tab);
            float y = TABS_Y + (active ? 0 : 16);
            p.save();
            p.rotate((Float) tab[3], x + w / 2f, y + h / 2f);
            if (!active) p.brightness(0.55f);
            p.spritePart(art, 0, 0, keep, size[1], x, y, 2, 1f);
            p.spritePart(art, from, 0, size[0] - from, size[1], x + keep * 2, y, 2, 1f);
            centered(Face.SMALL, 75, 75, (String) tab[1], x, y + (h - 75) / 2f + dy, w, INK);
            p.restore();
            ui.hit(x, y, w, h, () -> ui.tab = key);
            x += w + 12;
        }
    }
}
