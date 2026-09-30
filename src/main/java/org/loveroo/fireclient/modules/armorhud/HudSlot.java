package org.loveroo.fireclient.modules.armorhud;

import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Every item slot the Armor HUD can show.
 */
public enum HudSlot {

    HELMET("helmet", false),
    CHESTPLATE("chestplate", false),
    LEGGINGS("leggings", false),
    BOOTS("boots", false),
    MAIN_HAND("main_hand", true),
    OFF_HAND("off_hand", true);

    private final String id;
    private final boolean hand;

    // lazily created so we never touch item registries before they exist
    private ItemStack sample = null;

    HudSlot(String id, boolean hand) {
        this.id = id;
        this.hand = hand;
    }

    public String getId() {
        return id;
    }

    public boolean isHand() {
        return hand;
    }

    /**
     * A fake item shown in the HUD editor when the real slot is empty,
     * so the element can always be seen and positioned.
     */
    public ItemStack getSample() {
        if(sample == null) {
            sample = createSample();
        }

        return sample;
    }

    private ItemStack createSample() {
        return switch(this) {
            case HELMET -> damaged(Items.DIAMOND_HELMET, 120);
            case CHESTPLATE -> damaged(Items.DIAMOND_CHESTPLATE, 45);
            case LEGGINGS -> damaged(Items.DIAMOND_LEGGINGS, 300);
            case BOOTS -> damaged(Items.DIAMOND_BOOTS, 410); // low on purpose, shows the warning
            case MAIN_HAND -> damaged(Items.DIAMOND_SWORD, 150);
            case OFF_HAND -> new ItemStack(Items.GOLDEN_APPLE, 16);
        };
    }

    private static ItemStack damaged(ItemConvertible item, int damage) {
        var stack = new ItemStack(item);
        stack.setDamage(damage);

        return stack;
    }
}
