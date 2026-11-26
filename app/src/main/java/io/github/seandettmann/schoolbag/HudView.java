package io.github.seandettmann.schoolbag;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import io.github.seandettmann.schoolbag.core.Hud;
import io.github.seandettmann.schoolbag.core.Json;
import io.github.seandettmann.schoolbag.core.Ui;
import io.github.seandettmann.schoolbag.core.Words;

/**
 * The whole bottom screen: the HUD drawn at the mock-ups' 1240 x 1080 points,
 * scaled to fit the screen. It also keeps the OLED safe: the picture moves a
 * point at a time around a small loop, and fades down after two minutes with
 * nothing changing during a run.
 */
final class HudView extends View implements Poller.Listener {
    interface OnChange { void onSettingsChanged(); }

    static final long GRACE = 5_000;          // silence kept as the same run (a floor loading)
    static final long DIM_AFTER = 120_000;    // nothing changed for 2 minutes: dim
    static final long SHIFT_EVERY = 120_000;  // pixel shift: one step every 2 minutes
    static final int[][] SHIFT = {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 1}, {3, 2}, {2, 3}, {1, 4}, {0, 3}, {-1, 2},
            {-2, 1}, {-3, 0}, {-4, -1}, {-3, -2}, {-2, -3}, {-1, -4}, {0, -3}, {1, -2}, {0, -1}};

    final Ui ui = new Ui();
    final CanvasPainter painter;
    Words words;
    OnChange onChange;

    private volatile Json.J state;
    private volatile long lastAnswer;
    private volatile String signature = "";
    private volatile long lastChange = System.currentTimeMillis();
    private float scale = 1, ox, oy;
    private String lastSeed = null;
    private int[] shift = SHIFT[0];

    HudView(Context c) {
        super(c);
        painter = new CanvasPainter(c.getAssets());
        setKeepScreenOn(true);
    }

    // ---- from the poller thread ----

    @Override public void onState(Json.J s, String raw) {
        long now = System.currentTimeMillis();
        int protocol = s.get("protocol").integer(0);
        ui.outdated = protocol > Poller.PROTOCOL ? "app" : protocol < Poller.PROTOCOL ? "mod" : null;
        ui.modVersion = s.get("mod").get("version").str();
        // a change of anything but the clock counts as something happening
        String sig = raw.replaceAll("\"timeFrames\":[0-9.]+", "");
        if (!sig.equals(signature)) { signature = sig; lastChange = now; }
        if (!ui.everAnswered || (state != null && !state.get("inRun").bool() && s.get("inRun").bool())) {
            ui.newPaper();
        }
        // a run starting (or the app first seeing one, or a new seed): the Map tab
        String seed = s.get("inRun").bool() ? s.get("run").get("seed").str() : null;
        if (seed != null && (state == null || !state.get("inRun").bool() || !seed.equals(lastSeed))) {
            ui.tab = "map";
            ui.detail = false;
        }
        if (seed != null) lastSeed = seed;
        state = s;
        lastAnswer = now;
        ui.everAnswered = true;
        ui.lastAnswer = now;
        postInvalidate();
    }

    @Override public void onSilence() {
        long now = System.currentTimeMillis();
        if (state != null && now - lastAnswer > GRACE) {
            state = null;
            ui.newPaper();
        }
        postInvalidate();
    }

    // ---- drawing ----

    @Override protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();
        int w = getWidth(), h = getHeight();
        scale = Math.min(w / 1240f, h / 1080f);
        ox = (w - 1240 * scale) / 2f;
        oy = (h - 1080 * scale) / 2f;
        shift = SHIFT[(int) ((now / SHIFT_EVERY) % SHIFT.length)];
        canvas.drawColor(0xFF1D1410);
        painter.begin(canvas);
        canvas.save();
        canvas.translate(ox + shift[0] * scale, oy + shift[1] * scale);
        canvas.scale(scale, scale);
        // the floor reaches past the edges, so the shift never shows a gap
        painter.tile("floor_basement", -12, -12, 1264, 1104, 3);
        Hud.draw(painter, state, ui, words, now);
        canvas.restore();
        Json.J s = state;
        boolean live = s != null && s.get("inRun").bool();
        if (ui.dim && live && now - lastChange > DIM_AFTER) {
            float a = Math.min(1f, (now - lastChange - DIM_AFTER) / 1000f) * 0.7f;
            canvas.drawColor(Math.round(a * 255) << 24);
            if (a < 0.7f) postInvalidateDelayed(33);
        }
        // animations: the loading doodle, the ghost, draining hearts, stat changes
        if (live) postInvalidateDelayed(50);
        else postInvalidateDelayed(1000);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() != MotionEvent.ACTION_UP) return super.onTouchEvent(e);
        boolean wasDim = System.currentTimeMillis() - lastChange > DIM_AFTER;
        lastChange = System.currentTimeMillis();
        if (!wasDim) {
            float hx = (e.getX() - ox) / scale - shift[0], hy = (e.getY() - oy) / scale - shift[1];
            String before = settingsKey();
            ui.tap(hx, hy);
            if (!before.equals(settingsKey()) && onChange != null) onChange.onSettingsChanged();
        }
        performClick();
        invalidate();
        return true;
    }

    @Override public boolean performClick() { return super.performClick(); }

    String settingsKey() { return ui.hideHud + "," + ui.dim + "," + ui.language; }
}
