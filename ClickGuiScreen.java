package gcse.visuals;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** "Liquid glass" Click GUI. Right Shift opens / closes. Own design, drawn with plain rectangles. */
public class ClickGuiScreen extends Screen {
    // ---------------- model ----------------
    static final int HEADER = 0, TOGGLE = 1, CYCLE = 2, SLIDER = 3, INPUT = 4, BUTTON = 5;

    static class Input {
        String value;
        final int max;
        final boolean mask;
        final Consumer<String> onChange;
        Input(String v, int max, boolean mask, Consumer<String> onChange) {
            this.value = v; this.max = max; this.mask = mask; this.onChange = onChange;
        }
    }

    static class Item {
        int type;
        String ru, en;
        Supplier<Boolean> on;
        Runnable action;
        Supplier<String> text;
        DoubleSupplier get;
        DoubleConsumer set;
        double min, max;
        DoubleFunction<String> fmt;
        Input input;
        int h() { return type == HEADER ? 16 : type == INPUT ? 22 : 18; }
    }

    static class Hit {
        int x1, y1, x2, y2;
        Runnable click;
        DoubleConsumer drag;
        Input focus;
        boolean in(double x, double y) { return x >= x1 && x < x2 && y >= y1 && y < y2; }
    }

    static Item mk(int type, String ru, String en) { Item i = new Item(); i.type = type; i.ru = ru; i.en = en; return i; }
    static Item header(String ru, String en) { return mk(HEADER, ru, en); }
    static Item toggle(String ru, String en, Supplier<Boolean> on, Runnable flip) {
        Item i = mk(TOGGLE, ru, en); i.on = on; i.action = flip; return i;
    }
    static Item cycle(String ru, String en, Supplier<String> text, Runnable a) {
        Item i = mk(CYCLE, ru, en); i.text = text; i.action = a; return i;
    }
    static Item slider(String ru, String en, double min, double max, DoubleSupplier g, DoubleConsumer s, DoubleFunction<String> f) {
        Item i = mk(SLIDER, ru, en); i.min = min; i.max = max; i.get = g; i.set = s; i.fmt = f; return i;
    }
    static Item input(String ru, String en, Input in) { Item i = mk(INPUT, ru, en); i.input = in; return i; }
    static Item button(String ru, String en, Runnable a) { Item i = mk(BUTTON, ru, en); i.action = a; return i; }

    static String L(String ru, String en) { return Watermark.guiRu ? ru : en; }

    // ---------------- state ----------------
    static final String[] CAT_RU = {"Утилиты", "Визуал", "Ватермарка", "Админ"};
    static final String[] CAT_EN = {"Utility", "Visual", "Watermark", "Admin"};

    int cat = 0;
    double scroll = 0, maxScroll = 0;
    final List<Hit> hits = new ArrayList<>();
    Hit dragging;
    Input focus;
    String msg = "";
    long msgUntil = 0;
    final Map<String, Float> anim = new HashMap<>();
    final Input search, wmText, wmC1, wmC2, pass, fogC1, fogC2;

    // per-frame layout
    int fcx, fcy, fcw, fch, fmx, fmy;
    long ft;
    int faccent, ftext, fsub, fcard, fhover;
    boolean fdark;

    ClickGuiScreen() {
        super(Text.literal("Ezka Visuals"));
        search = new Input("", 24, false, v -> scroll = 0);
        wmText = new Input(Watermark.text, 48, false, v -> Watermark.text = v);
        wmC1 = new Input(String.format("#%06X", Watermark.color1), 7, false, v -> {
            Integer c = parseHex(v);
            if (c != null) Watermark.color1 = c;
        });
        wmC2 = new Input(String.format("#%06X", Watermark.color2), 7, false, v -> {
            Integer c = parseHex(v);
            if (c != null) Watermark.color2 = c;
        });
        pass = new Input("", 16, true, v -> { });
        fogC1 = new Input(String.format("#%06X", Fx.fogColor1), 7, false, v -> {
            Integer col = parseHex(v);
            if (col != null) Fx.fogColor1 = col;
        });
        fogC2 = new Input(String.format("#%06X", Fx.fogColor2), 7, false, v -> {
            Integer col = parseHex(v);
            if (col != null) Fx.fogColor2 = col;
        });
    }

