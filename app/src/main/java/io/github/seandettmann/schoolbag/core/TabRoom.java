package io.github.seandettmann.schoolbag.core;

import java.util.List;

/** The Room tab: the items on the pedestals in this room, numbered the same way everywhere. */
final class TabRoom {
    private TabRoom() {}

    // the Room tab's own pencil sketches (see Hud.sketches): {region, x, y, zoom, tilt};
    // the angel and the poop join them when there are items in the room
    private static final Object[][] SKETCHES = {
            {"wm_mushroom", 800, 40, 2, 6}, {"doodle_001", 650, 170, 1, -4}, {"wm_face", 130, 250, 2, -10},
            {"doodle_039", 110, 440, 1, 5}, {"doodle_030", 760, 410, 1, 10}};
    private static final Object[][] WITH_ITEMS = {
            {"doodle_033", 380, 320, 2, -3}, {"wm_poop", 90, 340, 2, 4}};

    static void draw(Hud h, Json.J s, Json.J player) {
        Painter p = h.p();
        Ui ui = h.ui();
        Words w = h.words();
        Hud.page(p, "paper_blank", Hud.EXTRA, 0);
        Json.J room = s.get("room");
        h.sketches(SKETCHES, TabMap.dead(s));
        if (TabMap.dead(s)) {
            // the run is over: the death picture, and the strip left blank
            TabMap.ghost(h, player);
            return;
        }
        int type = room.get("type").integer();
        String typeName = name(type);
        if (typeName == null) {
            h.text(Face.TITLE, 64, 64, "In the Room", 84, 58, Hud.INK);
        } else {
            String icon = TabMap.icon(type);
            float x = 84;
            if (icon != null) {
                p.sprite("i-" + icon, x - 18, 58 + (64 - 64) / 2f, 4, 1f);
                x += 64 - 18 - 14 + 18;
            }
            h.text(Face.TITLE, 64, 64, typeName, x, 58, Hud.INK);
        }
        List<Json.J> peds = room.get("pedestals").items();
        // a new room: select the first item
        int idx = room.get("index").integer(-1);
        if (idx != ui.roomIndexSeen) { ui.roomIndexSeen = idx; ui.roomSel = 0; ui.roomRow = 0; ui.roomDetail = false; }
        if (peds.isEmpty()) {
            p.sprite("doodle_006", 391, 140, 3, 1f);
            h.centered(Face.SMALL, Hud.BODY, Hud.BODY, "Nothing to pick up in here.", 96, 434, 768, Hud.INK);
            return;
        }
        if (ui.roomSel >= peds.size()) ui.roomSel = 0;
        if (ui.roomDetail && !peds.get(ui.roomSel).get("blind").bool()) {
            h.itemDetail(peds.get(ui.roomSel).get("id").integer(), 108);
            Json.J sel = peds.get(ui.roomSel);
            h.itemCard(sel.get("id").integer(), sel.get("name").str(), false, true,
                    "Tap here again for the room", () -> ui.roomDetail = !ui.roomDetail);
            return;
        }
        h.sketches(WITH_ITEMS, false);
        // items that share an options group: a pencil loop round them, "Take one" under it
        int g0 = -1, g1 = -1, group = 0;
        for (int i = 0; i < peds.size(); i++) {
            int g = peds.get(i).get("group").integer();
            if (g > 0 && (group == 0 || g == group)) { group = g; if (g0 < 0) g0 = i; g1 = i; }
        }
        // 7 across at 3x, three rows in view; past that the grid scrolls, as on the Items tab
        int rows = (peds.size() + Hud.GRID_COLS - 1) / Hud.GRID_COLS;
        int first = Math.min(ui.roomRow, Math.max(0, rows - Hud.GRID_ROWS));
        int from = first * Hud.GRID_COLS, to = Math.min(peds.size(), (first + Hud.GRID_ROWS) * Hud.GRID_COLS);
        int row0 = g0 / Hud.GRID_COLS;
        if (group > 0 && g1 > g0 && g1 / Hud.GRID_COLS == row0 && g0 >= from && g1 < to) {
            float lx = Hud.GRID_LEFT + (g0 % Hud.GRID_COLS) * Hud.GRID_STEP_X - 22, ly = 122 + (row0 - first) * Hud.GRID_STEP_Y;
            p.sprite("loop2", lx, ly, 3, 1f);
            // "Take one" under the loop, unless another row of items is there
            if (rows == 1) h.centered(Face.SMALL, 48, 48, "Take one", lx, 302, 261, Hud.INK);
        }
        for (int i = from; i < to; i++) {
            Json.J ped = peds.get(i);
            int k = i - from;
            float x = Hud.GRID_LEFT + (k % Hud.GRID_COLS) * Hud.GRID_STEP_X, y = 146 + (k / Hud.GRID_COLS) * Hud.GRID_STEP_Y;
            if (i == ui.roomSel) {
                p.rect(x - 5, y - 5, 106, 4, Hud.INK);
                p.rect(x - 5, y - 5 + 101, 106, 4, Hud.INK);
                p.rect(x - 5, y - 5, 4, 105, Hud.INK);
                p.rect(x - 5 + 102, y - 5, 4, 105, Hud.INK);
            }
            if (ped.get("blind").bool()) p.sprite("c_question", x, y, 3, 1f);
            else h.itemCell("items", ped.get("id").integer(), x, y, 3, 1f);
            h.text(Face.SMALL, 40, 40, String.valueOf(i + 1), x + 4, y + 98, Hud.NUMGREY);
            final int index = i;
            ui.hit(Hud.MX + x - 12, Hud.MY + y - 5, 120, 127, () -> { ui.roomSel = index; ui.roomDetail = false; });
        }
        h.gridScroll(rows, first);
        Json.J sel = peds.get(ui.roomSel);
        boolean blind = sel.get("blind").bool();
        h.itemCard(sel.get("id").integer(), sel.get("name").str(), blind, ui.roomDetail,
                "Tap here again for the room", () -> ui.roomDetail = !ui.roomDetail);
    }

    /** The room's name, where it has a type worth naming. */
    static String name(int type) {
        switch (type) {
            case 2: return "Shop";
            case 4: return "Treasure Room";
            case 5: return "Boss Room";
            case 7: return "Secret Room";
            case 8: return "Super Secret Room";
            case 9: return "Arcade";
            case 10: return "Curse Room";
            case 11: return "Challenge Room";
            case 12: return "Library";
            case 13: return "Sacrifice Room";
            case 14: return "Devil Room";
            case 15: return "Angel Room";
            case 16: return "Crawl Space";
            case 17: return "Boss Challenge";
            case 18: return "Bedroom";
            case 19: return "Bedroom";
            case 20: return "Vault";
            case 21: return "Dice Room";
            case 22: return "Black Market";
            case 24: return "Planetarium";
            case 29: return "Ultra Secret Room";
            default: return null;
        }
    }
}
