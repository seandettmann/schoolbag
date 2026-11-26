package io.github.seandettmann.schoolbag.core;

/** The pages outside a run: waiting for a run (ten papers), and settings. */
final class Pages {
    private Pages() {}

    // {region, w, h, x, y, inner width, doodle, watermarks...{region, x, y}}
    static final Object[][] PAPERS = {
            {"np_1", 1206, 684, 17, 198, 1026, "doodle_022", new Object[][]{{"wm_isaac", 874, 72}, {"wm_stick", 84, 482}, {"wm_knife", 1094, 500}}},
            {"np_2", 1212, 570, 14, 255, 1032, "doodle_017", new Object[][]{{"wm_bible", 84, 72}, {"wm_hanger", 1042, 426}}},
            {"np_3", 1137, 525, 51, 277, 957, "doodle_039", new Object[][]{}},
            {"np_4", 1164, 636, 38, 222, 984, "doodle_028", new Object[][]{{"wm_face", 998, 72}, {"wm_poop", 84, 536}}},
            {"np_5", 1176, 699, 32, 190, 996, "doodle_033", new Object[][]{}},
            {"np_6", 1170, 558, 35, 261, 990, "doodle_045", new Object[][]{{"wm_spider", 984, 72}, {"wm_mushroom", 84, 382}}},
            {"np_7", 1080, 669, 80, 205, 900, "doodle_030", new Object[][]{{"wm_hanger", 84, 72}, {"wm_face", 914, 523}}},
            {"np_8", 1098, 669, 71, 205, 918, "doodle_026", new Object[][]{{"wm_isaac", 766, 72}, {"wm_knife", 84, 485}}},
            {"np_9", 1086, 732, 77, 174, 906, "doodle_031", new Object[][]{{"wm_poop", 802, 568}}},
            {"np_10", 1080, 672, 80, 204, 900, "doodle_009", new Object[][]{{"wm_stick", 84, 72}, {"wm_spider", 894, 542}}}};

    static void waiting(Hud h) {
        Painter p = h.p();
        Ui ui = h.ui();
        // the gear, top right: settings
        p.save();
        p.rotate(3, 1052 + 159 / 2f, 14 + 150 / 2f);
        p.sprite("gear_button", 1052, 14, 3, 1f);
        p.restore();
        ui.hit(1052, 14, 159, 150, () -> ui.settings = "main");

        Object[] np = PAPERS[Math.max(0, Math.min(9, ui.paper))];
        float x = (Integer) np[3], y = (Integer) np[4], pw = (Integer) np[1], ph = (Integer) np[2], iw = (Integer) np[5];
        p.sprite((String) np[0], x, y, 3, 1f);
        for (Object[] wm : (Object[][]) np[7]) p.sprite((String) wm[0], x + (Integer) wm[1], y + (Integer) wm[2], 2, 0.14f);

        String title = "Waiting for a run...";
        String[] sub = {"Start or continue a run up top."};
        if ("mod".equals(ui.outdated)) { title = "Mod needs an update"; sub = new String[]{"Restart Isaac to get", "the newest Schoolbag mod."}; }
        else if ("app".equals(ui.outdated)) { title = "App needs an update"; sub = new String[]{"Update Schoolbag", "in Obtainium."}; }
        else if (!ui.everAnswered && h.now() - ui.openedAt > 60_000) sub = new String[]{"Mod on, with --luadebug set?"};
        if (!ui.everAnswered) ui.wake(ui.openedAt + 60_001);

        String doodle = (String) np[6];
        float dh = h.spriteH(doodle, 2), dw = h.spriteW(doodle, 2);
        float total = dh + 14 + 96 + 14 + 64 * sub.length;
        float cx = x + 90, top = y + (ph - total) / 2f;
        p.sprite(doodle, cx + (iw - dw) / 2f, top, 2, 1f);
        h.centered(Face.TITLE, 96, 96, title, cx, top + dh + 14, iw, Hud.INK);
        for (int i = 0; i < sub.length; i++)
            h.centered(Face.TITLE, 64, 64, sub[i], cx, top + dh + 14 + 96 + 14 + i * 64, iw, Hud.GREY);
    }

    static final String[] LANGS = {"auto", "en", "es", "de", "fr", "ru", "ja", "ko", "zh"};
    static final String[] LANG_NAMES = {"Automatic", "English", "Español", "Deutsch", "Français",
            "Русский", "日本語", "한국어", "简体中文"};

    static String langName(String code) {
        for (int i = 0; i < LANGS.length; i++) if (LANGS[i].equals(code)) return LANG_NAMES[i];
        return LANG_NAMES[0];
    }

    /** What the About page says about speed. */
    static String[] speedLines(Ui ui) {
        String win = ui.viewW + " x " + ui.viewH + " at " + String.format(java.util.Locale.ROOT, "%.2f", ui.viewScale) + "x"
                + (ui.viewW == 1240 && ui.viewH == 1080 ? " (pixel for pixel)" : "");
        String draw = ui.framesLastMinute + " frames in the last minute, " + String.format(java.util.Locale.ROOT, "%.1f", ui.frameMs) + " ms each";
        String read = ui.transport.isEmpty() ? "Not reading the mod yet"
                : "Reading the mod over " + ui.transport + (ui.readMs >= 0 ? String.format(java.util.Locale.ROOT, ", %.1f ms a read", ui.readMs) : "");
        return new String[]{"Screen: " + win, "Drawing: " + draw, read, ui.modPerf.isEmpty() ? "Mod: measuring" : "Mod: " + ui.modPerf};
    }