    static Integer parseHex(String v) {
        try {
            String h = v.startsWith("#") ? v.substring(1) : v;
            if (h.length() == 6) return Integer.parseInt(h, 16);
        } catch (Exception ignored) { }
        return null;
    }

    void say(String ru, String en) { msg = L(ru, en); msgUntil = System.currentTimeMillis() + 2500; }

    Runnable admin(Runnable flip) {
        return () -> {
            if (!AdminPanel.sp(client)) { say("Только в одиночном мире", "Singleplayer only"); return; }
            flip.run();
        };
    }

    void tryUnlock() {
        if (pass.value.equals(AdminPanel.PASSWORD)) { AdminPanel.unlocked = true; pass.value = ""; msg = ""; }
        else say("Неверный пароль", "Wrong password");
    }

    // ---------------- content ----------------
    List<Item> items(int catIdx) {
        List<Item> l = new ArrayList<>();
        switch (catIdx) {
            case 0 -> {
                l.add(header("Авто", "Auto"));
                l.add(toggle("Авто-свап (тотем)", "Auto Swap", () -> GCSEVisuals.autoSwap, () -> GCSEVisuals.autoSwap = !GCSEVisuals.autoSwap));
                l.add(toggle("Помощник элитры", "Elytra Helper", () -> GCSEVisuals.elytraHelper, () -> GCSEVisuals.elytraHelper = !GCSEVisuals.elytraHelper));
                l.add(toggle("Экономия фейерверков", "Firework Saver", () -> GCSEVisuals.fireworkSaver, () -> GCSEVisuals.fireworkSaver = !GCSEVisuals.fireworkSaver));
                l.add(header("Движение", "Movement"));
                l.add(toggle("Авто-спринт", "Auto Sprint", () -> Fx.autoSprint, () -> Fx.autoSprint = !Fx.autoSprint));
                l.add(header("Тейп-маус", "Tape Mouse"));
                l.add(toggle("Включить", "Tape Mouse", () -> Extras.tape, () -> { Extras.tape = !Extras.tape; if (!Extras.tape) Extras.releaseHold(client); }));
                l.add(cycle("Режим", "Mode",
                        () -> L(new String[]{"ЛКМ клик", "ЛКМ зажим", "ПКМ клик", "ПКМ зажим"}[Extras.tapeMode],
                                new String[]{"L-Click", "L-Hold", "R-Click", "R-Hold"}[Extras.tapeMode]),
                        () -> { Extras.tapeMode = (Extras.tapeMode + 1) % 4; Extras.releaseHold(client); }));
                l.add(slider("Интервал кликов", "Click interval", 2, 200, () -> Extras.tapeInterval,
                        v -> Extras.tapeInterval = (int) (Math.round(v / 2.0) * 2), v -> ((int) v) + " t"));
            }
            case 1 -> {
                l.add(header("Мир", "World"));
                l.add(toggle("Фуллбрайт", "Fullbright", () -> Extras.fullbright, () -> Extras.fullbright = !Extras.fullbright));
                l.add(toggle("Ясная погода", "Clear Weather", () -> Extras.clearWeather, () -> Extras.clearWeather = !Extras.clearWeather));
                l.add(cycle("Время суток", "Time of day", () -> Extras.ATMO[Extras.atmoMode],
                        () -> Extras.atmoMode = (Extras.atmoMode + 1) % Extras.ATMO.length));
                l.add(header("Туман", "Fog"));
                l.add(toggle("Свой туман", "Custom Fog", () -> Fx.fogOn, () -> Fx.fogOn = !Fx.fogOn));
                l.add(cycle("Режим тумана", "Fog mode",
                        () -> L(new String[]{"Цвет", "Радуга", "Градиент"}[Fx.fogMode], new String[]{"Color", "Rainbow", "Gradient"}[Fx.fogMode]),
                        () -> Fx.fogMode = (Fx.fogMode + 1) % 3));
                l.add(input("Цвет тумана 1", "Fog color 1", fogC1));
                l.add(input("Цвет тумана 2", "Fog color 2", fogC2));
                l.add(slider("Дальность тумана", "Fog distance", 20, 150, () -> Fx.fogDist * 100,
                        v -> Fx.fogDist = (float) (Math.round(v) / 100.0), v -> ((int) v) + "%"));
                l.add(header("Анимации", "Animations"));
                l.add(toggle("Скорость взмаха", "Swing speed", () -> Fx.swingOn, () -> Fx.swingOn = !Fx.swingOn));
                l.add(slider("Длительность взмаха", "Swing duration", 2, 30, () -> Fx.swingTicks,
                        v -> Fx.swingTicks = (int) Math.round(v), v -> ((int) v) + " t"));
                l.add(header("Игрок", "Player"));
                l.add(toggle("Китайская шляпа", "China Hat", () -> Extras.chinaHat, () -> Extras.chinaHat = !Extras.chinaHat));
                l.add(toggle("Метка цели", "Target Marker", () -> Extras.targetMarker, () -> Extras.targetMarker = !Extras.targetMarker));
                l.add(header("Интерфейс", "HUD"));
                l.add(toggle("Интерфейс мода", "HUD", () -> GCSEVisuals.visuals, () -> GCSEVisuals.visuals = !GCSEVisuals.visuals));
                l.add(toggle("Список модулей", "Module List", () -> Watermark.moduleList, () -> Watermark.moduleList = !Watermark.moduleList));
                l.add(toggle("Координаты", "Coordinates", () -> Watermark.coords, () -> Watermark.coords = !Watermark.coords));
            }
            case 2 -> {
                l.add(header("Текст", "Text"));
                l.add(input("Текст", "Text", wmText));
                l.add(header("Цвет", "Color"));
                l.add(cycle("Режим цвета", "Color mode",
                        () -> L(new String[]{"Радуга", "Один цвет", "Градиент"}[Watermark.colorMode],
                                new String[]{"Rainbow", "Static", "Gradient"}[Watermark.colorMode]),
                        () -> Watermark.colorMode = (Watermark.colorMode + 1) % 3));
                l.add(input("Цвет 1", "Color 1", wmC1));
                l.add(input("Цвет 2", "Color 2", wmC2));
                l.add(header("Вид", "Look"));
                l.add(toggle("Жирный", "Bold", () -> Watermark.bold, () -> Watermark.bold = !Watermark.bold));
                l.add(toggle("Тень", "Shadow", () -> Watermark.shadow, () -> Watermark.shadow = !Watermark.shadow));
                l.add(toggle("Фон", "Background", () -> Watermark.bg, () -> Watermark.bg = !Watermark.bg));
                l.add(toggle("Цветная линия", "Accent line", () -> Watermark.accent, () -> Watermark.accent = !Watermark.accent));
                l.add(slider("Прозрачность фона", "Opacity", 0, 255, () -> Watermark.bgAlpha,
                        v -> Watermark.bgAlpha = (int) Math.round(v), v -> Math.round(v * 100 / 255.0) + "%"));
                l.add(slider("Размер", "Scale", 0.5, 3.0, () -> Watermark.scale,
                        v -> Watermark.scale = Math.round(v * 10) / 10f, v -> String.format(Locale.ROOT, "%.1f", v)));
                l.add(header("Лого", "Logo"));
                l.add(toggle("Лого", "Logo", () -> Watermark.logo, () -> Watermark.logo = !Watermark.logo));
                l.add(cycle("Сторона", "Side", () -> Watermark.logoLeft ? L("Слева", "Left") : L("Справа", "Right"),
                        () -> Watermark.logoLeft = !Watermark.logoLeft));
                l.add(header("Элементы", "Elements"));
                l.add(toggle("FPS", "FPS", () -> Watermark.fps, () -> Watermark.fps = !Watermark.fps));
                l.add(toggle("Пинг", "Ping", () -> Watermark.ping, () -> Watermark.ping = !Watermark.ping));
                l.add(toggle("Время", "Clock", () -> Watermark.time, () -> Watermark.time = !Watermark.time));
                l.add(toggle("Ник", "Name", () -> Watermark.name, () -> Watermark.name = !Watermark.name));
                l.add(toggle("Координаты", "Coordinates", () -> Watermark.coords, () -> Watermark.coords = !Watermark.coords));
                l.add(toggle("Фейерверки", "Fireworks", () -> Watermark.fireworks, () -> Watermark.fireworks = !Watermark.fireworks));
                l.add(toggle("BPS", "BPS", () -> Watermark.bps, () -> Watermark.bps = !Watermark.bps));
                l.add(toggle("Список модулей", "Module List", () -> Watermark.moduleList, () -> Watermark.moduleList = !Watermark.moduleList));
                l.add(header("Позиция", "Position"));
                l.add(cycle("Угол", "Corner",
                        () -> L(new String[]{"Слева сверху", "Справа сверху", "Слева снизу", "Справа снизу", "Сверху по центру"}[Watermark.corner],
                                new String[]{"Top left", "Top right", "Bottom left", "Bottom right", "Top center"}[Watermark.corner]),
                        () -> Watermark.corner = (Watermark.corner + 1) % 5));
                l.add(slider("Отступ X", "Offset X", -200, 400, () -> Watermark.offX, v -> Watermark.offX = (int) Math.round(v), v -> String.valueOf((int) v)));
                l.add(slider("Отступ Y", "Offset Y", -200, 400, () -> Watermark.offY, v -> Watermark.offY = (int) Math.round(v), v -> String.valueOf((int) v)));
                l.add(button("Сбросить настройки", "Reset", () -> {
                    Watermark.defaults();
                    wmText.value = Watermark.text;
                    wmC1.value = String.format("#%06X", Watermark.color1);
                    wmC2.value = String.format("#%06X", Watermark.color2);
                }));
            }
            default -> {
                if (!AdminPanel.unlocked) {
                    l.add(header("Доступ закрыт", "Locked"));
                    l.add(input("Пароль", "Password", pass));
                    l.add(button("Открыть", "Unlock", this::tryUnlock));
                } else {
                    l.add(header("Только одиночный мир", "Singleplayer only"));
                    l.add(toggle("Полёт", "Fly", () -> AdminPanel.fly, admin(() -> AdminPanel.fly = !AdminPanel.fly)));
                    l.add(toggle("Триггербот", "Triggerbot", () -> AdminPanel.trigger, admin(() -> AdminPanel.trigger = !AdminPanel.trigger)));
                    l.add(toggle("Килаура", "Killaura", () -> AdminPanel.killaura, admin(() -> AdminPanel.killaura = !AdminPanel.killaura)));
                    l.add(toggle("Рентген (руды)", "Xray", () -> AdminPanel.xray, admin(() -> AdminPanel.xray = !AdminPanel.xray)));
                    l.add(toggle("Скорость", "Speed", () -> AdminPanel.speed, admin(() -> AdminPanel.speed = !AdminPanel.speed)));
                    l.add(toggle("Только криты", "Crits only", () -> AdminPanel.crits, admin(() -> AdminPanel.crits = !AdminPanel.crits)));
                }
            }
        }
        return l;
    }

