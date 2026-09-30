package org.loveroo.fireclient.modules.hud;

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

    private HudUtil() { }

    /**
     * Parses an AARRGGBB (or RRGGBB) hex string.
     * @param forceOpaque Forces alpha to 255 (used for text, which is invisible with alpha 0 on newer versions)
     */
    public static int parseColor(String hex, int fallback, boolean forceOpaque) {
        try {
            var text = hex.trim();
            var value = (int)Long.parseLong(text, 16);

            if(text.length() <= 6) {
                value |= 0xFF000000;
            }

            return (forceOpaque) ? (value | 0xFF000000) : value;
        }
        catch(Exception e) {
            return fallback;
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
