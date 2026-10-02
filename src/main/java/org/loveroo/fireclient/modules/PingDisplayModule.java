package org.loveroo.fireclient.modules;

import java.util.ArrayList;
import java.util.List;

import org.loveroo.fireclient.RooHelper;
import org.loveroo.fireclient.client.FireClientside;
import org.loveroo.fireclient.data.Color;
import org.loveroo.fireclient.data.JsonOption;
import org.loveroo.fireclient.data.ModuleData;
import org.loveroo.fireclient.keybind.Keybind;
import org.loveroo.fireclient.modules.hud.HudUi;
import org.loveroo.fireclient.modules.hud.HudUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Ping Display
 *
 * Shows your exact ping in milliseconds, read from your own player list entry.
 * A small signal meter and an auto color scale (green -> yellow -> red) make the
 * value readable at a glance, and the ping shown is the raw number the server
 * reports (no smoothing or rounding).
 */
public class PingDisplayModule extends ModuleBase {

    private static final Color color = Color.fromRGB(0x7FE3FF);

    private static final int PADDING = 4;
    private static final int TEXT_HEIGHT = 9;

    private static final int METER_BARS = 5;
    private static final int METER_BAR_WIDTH = 2;
    private static final int METER_BAR_GAP = 1;
    private static final int METER_GAP = 5;

    @JsonOption(name = "show_label")
    private boolean showLabel = true;

    @JsonOption(name = "show_unit")
    private boolean showUnit = true;

    @JsonOption(name = "show_meter")
    private boolean showMeter = true;

    @JsonOption(name = "show_background")
    private boolean showBackground = true;

    @JsonOption(name = "show_border")
    private boolean showBorder = false;

    @JsonOption(name = "show_shadow")
    private boolean showShadow = true;

    @JsonOption(name = "show_accent")
    private boolean showAccent = true;

    @JsonOption(name = "auto_color")
    private boolean autoColor = true;

    @JsonOption(name = "gradient_text")
    private boolean gradientText = true;

    @JsonOption(name = "sample_ping")
    private int samplePing = 42;

    @JsonOption(name = "bg_opacity")
    private int backgroundOpacity = 45;

    @JsonOption(name = "bg_color")
    private String backgroundColor = "000000";

    @JsonOption(name = "border_color")
    private String borderColor = "3C3C46";

    @JsonOption(name = "text_color")
    private String textColor = "FFFFFF";

    @JsonOption(name = "ping_color")
    private String pingColor = "7FE3FF";

    public PingDisplayModule() {
        super(new ModuleData("ping_display", "\uD83D\uDCF6", color,
            "Ping Display", "Shows your exact ping in milliseconds with an optional signal meter"));

        getData().setWidth(80);
        getData().setHeight(17);

        getData().setDefaultPosX(160, 640);
        getData().setDefaultPosY(2, 360);

        getData().setVisible(true);

        var toggleBind = new Keybind("toggle_ping_display",
                Text.translatable("fireclient.keybind.generic.toggle.name"),
                Text.translatable("fireclient.keybind.generic.toggle_visibility.description", getData().getShownName()),
                true, null,
                () -> getData().setVisible(!getData().isVisible()), null);

        FireClientside.getKeybindManager().registerKeybind(toggleBind);
    }

    /**
     * The raw latency of our own player list entry, or -1 when there is none
     * (not connected, or the server has not reported a value yet).
     */
    private int readPing(MinecraftClient client) {
        if(client.player == null || client.getNetworkHandler() == null) {
            return -1;
        }

        var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        if(entry == null) {
            return -1;
        }

        return entry.getLatency();
    }

    /**
     * How many of the {@link #METER_BARS} signal bars should be lit
     */
    private int signalStrength(int ping) {
        if(ping < 0) {
            return 0;
        }

        if(ping <= 40) {
            return 5;
        }

        if(ping <= 80) {
            return 4;
        }

        if(ping <= 140) {
            return 3;
        }

        if(ping <= 250) {
            return 2;
        }

        return 1;
    }

