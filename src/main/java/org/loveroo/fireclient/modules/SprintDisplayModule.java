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
import net.minecraft.util.Util;

/**
 * Sprint Display
 *
 * Shows your sprint state, read straight from Minecraft:
 *  - sprinting, sprint key set to "Toggle"   ->  Toggled Sprinting
 *  - sprinting, sprint key set to "Hold"     ->  Held Sprinting
 *  - not sprinting, sprint toggled on        ->  Sprint Toggled
 *  - anything else                           ->  Not Sprinting (can be hidden)
 *
 * Drawn as a small card with an accent stripe colored by the current state, a
 * pulsing status dot and a smooth gradient label.
 */
public class SprintDisplayModule extends ModuleBase {

    private static final Color color = Color.fromRGB(0x5BE37D);

    private static final int PADDING = 4;
    private static final int DOT_SIZE = 6;
    private static final int DOT_GAP = 5;
    private static final int TEXT_HEIGHT = 9;

    @JsonOption(name = "show_not_sprinting")
    private boolean showNotSprinting = true;

    @JsonOption(name = "show_background")
    private boolean showBackground = true;

    @JsonOption(name = "show_border")
    private boolean showBorder = false;

    @JsonOption(name = "show_shadow")
    private boolean showShadow = true;

    @JsonOption(name = "show_accent")
    private boolean showAccent = true;

    @JsonOption(name = "show_indicator")
    private boolean showIndicator = true;

    @JsonOption(name = "gradient_text")
    private boolean gradientText = true;

    @JsonOption(name = "pulse")
    private boolean pulse = true;

    @JsonOption(name = "bg_opacity")
    private int backgroundOpacity = 45;

    @JsonOption(name = "bg_color")
    private String backgroundColor = "000000";

    @JsonOption(name = "border_color")
    private String borderColor = "3C3C46";

    @JsonOption(name = "sprinting_color")
    private String sprintingColor = "55FF55";

    @JsonOption(name = "toggled_color")
    private String toggledColor = "FFFF55";

    @JsonOption(name = "idle_color")
    private String idleColor = "AAAAAA";

    public SprintDisplayModule() {
        super(new ModuleData("sprint_display", "\u00BB", color,
            "Sprint Display", "Shows whether you are sprinting, and whether sprint is toggled or held"));

        getData().setWidth(80);
        getData().setHeight(17);

        getData().setDefaultPosX(300, 640);
        getData().setDefaultPosY(300, 360);

        getData().setVisible(true);

        var toggleBind = new Keybind("toggle_sprint_display",
                Text.translatable("fireclient.keybind.generic.toggle.name"),
                Text.translatable("fireclient.keybind.generic.toggle_visibility.description", getData().getShownName()),
                true, null,
                () -> getData().setVisible(!getData().isVisible()), null);

        FireClientside.getKeybindManager().registerKeybind(toggleBind);
    }

    private enum State {
        SPRINTING_TOGGLED("Toggled Sprinting", true),
        SPRINTING_HELD("Held Sprinting", true),
        TOGGLED_IDLE("Sprint Toggled", false),
        NOT_SPRINTING("Not Sprinting", false);

        private final String label;
        private final boolean active;

        State(String label, boolean active) {
            this.label = label;
            this.active = active;
        }
    }

    private State getState(MinecraftClient client) {
        // "Toggle" or "Hold" in the vanilla controls settings
        var toggleMode = client.options.getSprintToggled().getValue();

        var sprinting = client.player.isSprinting();

        // for a toggled key this stays true while sprint is switched on, even when standing still
        var keyActive = client.options.sprintKey.isPressed();

        if(sprinting) {
            return (toggleMode) ? State.SPRINTING_TOGGLED : State.SPRINTING_HELD;
        }

        if(toggleMode && keyActive) {
            return State.TOGGLED_IDLE;
        }

        return State.NOT_SPRINTING;
    }

