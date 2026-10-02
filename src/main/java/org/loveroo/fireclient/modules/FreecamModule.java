package org.loveroo.fireclient.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.loveroo.fireclient.client.FireClientside;
import org.loveroo.fireclient.data.Color;
import org.loveroo.fireclient.data.JsonOption;
import org.loveroo.fireclient.data.ModuleData;
import org.loveroo.fireclient.keybind.Keybind;
import org.loveroo.fireclient.screen.widgets.ToggleButtonWidget;

import java.util.ArrayList;
import java.util.List;

public class FreecamModule extends ModuleBase {

    private static final Color color = Color.fromRGB(0x8FB4FF);

    @JsonOption(name = "speed")
    private double speed = 0.5;

    private FreecamEntity freecamEntity = null;

    public FreecamModule() {
        super(new ModuleData("freecam", "✈", color, "Freecam", "Client-side free camera (client-only)"));

        getData().setGuiElement(false);

        var toggleBind = new Keybind("toggle_freecam",
                Text.translatable("fireclient.keybind.freecam.toggle.name"),
                Text.translatable("fireclient.keybind.freecam.toggle.description", getData().getShownName()),
                true, null,
                this::toggleFreecam, null);

        FireClientside.getKeybindManager().registerKeybind(toggleBind);
    }

    private void toggleFreecam() {
        var client = MinecraftClient.getInstance();

        if(client.player == null || client.world == null) {
            return;
        }

        if(getData().isEnabled()) {
            stopFreecam();
            getData().setEnabled(false);
        }
        else {
            startFreecam();
            getData().setEnabled(true);
        }
    }

    private void startFreecam() {
        var client = MinecraftClient.getInstance();
        if(client.player == null || client.world == null) return;

        freecamEntity = new FreecamEntity(client.world);
        freecamEntity.updatePosition(client.player.getX(), client.player.getY(), client.player.getZ());
        freecamEntity.setYaw(client.player.getYaw());
        freecamEntity.setPitch(client.player.getPitch());

        client.setCameraEntity(freecamEntity);
    }

    private void stopFreecam() {
        var client = MinecraftClient.getInstance();
        if(client.player == null) return;

        client.setCameraEntity(client.player);
        freecamEntity = null;
    }

    @Override
    public void update(MinecraftClient client) {
        if(!getData().isEnabled() || freecamEntity == null) {
            return;
        }

        var options = client.options;

        double forward = options.forwardKey.isPressed() ? 1.0 : 0.0;
        forward -= options.backKey.isPressed() ? 1.0 : 0.0;

        double strafe = options.leftKey.isPressed() ? 1.0 : 0.0;
        strafe -= options.rightKey.isPressed() ? 1.0 : 0.0;

        double up = options.jumpKey.isPressed() ? 1.0 : 0.0;
        up -= options.sneakKey.isPressed() ? 1.0 : 0.0;

        // basic movement relative to camera yaw
        float yaw = freecamEntity.getYaw();
        double yawRad = Math.toRadians(yaw);

        double dx = (forward * -Math.sin(yawRad) + strafe * Math.cos(yawRad)) * speed;
        double dz = (forward * Math.cos(yawRad) + strafe * Math.sin(yawRad)) * speed;
        double dy = up * speed;

        if(dx != 0 || dy != 0 || dz != 0) {
            freecamEntity.updatePosition(freecamEntity.getX() + dx, freecamEntity.getY() + dy, freecamEntity.getZ() + dz);
        }

        // keep freecam entity orientation in sync with the camera entity
        var cam = client.getCameraEntity();
        if(cam != null) {
            freecamEntity.setYaw(cam.getYaw());
            freecamEntity.setPitch(cam.getPitch());
        }
    }

    @Override
    public List<ClickableWidget> getConfigScreen(Screen base) {
        var widgets = new ArrayList<ClickableWidget>();

        widgets.add(getToggleEnableButton(base.width/2 - 60, base.height/2 - 10));

        widgets.add(new ToggleButtonWidget.ToggleButtonBuilder(Text.translatable("fireclient.module.freecam.speed.name"))
            .getValue(() -> { return speed != 0.5; })
            .setValue((value) -> { speed = value ? 1.0 : 0.5; })
            .position(base.width/2 - 60, base.height/2 + 20)
            .tooltip(Tooltip.of(Text.translatable("fireclient.module.freecam.speed.tooltip")))
            .build());

        return widgets;
    }
}
