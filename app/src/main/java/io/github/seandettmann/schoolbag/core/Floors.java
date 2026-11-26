package io.github.seandettmann.schoolbag.core;

/** Floor and curse names, built from the game's codes the way its own banners name them. */
final class Floors {
    private Floors() {}

    // stage types: 0 normal, 1 and 2 the alternates, 4 and 5 the Repentance path
    private static final String[][] PAIRS = {
            {"BASEMENT", "CELLAR", "BURNING_BASEMENT", null, "DOWNPOUR", "DROSS"},
            {"CAVES", "CATACOMBS", "FLOODED_CAVES", null, "MINES", "ASHPIT"},
            {"DEPTHS", "NECROPOLIS", "DANK_DEPTHS", null, "MAUSOLEUM", "GEHENNA"},
            {"WOMB", "UTERO", "SCARRED_WOMB", null, "CORPSE", "CORPSE"}};
    private static final String[] CURSES = {"DARKNESS", "THE_LABYRINTH", "THE_LOST", "THE_UNKNOWN",
            "THE_CURSED", "THE_MAZE", "THE_BLIND", "THE_GIANT"};

    private static String pair(int index, int type) {
        if (index < 0 || index >= PAIRS.length) return null;
        String[] row = PAIRS[index];
        return type >= 0 && type < row.length && row[type] != null ? row[type] : row[0];
    }

    private static String key(Json.J floor, boolean greed) {
        int stage = floor.get("stage").integer(), type = floor.get("stageType").integer();
        if (greed) {
            if (stage >= 1 && stage <= 4) return pair(stage - 1, type);
            return stage == 5 ? "SHEOL" : stage == 6 ? "THE_SHOP" : stage == 7 ? "ULTRA_GREED" : null;
        }
        if (stage >= 1 && stage <= 8) return pair((stage - 1) / 2, type);
        switch (stage) {
            case 9: return "BLUE_WOMB";
            case 10: return type == 1 ? "CATHEDRAL" : "SHEOL";
            case 11: return type == 1 ? "CHEST" : "DARK_ROOM";
            case 12: return "THE_VOID";
            case 13: return "HOME";
            default: return null;
        }
    }

    /** "Basement II", "Caves XL", "Cathedral", "Downpour II" (mirror world too). */
    static String name(Words w, Json.J floor, boolean mirrored) {
        boolean greed = false;
        int stage = floor.get("stage").integer();
        String k = key(floor, greed);
        String name = k != null ? w.text("#" + k + "_NAME") : floor.get("name").str();
        if (k != null && stage >= 1 && stage <= 8) {
            name += (floor.get("curses").integer() & 2) != 0 ? " XL" : stage % 2 == 1 ? " I" : " II";
        }
        return name;
    }

    /** Greed mode's floors, which have no numbers ("Caves", "Sheol", "The Shop"). */
    static String greedName(Words w, Json.J floor) {
        String k = key(floor, true);
        return k != null ? w.text("#" + k + "_NAME") : floor.get("name").str();
    }

    /** "Curse of Darkness", without the game's "!" (the header has no room for shouting). */
    static String curseName(Words w, int index) {
        String s = w.text("#CURSE_OF_" + CURSES[index] + "_NAME");
        while (s.endsWith("!")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
