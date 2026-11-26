package io.github.seandettmann.schoolbag.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * The game's words, from the art bundle: its string table (one file per
 * language) and its item data (string keys for names and pickup lines, and
 * the info lines from info_display.xml).
 *
 * The game gives names as keys such as "#THE_SAD_ONION_NAME". Modded content
 * has plain names, which pass through. A key with no words yet becomes words
 * made from the key ("Moms Knife"), as the mod does.
 */
public final class Words {
    private final Map<String, String> words;
    private final Json.J items;

    private static final Set<String> SMALL = new HashSet<>(Arrays.asList(
            "of", "the", "a", "an", "and", "in", "on", "to", "for", "at", "with", "or"));

    public Words(Map<String, String> words, Json.J items) {
        this.words = words != null ? words : new HashMap<String, String>();
        this.items = items != null ? items : Json.J.NONE;
    }

    public static Words empty() { return new Words(null, null); }

    /** All the words, for sending to the mod with POST /strings. */
    public Map<String, String> all() { return words; }

    /** The words for a key or name as the game gives it; "" for nothing. */
    public String text(String code) {
        if (code == null || code.isEmpty()) return "";
        if (code.charAt(0) != '#') return code;
        String key = code.substring(1);
        String w = words.get(key);
        if (w != null) return w;
        return fromKey(key);
    }

    /** "MOMS_KNIFE_NAME" -> "Moms Knife". Descriptions get nothing. */
    static String fromKey(String key) {
        if (!key.endsWith("_NAME")) return "";
        String base = key.substring(0, key.length() - 5).toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder();
        boolean first = true;
        for (String w : base.split("_")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            if (first || !SMALL.contains(w)) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            else b.append(w);
            first = false;
        }
        return b.toString();
    }

    public boolean hasKey(String key) { return words.containsKey(key); }

    // ---- item data ----

    private Json.J entry(String kind, int id) { return items.get(kind).get(String.valueOf(id)); }

    /** The pickup line ("Tears up") for a collectible or trinket. */
    public String quote(String kind, int id) { return text(entry(kind, id).get("desc").str()); }

    /** The first of the game's own info lines for a collectible, or "". */

    public java.util.List<Json.J> lines(int id) { return entry("collectibles", id).get("lines").items(); }

    public String cardQuote(int id) { return text(entry("cards", id).get("desc").str()); }

    /** "+" good, "-" bad, "" neither, from the pill effect's class in pocketitems.xml. */
    public String pillClass(int effect) {
        String c = entry("pills", effect).get("class").str();
        if (c.contains("+")) return "+";
        if (c.contains("-")) return "-";
        return "";
    }
}
