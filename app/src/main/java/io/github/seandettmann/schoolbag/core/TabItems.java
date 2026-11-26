package io.github.seandettmann.schoolbag.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Items tab ("My Stuff!"): every item in pickup order, and a card for the one selected. */
final class TabItems {
    private TabItems() {}

    static final class Cell {
        final int id;
        final String name;
        int count = 1;
        Cell(int id, String name) { this.id = id; this.name = name; }
    }

    /** One cell per item, in order of first pickup; repeats count up instead. */
    static List<Cell> cells(Json.J player) {
        Map<Integer, Cell> byId = new LinkedHashMap<>();
        for (Json.J it : player.get("items").items()) {
            int id = it.get("id").integer();
            Cell c = byId.get(id);
            if (c != null) c.count++;
            else byId.put(id, new Cell(id, it.get("name").str()));
        }
        return new ArrayList<>(byId.values());
    }

    static void draw(Hud h, Json.J s, Json.J player) {
        Painter p = h.p();
        Ui ui = h.ui();
        Words w = h.words();
        p.sprite("paper", 0, 0, 3, 1f);
        List<Cell> cells = cells(player);
        // a new item: jump to it
        if (cells.size() != ui.itemCountSeen) {
            if (cells.size() > ui.itemCountSeen) { ui.sel = -1; ui.selKind = "run"; }
            ui.itemCountSeen = cells.size();
        }
        int selIndex = ui.sel < 0 || ui.sel >= cells.size() ? cells.size() - 1 : ui.sel;
        boolean detail = ui.detail && selIndex >= 0;

        if (!detail) p.sprite("wm_hanger", 772, 20, 2, 0.14f);
        p.sprite("wm_bible", 790, 660, 2, 0.14f);

        if (detail) {
            Cell c = cells.get(selIndex);
            float y = 108;
            for (Json.J line : w.lines(c.id)) {
                for (String l : h.wrap(Face.SMALL, 36, line.str(), 800)) {
                    h.text(Face.SMALL, 36, 42, l, 72, y, Hud.INK);
                    y += 42;
                }
                y += 10;
            }
        } else if (cells.size() <= 21) {
            // 7 across at 3x
            for (int i = 0; i < cells.size(); i++) {
                float x = 54 + (i % 7) * 120, y = 100 + (i / 7) * 126;
                cell(h, cells.get(i), i, selIndex, x, y, 3);
            }
        } else {
            // a long run: 10 across at 2x, newest rows in view
            int rows = (cells.size() + 9) / 10;
            int first = Math.max(0, rows - 4) * 10;
            p.save();
            p.clip(62, 100, 808, 384);
            for (int i = first; i < cells.size(); i++) {
                int k = i - first;
                cell(h, cells.get(i), i, selIndex, 62 + (k % 10) * 80, 100 + (k / 10) * 96, 2);
            }
            p.restore();
            float thumbH = Math.round(380f * 4 / rows);
            p.rect(892, 104, 6, 380, 0xFFB8A27F);
            p.rect(892, 104 + 380 - thumbH, 6, thumbH, 0xFF6B5A47);
        }

        // the card for the selected item
        if (selIndex >= 0) {
            Cell c = cells.get(selIndex);
            float cx = 72, cy = 580;
            p.save();
            p.clip(cx, cy, 790, 216);
            h.itemCell("items", c.id, cx, cy + 2, 3, 1f);
            h.text(Face.TITLE, 64, 64, h.fit(Face.TITLE, 64, w.text(c.name), 670), cx + 120, cy + 2, Hud.INK);
            h.text(Face.SMALL, 50, 50, h.fit(Face.SMALL, 50, w.quote("collectibles", c.id), 670), cx + 120, cy + 70, Hud.GREY);
            if (detail) h.text(Face.SMALL, 50, 50, "Tap here again for all your items", cx, cy + 126, Hud.GREY);
            else h.text(Face.SMALL, 50, 50, h.fit(Face.SMALL, 50, w.firstLine(c.id), 790), cx, cy + 126, Hud.INK);
            p.restore();
            if (!w.lines(c.id).isEmpty() || detail)
                ui.hit(Hud.MX + cx, Hud.MY + cy, 790, 216, () -> ui.detail = !ui.detail);
        }
    }

    private static void cell(Hud h, Cell c, int i, int selIndex, float x, float y, int zoom) {
        Painter p = h.p();
        Ui ui = h.ui();
        float size = 32 * zoom;
        if (i == selIndex) {
            float b = 4;
            p.rect(x - 5, y - 5, size + 10, b, Hud.INK);
            p.rect(x - 5, y - 5 + (zoom == 3 ? 105 : 74) - b, size + 10, b, Hud.INK);
            p.rect(x - 5, y - 5, b, zoom == 3 ? 105 : 74, Hud.INK);
            p.rect(x - 5 + size + 10 - b, y - 5, b, zoom == 3 ? 105 : 74, Hud.INK);
        }
        h.itemCell("items", c.id, x, y, zoom, 1f);
        if (c.count > 1) {
            float tx = zoom == 3 ? x + 50 : x + 34, ty = zoom == 3 ? y + 56 : y + 38, fs = zoom == 3 ? 40 : 32;
            String t = "x" + c.count;
            float b = h.p().width(Face.SMALL, fs, t);
            float base = Face.SMALL.baseline(ty, fs, fs);
            float[][] offs = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {3, 3}, {-3, -3}, {3, -3}, {-3, 3}};
            for (float[] d : offs) p.text(Face.SMALL, fs, t, tx + d[0], base + d[1], Hud.PAPER);
            p.text(Face.SMALL, fs, t, tx, base, Hud.INK);
        }
        if (zoom == 3) h.text(Face.SMALL, 40, 40, String.valueOf(i + 1), x + 4, y + 98, Hud.NUMGREY);
        else h.text(Face.SMALL, 30, 30, String.valueOf(i + 1), x + 2, y + 64, Hud.NUMGREY);
        final int index = i;
        ui.hit(Hud.MX + x - 5, Hud.MY + y - 5, size + 10, zoom == 3 ? 122 : 92, () -> {
            ui.sel = index;
            ui.selKind = "run";
            ui.detail = false;
        });
    }
}
