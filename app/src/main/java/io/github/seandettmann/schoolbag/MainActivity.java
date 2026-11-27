package io.github.seandettmann.schoolbag;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Display;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * Schoolbag: the bottom-screen HUD for The Binding of Isaac. One full-screen
 * view; Cocoon (or the launcher) opens and closes it.
 *
 * Opened on the big screen, it moves itself to the small one. Once the game
 * has the controller, this window stops taking it: a tap here never moves the
 * controller away from the game. While the app itself is in front, as just
 * after opening it, it takes the controller like any app (so the system never
 * waits on a window that can't take one), and hands it straight back to the
 * top screen ({@link HandBack}); if it can't, it says "Tap the top screen to
 * play".
 */
public final class MainActivity extends Activity {
    static final String MOVED = "io.github.seandettmann.schoolbag.MOVED";
    static final float SHAPE = 1240f / 1080f; // the bottom screen's shape

    private HudView view;
    private Poller poller;
    private SharedPreferences prefs;
    private final HandBack handBack = new HandBack(this::handedBack);
    private boolean top;          // the top app: the controller is here
    private boolean handBackDue;  // and hasn't been handed back since
    private long handBacksSince;  // uptime: the half minute the count below is for
    private int handBacks;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved == null && moveToSmallScreen()) return;
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        view = new HudView(this);
        view.ui.appVersion = BuildConfig.VERSION_NAME;
        view.ui.hideHud = prefs.getBoolean("hideHud", true);
        view.ui.dim = prefs.getBoolean("dim", true);
        view.ui.paperBrightness = prefs.getInt("paperBrightness", 100);
        view.ui.language = prefs.getString("language", "auto");
        view.words = App.words(getAssets(), view.ui.language);
        view.onChange = this::settingsChanged;
        setContentView(view);
    }

    /**
     * Opened on a screen that isn't shaped like the bottom one, while one that
     * is (and is on) exists: open again there, once. True if it did.
     */
    @SuppressWarnings("deprecation")
    private boolean moveToSmallScreen() {
        if (getIntent().getBooleanExtra(MOVED, false)) return false;
        try {
            DisplayManager dm = getSystemService(DisplayManager.class);
            Display here = getWindowManager().getDefaultDisplay(); // this activity's display
            if (dm == null || here == null || off(shape(here)) < 0.3f) return false;
            Display best = null;
            for (Display d : dm.getDisplays()) {
                if (d.getDisplayId() == here.getDisplayId() || d.getState() != Display.STATE_ON) continue;
                if ((d.getFlags() & Display.FLAG_PRIVATE) != 0) continue;
                if (off(shape(d)) < 0.15f && (best == null || off(shape(d)) < off(shape(best)))) best = d;
            }
            if (best == null) return false;
            Intent again = new Intent(this, MainActivity.class)
                    .putExtra(MOVED, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            ActivityOptions o = ActivityOptions.makeBasic().setLaunchDisplayId(best.getDisplayId());
            startActivity(again, o.toBundle());
            finishAndRemoveTask();
            return true;
        } catch (Exception e) {
            return false; // not allowed there, or no such screen: stay here
        }
    }

    private static float shape(Display d) {
        Display.Mode m = d.getMode();
        int a = m.getPhysicalWidth(), b = m.getPhysicalHeight();
        return Math.max(a, b) / (float) Math.max(1, Math.min(a, b));
    }

    private static float off(float shape) { return Math.abs(shape - SHAPE); }

    private void settingsChanged() {
        prefs.edit()
                .putBoolean("hideHud", view.ui.hideHud)
                .putBoolean("dim", view.ui.dim)
                .putInt("paperBrightness", view.ui.paperBrightness)
                .putString("language", view.ui.language)
                .apply();
        if (poller != null) poller.keepHud = !view.ui.hideHud;
        if (view.ui.languageChanged) {
            view.ui.languageChanged = false;
            view.words = App.words(getAssets(), view.ui.language);
            if (poller != null) {
                poller.words = view.words.all();
                poller.resendWords();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    // full screen on every return, not on focus: once the game has the
    // controller this window never gets focus
    @Override protected void onResume() {
        super.onResume();
        if (view != null) immersive();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && view != null) {
            immersive();
            if (top) giveBack(); // the window has the focus now: hand it on
        }
    }

    /**
     * In front (just opened): able to take the controller, so the system never
     * waits on this window for keys, and handing it back to the top screen
     * once the window has it. Behind the game: never able to, so a tap here
     * leaves the controller with the game.
     */
    @Override public void onTopResumedActivityChanged(boolean top) {
        super.onTopResumedActivityChanged(top);
        if (view == null) return;
        this.top = top;
        if (top) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            handBackDue = true;
            if (hasWindowFocus()) giveBack();
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            handBackDue = false;
            handBack.gone();
            view.focusGiven();
        }
    }

    /**
     * Hands the controller to the app on the other screen, once each time this
     * one takes it, and at most three times in half a minute: if something
     * keeps giving it back here (the Thor's Focus Mode locked to this screen,
     * say), Schoolbag stops and asks for a tap instead of fighting it.
     */
    @SuppressWarnings("deprecation")
    private void giveBack() {
        if (!handBackDue || handBack.active()) return;
        handBackDue = false;
        long now = SystemClock.uptimeMillis();
        if (now - handBacksSince > 30_000) { handBacksSince = now; handBacks = 0; }
        if (++handBacks > 3) {
            handedBack("Tap the top screen (it keeps coming back here)", true);
            return;
        }
        Display game = gameScreen();
        if (game == null) {
            handedBack("Tap the top screen (no other screen)", true);
            return;
        }
        Point size = new Point();
        game.getRealSize(size);
        handBack.start(getApplicationInfo().sourceDir, game.getDisplayId(), size.x, size.y);
    }

    /** The screen the game is on: the main one, unless that's this one; then any other that's on. */
    @SuppressWarnings("deprecation")
    private Display gameScreen() {
        DisplayManager dm = getSystemService(DisplayManager.class);
        Display here = getWindowManager().getDefaultDisplay(); // this activity's display
        if (dm == null || here == null) return null;
        Display other = null;
        for (Display d : dm.getDisplays()) {
            if (d.getDisplayId() == here.getDisplayId() || d.getState() != Display.STATE_ON) continue;
            if ((d.getFlags() & Display.FLAG_PRIVATE) != 0) continue;
            if (d.getDisplayId() == Display.DEFAULT_DISPLAY) return d;
            if (other == null) other = d;
        }
        return other;
    }

    /** How the hand-back went: a line for About, and the tap note if the controller is still here. */
    private void handedBack(String line, boolean failed) {
        if (view == null) return;
        view.ui.controller = line;
        if (failed && top) view.focusTaken();
        view.invalidate();
    }

    @Override protected void onDestroy() {
        handBack.stop();
        super.onDestroy();
    }

    // started and stopped with visibility, not focus: on a two-screen handheld the
    // game has the focus up top while this screen stays visible
    @Override protected void onStart() {
        super.onStart();
        if (view == null) return;
        view.ui.openedAt = System.currentTimeMillis();
        poller = new Poller(view);
        poller.keepHud = !view.ui.hideHud;
        poller.words = view.words.all();
        view.poller = poller;
        poller.start();
    }

    @Override protected void onStop() {
        super.onStop();
        if (poller != null) poller.finish();
        poller = null;
        if (view != null) view.poller = null;
    }
}
