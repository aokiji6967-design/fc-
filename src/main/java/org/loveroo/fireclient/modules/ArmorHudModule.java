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
 * Shows armor + held items with durability, stack counts and warnings.
 * Can be one unified bar, or every slot can be moved on its own (see {@link ArmorHudSlotModule}).
 */
public class ArmorHudModule extends ModuleBase {

    private static final Color color = Color.fromRGB(0x8FD3FF);

    private static final int ICON_SIZE = 16;
    private static final int TEXT_GAP = 3;
    private static final int BAR_HEIGHT = 2;

    // ---- layout ----
    @JsonOption(name = "layout")
    private Layout layout = Layout.UNIFIED;

    @JsonOption(name = "orientation")
    private Orientation orientation = Orientation.HORIZONTAL;

    @JsonOption(name = "spacing")
    private int spacing = 2;

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

    @JsonOption(name = "show_empty")
    private boolean showEmpty = false;

    // ---- durability ----
    @JsonOption(name = "durability_text")
    private DurabilityText durabilityText = DurabilityText.EXACT;

    @JsonOption(name = "show_bar")
    private boolean showBar = true;

    @JsonOption(name = "bar_gradient")
    private boolean barGradient = true;

    @JsonOption(name = "hide_full_durability")
    private boolean hideFullDurability = false;

    @JsonOption(name = "low_warning")
    private boolean lowWarning = true;

    @JsonOption(name = "low_flash")
    private boolean lowFlash = true;

    @JsonOption(name = "low_percent")
    private int lowPercent = 10;

    // ---- items ----
    @JsonOption(name = "show_stack_count")
    private boolean showStackCount = true;

    @JsonOption(name = "count_inventory")
    private boolean countInventory = false;

    @JsonOption(name = "show_cooldown")
    private boolean showCooldown = true;

    // ---- style ----
    @JsonOption(name = "show_background")
    private boolean showBackground = true;

    @JsonOption(name = "show_border")
    private boolean showBorder = true;

    @JsonOption(name = "cell_padding")
    private int cellPadding = 3;

    @JsonOption(name = "color_background")
    private String backgroundColor = "99000000";

    @JsonOption(name = "color_border")
    private String borderColor = "FF3C3C46";

    @JsonOption(name = "color_text")
    private String textColor = "FFFFFFFF";

    @JsonOption(name = "color_warn")
    private String warnColor = "FFFF5555";

    @JsonOption(name = "color_bar")
    private String barColor = "FF55FF55";

    @JsonOption(name = "color_bar_background")
    private String barBackgroundColor = "FF000000";

    private final ArrayList<ArmorHudSlotModule> slotModules = new ArrayList<>();

    public ArmorHudModule() {
        super(new ModuleData("armor_hud", "\uD83D\uDEE1", color));

        getData().setWidth(120);
        getData().setHeight(22);

        getData().setDefaultPosX(2, 640);
        getData().setDefaultPosY(330, 360);

        getData().setVisible(true);

        // one draggable module per slot, only used in the independent layout
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

    // ------------------------------------------------------------------
    // shared drawing (used by the unified bar and the slot modules)
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

        if(editing) {
            return new Entry(slot, slot.getSample(), true);
        }

        if(showEmpty) {
            return new Entry(slot, ItemStack.EMPTY, false);
        }

        return null;
    }

    /**
     * Cell size. Width is based on the item's max durability so it never jumps while durability changes
     */
    public Size measure(Entry entry) {
        var text = MinecraftClient.getInstance().textRenderer;
        var stack = entry.stack();

        var w = (cellPadding * 2) + ICON_SIZE;
        var h = (cellPadding * 2) + ICON_SIZE + ((showBar) ? (BAR_HEIGHT + 1) : 0);

        if(!stack.isEmpty() && stack.isDamageable()) {
            var max = stack.getMaxDamage();

            var widest = switch(durabilityText) {
                case EXACT -> max + "/" + max;
                case PERCENT -> "100%";
                case OFF -> "";
            };

            if(!widest.isEmpty()) {
                w += TEXT_GAP + text.getWidth(widest);
            }
        }

        return new Size(w, h);
    }

