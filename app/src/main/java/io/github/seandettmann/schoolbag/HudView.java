package io.github.seandettmann.schoolbag;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.seandettmann.schoolbag.core.Hud;
import io.github.seandettmann.schoolbag.core.Json;
import io.github.seandettmann.schoolbag.core.Ui;
import io.github.seandettmann.schoolbag.core.Words;

/**
 * The whole bottom screen: the HUD drawn at the mock-ups' 1240 x 1080 points,
 * scaled to fit the screen.
 *
 * It draws only when something changes (a new state, a tap, the run clock's
 * second) or when the frame just drawn asked to be drawn again for an
 * animation, and follows the game's tabs if that's switched on. It also looks
 * after the OLED: the picture creeps a point at a time around a loop up to 8
 * points from the middle, everything can be drawn darker, and the screen
 * fades down when nothing changes for a while.
 */
final class HudView extends View implements Poller.Listener {
    interface OnChange { void onSettingsChanged(); }

    static final long GRACE = 5_000;            // silence kept as the same run (a floor loading)
    static final long DIM_AFTER = 120_000;      // a run with nothing changing for 2 minutes: dim
    static final long DIM_PAUSED = 30_000;      // the game's pause menu, untouched for 30 seconds: dim
    static final long FADE = 1_000;
    static final float DIM = 0.7f;              // how much darker, dimmed
    static final long SHIFT_EVERY = 30_000;     // pixel shift: a point every 30 seconds
    static final int SHIFT_REACH = 8;
    static final int[][] SHIFT = loop(SHIFT_REACH);
    static final int MARGIN = 12;               // the floor reaches past the edges, so the shift never shows a gap
    static final long AWAKE_AFTER = 300_000;    // the screen kept on until 5 minutes after a run
    static final long NEW_ITEM_FOR = 4_000;     // a new item's card, before going back
    static final float TAP_SLOP = 30, SWIPE = 120; // view pixels
    static final long TAP_TIME = 500, SWIPE_TIME = 600;

    // parts of the state that change without anything happening: the run clock, the mod's timing
    private static final Pattern NOISE = Pattern.compile("\"timeFrames\":[0-9.]+|\"perf\":\\{[^}]*\\}");

    final Ui ui = new Ui();
    final CanvasPainter painter;
    Words words;
    OnChange onChange;
    volatile Poller poller;

    // the mod's last answer and what it changed (all on the main thread)
    private Json.J state;
    private long lastAnswer;
    private String signature = "";
    private long lastChange = System.currentTimeMillis();
    private long clockSecond = -1;
    private String lastSeed = null;

    // following the game
    private int lastRoom = -1;
    private boolean lastRoomItems = false;
    private long lastMapAt = -1;
    private boolean paused = false;
    private String tabBeforePause = null;
    private int lastItemCount = -1;
    private long newItemUntil = 0;
    private String tabBeforeNewItem = null;

    // drawing
    private float scale = 1, ox, oy;
    private int[] shift = SHIFT[0];
    private Bitmap floor;
    private float floorScale = -1;
    private boolean awake = false;
    private long awakeUntil = 0;
    private final Runnable tick = this::onTick;
    private final long[] frameAt = new long[4096], frameNs = new long[4096];
    private int frameHead = 0, frameCount = 0;
    private long frameNsSum = 0;

    // the touch in progress
    private float downX, downY;
    private long downAt;

    HudView(Context c) {
        super(c);
        painter = new CanvasPainter(c.getAssets());
        // the art decoded while the waiting page shows, not on a run's first frame
        Thread t = new Thread(painter::preload, "schoolbag-art");
        t.setPriority(Thread.MIN_PRIORITY);
        t.setDaemon(true);
        t.start();
    }

    // ---- from the poller thread ----

    @Override public void onState(Json.J s, String raw, String transport, float readMs) {
        String sig = NOISE.matcher(raw).replaceAll("");
        post(() -> apply(s, sig, transport, readMs));
    }

    @Override public void onSilence() {
        post(this::silence);
    }

    // ---- on the main thread ----

