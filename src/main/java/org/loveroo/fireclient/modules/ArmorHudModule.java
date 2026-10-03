package org.loveroo.fireclient.modules;

import java.util.ArrayList;
import java.util.List;

import org.loveroo.fireclient.client.FireClientside;
import org.loveroo.fireclient.data.Color;
import org.loveroo.fireclient.data.FireClientOption;
import org.loveroo.fireclient.data.JsonOption;
import org.loveroo.fireclient.data.ModuleData;
import org.loveroo.fireclient.keybind.Keybind;
import org.loveroo.fireclient.modules.armorhud.ArmorHudSlotModule;
import org.loveroo.fireclient.modules.armorhud.HudSlot;
import org.loveroo.fireclient.modules.hud.HudUi;
import org.loveroo.fireclient.modules.hud.HudUtil;
import org.loveroo.fireclient.screen.config.ModuleConfigScreen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Armor HUD (inspired by Inventory HUD+)
 *
 * Shows armor + held items with durability and stack counts.
 * Can be one bar, or every slot can be moved on its own (see {@link ArmorHudSlotModule}).
 */
public class ArmorHudModule extends ModuleBase {

    private static final Color color = Color.fromRGB(0x8FD3FF);

    private static final int ICON_SIZE = 16;
    private static final int PADDING = 3;
    private static final int TEXT_GAP = 3;

    // ---- layout ----
    @JsonOption(name = "layout")
    private Layout layout = Layout.UNIFIED;

    @JsonOption(name = "orientation")
    private Orientation orientation = Orientation.HORIZONTAL;

    // ---- slots ----
    @JsonOption(name = "show_helmet")
    private boolean showHelmet = true;

    @JsonOption(name = "show_chestplate")
    private boolean showChestplate = true;

    @JsonOption(name = "show_leggings")
    private boolean showLeggings = true;

    @JsonOption(name = "show_boots")
    private boolean showBoots = true;

    @JsonOption(name = "show_main_hand")
    private boolean showMainHand = true;

    @JsonOption(name = "show_off_hand")
    private boolean showOffHand = true;

    // ---- durability ----
    @JsonOption(name = "durability_style")
    private DurabilityStyle durabilityStyle = DurabilityStyle.NUMBERS_AND_BAR;

    @JsonOption(name = "number_format")
    private NumberFormat numberFormat = NumberFormat.EXACT;

    @JsonOption(name = "number_position")
    private NumberPosition numberPosition = NumberPosition.SIDE;

    @JsonOption(name = "low_warning")
    private boolean lowWarning = true;

    @JsonOption(name = "low_percent")
    private int lowPercent = 10;

    @JsonOption(name = "show_stack_count")
    private boolean showStackCount = true;

    // ---- style ----
    @JsonOption(name = "show_background")
    private boolean showBackground = true;

    @JsonOption(name = "show_border")
    private boolean showBorder = true;

    @JsonOption(name = "show_shadow")
    private boolean showShadow = true;

    @JsonOption(name = "accent")
    private boolean accent = true;

    @JsonOption(name = "accent_color")
    private String accentColor = "8FD3FF";

    @JsonOption(name = "bar_style")
    private BarStyle barStyle = BarStyle.GRADIENT;

    @JsonOption(name = "bar_height")
    private int barHeight = 2;

    @JsonOption(name = "spacing")
    private int spacing = 2;

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

    private final ArrayList<ArmorHudSlotModule> slotModules = new ArrayList<>();

    // Reused every frame by draw(). Layout is fixed at six slots, so the lists are
    // sized once and only cleared, rather than reallocated on each render.
    private static final int MAX_SLOTS = 6;

    private final ArrayList<Entry> drawEntries = new ArrayList<>(MAX_SLOTS);
    private final ArrayList<Size> drawSizes = new ArrayList<>(MAX_SLOTS);

