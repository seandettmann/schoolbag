package io.github.seandettmann.schoolbag;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gives the controller back to the game just after Schoolbag opens.
 *
 * Android gives the controller to the app that opened last, so when Cocoon
 * starts the game and then Schoolbag as its companion, Schoolbag ends up with
 * it. Schoolbag never uses the controller, so it hands it straight to the app
 * on the top screen, as a tap there would. Android only lets the system move
 * the focus between screens, so this goes through the helper AYN builds into
 * the Thor's firmware (the one behind its "Run Script as Root" feature): first
 * {@link FocusTool}, which asks Android to focus the top screen's app; if that
 * doesn't take, a touch in the middle of the top screen that is cancelled
 * instead of lifted, so the focus moves but no tap reaches the game. Those are
 * the only two commands, built from this APK's path and numbers.
 *
 * Only in the first seconds after opening, never during play. Where the helper
 * can't be reached (not a Thor, or the Thor's "Force SELinux" on), Schoolbag
 * asks for the tap instead, as before.
 */
final class HandBack {
    /** A line for the About page, and whether the controller is still here (so the app can ask for a tap). */
    interface Outcome { void handBack(String line, boolean failed); }

    private static final String HELPER = "PServerBinder";
    private static final long SETTLE_MS = 300;    // after Schoolbag has the focus, before the first try
    private static final long TRY_MS = 1_000;     // how long each try gets to move the focus
    private static final long WINDOW_MS = 6_000;  // no tries after this long
    private static final int TRIES = 4;           // FocusTool, the touch, then both again

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Schoolbag hand-back");
        t.setDaemon(true);
        return t;
    });
    private static volatile IBinder binder;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Outcome outcome;
    private int run;          // the current hand-back; steps of an older one see a newer number and stop
    private boolean active;
    private long startedAt;
    private int tries;
    private String last = ""; // the helper's last answer, for the About page

    HandBack(Outcome outcome) { this.outcome = outcome; }

    boolean active() { return active; }

    /**
     * Starts handing the controller to the app on top of display [display]
     * ([w] x [h]), from the APK at [apk]. Main thread.
     */
    void start(String apk, int display, int w, int h) {
        run++;
        active = true;
        startedAt = SystemClock.uptimeMillis();
        tries = 0;
        last = "";
        String focus = apk == null || apk.indexOf('\'') >= 0 ? null
                : "CLASSPATH='" + apk + "' app_process /system/bin " + FocusTool.class.getName() + " " + display + " 2>/dev/null";
        String touch = String.format(Locale.ROOT, "input -d %d motionevent DOWN %d %d; input -d %d motionevent CANCEL %d %d",
                display, w / 2, h / 2, display, w / 2, h / 2);
        int mine = run;
        main.postDelayed(() -> attempt(mine, focus, touch), SETTLE_MS);
    }

    private void attempt(int mine, String focus, String touch) {
        if (mine != run) return;
        if (tries >= TRIES || SystemClock.uptimeMillis() - startedAt > WINDOW_MS) {
            finish(mine, "Tap the top screen" + (last.isEmpty() ? "" : " (" + clip(last) + ")"), true);
            return;
        }
        String command = tries % 2 == 0 && focus != null ? focus : touch;
        tries++;
        WORKER.execute(() -> {
            String out = runAsSystem(command);
            main.post(() -> {
                if (mine != run) return;
                if (out == null) {
                    finish(mine, "Tap the top screen (no system helper)", true);
                    return;
                }
                if (!out.isEmpty()) last = out;
                main.postDelayed(() -> attempt(mine, focus, touch), TRY_MS);
            });
        });
    }

    /** Schoolbag isn't the top app any more: the top screen has the controller. Main thread. */
    void gone() {
        if (!active) return;
        float s = (SystemClock.uptimeMillis() - startedAt) / 1000f;
        finish(run, tries == 0 ? "Back on the top screen"
                : String.format(Locale.ROOT, "Given to the game %.1f s after opening%s", s, tries > 1 ? " (try " + tries + ")" : ""), false);
    }

    /** Stops without a word (closing). Main thread. */
    void stop() {
        run++;
        active = false;
    }

    private void finish(int mine, String line, boolean failed) {
        if (mine != run) return;
        run++;
        active = false;
        outcome.handBack(line, failed);
    }

    private static String clip(String s) {
        s = s.replace('\n', ' ');
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    /**
     * Runs [command] through the Thor's system helper. Its output (maybe
     * empty), or null if the helper can't be reached. Blocks: never on the
     * main thread.
     */
    private static String runAsSystem(String command) {
        IBinder b = helper();
        if (b == null) return null;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeStringArray(new String[]{command, "0"});
            if (!b.transact(0, data, reply, 0)) return null;
            try {
                byte[] out = reply.createByteArray();
                return out == null ? "" : new String(out, StandardCharsets.UTF_8).trim();
            } catch (RuntimeException e) {
                return ""; // sent, but the answer wasn't one we can read
            }
        } catch (Throwable t) { // refused (SELinux), gone, or not the helper we know
            binder = null;
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static IBinder helper() {
        IBinder b = binder;
        if (b != null && b.isBinderAlive()) return b;
        try {
            b = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, HELPER);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            b = null;
        }
        binder = b;
        return b;
    }
}
