package io.github.seandettmann.schoolbag;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.github.seandettmann.schoolbag.core.Json;

/**
 * Reads the mod's state about ten times a second, on its own thread. Each
 * request gives up after 1.5 seconds, and there's never more than one waiting
 * (the mod isn't listening on the game's menus, so most of those fail at once).
 * When the mod says it has no words yet, it gets them with POST /strings.
 */
final class Poller extends Thread {
    interface Listener {
        /** A reply from the Schoolbag mod. */
        void onState(Json.J state, String raw);
        /** No answer (or one that isn't Schoolbag's). */
        void onSilence();
    }

    static final String BASE = "http://127.0.0.1:8089";
    static final int PROTOCOL = 1;

    private final Listener listener;
    private volatile boolean running = true;
    volatile boolean keepHud = false;
    volatile Map<String, String> words;
    private volatile boolean sendWords = false;

    Poller(Listener listener) {
        super("schoolbag-poller");
        this.listener = listener;
        setDaemon(true);
    }

    void finish() { running = false; interrupt(); }

    /** Sends the words again (the language changed). */
    void resendWords() { sendWords = true; }

    @Override public void run() {
        while (running) {
            long t0 = System.currentTimeMillis();
            try {
                String raw = get("/state" + (keepHud ? "?hud=keep" : ""));
                Json.J s = Json.parse(raw);
                if (!"Schoolbag".equals(s.get("mod").get("name").str())) {
                    listener.onSilence();
                } else {
                    listener.onState(s, raw);
                    if (s.get("inRun").bool() && (s.get("strings").integer(-1) == 0 || sendWords)) {
                        sendWords = false;
                        postWords();
                    }
                }
            } catch (Exception e) {
                listener.onSilence();
            }
            long wait = 100 - (System.currentTimeMillis() - t0);
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException ignored) { }
            }
        }
    }

    private static HttpURLConnection open(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(1500);
        c.setReadTimeout(1500);
        c.setUseCaches(false);
        return c;
    }

    private static String get(String path) throws Exception {
        HttpURLConnection c = open(path);
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            return App.read(in);
        } finally {
            c.disconnect();
        }
    }

    private void postWords() {
        Map<String, String> w = words;
        if (w == null || w.isEmpty()) return;
        StringBuilder b = new StringBuilder("{");
        for (Map.Entry<String, String> e : w.entrySet()) {
            if (b.length() > 1) b.append(',');
            b.append(Json.quote(e.getKey())).append(':').append(Json.quote(e.getValue()));
        }
        b.append('}');
        byte[] body = b.toString().getBytes(StandardCharsets.UTF_8);
        try {
            HttpURLConnection c = open("/strings");
            c.setReadTimeout(5000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = c.getOutputStream()) { out.write(body); }
            c.getResponseCode();
            c.disconnect();
        } catch (Exception ignored) {
            // tried again on the next read that says it has none
        }
    }
}
