package org.loveroo.fireclient.mixin.modules.keystrokes;

import org.loveroo.fireclient.modules.keystrokes.ClickTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;

/**
 * Feeds raw mouse presses into {@link ClickTracker} so the Keystrokes module
 * can show clicks per second.
 *
 * Only the press edge (GLFW_PRESS) is counted, so holding a button down stays
 * at a single click.
 *
 * Injected at HEAD, never cancels. MouseMixin also injects at HEAD and is
 * cancellable, so a TAIL inject here would be skipped whenever a keybind
 * swallows a mouse button and the click count would silently under-report.
 * Running at HEAD means the count is taken before anything can cancel it, and
 * since this handler never cancels it cannot affect the click itself.
 */
@Mixin(Mouse.class)
public class KeystrokesClickMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void trackClick(long window, MouseInput input, int action, CallbackInfo info) {
        if(action != GLFW.GLFW_PRESS) {
            return;
        }

        if(input.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
            ClickTracker.registerLeftClick();
        }
        else if(input.button() == GLFW.GLFW_MOUSE_BUTTON_2) {
            ClickTracker.registerRightClick();
        }
    }
}
