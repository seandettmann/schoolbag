package io.github.seandettmann.schoolbag.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * What the person has chosen on the bottom screen (tab, selection, settings
 * page), plus the little memory the HUD needs between reads: when the mod
 * last answered, the last stats (for the green and red differences), and the
 * waiting page's paper.
 */
public final class Ui {
    public String tab = "items";
    /** "run", "trinket" or "card": what the item card shows. -1 = the newest item. */
    public String selKind = "run";
    public int sel = -1;
    public int roomSel = 0;
    /** The item card swapped for the item's full description. */
    public boolean detail = false;
    public int roomIndexSeen = -1;
    public int itemCountSeen = -1;

    /** "none", "main", "language" or "about". */
    public String settings = "none";

    public boolean hideHud = true;
    public boolean dim = true;
    public boolean roomOnWalkIn = false;
    public String language = "auto";
    /** Set when the language changes, so the app sends the mod the new words. */
    public boolean languageChanged = false;
    public String appVersion = "";
    public String modVersion = "";

    public int paper = new Random().nextInt(10);
    private int lastPaper = -1;

    /** When the app opened, and when the mod last answered (System time, ms). 0 = never. */
    public long openedAt = System.currentTimeMillis();
    public long lastAnswer = 0;
    public boolean everAnswered = false;
    /** A different protocol version: "mod" or "app" is the one to update, or null. */
    public String outdated = null;

    // the last stats seen, and when each changed (for the differences)
    final Map<String, Double> lastStats = new HashMap<>();
    final Map<String, double[]> changes = new HashMap<>(); // key -> {difference, at ms}
    String statsOf = null;

    /** Tap targets from the last frame drawn. */
    public final List<Hit> hits = new ArrayList<>();

    public static final class Hit {
        public final float x, y, w, h;
        public final Runnable action;

        Hit(float x, float y, float w, float h, Runnable action) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.action = action;
        }
    }

    void hit(float x, float y, float w, float h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    /** Runs whatever is under a tap (in HUD points). True if something was there. */
    public boolean tap(float x, float y) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit t = hits.get(i);
            if (x >= t.x && x < t.x + t.w && y >= t.y && y < t.y + t.h) {
                t.action.run();
                return true;
            }
        }
        return false;
    }

    /** A new paper for the waiting page, never the same twice in a row. */
    public void newPaper() {
        int p;
        do { p = new Random().nextInt(10); } while (p == lastPaper);
        lastPaper = paper = p;
    }
}