    @Override
    public void draw(DrawContext context, RenderTickCounter ticks) {
        if(!canDraw()) {
            return;
        }

        var client = MinecraftClient.getInstance();
        if(client.player == null) {
            return;
        }

        var text = client.textRenderer;

        var editing = HudUtil.isEditing();
        var ping = readPing(client);

        // in the editor show a sample value so the HUD can be moved and styled
        if(ping < 0 && editing) {
            ping = Math.max(0, samplePing);
        }

        var valueText = (ping < 0) ? "--" : String.valueOf(ping);
        var valueColor = (autoColor) ? HudUtil.pingColor(ping) : HudUtil.parseColor(pingColor, 0xFF7FE3FF);
        var labelColor = HudUtil.parseColor(textColor, 0xFFFFFFFF);

        var labelText = (showLabel) ? "Ping" : "";
        var unitText = (showUnit) ? "ms" : "";

        var labelWidth = labelText.isEmpty() ? 0 : text.getWidth(labelText) + 4;
        var unitWidth = unitText.isEmpty() ? 0 : text.getWidth(unitText) + 4;
        var valueWidth = text.getWidth(valueText);

        var meterWidth = (showMeter) ? ((METER_BARS * METER_BAR_WIDTH) + ((METER_BARS - 1) * METER_BAR_GAP) + METER_GAP) : 0;

        var w = (PADDING * 2) + meterWidth + labelWidth + valueWidth + unitWidth;
        var h = TEXT_HEIGHT + (PADDING * 2);

        var accentColor = (showAccent) ? valueColor : 0;

        transform(context.getMatrices());

        HudUtil.drawPanel(context, 0, 0, w, h,
            showBackground, HudUtil.parseColorWithOpacity(backgroundColor, 0xFF000000, backgroundOpacity),
            showBorder, HudUtil.parseColor(borderColor, 0xFF3C3C46),
            showShadow, accentColor);

        var cursorX = PADDING;

        if(showMeter) {
            drawMeter(context, cursorX, h, ping, valueColor);
            cursorX += meterWidth;
        }

        if(!labelText.isEmpty()) {
            context.drawText(text, labelText, cursorX, PADDING, labelColor, true);
            cursorX += labelWidth;
        }

        var value = RooHelper.gradientText(valueText,
            Color.fromARGB(valueColor),
            Color.fromARGB(HudUtil.lighten(valueColor, gradientText ? 0.55f : 0.0f)));

        context.drawText(text, value, cursorX, PADDING, 0xFFFFFFFF, true);
        cursorX += valueWidth;

        if(!unitText.isEmpty()) {
            context.drawText(text, unitText, cursorX, PADDING, labelColor, true);
        }

        endTransform(context.getMatrices());

        getData().setWidth(w);
        getData().setHeight(h);
    }

    /**
     * A small wifi style meter. Lit bars use the ping color, unlit bars stay dim.
     */
    private void drawMeter(DrawContext context, int x, int h, int ping, int pingColorInt) {
        var strength = signalStrength(ping);

        var lit = (ping < 0) ? 0 : strength;
        var dimColor = 0x33FFFFFF;

        for(var i = 0; i < METER_BARS; i++) {
            var barHeight = 3 + (i * 2);
            var barX = x + (i * (METER_BAR_WIDTH + METER_BAR_GAP));
            var barY = h - PADDING - barHeight;

            if(i < lit) {
                HudUtil.drawVerticalGradient(context, barX, barY, METER_BAR_WIDTH, barHeight,
                    HudUtil.lighten(pingColorInt, 0.30f), HudUtil.darken(pingColorInt, 0.10f));
            }
            else {
                context.fill(barX, barY, barX + METER_BAR_WIDTH, barY + barHeight, dimColor);
            }
        }
    }

    @Override
    public List<ClickableWidget> getConfigScreen(Screen base) {
        var widgets = new ArrayList<ClickableWidget>();

        widgets.add(FireClientside.getKeybindManager().getKeybind("toggle_ping_display").getRebindButton(5, base.height - 25, 120, 20));

        var ui = new HudUi(base, "ping_display");

        ui.header("Display")
            .toggle("Visible", getData()::isVisible, getData()::setVisible)
            .toggle("Label", () -> showLabel, (value) -> showLabel = value)
            .toggle("Unit (ms)", () -> showUnit, (value) -> showUnit = value)
            .toggle("Signal Meter", () -> showMeter, (value) -> showMeter = value)

            .header("Style")
            .toggle("Background", () -> showBackground, (value) -> showBackground = value)
            .toggle("Border", () -> showBorder, (value) -> showBorder = value)
            .toggle("Drop Shadow", () -> showShadow, (value) -> showShadow = value)
            .toggle("Accent Stripe", () -> showAccent, (value) -> showAccent = value)
            .toggle("Auto Color", () -> autoColor, (value) -> autoColor = value)
            .toggle("Gradient Text", () -> gradientText, (value) -> gradientText = value)
            .slider("Opacity", 0, 100, "%", () -> backgroundOpacity, (value) -> backgroundOpacity = value)
            .slider("Editor Sample", 0, 500, "ms", () -> samplePing, (value) -> samplePing = value)

            .header("Colors (hex)")
            .color("Ping", () -> pingColor, (value) -> pingColor = value)
            .color("Text", () -> textColor, (value) -> textColor = value)
            .color("Background", () -> backgroundColor, (value) -> backgroundColor = value)
            .color("Border", () -> borderColor, (value) -> borderColor = value);

        widgets.add(ui.build());
        return widgets;
    }

    @Override
    public void drawScreen(Screen base, DrawContext context, float delta) {
        drawScreenHeader(context, base.width / 2, 26);
    }

    @Override
    public void closeScreen(Screen screen) {
        FireClientside.saveConfig();
    }
}
