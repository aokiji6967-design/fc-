package org.loveroo.fireclient.mixin.modules.blockoutline;

import net.minecraft.client.render.*;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.loveroo.fireclient.client.FireClientside;
import org.loveroo.fireclient.modules.BlockOutlineModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class ChangeBlockOutlineMixin {

    @Unique
    private VertexConsumerProvider.Immediate consumer;

    @Inject(method = "renderTargetBlockOutline", at = @At("HEAD"))
    private void getConsumer(VertexConsumerProvider.Immediate immediate, MatrixStack matrices, boolean renderBlockOutline, WorldRenderState renderStates, CallbackInfo ci) {
        consumer = immediate;
    }

    @ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int changeColor(int original) {
        var outline = (BlockOutlineModule) FireClientside.getModule("block_outline");
        if(outline == null || !outline.getData().isEnabled()) {
            return original;
        }

        return outline.getOutline();
    }

    @ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private VertexConsumer changeLayer(VertexConsumer original) {
        var outline = (BlockOutlineModule) FireClientside.getModule("block_outline");
        if(outline == null || !outline.getData().isEnabled()) {
            return original;
        }

        var layer = (outline.isThick()) ? RenderLayers.secondaryBlockOutline() : RenderLayers.lines();
        return consumer.getBuffer(layer);
    }

    // The render layer alone does not change the outline thickness, the width is the last argument of
    // drawBlockOutline and is what vanilla bumps to 7.0f for its own thick outline.
    @ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float changeLineWidth(float original) {
        var outline = (BlockOutlineModule) FireClientside.getModule("block_outline");
        if(outline == null || !outline.getData().isEnabled() || !outline.isThick()) {
            return original;
        }

        return BlockOutlineModule.THICK_LINE_WIDTH;
    }
}
