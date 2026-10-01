package org.loveroo.fireclient.modules.armorhud;

import java.util.List;

import org.loveroo.fireclient.client.FireClientside;
import org.loveroo.fireclient.data.Color;
import org.loveroo.fireclient.data.ModuleData;
import org.loveroo.fireclient.modules.ArmorHudModule;
import org.loveroo.fireclient.modules.ModuleBase;
import org.loveroo.fireclient.modules.hud.HudUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.RenderTickCounter;

/**
 * One independently draggable slot of the {@link ArmorHudModule}.
 * Only active when the Armor HUD is set to the "Independent" layout.
 * Hidden from the module list, all settings live in the Armor HUD.
 */
public class ArmorHudSlotModule extends ModuleBase {

    private static final Color color = Color.fromRGB(0x8FD3FF);

    private final ArmorHudModule parent;
    private final HudSlot slot;

    public ArmorHudSlotModule(ArmorHudModule parent, HudSlot slot, int index) {
        super(new ModuleData("armor_hud_" + slot.getId(), "\uD83D\uDEE1", color,
            "Armor HUD: " + slot.getLabel(), "The " + slot.getLabel().toLowerCase() + " slot of the Armor HUD (separate layout)"));

        this.parent = parent;
        this.slot = slot;

        getData().setWidth(22);
        getData().setHeight(22);

        getData().setDefaultPosX(40, 640);
        getData().setDefaultPosY(90 + (index * 26), 360);

        getData().setVisible(true);
        getData().setSkip(true);

        FireClientside.registerModule(this);
    }

    public void resetPosition() {
        getData().setRawPosX(getData().getDefaultPosX());
        getData().setRawPosY(getData().getDefaultPosY());
    }

    @Override
    public void draw(DrawContext context, RenderTickCounter ticks) {
        if(!canDraw() || !parent.isMasterVisible() || !parent.isSlotActive(slot)) {
            return;
        }

        var client = MinecraftClient.getInstance();
        if(client.player == null) {
            return;
        }

        var entry = parent.resolve(client, slot, HudUtil.isEditing());
        if(entry == null) {
            return;
        }

        var size = parent.measure(entry);

        transform(context.getMatrices());
        parent.drawCell(context, entry, 0, 0, size.w(), size.h(), ticks.getTickProgress(true));
        endTransform(context.getMatrices());

        getData().setWidth(size.w());
        getData().setHeight(size.h());
    }

    @Override
    public boolean isPointInside(int mouseX, int mouseY) {
        return parent.isSlotActive(slot) && super.isPointInside(mouseX, mouseY);
    }

    @Override
    public void drawOutline(DrawContext context) {
        if(!parent.isSlotActive(slot)) {
            return;
        }

        super.drawOutline(context);
    }

    @Override
    public List<ClickableWidget> getConfigScreen(Screen base) {
        return List.of();
    }

    @Override
    public void drawScreen(Screen base, DrawContext context, float delta) { }
}
