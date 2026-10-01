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

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Potion HUD (inspired by Inventory HUD+)
 *
 * Shows active status effects as a compact icon list or a detailed list with
 * names, levels and remaining time. Effects that are about to run out flash.
 */
public class PotionHudModule extends ModuleBase {

    private static final Color color = Color.fromRGB(0xB57BFF);

    private static final int ICON_SIZE = 18;
    private static final int TEXT_GAP = 3;
    private static final float SMALL_TEXT = 0.75f;

    private static final int PADDING = 3;
    private static final int SPACING = 2;

    // ---- display ----
    @JsonOption(name = "mode")
    private Mode mode = Mode.DETAILED;

    @JsonOption(name = "layout")
    private Layout layout = Layout.VERTICAL;

    @JsonOption(name = "hide_vanilla")
    private boolean hideVanilla = true;

    @JsonOption(name = "show_name")
    private boolean showName = true;

    @JsonOption(name = "show_level")
    private boolean showLevel = true;

    @JsonOption(name = "show_duration")
    private boolean showDuration = true;

    // ---- filters ----
    @JsonOption(name = "show_beneficial")
    private boolean showBeneficial = true;

    @JsonOption(name = "show_harmful")
    private boolean showHarmful = true;

    // ---- expiring warning ----
    @JsonOption(name = "warn_enabled")
    private boolean warnEnabled = true;

    @JsonOption(name = "warn_seconds")
    private int warnSeconds = 10;

    // ---- style ----
    @JsonOption(name = "show_background")
    private boolean showBackground = true;

    @JsonOption(name = "show_border")
    private boolean showBorder = false;

    @JsonOption(name = "bg_opacity")
    private int backgroundOpacity = 60;

    @JsonOption(name = "bg_color")
    private String backgroundColor = "000000";

    @JsonOption(name = "border_color")
    private String borderColor = "3C3C46";

    @JsonOption(name = "text_color")
    private String textColor = "FFFFFF";

    @JsonOption(name = "warn_color")
    private String warnColor = "FF5555";

    public PotionHudModule() {
        super(new ModuleData("potion_hud", "⚗", color,
            "Potion HUD", "Shows your active status effects as icons or a detailed list, and warns you when they are about to run out"));

        getData().setWidth(90);
        getData().setHeight(24);

        getData().setDefaultPosX(520, 640);
        getData().setDefaultPosY(30, 360);

        getData().setVisible(true);

        var toggleBind = new Keybind("toggle_potion_hud",
                Text.translatable("fireclient.keybind.generic.toggle.name"),
                Text.translatable("fireclient.keybind.generic.toggle_visibility.description", getData().getShownName()),
                true, null,
                () -> getData().setVisible(!getData().isVisible()), null);

        FireClientside.getKeybindManager().registerKeybind(toggleBind);
    }