    List<Item> visibleItems() {
        String q = search.value.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return items(cat);
        List<Item> r = new ArrayList<>();
        for (int c = 0; c < 4; c++) {
            for (Item it : items(c)) {
                if (it.type == HEADER) continue;
                if (it.ru.toLowerCase(Locale.ROOT).contains(q) || it.en.toLowerCase(Locale.ROOT).contains(q)) r.add(it);
            }
        }
        return r;
    }

    // ---------------- drawing helpers ----------------
    static int corner(int r, int i) {
        double d = r - i - 0.5;
        return r - (int) Math.round(Math.sqrt(Math.max(0, r * (double) r - d * d)));
    }

    static int mix(int a, int b, float f) {
        int aa = (int) (((a >>> 24) & 255) * (1 - f) + ((b >>> 24) & 255) * f);
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return (aa << 24) | (r << 16) | (g << 8) | bl;
    }

    static int alpha(int rgb, int a) { return (a << 24) | (rgb & 0xFFFFFF); }

    static void rrect(DrawContext c, int x1, int y1, int x2, int y2, int r, int color) {
        r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) { c.fill(x1, y1, x2, y2, color); return; }
        for (int i = 0; i < r; i++) {
            int in = corner(r, i);
            c.fill(x1 + in, y1 + i, x2 - in, y1 + i + 1, color);
            c.fill(x1 + in, y2 - i - 1, x2 - in, y2 - i, color);
        }
        c.fill(x1, y1 + r, x2, y2 - r, color);
    }

    static void rrectV(DrawContext c, int x1, int y1, int x2, int y2, int r, int top, int bot) {
        int h = y2 - y1;
        r = Math.min(r, Math.min((x2 - x1) / 2, h / 2));
        for (int row = 0; row < h; row++) {
            int in = 0;
            if (row < r) in = corner(r, row);
            else if (row >= h - r) in = corner(r, h - 1 - row);
            c.fill(x1 + in, y1 + row, x2 - in, y1 + row + 1, mix(top, bot, h <= 1 ? 0f : row / (float) (h - 1)));
        }
    }

    static void blob(DrawContext c, int cx, int cy, int r, int rgb, int a) {
        for (int k = 0; k < 4; k++) {
            int rr = r - k * r / 5;
            int col = alpha(rgb, a);
            for (int dy = -rr; dy < rr; dy += 2) {
                int hw = (int) Math.sqrt((double) rr * rr - (double) dy * dy);
                c.fill(cx - hw, cy + dy, cx + hw, cy + dy + 2, col);
            }
        }
    }

    static String tail(TextRenderer tr, String s, int w) {
        while (!s.isEmpty() && tr.getWidth(s) > w) s = s.substring(1);
        return s;
    }

    float anim(String key, float target) {
        float v = anim.getOrDefault(key, target);
        v += (target - v) * 0.3f;
        if (Math.abs(target - v) < 0.01f) v = target;
        anim.put(key, v);
        return v;
    }

    Hit addHit(int x1, int y1, int x2, int y2) {
        Hit h = new Hit();
        h.x1 = x1; h.y1 = y1; h.x2 = x2; h.y2 = y2;
        hits.add(h);
        return h;
    }

    // ---------------- render ----------------
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long t = System.currentTimeMillis();
        TextRenderer tr = textRenderer;
        hits.clear();
        ft = t; fmx = mx; fmy = my;
        fdark = Watermark.guiDark;

        int ww = Math.min(width - 12, 400), wh = Math.min(height - 12, 250);
        int wx = (width - ww) / 2, wy = (height - wh) / 2;
        int sbW = 86, topH = 26;
        fcx = wx + sbW; fcy = wy + topH; fcw = ww - sbW; fch = wh - topH - 14;

        faccent = Watermark.colorAt(0.5f, t);
        ftext = fdark ? 0xFFFFFFFF : 0xFF1B2333;
        fsub = fdark ? 0xFFA9B3C6 : 0xFF55607A;
        fcard = fdark ? 0x2AFFFFFF : 0x55FFFFFF;
        fhover = fdark ? 0x22FFFFFF : 0x20000000;

        // backdrop + soft color blobs seen through the glass
        ctx.fill(0, 0, width, height, fdark ? 0x70000000 : 0x40000000);
        blob(ctx, wx + ww / 4 + (int) (Math.sin(t / 1700.0) * 40), wy + wh / 3 + (int) (Math.cos(t / 2100.0) * 30), 70, Watermark.colorAt(0.1f, t), 0x1C);
        blob(ctx, wx + ww * 3 / 4 + (int) (Math.cos(t / 1900.0) * 40), wy + wh * 2 / 3 + (int) (Math.sin(t / 1500.0) * 30), 80, Watermark.colorAt(0.7f, t), 0x1C);

        // shadow + glass body
        for (int k = 1; k <= 5; k++) rrect(ctx, wx - k, wy - k + 3, wx + ww + k, wy + wh + k + 3, 12 + k, 0x0C000000);
        rrectV(ctx, wx, wy, wx + ww, wy + wh, 12, fdark ? 0xA01C2638 : 0xB8F6F9FF, fdark ? 0xA00E1522 : 0xB8DDE7F6);
        ctx.fill(wx + 12, wy, wx + ww - 12, wy + 1, fdark ? 0x70FFFFFF : 0xB0FFFFFF);
        ctx.fill(wx, wy + 12, wx + 1, wy + wh - 12, fdark ? 0x30FFFFFF : 0x80FFFFFF);
        ctx.fill(wx + ww - 1, wy + 12, wx + ww, wy + wh - 12, fdark ? 0x20FFFFFF : 0x60FFFFFF);
        ctx.fill(wx + 12, wy + wh - 1, wx + ww - 12, wy + wh, fdark ? 0x18FFFFFF : 0x50FFFFFF);
        rrectV(ctx, wx + 2, wy + 2, wx + ww - 2, wy + wh / 3, 10, 0x18FFFFFF, 0x00FFFFFF);

        // sidebar
        rrect(ctx, wx + 5, wy + 5, wx + sbW - 3, wy + wh - 5, 9, fdark ? 0x30000000 : 0x28FFFFFF);
        ctx.drawTexture(RenderLayer::getGuiTextured, Watermark.LOGO, wx + 10, wy + 9, 0f, 0f, 16, 16, 64, 64, 64, 64);
        ctx.drawText(tr, "Ezka", wx + 31, wy + 13, ftext, false);
        ctx.drawText(tr, "Visuals (beta)", wx + 10, wy + 28, fsub, false);
        for (int i = 0; i < CAT_EN.length; i++) {
            final int idx = i;
            int y1 = wy + 46 + i * 21, y2 = y1 + 18, x1 = wx + 9, x2 = wx + sbW - 7;
            boolean sel = cat == i && search.value.isEmpty();
            boolean hov = mx >= x1 && mx < x2 && my >= y1 && my < y2;
            if (sel) rrect(ctx, x1, y1, x2, y2, 6, alpha(faccent, 0x55));
            else if (hov) rrect(ctx, x1, y1, x2, y2, 6, fhover);
            ctx.drawText(tr, L(CAT_RU[i], CAT_EN[i]), x1 + 8, y1 + 5, sel ? ftext : fsub, false);
            addHit(x1, y1, x2, y2).click = () -> { cat = idx; scroll = 0; search.value = ""; };
        }
        ctx.drawText(tr, "Right Shift", wx + 10, wy + wh - 16, fsub, false);

        // top bar: search, language, theme
        int sx1 = fcx + 6, sx2 = wx + ww - 104, sy1 = wy + 6;
        rrect(ctx, sx1, sy1, sx2, sy1 + 16, 6, fdark ? 0x30000000 : 0x55FFFFFF);
        boolean empty = search.value.isEmpty() && focus != search;
        String shown = empty ? L("Поиск...", "Search...") : search.value + ((focus == search && (t / 500) % 2 == 0) ? "_" : "");
        ctx.drawText(tr, tail(tr, shown, sx2 - sx1 - 10), sx1 + 5, sy1 + 4, empty ? fsub : ftext, false);
        addHit(sx1, sy1, sx2, sy1 + 16).focus = search;

        int lx1 = wx + ww - 98, lx2 = lx1 + 40;
        rrect(ctx, lx1, sy1, lx2, sy1 + 16, 6, fdark ? 0x30000000 : 0x55FFFFFF);
        rrect(ctx, Watermark.guiRu ? lx1 : lx1 + 20, sy1, Watermark.guiRu ? lx1 + 20 : lx2, sy1 + 16, 6, alpha(faccent, 0x66));
        ctx.drawText(tr, "RU", lx1 + 4, sy1 + 4, Watermark.guiRu ? ftext : fsub, false);
        ctx.drawText(tr, "EN", lx1 + 24, sy1 + 4, Watermark.guiRu ? fsub : ftext, false);
        addHit(lx1, sy1, lx2, sy1 + 16).click = () -> Watermark.guiRu = !Watermark.guiRu;

        int tx1 = wx + ww - 52, tx2 = tx1 + 44;
        rrect(ctx, tx1, sy1, tx2, sy1 + 16, 6, fdark ? 0x30000000 : 0x55FFFFFF);
        String tn = fdark ? L("Тёмная", "Dark") : L("Светлая", "Light");
        ctx.drawText(tr, tn, tx1 + (44 - tr.getWidth(tn)) / 2, sy1 + 4, ftext, false);
        addHit(tx1, sy1, tx2, sy1 + 16).click = () -> Watermark.guiDark = !Watermark.guiDark;

        // content (scrollable)
        List<Item> list = visibleItems();
        int total = 6;
        for (Item it : list) total += it.h() + 2;
        maxScroll = Math.max(0, total - fch);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        ctx.enableScissor(fcx, fcy, fcx + fcw - 2, fcy + fch);
        int y = fcy + 4 - (int) scroll;
        for (Item it : list) {
            int h = it.h();
            if (y + h >= fcy && y <= fcy + fch) drawItem(ctx, it, y);
            y += h + 2;
        }
        ctx.disableScissor();
        if (maxScroll > 0) {
            int bh = Math.max(14, (int) (fch * (fch / (double) total)));
            int by = fcy + (int) ((fch - bh) * (scroll / maxScroll));
            rrect(ctx, fcx + fcw - 6, by, fcx + fcw - 3, by + bh, 1, alpha(faccent, 0x99));
        }

        if (!msg.isEmpty() && System.currentTimeMillis() < msgUntil) {
            ctx.drawText(tr, msg, fcx + 10, wy + wh - 14, 0xFFFF6B6B, false);
        }
    }

    void drawItem(DrawContext ctx, Item it, int y) {
        TextRenderer tr = textRenderer;
        int h = it.h();
        int x1 = fcx + 8, x2 = fcx + fcw - 10;
        int hy1 = Math.max(y, fcy), hy2 = Math.min(y + h, fcy + fch);
        boolean vis = hy2 > hy1;
        String label = L(it.ru, it.en);

        if (it.type == HEADER) {
            ctx.drawText(tr, label, x1 + 2, y + 5, faccent, false);
            return;
        }
        boolean hov = vis && fmx >= x1 && fmx < x2 && fmy >= hy1 && fmy < hy2;
        rrect(ctx, x1, y, x2, y + h, 5, fcard);
        if (hov) rrect(ctx, x1, y, x2, y + h, 5, fhover);
        int ty = y + (h - 8) / 2;

        switch (it.type) {
            case TOGGLE -> {
                ctx.drawText(tr, label, x1 + 6, ty, ftext, false);
                boolean on = it.on.get();
                float p = anim(it.en, on ? 1f : 0f);
                int sw = 22, sh = 10, sx = x2 - 6 - sw, sy = y + (h - sh) / 2;
                rrect(ctx, sx, sy, sx + sw, sy + sh, 5, mix(fdark ? 0x55FFFFFF : 0x55000000, faccent, p));
                int kx = sx + 1 + Math.round((sw - sh) * p);
                rrect(ctx, kx, sy + 1, kx + sh - 2, sy + sh - 1, 4, 0xFFFFFFFF);
                if (vis) addHit(x1, hy1, x2, hy2).click = it.action;
            }
            case CYCLE -> {
                ctx.drawText(tr, label, x1 + 6, ty, ftext, false);
                String vt = it.text.get();
                int px2 = x2 - 6, px1 = px2 - (tr.getWidth(vt) + 12);
                rrect(ctx, px1, y + 3, px2, y + h - 3, 5, alpha(faccent, 0x44));
                ctx.drawText(tr, vt, px1 + 6, ty, ftext, false);
                if (vis) addHit(x1, hy1, x2, hy2).click = it.action;
            }
            case SLIDER -> {
                ctx.drawText(tr, label, x1 + 6, ty, ftext, false);
                double val = it.get.getAsDouble();
                String vt = it.fmt.apply(val);
                int tw = 64, tx2 = x2 - 8, tx1 = tx2 - tw, cy2 = y + h / 2;
                ctx.drawText(tr, vt, tx1 - 6 - tr.getWidth(vt), ty, fsub, false);
                rrect(ctx, tx1, cy2 - 1, tx2, cy2 + 2, 1, fdark ? 0x44FFFFFF : 0x44000000);
                double frac = Math.max(0, Math.min(1, (val - it.min) / (it.max - it.min)));
                int fx = tx1 + (int) (tw * frac);
                rrect(ctx, tx1, cy2 - 1, Math.max(tx1 + 2, fx), cy2 + 2, 1, faccent);
                rrect(ctx, fx - 3, cy2 - 3, fx + 3, cy2 + 3, 3, 0xFFFFFFFF);
                if (vis) {
                    Hit hh = addHit(tx1 - 4, hy1, tx2 + 4, hy2);
                    final Item sl = it;
                    final int sx1 = tx1, sw2 = tw;
                    hh.drag = mxv -> {
                        double f = Math.max(0, Math.min(1, (mxv - sx1) / (double) sw2));
                        sl.set.accept(sl.min + f * (sl.max - sl.min));
                    };
                }
            }
            case INPUT -> {
                ctx.drawText(tr, label, x1 + 6, ty, ftext, false);
                Input in = it.input;
                int bw = Math.max(50, Math.min(150, (x2 - x1) - tr.getWidth(label) - 30));
                int bx2 = x2 - 6, bx1 = bx2 - bw;
                boolean f = focus == in;
                rrect(ctx, bx1, y + 3, bx2, y + h - 3, 5, fdark ? 0x44000000 : 0x55FFFFFF);
                if (f) ctx.fill(bx1 + 3, y + h - 4, bx2 - 3, y + h - 3, faccent);
                String sv = in.mask ? "*".repeat(in.value.length()) : in.value;
                if (f && (ft / 500) % 2 == 0) sv += "_";
                ctx.drawText(tr, tail(tr, sv, bw - 10), bx1 + 5, ty, ftext, false);
                if (vis) addHit(bx1, hy1, bx2, hy2).focus = in;
            }
            case BUTTON -> {
                rrect(ctx, x1, y, x2, y + h, 5, alpha(faccent, hov ? 0x88 : 0x55));
                ctx.drawText(tr, label, x1 + ((x2 - x1) - tr.getWidth(label)) / 2, ty, ftext, false);
                if (vis) addHit(x1, hy1, x2, hy2).click = it.action;
            }
            default -> { }
        }
    }

    // ---------------- input ----------------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        focus = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.in(mx, my)) continue;
            if (h.focus != null) focus = h.focus;
            if (h.drag != null) { dragging = h; h.drag.accept(mx); }
            if (h.click != null) h.click.run();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null && dragging.drag != null) { dragging.drag.accept(mx); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - vAmount * 14));
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (focus != null && chr >= 32 && chr != 127 && focus.value.length() < focus.max) {
            focus.value += chr;
            focus.onChange.accept(focus.value);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focus != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!focus.value.isEmpty()) {
                    focus.value = focus.value.substring(0, focus.value.length() - 1);
                    focus.onChange.accept(focus.value);
                }
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (focus == pass) tryUnlock();
                focus = null;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                focus = null;
            }
            return true;
        }
        if (Watermark.kOpen.matchesKey(keyCode, scanCode)) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() { Watermark.save(); Fx.save(); }

    @Override
    public boolean shouldPause() { return false; }
}
