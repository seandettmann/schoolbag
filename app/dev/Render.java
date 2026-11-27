import io.github.seandettmann.schoolbag.core.Hud;
import io.github.seandettmann.schoolbag.core.Json;
import io.github.seandettmann.schoolbag.core.Ui;
import io.github.seandettmann.schoolbag.core.Words;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders one HUD frame to a PNG, off the device:
 *   java -cp <classes> Render <art bundle> <state.json or -> <out.png> [tab] [settings page] [speed]
 */
public final class Render {
    public static void main(String[] a) throws Exception {
        File art = new File(a[0]);
        Json.J state = a[1].equals("-") ? null : Json.parse(new String(Files.readAllBytes(new File(a[1]).toPath()), "UTF-8"));
        Ui ui = new Ui();
        ui.paper = 0;
        ui.appVersion = "0.1.0";
        if (a.length > 3) ui.tab = a[3];
        if (a.length > 4 && !a[4].equals("-")) ui.settings = a[4];
        if (a.length > 6 && a[6].equals("open")) { ui.detail = true; ui.roomDetail = true; } // the item card opened
        if (a.length > 5 && a[5].equals("speed")) { // the About page with the longest speed lines likely
            ui.viewW = 1240; ui.viewH = 1080; ui.viewScale = 1; ui.framesLastMinute = 3600; ui.frameMs = 12.4f;
            ui.transport = "UDP (sent on changes)"; ui.modVersion = "0.3.0";
            ui.modPerf = "0.85 ms a state, worst 12.5, worst frame 14.2";
        }
        Json.J words = Json.parse(new String(Files.readAllBytes(new File(art, "strings/en.json").toPath()), "UTF-8"));
        Map<String, String> w = new HashMap<>();
        for (Map.Entry<String, Object> e : words.map().entrySet()) w.put(e.getKey(), (String) e.getValue());
        Json.J items = Json.parse(new String(Files.readAllBytes(new File(art, "data/items.json").toPath()), "UTF-8"));
        BufferedImage img = new BufferedImage(1240, 1080, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        DesktopPainter p = new DesktopPainter(art, g);
        Hud.draw(p, state, ui, new Words(w, items), 1000);
        g.dispose();
        ImageIO.write(img, "png", new File(a[2]));
    }
}