    private void apply(Json.J s, String sig, String transport, float readMs) {
        long now = System.currentTimeMillis();
        int protocol = s.get("protocol").integer(0);
        String outdated = protocol > Poller.PROTOCOL ? "app" : protocol < Poller.PROTOCOL ? "mod" : null;
        boolean redraw = !ui.everAnswered || !Objects.equals(outdated, ui.outdated);
        ui.outdated = outdated;
        ui.modVersion = s.get("mod").get("version").str();
        ui.transport = transport;
        ui.readMs = readMs;
        Json.J perf = s.get("perf");
        if (perf.exists()) {
            ui.modPerf = String.format(java.util.Locale.ROOT, "%.2f ms a state, worst %.1f, worst frame %.1f",
                    perf.get("avgMs").num(), perf.get("maxMs").num(), perf.get("frameMaxMs").num());
        }
        if (!sig.equals(signature)) { signature = sig; lastChange = now; redraw = true; }

        boolean inRun = s.get("inRun").bool();
        boolean wasInRun = state != null && state.get("inRun").bool();
        if (!ui.everAnswered || (state != null && !wasInRun && inRun)) ui.newPaper();
        long second = inRun ? s.get("run").get("timeFrames").lng() / 30 : -1;
        if (second != clockSecond) { clockSecond = second; redraw = true; }

        String seed = inRun ? s.get("run").get("seed").str() : null;
        boolean newRun = seed != null && (!wasInRun || !seed.equals(lastSeed));
        int room = s.get("room").get("index").integer(-1);
        boolean roomItems = s.get("room").get("pedestals").size() > 0;
        long mapAt = s.get("input").get("mapAt").lng();
        boolean pausedNow = inRun && s.get("pauseMenu").bool();
        int items = s.get("players").at(0).get("items").size();
        String tabBefore = ui.tab;

        if (newRun) {
            // every run starts on the Map tab
            ui.tab = "map";
            ui.detail = false;
            ui.roomDetail = false;
            tabBeforePause = null;
            cancelNewItem();
        } else if (inRun && wasInRun) {
            if (ui.follow) {
                // the Room tab in a room with items (or when items turn up), the Map tab on leaving it
                if (room != lastRoom) {
                    if (roomItems) show("room");
                    else if ("room".equals(ui.tab)) show("map");
                } else if (roomItems && !lastRoomItems) {
                    show("room");
                }
                // the game's Map button
                if (lastMapAt >= 0 && mapAt != lastMapAt) show("map");
                // the pause menu: the Items tab, and back again after
                if (pausedNow && !paused) {
                    tabBeforePause = ui.tab;
                    show("items");
                } else if (!pausedNow && paused) {
                    if ("items".equals(ui.tab) && tabBeforePause != null) show(tabBeforePause);
                    tabBeforePause = null;
                }
            }
            // a new item: its card for a few seconds, then back
            if (ui.showNewItems && lastItemCount >= 0 && items > lastItemCount && !pausedNow) {
                if (!"items".equals(ui.tab)) tabBeforeNewItem = ui.tab;
                ui.tab = "items";
                ui.sel = -1;
                ui.selKind = "run";
                ui.detail = false;
                if (tabBeforeNewItem != null) newItemUntil = now + NEW_ITEM_FOR;
            }
        }
        if (!ui.tab.equals(tabBefore)) redraw = true;

        lastRoom = room;
        lastRoomItems = roomItems;
        lastMapAt = inRun ? mapAt : -1;
        paused = pausedNow;
        lastItemCount = inRun ? items : -1;
        if (seed != null) lastSeed = seed;
        state = s;
        lastAnswer = now;
        ui.everAnswered = true;
        ui.lastAnswer = now;
        if (inRun) {
            awakeUntil = now + AWAKE_AFTER;
            if (!awake) { awake = true; setKeepScreenOn(true); }
        }
        if (redraw) invalidate();
    }

    /** A tab the game asked for; anything but Items ends a new item's visit there. */
    private void show(String tab) {
        ui.tab = tab;
        if (!"items".equals(tab)) cancelNewItem();
    }

    private void cancelNewItem() {
        newItemUntil = 0;
        tabBeforeNewItem = null;
    }

    private void silence() {
        long now = System.currentTimeMillis();
        if (state != null && now - lastAnswer > GRACE) {
            state = null;
            signature = "";
            clockSecond = -1;
            lastChange = now;
            paused = false;
            cancelNewItem();
            ui.newPaper();
            invalidate();
        }
    }