    @Override
    public void postLoad() {
        // hides the vanilla effect list while this HUD is visible, so effects don't show twice
        HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, (original) -> (context, tickCounter) -> {
            if(hideVanilla && getData().isVisible()) {
                return;
            }

            original.render(context, tickCounter);
        });
    }

    // ------------------------------------------------------------------
    // data
    // ------------------------------------------------------------------

    private record Entry(RegistryEntry<StatusEffect> type, int amplifier, int duration, boolean infinite, boolean ambient) { }

    private record Cell(Entry entry, String title, String level, String duration, int w, int h) { }

    private List<Entry> collect(MinecraftClient client, boolean editing) {
        var raw = new ArrayList<Entry>();

        if(client.player != null) {
            for(var instance : client.player.getStatusEffects()) {
                if(!instance.shouldShowIcon()) {
                    continue;
                }

                raw.add(new Entry(instance.getEffectType(), instance.getAmplifier(), instance.getDuration(), instance.isInfinite(), instance.isAmbient()));
            }
        }

        // fake effects so the HUD can be seen and moved in the editor
        if(raw.isEmpty() && editing) {
            raw.add(new Entry(StatusEffects.STRENGTH, 1, 20 * 105, false, false));
            raw.add(new Entry(StatusEffects.SPEED, 0, 20 * 600, false, false));
            raw.add(new Entry(StatusEffects.REGENERATION, 1, 20 * 8, false, false));
            raw.add(new Entry(StatusEffects.FIRE_RESISTANCE, 0, -1, true, false));
            raw.add(new Entry(StatusEffects.POISON, 0, 20 * 30, false, false));
        }

        var entries = new ArrayList<Entry>();
        for(var entry : raw) {
            var category = entry.type().value().getCategory();

            if(category == StatusEffectCategory.BENEFICIAL && !showBeneficial) {
                continue;
            }

            if(category == StatusEffectCategory.HARMFUL && !showHarmful) {
                continue;
            }

            entries.add(entry);
        }

        return entries;
    }

    private String levelString(Entry entry) {
        var level = entry.amplifier() + 1;

        // level I is not shown, like in vanilla
        return (level == 1) ? null : HudUtil.roman(level);
    }

    private String durationString(Entry entry) {
        if(entry.infinite()) {
            return "Infinite";
        }

        return HudUtil.formatDuration(entry.duration());
    }

    private boolean isExpiring(Entry entry) {
        return warnEnabled && !entry.infinite() && entry.duration() < (warnSeconds * 20);
    }

    /**
     * True when the warning color should currently be used (it blinks)
     */
    private boolean warnActive(Entry entry) {
        return isExpiring(entry) && HudUtil.flashOn();
    }

    private Cell buildCell(Entry entry) {
        var text = MinecraftClient.getInstance().textRenderer;

        var level = levelString(entry);
        var duration = (showDuration) ? durationString(entry) : "";

        if(mode == Mode.COMPACT) {
            var durationWidth = (duration.isEmpty()) ? 0 : (int)Math.ceil(text.getWidth(duration) * SMALL_TEXT);

            var w = (PADDING * 2) + Math.max(ICON_SIZE, durationWidth);
            var h = (PADDING * 2) + ICON_SIZE + ((duration.isEmpty()) ? 0 : 1 + (int)Math.ceil(8 * SMALL_TEXT));

            return new Cell(entry, "", (showLevel) ? level : null, duration, w, h);
        }

        var title = new StringBuilder();
        if(showName) {
            title.append(entry.type().value().getName().getString());
        }

        if(showLevel && level != null) {
            if(title.length() > 0) {
                title.append(' ');
            }

            title.append(level);
        }

        var titleText = title.toString();

        var lines = ((titleText.isEmpty()) ? 0 : 1) + ((duration.isEmpty()) ? 0 : 1);
        var textHeight = (lines == 0) ? 0 : (lines * 9) + (lines - 1);
        var textWidth = Math.max(text.getWidth(titleText), text.getWidth(duration));

        var w = (PADDING * 2) + ICON_SIZE + ((textWidth > 0) ? TEXT_GAP : 0) + textWidth;
        var h = (PADDING * 2) + Math.max(ICON_SIZE, textHeight);

        return new Cell(entry, titleText, level, duration, w, h);
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------

    @Override
    public void draw(DrawContext context, RenderTickCounter ticks) {
        if(!canDraw()) {
            return;
        }

        var client = MinecraftClient.getInstance();
        if(client.player == null) {
            return;
        }

        var entries = collect(client, HudUtil.isEditing());

        var cells = new ArrayList<Cell>();
        var maxWidth = 0;
        var maxHeight = 0;

        for(var entry : entries) {
            var cell = buildCell(entry);

            // nothing to show for this effect with the current settings
            if(cell.h() <= (PADDING * 2)) {
                continue;
            }

            cells.add(cell);

            maxWidth = Math.max(maxWidth, cell.w());
            maxHeight = Math.max(maxHeight, cell.h());
        }

        if(cells.isEmpty()) {
            getData().setWidth(ICON_SIZE);
            getData().setHeight(ICON_SIZE);

            return;
        }

        var horizontal = (layout == Layout.HORIZONTAL);

        transform(context.getMatrices());

        var x = 0;
        var y = 0;

        for(var cell : cells) {
            // keep rows / columns even so the backgrounds line up
            var w = (mode == Mode.COMPACT || !horizontal) ? maxWidth : cell.w();
            var h = (horizontal) ? maxHeight : cell.h();

            drawCell(context, cell, x, y, w, h);

            if(horizontal) {
                x += w + SPACING;
            }
            else {
                y += h + SPACING;
            }
        }

        endTransform(context.getMatrices());

        getData().setWidth((horizontal) ? (x - SPACING) : maxWidth);
        getData().setHeight((horizontal) ? maxHeight : (y - SPACING));
    }

    private void drawCell(DrawContext context, Cell cell, int x, int y, int w, int h) {
        var text = MinecraftClient.getInstance().textRenderer;
        var entry = cell.entry();

        HudUtil.drawBox(context, x, y, w, h,
            showBackground, HudUtil.parseColorWithOpacity(backgroundColor, 0xFF000000, backgroundOpacity),
            showBorder, HudUtil.parseColor(borderColor, 0xFF3C3C46));

        var warn = warnActive(entry);
        var normalColor = HudUtil.parseColor(textColor, 0xFFFFFFFF);
        var warningColor = HudUtil.parseColor(warnColor, 0xFFFF5555);

        var titleColor = (warn) ? warningColor : normalColor;
        var durationColor = titleColor;

        if(mode == Mode.COMPACT) {
            var iconX = x + ((w - ICON_SIZE) / 2);
            var iconY = y + PADDING;

            drawIcon(context, entry, iconX, iconY);

            if(cell.level() != null) {
                var levelWidth = text.getWidth(cell.level()) * SMALL_TEXT;
                HudUtil.drawScaledText(context, text, cell.level(), iconX + ICON_SIZE - (float)levelWidth, iconY, SMALL_TEXT, titleColor);
            }

            if(!cell.duration().isEmpty()) {
                var durationWidth = text.getWidth(cell.duration()) * SMALL_TEXT;
                HudUtil.drawScaledText(context, text, cell.duration(), x + ((w - (float)durationWidth) / 2.0f), iconY + ICON_SIZE + 1, SMALL_TEXT, durationColor);
            }

            return;
        }

        var textX = x + PADDING + ICON_SIZE + TEXT_GAP;

        drawIcon(context, entry, x + PADDING, y + ((h - ICON_SIZE) / 2));

        var hasTitle = !cell.title().isEmpty();
        var hasDuration = !cell.duration().isEmpty();

        var lines = ((hasTitle) ? 1 : 0) + ((hasDuration) ? 1 : 0);
        if(lines == 0) {
            return;
        }

        var textHeight = (lines * 9) + (lines - 1);
        var textY = y + ((h - textHeight) / 2);

        if(hasTitle) {
            context.drawText(text, cell.title(), textX, textY, titleColor, true);
            textY += 10;
        }

        if(hasDuration) {
            context.drawText(text, cell.duration(), textX, textY, durationColor, true);
        }
    }

    private void drawIcon(DrawContext context, Entry entry, int x, int y) {
        var id = getIconId(entry.type());
        if(id == null) {
            return;
        }

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, id, x, y, ICON_SIZE, ICON_SIZE);
    }

    private Identifier getIconId(RegistryEntry<StatusEffect> type) {
        return type.getKey()
            .map((key) -> key.getValue().withPrefixedPath("mob_effect/"))
            .orElse(null);
    }

    // ------------------------------------------------------------------
    // config screen
    // ------------------------------------------------------------------

    @Override
    public List<ClickableWidget> getConfigScreen(Screen base) {
        var widgets = new ArrayList<ClickableWidget>();

        widgets.add(FireClientside.getKeybindManager().getKeybind("toggle_potion_hud").getRebindButton(5, base.height - 25, 120, 20));

        var ui = new HudUi(base, "potion_hud");

        ui.header("Display")
            .toggle("Visible", getData()::isVisible, getData()::setVisible)
            .cycle("Mode", Mode.values(), () -> mode, (value) -> mode = value)
            .cycle("Layout", Layout.values(), () -> layout, (value) -> layout = value)
            .toggle("Hide Vanilla Effects", () -> hideVanilla, (value) -> hideVanilla = value)

            .header("Show")
            .toggle("Name", () -> showName, (value) -> showName = value)
            .toggle("Level", () -> showLevel, (value) -> showLevel = value)
            .toggle("Time Left", () -> showDuration, (value) -> showDuration = value)
            .toggle("Good Effects", () -> showBeneficial, (value) -> showBeneficial = value)
            .toggle("Bad Effects", () -> showHarmful, (value) -> showHarmful = value)

            .header("Running Out Warning")
            .toggle("Warning", () -> warnEnabled, (value) -> warnEnabled = value)
            .slider("Warn Under", 1, 60, "s", () -> warnSeconds, (value) -> warnSeconds = value)

            .header("Style")
            .toggle("Background", () -> showBackground, (value) -> showBackground = value)
            .toggle("Border", () -> showBorder, (value) -> showBorder = value)
            .slider("Opacity", 0, 100, "%", () -> backgroundOpacity, (value) -> backgroundOpacity = value)

            .header("Colors (hex)")
            .color("Background", () -> backgroundColor, (value) -> backgroundColor = value)
            .color("Border", () -> borderColor, (value) -> borderColor = value)
            .color("Text", () -> textColor, (value) -> textColor = value)
            .color("Warning", () -> warnColor, (value) -> warnColor = value);

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

    public enum Mode implements HudUi.Labeled {
        DETAILED("Detailed"),
        COMPACT("Icons Only");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum Layout implements HudUi.Labeled {
        VERTICAL("Vertical"),
        HORIZONTAL("Horizontal");

        private final String label;

        Layout(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }
}