    public ArmorHudModule() {
        super(new ModuleData("armor_hud", "\uD83D\uDEE1", color,
            "Armor HUD", "Shows your armor and held items with durability and stack counts. Use one bar, or move each slot on its own"));

        getData().setWidth(120);
        getData().setHeight(22);

        getData().setDefaultPosX(2, 640);
        getData().setDefaultPosY(330, 360);

        getData().setVisible(true);

        // one draggable module per slot, only used in the separate layout
        var index = 0;
        for(var slot : HudSlot.values()) {
            slotModules.add(new ArmorHudSlotModule(this, slot, index++));
        }

        var toggleBind = new Keybind("toggle_armor_hud",
                Text.translatable("fireclient.keybind.generic.toggle.name"),
                Text.translatable("fireclient.keybind.generic.toggle_visibility.description", getData().getShownName()),
                true, null,
                () -> getData().setVisible(!getData().isVisible()), null);

        FireClientside.getKeybindManager().registerKeybind(toggleBind);
    }

    // ------------------------------------------------------------------
    // state used by the slot modules
    // ------------------------------------------------------------------

    public boolean isIndependent() {
        return layout == Layout.INDEPENDENT;
    }

    public boolean isSlotEnabled(HudSlot slot) {
        return switch(slot) {
            case HELMET -> showHelmet;
            case CHESTPLATE -> showChestplate;
            case LEGGINGS -> showLeggings;
            case BOOTS -> showBoots;
            case MAIN_HAND -> showMainHand;
            case OFF_HAND -> showOffHand;
        };
    }

    /**
     * Whether a per-slot module should be drawn / draggable right now
     */
    public boolean isSlotActive(HudSlot slot) {
        if(!isIndependent() || !isSlotEnabled(slot)) {
            return false;
        }

        return getData().isVisible() || FireClientside.getSetting(FireClientOption.SHOW_HIDDEN_MODULES) != 0;
    }

    public boolean isMasterVisible() {
        return getData().isVisible();
    }

    private boolean showsNumbers() {
        return durabilityStyle == DurabilityStyle.NUMBERS_AND_BAR || durabilityStyle == DurabilityStyle.NUMBERS;
    }

    private boolean showsBar() {
        return durabilityStyle == DurabilityStyle.NUMBERS_AND_BAR || durabilityStyle == DurabilityStyle.BAR;
    }

    private static boolean hasDurability(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageable() && stack.getMaxDamage() > 0;
    }

    // ------------------------------------------------------------------
    // shared drawing (used by the bar and the slot modules)
    // ------------------------------------------------------------------

    public record Entry(HudSlot slot, ItemStack stack, boolean sample) { }

    public record Size(int w, int h) { }

    /**
     * Finds what to show for a slot, or null if nothing should be drawn
     */
    public Entry resolve(MinecraftClient client, HudSlot slot, boolean editing) {
        var player = client.player;
        if(player == null) {
            return null;
        }

        var stack = switch(slot) {
            case HELMET -> player.getEquippedStack(EquipmentSlot.HEAD);
            case CHESTPLATE -> player.getEquippedStack(EquipmentSlot.CHEST);
            case LEGGINGS -> player.getEquippedStack(EquipmentSlot.LEGS);
            case BOOTS -> player.getEquippedStack(EquipmentSlot.FEET);
            case MAIN_HAND -> player.getMainHandStack();
            case OFF_HAND -> player.getOffHandStack();
        };

        if(!stack.isEmpty()) {
            return new Entry(slot, stack, false);
        }

        // so the slot can still be seen and moved in the editor
        if(editing) {
            return new Entry(slot, slot.getSample(), true);
        }

        return null;
    }

    private String numberText(int remaining, int max) {
        return switch(numberFormat) {
            case EXACT -> remaining + "/" + max;
            case PERCENT -> Math.round((remaining / (double)max) * 100.0) + "%";
        };
    }

