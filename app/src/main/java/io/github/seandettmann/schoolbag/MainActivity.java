package io.github.seandettmann.schoolbag;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * Schoolbag: the bottom-screen HUD for The Binding of Isaac. One full-screen
 * view; Cocoon (or the launcher) opens and closes it.
 */
public final class MainActivity extends Activity {
    private HudView view;
    private Poller poller;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        view = new HudView(this);
        view.ui.appVersion = BuildConfig.VERSION_NAME;
        view.ui.hideHud = prefs.getBoolean("hideHud", true);
        view.ui.dim = prefs.getBoolean("dim", true);
        view.ui.roomOnWalkIn = prefs.getBoolean("roomOnWalkIn", true);
        view.ui.language = prefs.getString("language", "auto");
        view.words = App.words(getAssets(), view.ui.language);
        view.onChange = this::settingsChanged;
        setContentView(view);
        immersive();
    }

    private void settingsChanged() {
        prefs.edit()
                .putBoolean("hideHud", view.ui.hideHud)
                .putBoolean("dim", view.ui.dim)
                .putBoolean("roomOnWalkIn", view.ui.roomOnWalkIn)
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

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    // started and stopped with visibility, not focus: on a two-screen handheld the
    // game has the focus up top while this screen stays visible
    @Override protected void onStart() {
        super.onStart();
        view.ui.openedAt = System.currentTimeMillis();
        poller = new Poller(view);
        poller.keepHud = !view.ui.hideHud;
        poller.words = view.words.all();
        poller.start();
    }

    @Override protected void onStop() {
        super.onStop();
        if (poller != null) poller.finish();
        poller = null;
    }
}
