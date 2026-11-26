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
 *   java -cp <classes> Render <art bundle> <state.json or -> <out.png> [tab] [settings page]
 */
public final class Render {
    public static void main(String[] a) throws Exception {
        File art = new File(a[0]);
        Json.J state = a[1].equals("-") ? null : Json.parse(new String(Files.readAllBytes(new File(a[1]).toPath()), "UTF-8"));
        Ui ui = new Ui();
        ui.paper = 0;
        ui.appVersion = "0.1.0";
        if (a.length > 3) ui.tab = a[3];
        if (a.length > 4) ui.settings = a[4];
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