    /**
     * Cell size. Width is based on the item's max durability so it never jumps while durability changes
     */
    public Size measure(Entry entry) {
        var text = MinecraftClient.getInstance().textRenderer;
        var stack = entry.stack();

        var durability = hasDurability(stack);
        var numbers = durability && showsNumbers();
        var bar = durability && showsBar();

        var textWidth = (numbers) ? text.getWidth(numberText(stack.getMaxDamage(), stack.getMaxDamage())) : 0;

        if(numbers && numberPosition == NumberPosition.BELOW) {
            var w = (PADDING * 2) + Math.max(ICON_SIZE, textWidth);
            var h = (PADDING * 2) + ICON_SIZE + 1 + 9 + ((bar) ? (1 + barHeight) : 0);

            return new Size(w, h);
        }

        var w = (PADDING * 2) + ICON_SIZE + ((numbers) ? (TEXT_GAP + textWidth) : 0);
        var h = (PADDING * 2) + ICON_SIZE + ((bar) ? (1 + barHeight) : 0);

        return new Size(w, h);
    }

    public void drawCell(DrawContext context, Entry entry, int x, int y, int w, int h, float tickProgress) {
        var client = MinecraftClient.getInstance();
        var text = client.textRenderer;
        var stack = entry.stack();

        var panelBackground = HudUtil.parseColorWithOpacity(backgroundColor, 0xFF000000, backgroundOpacity);
        var panelBorder = HudUtil.parseColor(borderColor, 0xFF3C3C46);

        if(stack.isEmpty()) {
            HudUtil.drawPanel(context, x, y, w, h, showBackground, panelBackground, showBorder, panelBorder, showShadow, 0);
            return;
        }

        var durability = hasDurability(stack);
        var numbers = durability && showsNumbers();
        var bar = durability && showsBar();
        var below = (numberPosition == NumberPosition.BELOW);

        var max = stack.getMaxDamage();
        var remaining = durability ? Math.max(0, max - stack.getDamage()) : 0;
        var ratio = durability ? Math.min(1.0, remaining / (double)max) : 1.0;

        var warn = durability && lowWarning && (ratio * 100.0) < lowPercent && HudUtil.flashOn();
        var warningColor = HudUtil.parseColor(warnColor, 0xFFFF5555);

        // the accent stripe is colored by durability, so a glance is enough to spot a broken piece
        var accentStripe = 0;
        if(accent) {
            accentStripe = (warn) ? warningColor : (durability ? HudUtil.durabilityColor((float)ratio) : HudUtil.parseColor(accentColor, 0xFF8FD3FF));
        }

        HudUtil.drawPanel(context, x, y, w, h, showBackground, panelBackground, showBorder, panelBorder, showShadow, accentStripe);

        // with the numbers under the item, the item is centered in the cell
        var iconX = (numbers && below) ? (x + ((w - ICON_SIZE) / 2)) : (x + PADDING);
        var iconY = y + PADDING;

        context.drawItem(stack, iconX, iconY);

        // item cooldown (ender pearls, shields, etc)
        if(!entry.sample() && client.player != null) {
            var progress = client.player.getItemCooldownManager().getCooldownProgress(stack, tickProgress);

            if(progress > 0.0f) {
                var height = (int)Math.ceil(progress * ICON_SIZE);
                context.fill(iconX, iconY + ICON_SIZE - height, iconX + ICON_SIZE, iconY + ICON_SIZE, 0x809F9F9F);
            }
        }

        // stack size on held blocks / consumables
        if(showStackCount && entry.slot().isHand() && stack.getMaxCount() > 1 && stack.getCount() > 1) {
            var countText = String.valueOf(stack.getCount());
            context.drawText(text, countText, iconX + ICON_SIZE + 1 - text.getWidth(countText), iconY + 9, 0xFFFFFFFF, true);
        }

        if(!durability) {
            return;
        }

        if(numbers) {
            var message = numberText(remaining, max);
            var color = (warn) ? warningColor : HudUtil.parseColor(textColor, 0xFFFFFFFF);

            if(below) {
                context.drawText(text, message, x + ((w - text.getWidth(message)) / 2), iconY + ICON_SIZE + 1, color, true);
            }
            else {
                context.drawText(text, message, iconX + ICON_SIZE + TEXT_GAP, iconY + 4, color, true);
            }
        }

        // the bar always sits at the very bottom of the cell
        if(bar) {
            var barX = x + PADDING;
            var barWidth = w - (PADDING * 2);
            var barY = y + h - PADDING - barHeight;

            var fill = (barStyle == BarStyle.GRADIENT) ? HudUtil.durabilityColor((float)ratio) : (0xFF000000 | stack.getItemBarColor());
            if(warn) {
                fill = warningColor;
            }

            HudUtil.drawBar(context, barX, barY, barWidth, barHeight, (float)ratio, fill);
        }
    }

