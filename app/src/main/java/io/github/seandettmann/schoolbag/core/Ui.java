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
    /** The tab showing. Every run starts on the Map tab. */
    public String tab = "map";
    /** "run", "trinket" or "card": what the item card shows. -1 = the newest item. */
    public String selKind = "run";
    public int sel = -1;
    public int roomSel = 0;
    /** The item card swapped for the item's full description. */
    public boolean detail = false;
    public boolean roomDetail = false;
    public int roomIndexSeen = -1;
    public int itemCountSeen = -1;

    /** "none", "main", "language" or "about". */
    public String settings = "none";

    public boolean hideHud = true;
    public boolean dim = true;
    /**
     * Follow the game: the Room tab in a room with items and the Map tab on
     * leaving it, the Map tab when the game's Map button is pressed, and the
     * Items tab while the game is on its pause menu.
     */
    public boolean follow = true;
    /** A newly picked-up item's card on the Items tab for a few seconds. */
    public boolean showNewItems = true;
    /** The whole screen's brightness, in percent: 100, 85 or 70. */
    public int paperBrightness = 100;
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

    /** Until when (System time, ms) to show "Tap the top screen to play". */
    public long focusNoteUntil = 0;
    /** How the controller went back to the game after opening, for About ("" before it's tried). */
    public String controller = "";
    /** The controller is still here (the hand-back didn't work): the intro asks for a tap. */
    public boolean tapForController = false;

    /**
     * When the frame just drawn next needs drawing again for an animation
     * (System time, ms), or Long.MAX_VALUE if nothing on it moves. Drawing
     * code asks with wake(); the view redraws then, or sooner on a change.
     */
    public long wakeAt = Long.MAX_VALUE;

    public void wake(long at) { if (at < wakeAt) wakeAt = at; }

    // what the About page shows about speed, filled in by the app
    public int viewW, viewH;
    public float viewScale;
    public int framesLastMinute;
    public float frameMs;
    public float readMs = -1;
    public String transport = "";
    /** The mod's own timing, from its last report. */
    public String modPerf = "";

    /**
     * The first two pocket items as last drawn ("kind:id:color"), and when they
     * last switched places (the drop button), for the Map strip's animation.
     */
    public String pocket0 = "", pocket1 = "", swapFrom0 = "", swapFrom1 = "";
    public long pocketSwapAt = Long.MIN_VALUE;

    /** The first row of 7 in view on the Items tab (-1: the newest rows) and on the Room tab. */
    public int itemsRow = -1, roomRow = 0;
    // the grid on the tab last drawn: how many rows it has, and the first in view
    int gridRows, gridFirst;

    /**
     * Moves the open tab's grid a row down (+1) or up (-1), as a swipe up or
     * down or its arrows do. False if there's nothing to scroll that way.
     */
    public boolean scroll(int dir) {
        if (gridRows <= Hud.GRID_ROWS) return false;
        int first = Math.max(0, Math.min(gridRows - Hud.GRID_ROWS, gridFirst + dir));
        if (first == gridFirst) return false;
        if (tab.equals("items")) itemsRow = first;
        else if (tab.equals("room")) roomRow = first;
        else return false;
        gridFirst = first;
        return true;
    }

    /** Switches to the next tab (+1) or the previous one (-1), as a swipe does. */
    public void stepTab(int dir) {
        String[] order = {"map", "items", "room"};
        int i = 0;
        for (int k = 0; k < order.length; k++) if (order[k].equals(tab)) i = k;
        i = Math.max(0, Math.min(order.length - 1, i + dir));
        tab = order[i];
    }

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
