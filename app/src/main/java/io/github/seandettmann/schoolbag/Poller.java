package io.github.seandettmann.schoolbag;

import android.os.Process;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.github.seandettmann.schoolbag.core.Json;

/**
 * Reads the mod's state on its own low-priority thread.
 *
 * The mod sends the state over UDP when something in it changes (and once a
 * second anyway); the app says "hello" once a second so the mod knows where to
 * send it. If no UDP comes for a moment and the mod answers HTTP instead (an
 * older mod, or UDP not working), the app reads GET /state ten times a second
 * (twice a second while the screen is dimmed or the game paused) over one
 * kept-open connection, and tries UDP again every 30 seconds. A state too big
 * for one UDP message is read over HTTP the same way.
 *
 * When the mod says it has no words yet, it gets them with POST /strings.
 */
final class Poller extends Thread {
    interface Listener {
        /** A reply from the Schoolbag mod, how it came, and how long an HTTP read took (-1 for UDP). */
        void onState(Json.J state, String raw, String transport, float readMs);
        /** No answer (or one that isn't Schoolbag's). */
        void onSilence();
    }

    static final String HOST = "127.0.0.1";
    static final int PORT = 8089;
    static final String BASE = "http://" + HOST + ":" + PORT;
    static final int PROTOCOL = 1;

    static final long HELLO_EVERY = 1_000;
    static final long UDP_QUIET = 1_500;     // no UDP for this long: see if HTTP answers
    static final long HTTP_FOR = 30_000;     // HTTP answering: keep to it this long, then try UDP again
    static final long OLD_MOD_FOR = 300_000; // a mod from before UDP: HTTP for 5 minutes
    static final long FAST = 100, SLOW = 500;

    static final String UDP = "UDP (sent on changes)";
    static final String HTTP = "HTTP";

    private final Listener listener;
    private volatile boolean running = true;
    volatile boolean keepHud = false;
    /** Fewer HTTP reads: the screen is dimmed or the game is on its pause menu. */
    volatile boolean slow = false;
    volatile Map<String, String> words;
    private volatile boolean sendWords = false;
    private volatile DatagramSocket udp;

    Poller(Listener listener) {
        super("schoolbag-poller");
        this.listener = listener;
        setDaemon(true);
    }

    void finish() {
        running = false;
        interrupt();
        closeUdp();
    }

    /** Sends the words again (the language changed). */
    void resendWords() { sendWords = true; }

    @Override public void run() {
        // the game needs the fast cores far more than this does
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        byte[] buf = new byte[65536];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        InetAddress host = null;
        try { host = InetAddress.getByName(HOST); } catch (Exception ignored) { }
        long lastHello = 0, lastUdp = System.currentTimeMillis(), lastProbe = 0, httpUntil = 0, udpTrialUntil = 0;

        while (running) {
            long now = System.currentTimeMillis();

            if (now < httpUntil) {
                long t0 = System.nanoTime();
                String mod = readHttp(t0);
                if (mod == null) {
                    httpUntil = 0;                  // stopped answering: back to UDP
                    lastProbe = System.currentTimeMillis();
                } else if (olderThanUdp(mod)) {
                    httpUntil = Math.max(httpUntil, now + OLD_MOD_FOR);
                }
                pause(now + (slow ? SLOW : FAST) - System.currentTimeMillis());
                if (System.currentTimeMillis() >= httpUntil) lastUdp = System.currentTimeMillis(); // UDP's turn again
                continue;
            }

            try {
                DatagramSocket u = udp;
                if (u == null) {
                    u = new DatagramSocket(0, host);
                    u.setSoTimeout(250);
                    udp = u;
                    if (!running) { closeUdp(); break; }
                }
                if (now - lastHello >= HELLO_EVERY) {
                    byte[] h = (keepHud ? "hello keep" : "hello").getBytes(StandardCharsets.UTF_8);
                    u.send(new DatagramPacket(h, h.length, host, PORT));
                    lastHello = now;
                }
                packet.setLength(buf.length);
                u.receive(packet);
                String raw = new String(buf, 0, packet.getLength(), StandardCharsets.UTF_8);
                if (raw.length() < 40 && raw.contains("\"tooBig\"")) {
                    httpUntil = System.currentTimeMillis() + HTTP_FOR;
                    continue;
                }
                if (deliver(raw, UDP, -1) != null) {
                    lastUdp = System.currentTimeMillis();
                    udpTrialUntil = 0;
                }
            } catch (SocketTimeoutException | PortUnreachableException e) {
                // nothing this time
            } catch (Exception e) {
                // the socket broke: a new one next time round
                closeUdp();
                pause(250);
            }

            now = System.currentTimeMillis();
            if (now - lastUdp > UDP_QUIET && now - lastProbe > UDP_QUIET) {
                lastProbe = now;
                String mod = readHttp(System.nanoTime());
                if (mod == null) {
                    udpTrialUntil = 0;
                } else if (olderThanUdp(mod)) {
                    httpUntil = now + OLD_MOD_FOR;
                } else if (udpTrialUntil == 0) {
                    // the mod has just come up: say hello now and give UDP two seconds
                    udpTrialUntil = now + 2_000;
                    lastHello = 0;
                } else if (now >= udpTrialUntil) {
                    httpUntil = now + HTTP_FOR;     // HTTP answers and UDP doesn't
                    udpTrialUntil = 0;
                }
            }
        }
        closeUdp();
    }