    // ------------------------------------------------------------------
    // bar layout
    // ------------------------------------------------------------------

    @Override
    public void draw(DrawContext context, RenderTickCounter ticks) {
        if(layout != Layout.UNIFIED || !canDraw()) {
            return;
        }

        var client = MinecraftClient.getInstance();
        if(client.player == null) {
            return;
        }

        var editing = HudUtil.isEditing();

        var entries = drawEntries;
        entries.clear();

        for(var slot : HudSlot.values()) {
            if(!isSlotEnabled(slot)) {
                continue;
            }

            var entry = resolve(client, slot, editing);
            if(entry != null) {
                entries.add(entry);
            }
        }

        if(entries.isEmpty()) {
            getData().setWidth(ICON_SIZE);
            getData().setHeight(ICON_SIZE);

            return;
        }

        var sizes = drawSizes;
        sizes.clear();

        var maxWidth = 0;
        var maxHeight = 0;

        for(var entry : entries) {
            var size = measure(entry);
            sizes.add(size);

            maxWidth = Math.max(maxWidth, size.w());
            maxHeight = Math.max(maxHeight, size.h());
        }

        var horizontal = (orientation == Orientation.HORIZONTAL);
        var tickProgress = ticks.getTickProgress(true);

        transform(context.getMatrices());

        var x = 0;
        var y = 0;

        for(var i = 0; i < entries.size(); i++) {
            var w = (horizontal) ? sizes.get(i).w() : maxWidth;
            var h = (horizontal) ? maxHeight : sizes.get(i).h();

            drawCell(context, entries.get(i), x, y, w, h, tickProgress);

            if(horizontal) {
                x += w + spacing;
            }
            else {
                y += h + spacing;
            }
        }

        endTransform(context.getMatrices());

        getData().setWidth((horizontal) ? (x - spacing) : maxWidth);
        getData().setHeight((horizontal) ? maxHeight : (y - spacing));
    }

    @Override
    public boolean isPointInside(int mouseX, int mouseY) {
        return layout == Layout.UNIFIED && super.isPointInside(mouseX, mouseY);
    }

    @Override
    public void drawOutline(DrawContext context) {
        if(layout != Layout.UNIFIED) {
            return;
        }

        super.drawOutline(context);
    }

    // ------------------------------------------------------------------
    // config screen
    // ------------------------------------------------------------------

    @Override
    public void moduleConfigPressed(ButtonWidget button) {
        var client = MinecraftClient.getInstance();

        // every slot module is shown too, so slots can be dragged in the separate layout
        var modules = new ArrayList<ModuleBase>(slotModules);
        modules.add(this);

        client.setScreen(new ModuleConfigScreen(getData().getShownName(), getData().getDescription(), modules));
    }

