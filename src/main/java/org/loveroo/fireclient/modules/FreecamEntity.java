package org.loveroo.fireclient.modules;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

public class FreecamEntity extends Entity {

    public FreecamEntity(World world) {
        super(EntityType.ARMOR_STAND, world);
        this.noClip = true;
        this.setInvisible(true);
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public boolean collides() {
        return false;
    }

    @Override
    public boolean isImmuneToExplosions() {
        return true;
    }
}