    /** One GET /state, handed on. The mod's version, or null when nothing answered. */
    private String readHttp(long t0) {
        try {
            String raw = get("/state" + (keepHud ? "?hud=keep" : ""));
            return deliver(raw, HTTP, (System.nanoTime() - t0) / 1e6f);
        } catch (Exception e) {
            listener.onSilence();
            return null;
        }
    }

    /** Hands a state on. The mod's version, or null if it wasn't Schoolbag's. */
    private String deliver(String raw, String transport, float readMs) {
        Json.J s;
        try { s = Json.parse(raw); } catch (Exception e) { listener.onSilence(); return null; }
        if (!"Schoolbag".equals(s.get("mod").get("name").str())) {
            listener.onSilence();
            return null;
        }
        listener.onState(s, raw, transport, readMs);
        if (s.get("inRun").bool() && (s.get("strings").integer(-1) == 0 || sendWords)) {
            sendWords = false;
            postWords();
        }
        return s.get("mod").get("version").str();
    }

    /** True for a mod before 0.3.0, which has no UDP. */
    static boolean olderThanUdp(String version) {
        String[] v = version.split("\\.");
        try {
            int major = Integer.parseInt(v[0]), minor = v.length > 1 ? Integer.parseInt(v[1]) : 0;
            return major == 0 && minor < 3;
        } catch (Exception e) {
            return false;
        }
    }

    private void closeUdp() {
        DatagramSocket u = udp;
        udp = null;
        if (u != null) u.close();
    }

    private static void pause(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    private static HttpURLConnection open(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(1500);
        c.setReadTimeout(1500);
        c.setUseCaches(false);
        return c;
    }

    /**
     * A GET whose connection is kept for the next one: reading the answer to
     * its end and closing the stream hands the connection back to be reused.
     */
    private static String get(String path) throws Exception {
        HttpURLConnection c = open(path);
        boolean ok = false;
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            String body;
            try (InputStream in = c.getInputStream()) { body = App.read(in); }
            ok = true;
            return body;
        } finally {
            if (!ok) c.disconnect();
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
        HttpURLConnection c = null;
        try {
            c = open("/strings");
            c.setReadTimeout(5000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = c.getOutputStream()) { out.write(body); }
            c.getResponseCode();
            try (InputStream in = c.getInputStream()) { App.read(in); }
        } catch (Exception ignored) {
            // tried again on the next read that says it has none
            if (c != null) c.disconnect();
        }
    }
}
