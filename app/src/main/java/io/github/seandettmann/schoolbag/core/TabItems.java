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
        Hud.page(p, "paper", Hud.EXTRA, 0);
        List<Cell> cells = cells(player);
        // a new item: jump to it
        if (cells.size() != ui.itemCountSeen) {
            if (cells.size() > ui.itemCountSeen) { ui.sel = -1; ui.selKind = "run"; }
            ui.itemCountSeen = cells.size();
        }
        int selIndex = ui.sel < 0 || ui.sel >= cells.size() ? cells.size() - 1 : ui.sel;
        boolean dead = TabMap.dead(s);
        boolean detail = ui.detail && selIndex >= 0 && !dead;

        // the Items tab's own pencil sketches: under the grid's corners, but not under a description
        if (!detail) {
            p.sprite("wm_hanger", 772, 20, 2, 0.14f);
            p.sprite("doodle_028", 40, 490, 1, 0.13f);
            p.sprite("doodle_017", 812, 500, 1, 0.13f);
        }
        p.sprite("wm_bible", 790, 660 + Hud.EXTRA * 3, 2, 0.14f);

        if (dead) {
            TabMap.ghost(h, player);
        } else if (detail) {
            h.itemDetail(cells.get(selIndex).id);
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
        if (selIndex >= 0 && !dead) { // after a death the strip is left blank
            Cell c = cells.get(selIndex);
            h.itemCard(c.id, c.name, false, detail, "Tap here again for all your items", () -> ui.detail = !ui.detail);
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
        // the whole cell, gaps included (120 x 126 at 3x, 80 x 96 at 2x)
        ui.hit(Hud.MX + x - (zoom == 3 ? 12 : 8), Hud.MY + y - 5, zoom == 3 ? 120 : 80, zoom == 3 ? 126 : 96, () -> {
            ui.sel = index;
            ui.selKind = "run";
            ui.detail = false;
        });
    }
}
