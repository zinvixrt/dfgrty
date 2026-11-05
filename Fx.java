package gcse.visuals;

import net.fabricmc.loader.api.FabricLoader;

import java.awt.Color;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Settings for auto sprint, custom fog and swing animation. Public so mixins can read them. */
public class Fx {
    public static boolean autoSprint = false, fogOn = false, swingOn = false;
    public static int fogMode = 0;               // 0 = one color, 1 = rainbow, 2 = gradient
    public static int fogColor1 = 0x8A2BE2, fogColor2 = 0x00BFFF;
    public static float fogDist = 1.0f;          // 0.2 .. 1.5 of normal fog distance
    public static int swingTicks = 6;            // vanilla = 6

    static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("gcsevisuals-fx.properties"); }

    public static int fogRgb(long t) {
        if (fogMode == 1) return Color.HSBtoRGB(((t / 30) % 360) / 360f, 0.6f, 1f) & 0xFFFFFF;
        if (fogMode == 2) {
            float f = (float) ((t / 6000.0) % 1.0);
            float v = Math.abs(f * 2f - 1f);
            int r = (int) (((fogColor1 >> 16) & 255) * (1 - v) + ((fogColor2 >> 16) & 255) * v);
            int g = (int) (((fogColor1 >> 8) & 255) * (1 - v) + ((fogColor2 >> 8) & 255) * v);
            int b = (int) ((fogColor1 & 255) * (1 - v) + (fogColor2 & 255) * v);
            return (r << 16) | (g << 8) | b;
        }
        return fogColor1 & 0xFFFFFF;
    }

    static void save() {
        try {
            Properties p = new Properties();
            p.setProperty("autoSprint", String.valueOf(autoSprint));
            p.setProperty("fogOn", String.valueOf(fogOn));
            p.setProperty("swingOn", String.valueOf(swingOn));
            p.setProperty("fogMode", String.valueOf(fogMode));
            p.setProperty("fogColor1", String.valueOf(fogColor1));
            p.setProperty("fogColor2", String.valueOf(fogColor2));
            p.setProperty("fogDist", String.valueOf(fogDist));
            p.setProperty("swingTicks", String.valueOf(swingTicks));
            try (OutputStream out = Files.newOutputStream(file())) { p.store(out, "Ezka FX"); }
        } catch (Exception ignored) { }
    }

    static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(f)) { p.load(in); }
            autoSprint = Boolean.parseBoolean(p.getProperty("autoSprint", "false"));
            fogOn = Boolean.parseBoolean(p.getProperty("fogOn", "false"));
            swingOn = Boolean.parseBoolean(p.getProperty("swingOn", "false"));
            fogMode = Integer.parseInt(p.getProperty("fogMode", "0"));
            fogColor1 = Integer.parseInt(p.getProperty("fogColor1", String.valueOf(fogColor1)));
            fogColor2 = Integer.parseInt(p.getProperty("fogColor2", String.valueOf(fogColor2)));
            fogDist = Float.parseFloat(p.getProperty("fogDist", "1.0"));
            swingTicks = Integer.parseInt(p.getProperty("swingTicks", "6"));
        } catch (Exception ignored) { }
    }
}