    private int stateColor(State state) {
        return switch(state) {
            case SPRINTING_TOGGLED, SPRINTING_HELD -> HudUtil.parseColor(sprintingColor, 0xFF55FF55);
            case TOGGLED_IDLE -> HudUtil.parseColor(toggledColor, 0xFFFFFF55);
            case NOT_SPRINTING -> HudUtil.parseColor(idleColor, 0xFFAAAAAA);
        };
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

        var state = getState(client);

        if(state == State.NOT_SPRINTING && !showNotSprinting && !HudUtil.isEditing()) {
            return;
        }

        var text = client.textRenderer;

        var baseColor = stateColor(state);
        var accentColor = (showAccent) ? baseColor : 0;

        // a soft pulse so an active sprint feels alive (only while actually sprinting)
        var pulseAmount = 1.0f;
        if(pulse && state.active) {
            pulseAmount = (float)(0.60 + 0.40 * Math.sin(Util.getMeasuringTimeMs() / 160.0));
        }

        var dotColor = HudUtil.withOpacity(baseColor, Math.round(pulseAmount * 100.0f));

        var label = RooHelper.gradientText(state.label,
            Color.fromARGB(baseColor),
            Color.fromARGB(HudUtil.lighten(baseColor, gradientText ? 0.55f : 0.0f)));

        var textWidth = text.getWidth(label);
        var indicatorWidth = (showIndicator) ? (DOT_SIZE + DOT_GAP) : 0;

        var w = (PADDING * 2) + indicatorWidth + textWidth;
        var h = TEXT_HEIGHT + (PADDING * 2);

        transform(context.getMatrices());

        HudUtil.drawPanel(context, 0, 0, w, h,
            showBackground, HudUtil.parseColorWithOpacity(backgroundColor, 0xFF000000, backgroundOpacity),
            showBorder, HudUtil.parseColor(borderColor, 0xFF3C3C46),
            showShadow, accentColor);

        if(showIndicator) {
            var dotX = PADDING;
            var dotY = (h - DOT_SIZE) / 2;

            context.fill(dotX, dotY, dotX + DOT_SIZE, dotY + DOT_SIZE, dotColor);
            context.fill(dotX, dotY, dotX + DOT_SIZE - 1, dotY + DOT_SIZE - 1, HudUtil.lighten(dotColor, 0.35f));
        }

        context.drawText(text, label, PADDING + indicatorWidth, PADDING, 0xFFFFFFFF, true);

        endTransform(context.getMatrices());

        getData().setWidth(w);
        getData().setHeight(h);
    }

    @Override
    public List<ClickableWidget> getConfigScreen(Screen base) {
        var widgets = new ArrayList<ClickableWidget>();

        widgets.add(FireClientside.getKeybindManager().getKeybind("toggle_sprint_display").getRebindButton(5, base.height - 25, 120, 20));

        var ui = new HudUi(base, "sprint_display");

        ui.header("Display")
            .toggle("Visible", getData()::isVisible, getData()::setVisible)
            .toggle("Show Not Sprinting", () -> showNotSprinting, (value) -> showNotSprinting = value)

            .header("Style")
            .toggle("Background", () -> showBackground, (value) -> showBackground = value)
            .toggle("Border", () -> showBorder, (value) -> showBorder = value)
            .toggle("Drop Shadow", () -> showShadow, (value) -> showShadow = value)
            .toggle("Accent Stripe", () -> showAccent, (value) -> showAccent = value)
            .toggle("Status Dot", () -> showIndicator, (value) -> showIndicator = value)
            .toggle("Gradient Text", () -> gradientText, (value) -> gradientText = value)
            .toggle("Pulse When Sprinting", () -> pulse, (value) -> pulse = value)
            .slider("Opacity", 0, 100, "%", () -> backgroundOpacity, (value) -> backgroundOpacity = value)

            .header("Colors (hex)")
            .color("Sprinting", () -> sprintingColor, (value) -> sprintingColor = value)
            .color("Sprint Toggled", () -> toggledColor, (value) -> toggledColor = value)
            .color("Not Sprinting", () -> idleColor, (value) -> idleColor = value)
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