    public void drawCell(DrawContext context, Entry entry, int x, int y, int w, int h, float tickProgress) {
        var client = MinecraftClient.getInstance();
        var text = client.textRenderer;
        var stack = entry.stack();

        HudUtil.drawBox(context, x, y, w, h,
            showBackground, HudUtil.parseColor(backgroundColor, 0x99000000, false),
            showBorder, HudUtil.parseColor(borderColor, 0xFF3C3C46, false));

        if(stack.isEmpty()) {
            return;
        }

        var iconX = x + cellPadding;
        var iconY = y + cellPadding;

        context.drawItem(stack, iconX, iconY);

        // item cooldown (ender pearls, shields, etc)
        if(showCooldown && !entry.sample() && client.player != null) {
            var progress = client.player.getItemCooldownManager().getCooldownProgress(stack, tickProgress);

            if(progress > 0.0f) {
                var height = (int)Math.ceil(progress * ICON_SIZE);
                context.fill(iconX, iconY + ICON_SIZE - height, iconX + ICON_SIZE, iconY + ICON_SIZE, 0x809F9F9F);
            }
        }

        // stack size on held blocks / consumables
        if(showStackCount && entry.slot().isHand() && stack.getMaxCount() > 1) {
            var count = getDisplayCount(client, stack, entry.sample());

            if(count > 1) {
                var countText = String.valueOf(count);
                context.drawText(text, countText, iconX + ICON_SIZE + 1 - text.getWidth(countText), iconY + 9, 0xFFFFFFFF, true);
            }
        }

        // durability
        if(!stack.isDamageable() || stack.getMaxDamage() <= 0) {
            return;
        }

        if(hideFullDurability && stack.getDamage() <= 0) {
            return;
        }

        var max = stack.getMaxDamage();
        var remaining = Math.max(0, max - stack.getDamage());
        var ratio = Math.min(1.0, remaining / (double)max);

        var low = lowWarning && (ratio * 100.0) < lowPercent;
        var showWarn = low && (!lowFlash || HudUtil.flashOn());

        var normalColor = HudUtil.parseColor(textColor, 0xFFFFFFFF, true);
        var warningColor = HudUtil.parseColor(warnColor, 0xFFFF5555, true);

        var message = switch(durabilityText) {
            case EXACT -> remaining + "/" + max;
            case PERCENT -> Math.round(ratio * 100.0) + "%";
            case OFF -> null;
        };

        if(message != null) {
            context.drawText(text, message, iconX + ICON_SIZE + TEXT_GAP, iconY + 4, (showWarn) ? warningColor : normalColor, true);
        }

        if(showBar) {
            var barX = x + cellPadding;
            var barWidth = w - (cellPadding * 2);
            var barY = iconY + ICON_SIZE + 1;

            context.fill(barX, barY, barX + barWidth, barY + BAR_HEIGHT, HudUtil.parseColor(barBackgroundColor, 0xFF000000, true));

            var filled = (int)Math.round(barWidth * ratio);
            if(filled > 0) {
                var fillColor = (barGradient) ? (0xFF000000 | stack.getItemBarColor()) : HudUtil.parseColor(barColor, 0xFF55FF55, true);
                context.fill(barX, barY, barX + filled, barY + BAR_HEIGHT, (showWarn) ? warningColor : fillColor);
            }
        }
    }

    private int getDisplayCount(MinecraftClient client, ItemStack stack, boolean sample) {
        if(!countInventory || sample || client.player == null) {
            return stack.getCount();
        }

        var player = client.player;
        var total = 0;

        for(var inventoryStack : player.getInventory().getMainStacks()) {
            if(ItemStack.areItemsEqual(inventoryStack, stack)) {
                total += inventoryStack.getCount();
            }
        }

        var offHand = player.getOffHandStack();
        if(ItemStack.areItemsEqual(offHand, stack)) {
            total += offHand.getCount();
        }

        return total;
    }