    /**
     * Called when this screen has the controller and couldn't hand it back to
     * the game: "Tap the top screen to play" for 4 seconds in a run, and on the
     * intro's line until the game has it again.
     */
    void focusTaken() {
        ui.tapForController = true;
        if (live()) ui.focusNoteUntil = System.currentTimeMillis() + 4_000;
        invalidate();
    }

    /** Called when the game has the controller again. */
    void focusGiven() {
        if (ui.focusNoteUntil != 0 || ui.tapForController) {
            ui.focusNoteUntil = 0;
            ui.tapForController = false;
            invalidate();
        }
    }

    private boolean live() { return state != null && state.get("inRun").bool(); }

    // ---- drawing ----

    @Override protected void onDraw(Canvas canvas) {
        long t0 = System.nanoTime();
        long now = System.currentTimeMillis();
        int w = getWidth(), h = getHeight();
        scale = Math.min(w / 1240f, h / 1080f);
        ox = (w - 1240 * scale) / 2f;
        oy = (h - 1080 * scale) / 2f;
        shift = SHIFT[(int) ((now / SHIFT_EVERY) % SHIFT.length)];
        float x = ox + shift[0] * scale, y = oy + shift[1] * scale;
        canvas.drawColor(0xFF1D1410);
        canvas.drawBitmap(floor(), x - MARGIN * scale, y - MARGIN * scale, null);
        painter.begin(canvas);
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        Hud.draw(painter, state, ui, words, now, false);
        canvas.restore();
        // the brightness setting and dimming, as one layer of black
        float dim = dimLevel(now);
        float light = ui.paperBrightness / 100f * (1 - dim);
        int shade = Math.round((1 - light) * 255);
        if (shade > 0) canvas.drawColor(Math.min(255, shade) << 24);
        Poller pl = poller;
        if (pl != null) pl.slow = dim > 0 || paused;
        ui.viewW = w;
        ui.viewH = h;
        ui.viewScale = scale;
        countFrame(now, System.nanoTime() - t0);
        schedule(now);
    }

    /** The floor, drawn once at the screen's scale: it never changes. */
    private Bitmap floor() {
        if (floor == null || floorScale != scale) {
            int fw = Math.max(1, Math.round((1240 + 2 * MARGIN) * scale));
            int fh = Math.max(1, Math.round((1080 + 2 * MARGIN) * scale));
            floor = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(floor);
            c.drawColor(0xFF1D1410);
            c.scale(scale, scale);
            painter.begin(c);
            painter.tile("floor_basement", 0, 0, 1240 + 2 * MARGIN, 1080 + 2 * MARGIN, 3);
            floorScale = scale;
        }
        return floor;
    }

    /** 0 (not dimmed) to DIM (fully dimmed). */
    private float dimLevel(long now) {
        long start = dimStart();
        if (now < start) return 0;
        return Math.min(1f, (now - start) / (float) FADE) * DIM;
    }

    // only during a run: the waiting page relies on the pixel shift and its random paper
    private long dimStart() {
        if (!ui.dim || !live()) return Long.MAX_VALUE;
        return lastChange + (paused ? DIM_PAUSED : DIM_AFTER);
    }

    /** The next time anything on screen needs drawing again, asked for once. */
    private void schedule(long now) {
        long next = ui.wakeAt;                                        // an animation
        long dimAt = dimStart();
        if (now < dimAt) next = Math.min(next, dimAt);                // dimming starts
        else if (now < dimAt + FADE) next = Math.min(next, now + 33); // dimming
        next = Math.min(next, (now / SHIFT_EVERY + 1) * SHIFT_EVERY); // the next pixel shift
        if (newItemUntil > 0) next = Math.min(next, newItemUntil);    // back from a new item
        if (awake) next = Math.min(next, awakeUntil);                 // the screen allowed to sleep
        removeCallbacks(tick);
        postDelayed(tick, Math.max(1, next - now));
    }

