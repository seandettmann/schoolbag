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
        p.sprite("wm_hanger", sx + (main ? 690 : 930), sy + (main ? 630 : 560), 2, 0.14f);
        float x = sx + 96, y = sy + 62, width = 860;
        String title = "language".equals(page) ? "Banner language" : "about".equals(page) ? "About" : "Settings";
        h.text(Face.TITLE, 90, 90, title, x, y, Hud.INK);
        y += 90 + 14;
        if (main) {
            Object[][] rows = {
                    {"Hide the game’s HUD", ui.hideHud, (Runnable) () -> ui.hideHud = !ui.hideHud},
                    {"Dim when nothing changes", ui.dim, (Runnable) () -> ui.dim = !ui.dim},
                    {"Room tab on walk-in", ui.roomOnWalkIn, (Runnable) () -> ui.roomOnWalkIn = !ui.roomOnWalkIn}};
            for (Object[] r : rows) {
                h.text(Face.TITLE, 54, 54, (String) r[0], x, y + 19, Hud.INK);
                p.sprite((Boolean) r[1] ? "check_on" : "check_off", x + width - 57, y + (92 - 51) / 2f, 3, 1f);
                ui.hit(x, y, width, 92, (Runnable) r[2]);
                y += 92;
            }
            h.text(Face.TITLE, 54, 54, "Banner language", x, y + 19, Hud.INK);
            String ln = langName(ui.language);
            float lw = p.width(Face.TITLE, 54, ln);
            h.text(Face.TITLE, 54, 54, ln, x + width - 33 - 14 - lw, y + 19, Hud.INK);
            p.sprite("arrow", x + width - 33, y + (92 - 27) / 2f, 3, 1f);
            ui.hit(x, y, width, 92, () -> ui.settings = "language");
            y += 92;
            h.text(Face.TITLE, 54, 54, "About", x, y + 19, Hud.INK);
            String aw = "Credits, AI, version";
            float tw = p.width(Face.SMALL, 40, aw);
            h.text(Face.SMALL, 40, 40, aw, x + width - 33 - 14 - tw, y + 26, Hud.GREY);
            p.sprite("arrow", x + width - 33, y + (92 - 27) / 2f, 3, 1f);
            ui.hit(x, y, width, 92, () -> ui.settings = "about");
        } else if ("about".equals(page)) {
            y -= 4;
            h.text(Face.SMALL, 40, 44, "Schoolbag app " + ui.appVersion + " · mod " + (ui.modVersion.isEmpty() ? "?" : ui.modVersion) + " · GPL-3.0", x, y, Hud.GREY);
            y += 44 + 22;
            String[] lines = {"An unofficial fan app. Game art and words © Edmund McMillen and Nicalis.",
                    "Inspired by isaac-hud and the Eden Duo emulator’s companion for The Binding of Isaac.",
                    "Made with AI, using Claude by Anthropic."};
            for (String l : lines) {
                for (String part : h.wrap(Face.SMALL, 42, l, width)) {
                    h.text(Face.SMALL, 42, 50, part, x, y, Hud.INK);
                    y += 50;
                }
                y += 22;
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