    static void settings(Hud h) {
        Painter p = h.p();
        Ui ui = h.ui();
        String page = ui.settings;
        // back, top right
        p.save();
        p.rotate(-4, 1028 + 189 / 2f, 18 + 180 / 2f);
        p.sprite("back_tag", 1028, 18, 3, 1f);
        p.restore();
        ui.hit(1028, 18, 189, 180, () -> ui.settings = "main".equals(ui.settings) ? "none" : "main");

        float sx = 40, sy = 214;
        p.sprite("np_9", sx, sy, 3, 1f);
        boolean main = "main".equals(page);
        if (!main) p.sprite("wm_hanger", sx + 930, sy + 560, 2, 0.14f); // the main page's rows fill the paper
        float x = sx + 96, y = sy + 62, width = 860;
        String title = "language".equals(page) ? "Banner language" : "about".equals(page) ? "About" : "Settings";
        h.text(Face.TITLE, 90, 90, title, x, y, Hud.INK);
        y += 90 + 14;
        if (main) {
            // seven rows, 74 points each, to fit the paper
            float rowH = 74, ts = 50;
            Object[][] rows = {
                    {"Hide the game’s HUD", ui.hideHud, (Runnable) () -> ui.hideHud = !ui.hideHud},
                    {"Dim when nothing changes", ui.dim, (Runnable) () -> ui.dim = !ui.dim},
                    {"Follow the game", ui.follow, (Runnable) () -> ui.follow = !ui.follow},
                    {"Show new items", ui.showNewItems, (Runnable) () -> ui.showNewItems = !ui.showNewItems}};
            for (Object[] r : rows) {
                h.text(Face.TITLE, ts, ts, (String) r[0], x, y + (rowH - ts) / 2f, Hud.INK);
                p.sprite((Boolean) r[1] ? "check_on" : "check_off", x + width - 57, y + (rowH - 51) / 2f, 3, 1f);
                ui.hit(x, y, width, rowH, (Runnable) r[2]);
                y += rowH;
            }
            // brightness: each tap steps 100% -> 85% -> 70% -> 100%
            h.text(Face.TITLE, ts, ts, "Brightness", x, y + (rowH - ts) / 2f, Hud.INK);
            String bv = ui.paperBrightness + "%";
            h.text(Face.TITLE, ts, ts, bv, x + width - 33 - 14 - p.width(Face.TITLE, ts, bv), y + (rowH - ts) / 2f, Hud.INK);
            p.sprite("arrow", x + width - 33, y + (rowH - 27) / 2f, 3, 1f);
            ui.hit(x, y, width, rowH, () -> ui.paperBrightness = ui.paperBrightness <= 70 ? 100 : ui.paperBrightness - 15);
            y += rowH;
            h.text(Face.TITLE, ts, ts, "Banner language", x, y + (rowH - ts) / 2f, Hud.INK);
            String ln = langName(ui.language);
            float lw = p.width(Face.TITLE, ts, ln);
            h.text(Face.TITLE, ts, ts, ln, x + width - 33 - 14 - lw, y + (rowH - ts) / 2f, Hud.INK);
            p.sprite("arrow", x + width - 33, y + (rowH - 27) / 2f, 3, 1f);
            ui.hit(x, y, width, rowH, () -> ui.settings = "language");
            y += rowH;
            h.text(Face.TITLE, ts, ts, "About", x, y + (rowH - ts) / 2f, Hud.INK);
            String aw = "Credits, version, speed";
            float tw = p.width(Face.SMALL, 40, aw);
            h.text(Face.SMALL, 40, 40, aw, x + width - 33 - 14 - tw, y + (rowH - 40) / 2f + 4, Hud.GREY);
            p.sprite("arrow", x + width - 33, y + (rowH - 27) / 2f, 3, 1f);
            ui.hit(x, y, width, rowH, () -> ui.settings = "about");
        } else if ("about".equals(page)) {
            y -= 4;
            h.text(Face.SMALL, 40, 44, "Schoolbag app " + ui.appVersion + " · mod " + (ui.modVersion.isEmpty() ? "?" : ui.modVersion) + " · GPL-3.0", x, y, Hud.GREY);
            y += 44 + 22;
            String[] lines = {"An unofficial fan app. Game art and words © Edmund McMillen and Nicalis.",
                    "Inspired by isaac-hud and the Eden Duo emulator’s companion for The Binding of Isaac.",
                    "Made with AI, using Claude by Anthropic."};
            for (String l : lines) {
                for (String part : h.wrap(Face.SMALL, 42, l, width)) {
                    h.text(Face.SMALL, 42, 46, part, x, y, Hud.INK);
                    y += 46;
                }
                y += 12;
            }
            // speed, for testing on the device: the window, drawing, reading, the mod
            // (kept up to date once a second while the page is open)
            ui.wake(h.now() + 1000);
            y += 8;
            for (String l : speedLines(ui)) {
                h.text(Face.SMALL, 36, 40, l, x, y, Hud.GREY);
                y += 40;
            }
        } else {
            y -= 4;
            h.text(Face.SMALL, 40, 44, "For the banners the mod draws up top", x, y, Hud.GREY);
            y += 44 + 24;
            float colW = (width - 40) / 3f;
            for (int i = 0; i < LANGS.length; i++) {
                float cx = x + (i % 3) * (colW + 20), cy = y + (i / 3) * 92;
                boolean on = LANGS[i].equals(ui.language);
                if (on) p.sprite("arrow", cx, cy + (92 - 27) / 2f, 3, 1f);
                Face f = i >= 5 ? Face.SYSTEM : Face.TITLE;
                h.text(f, 52, 52, LANG_NAMES[i], cx + 48, cy + 20, Hud.INK);
                final String code = LANGS[i];
                ui.hit(cx, cy, colW, 92, () -> { ui.language = code; ui.languageChanged = true; });
            }
        }
    }
}