    private void onTick() {
        long now = System.currentTimeMillis();
        if (newItemUntil > 0 && now >= newItemUntil) {
            String back = tabBeforeNewItem;
            if ("items".equals(ui.tab) && back != null) {
                // the item was taken from the room: nothing left to show there
                if ("room".equals(back) && !lastRoomItems) back = "map";
                ui.tab = back;
            }
            cancelNewItem();
        }
        if (awake && now >= awakeUntil) {
            awake = false;
            setKeepScreenOn(false);
        }
        invalidate();
    }

    private void countFrame(long now, long ns) {
        int n = frameAt.length;
        if (frameCount == n) { // full: drop the oldest
            int oldest = (frameHead - frameCount + n) % n;
            frameNsSum -= frameNs[oldest];
            frameCount--;
        }
        frameAt[frameHead] = now;
        frameNs[frameHead] = ns;
        frameNsSum += ns;
        frameHead = (frameHead + 1) % n;
        frameCount++;
        while (frameCount > 0) {
            int oldest = (frameHead - frameCount + n) % n;
            if (frameAt[oldest] >= now - 60_000) break;
            frameNsSum -= frameNs[oldest];
            frameCount--;
        }
        ui.framesLastMinute = frameCount;
        ui.frameMs = frameCount == 0 ? 0 : frameNsSum / (float) frameCount / 1e6f;
    }

    // ---- touch ----

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                downAt = e.getEventTime();
                return true;
            case MotionEvent.ACTION_UP:
                break;
            default:
                return true;
        }
        long now = System.currentTimeMillis();
        boolean wasDim = dimLevel(now) > 0;
        lastChange = now;
        if (wasDim) { // the first touch only wakes the screen
            invalidate();
            return true;
        }
        float dx = e.getX() - downX, dy = e.getY() - downY;
        long held = e.getEventTime() - downAt;
        String before = settingsKey();
        String tabBefore = ui.tab;
        boolean acted = false;
        if (Math.abs(dx) > SWIPE && Math.abs(dx) > 2 * Math.abs(dy) && held < SWIPE_TIME
                && live() && "none".equals(ui.settings) && ui.outdated == null) {
            ui.stepTab(dx < 0 ? 1 : -1); // a swipe left: the next tab
            acted = !ui.tab.equals(tabBefore);
        } else if (Math.abs(dy) > SWIPE && Math.abs(dy) > 2 * Math.abs(dx) && held < SWIPE_TIME
                && live() && "none".equals(ui.settings) && ui.outdated == null) {
            acted = ui.scroll(dy < 0 ? 1 : -1); // a swipe up: the rows further down
        } else if (Math.hypot(dx, dy) < TAP_SLOP && held < TAP_TIME) {
            float hx = (downX - ox) / scale - shift[0], hy = (downY - oy) / scale - shift[1];
            acted = ui.tap(hx, hy);
        }
        if (acted) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            cancelNewItem(); // the person chose: don't switch back for them
            performClick();
        }
        if (!before.equals(settingsKey()) && onChange != null) onChange.onSettingsChanged();
        invalidate();
        return true;
    }

    @Override public boolean performClick() { return super.performClick(); }

    String settingsKey() {
        return ui.hideHud + "," + ui.dim + "," + ui.follow + "," + ui.showNewItems + "," + ui.paperBrightness + "," + ui.language;
    }

    /**
     * The pixel shift's loop: a square spiral out from the middle to reach
     * points away, then back in along the same path, one point a step, so
     * the picture never jumps and spends as long at each place.
     */
    static int[][] loop(int reach) {
        List<int[]> out = new ArrayList<>();
        int cells = (2 * reach + 1) * (2 * reach + 1);
        int px = 0, py = 0, dir = 0, len = 1;
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        out.add(new int[]{0, 0});
        while (out.size() < cells) {
            for (int leg = 0; leg < 2 && out.size() < cells; leg++) {
                for (int i = 0; i < len && out.size() < cells; i++) {
                    px += dirs[dir][0];
                    py += dirs[dir][1];
                    out.add(new int[]{px, py});
                }
                dir = (dir + 1) % 4;
            }
            len++;
        }
        List<int[]> all = new ArrayList<>(out);
        for (int i = out.size() - 2; i >= 1; i--) all.add(out.get(i));
        return all.toArray(new int[0][]);
    }
}
