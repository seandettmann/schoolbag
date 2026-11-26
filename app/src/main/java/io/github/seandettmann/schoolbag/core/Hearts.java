package io.github.seandettmann.schoolbag.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a player's health, as the mod sends it, into the row of hearts the
 * game draws: red containers first, then soul, black and bone hearts in the
 * order they were picked up, then broken hearts. Each is one of the game's
 * own heart frames (ui_hearts.anm2), named as in the art bundle ("h-" + name).
 */
public final class Hearts {
    public static final class Heart {
        public final String sprite;
        public String overlay;   // drawn on top (gold), or null
        public float alpha = 1f;
        public boolean pulse;    // Tainted Magdalene's draining hearts
        public boolean gap;      // keeps a slot empty

        public Heart(String sprite) { this.sprite = sprite; }
    }

    public static final int KEEPER = 14, T_KEEPER = 33, LOST = 10, T_LOST = 31, T_MAGDALENE = 22;

    private Hearts() {}

    public static List<Heart> of(Json.J player) {
        List<Heart> row = new ArrayList<>();
        Json.J h = player.get("health");
        int type = player.get("type").integer();
        if (type == LOST || type == T_LOST) return row;
        boolean keeper = type == KEEPER || type == T_KEEPER;

        int max = h.get("maxHearts").integer();
        int red = h.get("hearts").integer();
        int soul = h.get("soulHearts").integer();
        int blackMask = h.get("blackMask").integer();
        int bones = h.get("boneHearts").integer();
        int boneMask = h.get("boneMask").integer();
        int rotten = h.get("rottenHearts").integer();
        int golden = h.get("goldenHearts").integer();
        int eternal = h.get("eternalHearts").integer();
        int broken = h.get("brokenHearts").integer();

        // red containers, then the slots after them: bone hearts where the
        // bone mask says so, soul (or black) hearts in the rest
        List<int[]> redSlots = new ArrayList<>(); // {row index, 0 container / 1 bone}
        int containers = (max + 1) / 2;
        for (int i = 0; i < containers; i++) {
            row.add(null);
            redSlots.add(new int[]{row.size() - 1, 0});
        }
        int soulSlots = (soul + 1) / 2;
        int extra = bones + soulSlots;
        int soulLeft = soul, soulIndex = 0;
        for (int k = 0; k < extra; k++) {
            boolean bone = ((boneMask >> k) & 1) != 0;
            if (!bone && soulIndex >= soulSlots) bone = true;     // the mask ran short
            if (bone) {
                row.add(null);
                redSlots.add(new int[]{row.size() - 1, 1});
            } else {
                boolean black = ((blackMask >> soulIndex) & 1) != 0;
                boolean half = soulLeft < 2;
                row.add(new Heart(black ? (half ? "BlackHeartHalf" : "BlackHeartFull") : (half ? "BlueHeartHalf" : "BlueHeartFull")));
                soulLeft -= half ? 1 : 2;
                soulIndex++;
            }
        }

        // red fills the containers, then the bone hearts
        int redLeft = red;
        List<Integer> fullRed = new ArrayList<>();
        for (int[] s : redSlots) {
            int fill = Math.min(2, Math.max(0, redLeft));
            redLeft -= fill;
            String name;
            if (s[1] == 1) name = fill == 2 ? "BoneHeartFull" : fill == 1 ? "BoneHeartHalf" : "BoneHeartEmpty";
            else if (keeper) name = fill == 2 ? "CoinHeartFull" : fill == 1 ? "CoinHeartHalf" : "CoinEmpty";
            else name = fill == 2 ? "RedHeartFull" : fill == 1 ? "RedHeartHalf" : "EmptyHeart";
            row.set(s[0], new Heart(name));
            if (fill == 2) fullRed.add(s[0]);
        }
        // rotten hearts are the last of the full red ones
        for (int i = 0; i < rotten && i < fullRed.size(); i++) {
            int at = fullRed.get(fullRed.size() - 1 - i);
            row.set(at, new Heart(row.get(at).sprite.startsWith("Bone") ? "RottenBoneHeartFull" : "RottenHeartFull"));
        }
        // an eternal heart sits on the last container or bone heart
        if (eternal > 0 && !redSlots.isEmpty() && !keeper) {
            int at = redSlots.get(redSlots.size() - 1)[0];
            row.set(at, new Heart(row.get(at).sprite.startsWith("Bone") ? "BoneEternal" : "RedEternal"));
        }
        // gold hearts: an outline over the rightmost hearts
        for (int i = row.size() - 1, g = golden; i >= 0 && g > 0; i--, g--) {
            Heart x = row.get(i);
            switch (x.sprite) {
                case "RedHeartFull": row.set(i, new Heart("RedGold")); break;
                case "BlueHeartFull": row.set(i, new Heart("BlueGold")); break;
                case "BlackHeartFull": row.set(i, new Heart("BlackGold")); break;
                default: x.overlay = "GoldHeartOverlay";
            }
        }
        for (int i = 0; i < broken; i++) row.add(new Heart(keeper ? "BrokenCoinHeart" : "BrokenHeart"));

        // Tainted Magdalene: everything past her first 2 red hearts drains
        if (type == T_MAGDALENE) {
            int seen = 0;
            for (int[] s : redSlots) {
                Heart x = row.get(s[0]);
                if (x.sprite.startsWith("Red") && !x.sprite.equals("RedEternal")) {
                    if (++seen > 2) x.pulse = true;
                }
            }
        }
        return row;
    }

    /** The game's icon for each kind of shield, in the order the mod sends them. */
    public static String shield(String kind) {
        switch (kind) {
            case "holyMantle": return "HolyMantle";
            case "blanket": return "HolyMantleBlanket";
            case "holyCard": return "HolyMantleHolyCard";
            case "woodenCross": return "HolyMantleWoodenCross";
            default: return "HolyMantle";
        }
    }
}