    // ------------------------------------------------------------------
    // unified bar
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

        var entries = new ArrayList<Entry>();
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

        var sizes = new ArrayList<Size>();
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

        // every slot module is shown too, so slots can be dragged in the independent layout
        var modules = new ArrayList<ModuleBase>(slotModules);
        modules.add(this);

        client.setScreen(new ModuleConfigScreen(getData().getShownName(), getData().getDescription(), modules));
    }

    @Override
    public List<ClickableWidget> getConfigScreen(Screen base) {
        var widgets = new ArrayList<ClickableWidget>();

        widgets.add(FireClientside.getKeybindManager().getKeybind("toggle_armor_hud").getRebindButton(5, base.height - 25, 120, 20));

        var ui = new HudUi(base, "armor_hud");

        ui.header("section_layout")
            .toggle("visible", getData()::isVisible, getData()::setVisible)
            .cycle("layout", Layout.values(), () -> layout, (value) -> layout = value)
            .cycle("orientation", Orientation.values(), () -> orientation, (value) -> orientation = value)
            .slider("spacing", 0, 12, () -> spacing, (value) -> spacing = value)
            .button("reset_positions", this::resetPositions)

            .header("section_slots")
            .toggle("helmet", () -> showHelmet, (value) -> showHelmet = value)
            .toggle("chestplate", () -> showChestplate, (value) -> showChestplate = value)
            .toggle("leggings", () -> showLeggings, (value) -> showLeggings = value)
            .toggle("boots", () -> showBoots, (value) -> showBoots = value)
            .toggle("main_hand", () -> showMainHand, (value) -> showMainHand = value)
            .toggle("off_hand", () -> showOffHand, (value) -> showOffHand = value)
            .toggle("show_empty", () -> showEmpty, (value) -> showEmpty = value)

            .header("section_durability")
            .cycle("durability_text", DurabilityText.values(), () -> durabilityText, (value) -> durabilityText = value)
            .toggle("show_bar", () -> showBar, (value) -> showBar = value)
            .toggle("bar_gradient", () -> barGradient, (value) -> barGradient = value)
            .toggle("hide_full_durability", () -> hideFullDurability, (value) -> hideFullDurability = value)
            .toggle("low_warning", () -> lowWarning, (value) -> lowWarning = value)
            .toggle("low_flash", () -> lowFlash, (value) -> lowFlash = value)
            .slider("low_percent", 1, 50, () -> lowPercent, (value) -> lowPercent = value)

            .header("section_items")
            .toggle("show_stack_count", () -> showStackCount, (value) -> showStackCount = value)
            .toggle("count_inventory", () -> countInventory, (value) -> countInventory = value)
            .toggle("show_cooldown", () -> showCooldown, (value) -> showCooldown = value)

            .header("section_style")
            .toggle("show_background", () -> showBackground, (value) -> showBackground = value)
            .toggle("show_border", () -> showBorder, (value) -> showBorder = value)
            .slider("cell_padding", 0, 8, () -> cellPadding, (value) -> cellPadding = value)

            .header("section_colors")
            .color("color_background", () -> backgroundColor, (value) -> backgroundColor = value, 0x99000000)
            .color("color_border", () -> borderColor, (value) -> borderColor = value, 0xFF3C3C46)
            .color("color_text", () -> textColor, (value) -> textColor = value, 0xFFFFFFFF)
            .color("color_warn", () -> warnColor, (value) -> warnColor = value, 0xFFFF5555)
            .color("color_bar", () -> barColor, (value) -> barColor = value, 0xFF55FF55)
            .color("color_bar_background", () -> barBackgroundColor, (value) -> barBackgroundColor = value, 0xFF000000);

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

    public enum Layout {
        UNIFIED,
        INDEPENDENT
    }

    public enum Orientation {
        HORIZONTAL,
        VERTICAL
    }

    public enum DurabilityText {
        EXACT,
        PERCENT,
        OFF
    }
}
