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

    // how far "My Stuff!" sits below where the game's page has it (game pixels), and
    // where the grid starts under it
    static final int HEADING_DOWN = 6;
    static final float GRID_TOP = 100 + HEADING_DOWN * 3;
    // the Items tab's own pencil sketches (see Hud.sketches): {region, x, y, zoom, tilt};
    // the last is on the strip
    private static final Object[][] SKETCHES = {
            {"wm_hanger", 760, 34, 2, -8}, {"doodle_022", 250, 180, 1, 6}, {"doodle_009", 690, 196, 1, -12},
            {"wm_isaac", 440, 290, 1, -6}, {"wm_knife", 120, 300, 2, 18}, {"doodle_028", 180, 450, 1, -5},
            {"doodle_017", 700, 420, 1, 7}, {"wm_bible", 790, 660 + Hud.EXTRA * 3, 2, -4},
            // scribbles: a drip off the top edge, scratches, and on the strip
            {"sc_drip3", 600, 28, 3, 0}, {"sc_s4", 30, 496, 3, 0}, {"sc_s9", 874, 360, 3, 0}, {"sc_star", 40, 40, 3, 0},
            {"sc_s3", 28, 640, 3, 0}, {"sc_blot", 862, 652, 3, 0}, {"sc_s10", 440, 790, 3, 0}};

    static void draw(Hud h, Json.J s, Json.J player) {
        Painter p = h.p();
        Ui ui = h.ui();
        Words w = h.words();
        // the page without its "My Stuff!", which goes back on a little lower, clear of the top edge
        Hud.page(p, "paper_blank", Hud.EXTRA, 0);
        p.spritePart("paper", 45, 15, 69, 13, 45 * 3, (15 + HEADING_DOWN) * 3, 3, 1f);
        List<Cell> cells = cells(player);
        // a new item: jump to it
        if (cells.size() != ui.itemCountSeen) {
            if (cells.size() > ui.itemCountSeen) { ui.sel = -1; ui.selKind = "run"; ui.itemsRow = -1; }
            ui.itemCountSeen = cells.size();
        }
        int selIndex = ui.sel < 0 || ui.sel >= cells.size() ? cells.size() - 1 : ui.sel;
        boolean dead = TabMap.dead(s);
        boolean detail = ui.detail && selIndex >= 0 && !dead;

        h.sketches(SKETCHES, dead);

        if (dead) {
            TabMap.ghost(h, player);
        } else if (detail) {
            h.itemDetail(cells.get(selIndex).id, GRID_TOP + 8);
        } else {
            // 7 across at 3x, three rows in view; past that the grid scrolls (newest rows
            // first, until you scroll), with an arrow under it
            int rows = (cells.size() + Hud.GRID_COLS - 1) / Hud.GRID_COLS;
            int most = Math.max(0, rows - Hud.GRID_ROWS);
            int first = ui.itemsRow < 0 ? most : Math.min(ui.itemsRow, most);
            for (int i = first * Hud.GRID_COLS; i < cells.size() && i < (first + Hud.GRID_ROWS) * Hud.GRID_COLS; i++) {
                int k = i - first * Hud.GRID_COLS;
                float x = Hud.GRID_LEFT + (k % Hud.GRID_COLS) * Hud.GRID_STEP_X, y = GRID_TOP + (k / Hud.GRID_COLS) * Hud.GRID_STEP_Y;
                cell(h, cells.get(i), i, selIndex, x, y, 3);
            }
            h.gridScroll(rows, first);
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
        h.itemCell("items", c.id, x, y, zoom, 1f);
        // the selected one: a pencil line under it, as under the pocket item in use
        if (i == selIndex) h.underItem("items", c.id, false, x, y);
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