    @Override
    public List<ClickableWidget> getConfigScreen(Screen base) {
        var widgets = new ArrayList<ClickableWidget>();

        widgets.add(FireClientside.getKeybindManager().getKeybind("toggle_armor_hud").getRebindButton(5, base.height - 25, 120, 20));

        var ui = new HudUi(base, "armor_hud");

        ui.header("Layout")
            .toggle("Visible", getData()::isVisible, getData()::setVisible)
            .cycle("Layout", Layout.values(), () -> layout, (value) -> layout = value)
            .cycle("Direction", Orientation.values(), () -> orientation, (value) -> orientation = value)
            .button("Reset Positions", this::resetPositions)

            .header("Show")
            .toggle("Helmet", () -> showHelmet, (value) -> showHelmet = value)
            .toggle("Chestplate", () -> showChestplate, (value) -> showChestplate = value)
            .toggle("Leggings", () -> showLeggings, (value) -> showLeggings = value)
            .toggle("Boots", () -> showBoots, (value) -> showBoots = value)
            .toggle("Main Hand", () -> showMainHand, (value) -> showMainHand = value)
            .toggle("Off Hand", () -> showOffHand, (value) -> showOffHand = value)
            .toggle("Stack Count", () -> showStackCount, (value) -> showStackCount = value)

            .header("Durability")
            .cycle("Display", DurabilityStyle.values(), () -> durabilityStyle, (value) -> durabilityStyle = value)
            .cycle("Numbers", NumberFormat.values(), () -> numberFormat, (value) -> numberFormat = value)
            .cycle("Numbers Position", NumberPosition.values(), () -> numberPosition, (value) -> numberPosition = value)
            .toggle("Low Warning", () -> lowWarning, (value) -> lowWarning = value)
            .slider("Warn Below", 1, 50, "%", () -> lowPercent, (value) -> lowPercent = value)

            .header("Style")
            .toggle("Background", () -> showBackground, (value) -> showBackground = value)
            .toggle("Border", () -> showBorder, (value) -> showBorder = value)
            .toggle("Drop Shadow", () -> showShadow, (value) -> showShadow = value)
            .toggle("Accent Stripe", () -> accent, (value) -> accent = value)
            .slider("Opacity", 0, 100, "%", () -> backgroundOpacity, (value) -> backgroundOpacity = value)
            .slider("Spacing", 0, 8, "px", () -> spacing, (value) -> spacing = value)

            .header("Durability Bar")
            .cycle("Bar Color", BarStyle.values(), () -> barStyle, (value) -> barStyle = value)
            .slider("Bar Height", 1, 5, "px", () -> barHeight, (value) -> barHeight = value)

            .header("Colors (hex)")
            .color("Background", () -> backgroundColor, (value) -> backgroundColor = value)
            .color("Border", () -> borderColor, (value) -> borderColor = value)
            .color("Text", () -> textColor, (value) -> textColor = value)
            .color("Warning", () -> warnColor, (value) -> warnColor = value)
            .color("Accent", () -> accentColor, (value) -> accentColor = value);

        widgets.add(ui.build());
        return widgets;
    }

    private void resetPositions() {
        getData().setRawPosX(getData().getDefaultPosX());
        getData().setRawPosY(getData().getDefaultPosY());

        for(var module : slotModules) {
            module.resetPosition();
        }
    }

    @Override
    public void drawScreen(Screen base, DrawContext context, float delta) {
        drawScreenHeader(context, base.width / 2, 26);
    }

    @Override
    public void closeScreen(Screen screen) {
        FireClientside.saveConfig();
    }

    public enum Layout implements HudUi.Labeled {
        UNIFIED("One Bar"),
        INDEPENDENT("Separate");

        private final String label;

        Layout(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum Orientation implements HudUi.Labeled {
        HORIZONTAL("Horizontal"),
        VERTICAL("Vertical");

        private final String label;

        Orientation(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum DurabilityStyle implements HudUi.Labeled {
        NUMBERS_AND_BAR("Numbers + Bar"),
        NUMBERS("Numbers Only"),
        BAR("Bar Only"),
        HIDDEN("Hidden");

        private final String label;

        DurabilityStyle(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum NumberFormat implements HudUi.Labeled {
        EXACT("245/435"),
        PERCENT("56%");

        private final String label;

        NumberFormat(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum BarStyle implements HudUi.Labeled {
        GRADIENT("Durability"),
        ITEM("Item Color");

        private final String label;

        BarStyle(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum NumberPosition implements HudUi.Labeled {
        SIDE("Side"),
        BELOW("Below");

        private final String label;

        NumberPosition(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }
}
