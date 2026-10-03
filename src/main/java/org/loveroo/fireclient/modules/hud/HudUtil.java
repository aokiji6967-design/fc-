package org.loveroo.fireclient.modules.hud;

import java.util.HashMap;

import org.loveroo.fireclient.screen.config.MainConfigScreen;
import org.loveroo.fireclient.screen.config.ModuleConfigScreen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

/**
 * Small shared helpers for the Armor HUD and Potion HUD modules.
 */
public final class HudUtil {

    private static final int[] ROMAN_VALUES = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
    private static final String[] ROMAN_SYMBOLS = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };

    private static final int MAX_COLOR_CACHE_SIZE = 64;

    private static final HashMap<String, Integer> colorCache = new HashMap<>(MAX_COLOR_CACHE_SIZE);

    private HudUtil() { }

    /**
     * Marker for "this hex string failed to parse", so a legitimate 0x000000 (which
     * parses fine but is indistinguishable from a sentinel int) is cached correctly.
     */
    private static final int PARSE_FAILED = -1;

    /**
     * Parses a 6 digit RRGGBB hex string into an opaque ARGB color
 *
     * Memoized: the HUD modules call this several times per cell per frame with the
     * same handful of config strings, and the parse is wrapped in a try/catch.
 * Only successful parses are cached, since a fallback result depends on the caller.
 */
    public static int parseColor(String hex, int fallback) {
        var cached = colorCache.get(hex);

        if(cached != null) {
            return (cached == PARSE_FAILED) ? fallback : cached;
        }

        var parsed = PARSE_FAILED;

        try {
            parsed = 0xFF000000 | (Integer.parseInt(hex.trim(), 16) & 0xFFFFFF);
        }
        catch(Exception e) {
            // keep the failure marker so bad input is not re-parsed every frame
        }

        if(colorCache.size() >= MAX_COLOR_CACHE_SIZE) {
            colorCache.clear();
        }

        colorCache.put(hex, parsed);

        return (parsed == PARSE_FAILED) ? fallback : parsed;
    }

    /**
     * Same as {@link #parseColor(String, int)} but with an opacity from 0 to 100
     */
    public static int parseColorWithOpacity(String hex, int fallback, int opacityPercent) {
        var rgb = parseColor(hex, fallback) & 0xFFFFFF;
        var alpha = (int)Math.round(Math.clamp(opacityPercent, 0, 100) * 2.55);

        return (alpha << 24) | rgb;
    }

    /**
     * Replaces the alpha of an ARGB color (0 to 255)
     */
    public static int withAlpha(int color, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (color & 0xFFFFFF);
    }

    /**
     * Replaces the alpha of an ARGB color using a percentage (0 to 100)
     */
    public static int withOpacity(int color, int opacityPercent) {
        return withAlpha(color, (int)Math.round(Math.clamp(opacityPercent, 0, 100) * 2.55));
    }

    /**
     * Smoothly blends between two ARGB colors. Amount runs from 0.0 (from) to 1.0 (to)
     */
    public static int lerpColor(int from, int to, float amount) {
        var t = Math.clamp(amount, 0.0f, 1.0f);

        var a = lerpPoint((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        var r = lerpPoint((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        var g = lerpPoint((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        var b = lerpPoint(from & 0xFF, to & 0xFF, t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpPoint(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    /**
     * Mixes a color towards white, keeping the original alpha
     */
    public static int lighten(int color, float amount) {
        return lerpColor(color, withAlpha(0xFFFFFF, (color >>> 24) & 0xFF), amount);
    }

    /**
     * Mixes a color towards black, keeping the original alpha
     */
    public static int darken(int color, float amount) {
        return lerpColor(color, withAlpha(0x000000, (color >>> 24) & 0xFF), amount);
    }

    /**
     * Green -> yellow -> red color that describes a durability ratio (0.0 to 1.0)
     */
    public static int durabilityColor(float ratio) {
        var t = Math.clamp(ratio, 0.0f, 1.0f);

        if(t >= 0.5f) {
            return lerpColor(0xFFF2B33C, 0xFF48D96B, (t - 0.5f) * 2.0f);
        }

        return lerpColor(0xFFE14B4B, 0xFFF2B33C, t * 2.0f);
    }

    /**
     * Green -> yellow -> red color for a ping in milliseconds
     */
    public static int pingColor(int ping) {
        if(ping < 0) {
            return 0xFF9E9E9E;
        }

        if(ping < 100) {
            return lerpColor(0xFF48D96B, 0xFFF2B33C, ping / 100.0f);
        }

        if(ping < 300) {
            return lerpColor(0xFFF2B33C, 0xFFE14B4B, (ping - 100) / 200.0f);
        }

        return 0xFFE14B4B;
    }

    /**
     * Maximum number of bands a gradient is split into.
     *
     * A gradient used to issue one fill call per pixel, so a 120px wide panel cost
     * 120 draw calls every frame. Splitting into a fixed number of bands keeps the
     * look (a gradient across a small HUD element cannot show visible banding)
     * while making the cost constant instead of width dependent.
     */
    private static final int MAX_GRADIENT_BANDS = 16;

    /**
     * Horizontal gradient fill, drawn as a small fixed number of bands
     */
    public static void drawHorizontalGradient(net.minecraft.client.gui.DrawContext context, int x, int y, int w, int h, int from, int to) {
        if(w <= 0 || h <= 0) {
            return;
        }

        if(w == 1) {
            context.fill(x, y, x + 1, y + h, from);
            return;
        }

        var bands = Math.min(w, MAX_GRADIENT_BANDS);

        for(var band = 0; band < bands; band++) {
            var start = (band * w) / bands;
            var end = ((band + 1) * w) / bands;

            context.fill(x + start, y, x + end, y + h, lerpColor(from, to, (float)start / (w - 1)));
        }
    }

    /**
     * Vertical gradient fill, drawn as a small fixed number of bands
     */
    public static void drawVerticalGradient(net.minecraft.client.gui.DrawContext context, int x, int y, int w, int h, int from, int to) {
        if(w <= 0 || h <= 0) {
            return;
        }

        if(h == 1) {
            context.fill(x, y, x + w, y + 1, from);
            return;
        }

        var bands = Math.min(h, MAX_GRADIENT_BANDS);

        for(var band = 0; band < bands; band++) {
            var start = (band * h) / bands;
            var end = ((band + 1) * h) / bands;

            context.fill(x, y + start, x + w, y + end, lerpColor(from, to, (float)start / (h - 1)));
        }
    }

    /**
     * A card / panel used by the HUD modules.
     *
     * Draws an optional drop shadow, a soft vertical gradient background with a
     * subtle top highlight, a border and an optional accent stripe on the left.
     * An accent color with a zero alpha is treated as "no accent".
     */
    public static void drawPanel(net.minecraft.client.gui.DrawContext context, int x, int y, int w, int h,
            boolean background, int backgroundColor,
            boolean border, int borderColor,
            boolean shadow, int accentColor) {
        if(shadow) {
            context.fill(x + 1, y + 2, x + w + 1, y + h + 2, 0x40000000);
        }

        if(background) {
            drawVerticalGradient(context, x, y, w, h, lighten(backgroundColor, 0.12f), darken(backgroundColor, 0.10f));
            context.fill(x, y, x + w, y + 1, 0x1AFFFFFF);
        }

        if(border) {
            drawBorder(context, x, y, w, h, borderColor);
        }

        if(((accentColor >>> 24) & 0xFF) > 0) {
            drawVerticalGradient(context, x, y, 2, h, lighten(accentColor, 0.25f), darken(accentColor, 0.15f));
        }
    }

    /**
     * A slim progress bar with a dark track, a gradient fill and a top highlight.
     * Used for durability and status effect timers.
     */
    public static void drawBar(net.minecraft.client.gui.DrawContext context, int x, int y, int w, int h, float ratio, int color) {
        context.fill(x, y, x + w, y + h, 0xB0101014);

        var filled = Math.round(w * Math.clamp(ratio, 0.0f, 1.0f));
        if(filled <= 0) {
            return;
        }

        drawHorizontalGradient(context, x, y, filled, h, darken(color, 0.10f), lighten(color, 0.35f));

        if(h > 1) {
            context.fill(x, y, x + filled, y + 1, 0x33FFFFFF);
        }
    }

    /**
     * True while one of the FireClient HUD editor screens is open
     */
    public static boolean isEditing() {
        var screen = MinecraftClient.getInstance().currentScreen;
        return screen instanceof MainConfigScreen || screen instanceof ModuleConfigScreen;
    }

    /**
     * Global on/off blink used by the warnings (~3 blinks per second)
     */
    public static boolean flashOn() {
        return (Util.getMeasuringTimeMs() / 300L) % 2L == 0L;
    }

    public static String roman(int number) {
        if(number <= 0 || number > 3999) {
            return String.valueOf(number);
        }

        var builder = new StringBuilder();
        for(var i = 0; i < ROMAN_VALUES.length; i++) {
            while(number >= ROMAN_VALUES[i]) {
                builder.append(ROMAN_SYMBOLS[i]);
                number -= ROMAN_VALUES[i];
            }
        }

        return builder.toString();
    }

    /**
     * Formats ticks as MM:SS (or H:MM:SS when over an hour)
     */
    public static String formatDuration(int ticks) {
        var seconds = Math.max(0, (ticks + 19) / 20);

        var hours = seconds / 3600;
        var minutes = (seconds % 3600) / 60;
        var secs = seconds % 60;

        if(hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }

        return String.format("%02d:%02d", minutes, secs);
    }

    public static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static void drawBox(DrawContext context, int x, int y, int w, int h, boolean background, int backgroundColor, boolean border, int borderColor) {
        if(background) {
            context.fill(x, y, x + w, y + h, backgroundColor);
        }

        if(border) {
            drawBorder(context, x, y, w, h, borderColor);
        }
    }

    public static void drawScaledText(DrawContext context, net.minecraft.client.font.TextRenderer text, String message, float x, float y, float scale, int color) {
        var matrix = context.getMatrices();

        matrix.pushMatrix();
        matrix.translate(x, y);
        matrix.scale(scale, scale);

        context.drawText(text, message, 0, 0, color, true);

        matrix.popMatrix();
    }
}
