package org.loveroo.fireclient.modules;

import java.util.ArrayList;
import java.util.List;

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
 * Sprint Display
 *
 * Shows your sprint state, read straight from Minecraft:
 *  - sprinting, sprint key set to "Toggle"   ->  Toggled Sprinting
 *  - sprinting, sprint key set to "Hold"     ->  Held Sprinting
 *  - not sprinting, sprint toggled on        ->  Sprint Toggled
 *  - anything else                           ->  Not Sprinting (can be hidden)
 */
public class SprintDisplayModule extends ModuleBase {

    private static final Color color = Color.fromRGB(0x5BE37D);

    private static final int PADDING = 3;

    @JsonOption(name = "show_not_sprinting")
    private boolean showNotSprinting = true;

    @JsonOption(name = "show_background")
    private boolean showBackground = true;

    @JsonOption(name = "show_border")
    private boolean showBorder = false;

    @JsonOption(name = "bg_opacity")
    private int backgroundOpacity = 40;

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
        getData().setHeight(15);

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
        SPRINTING_TOGGLED("Toggled Sprinting"),
        SPRINTING_HELD("Held Sprinting"),
        TOGGLED_IDLE("Sprint Toggled"),
        NOT_SPRINTING("Not Sprinting");

        private final String label;

        State(String label) {
            this.label = label;
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

        var textColor = switch(state) {
            case SPRINTING_TOGGLED, SPRINTING_HELD -> HudUtil.parseColor(sprintingColor, 0xFF55FF55);
            case TOGGLED_IDLE -> HudUtil.parseColor(toggledColor, 0xFFFFFF55);
            case NOT_SPRINTING -> HudUtil.parseColor(idleColor, 0xFFAAAAAA);
        };

        var text = client.textRenderer;

        var w = text.getWidth(state.label) + (PADDING * 2) + 2;
        var h = 9 + (PADDING * 2);

        transform(context.getMatrices());

        HudUtil.drawBox(context, 0, 0, w, h,
            showBackground, HudUtil.parseColorWithOpacity(backgroundColor, 0xFF000000, backgroundOpacity),
            showBorder, HudUtil.parseColor(borderColor, 0xFF3C3C46));

        context.drawText(text, state.label, PADDING + 1, PADDING, textColor, true);

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
