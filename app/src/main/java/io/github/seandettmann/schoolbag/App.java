package io.github.seandettmann.schoolbag;

import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.github.seandettmann.schoolbag.core.Json;
import io.github.seandettmann.schoolbag.core.Words;

/** Small helpers shared by the activity, the view and the poller. */
final class App {
    private App() {}

    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static String read(AssetManager assets, String path) throws IOException {
        try (InputStream in = assets.open(path)) { return read(in); }
    }

    /** The game's languages: the app's choice, or the Thor's language if the game has it. */
    static String language(String choice) {
        if (choice != null && !choice.equals("auto")) return choice;
        String l = Locale.getDefault().getLanguage();
        switch (l) {
            case "es": case "de": case "fr": case "ru": case "ja": case "ko": case "zh": return l;
            default: return "en";
        }
    }

    /** The game's words in a language, with its item data, from the art bundle. */
    static Words words(AssetManager assets, String language) {
        Map<String, String> w = new HashMap<>();
        Json.J items = Json.J.NONE;
        try {
            Json.J j = Json.parse(read(assets, "art/strings/" + language(language) + ".json"));
            for (Map.Entry<String, Object> e : j.map().entrySet())
                if (e.getValue() instanceof String) w.put(e.getKey(), (String) e.getValue());
        } catch (Exception ignored) { }
        try {
            items = Json.parse(read(assets, "art/data/items.json"));
        } catch (Exception ignored) { }
        return new Words(w, items);
    }
}
