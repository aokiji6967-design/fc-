package org.loveroo.fireclient.modules;

import java.util.ArrayList;
import java.util.List;

import org.loveroo.fireclient.FireClient;
import org.loveroo.fireclient.client.FireClientside;
import org.loveroo.fireclient.data.Color;
import org.loveroo.fireclient.data.JsonOption;
import org.loveroo.fireclient.data.ModuleData;
import org.loveroo.fireclient.modules.hud.HudUi;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.sound.SoundCategory;

/**
 * Applies a low-spec profile to vanilla's own graphics options.
 *
 * This is the only part of FireClient that can meaningfully raise the framerate,
 * because render distance, chunk simulation and particles are handled by the vanilla
 * renderer rather than by the mod. Every setting is optional, so the profile can be
 * trimmed to taste, and a disabled setting leaves the player's own choice alone.
 */
public class PerformanceModule extends ModuleBase {

    private static final Color color = Color.fromRGB(0x7FE0A0);

    @JsonOption(name = "apply_on_launch")
    private boolean applyOnLaunch = false;

    @JsonOption(name = "render_distance")
    private int renderDistance = 6;

    @JsonOption(name = "simulation_distance")
    private int simulationDistance = 4;

    @JsonOption(name = "limit_fps")
    private boolean limitFps = false;

    @JsonOption(name = "max_fps")
    private int maxFps = 60;

    @JsonOption(name = "graphics_fast")
    private boolean graphicsFast = true;

    @JsonOption(name = "clouds_off")
    private boolean cloudsOff = true;

    @JsonOption(name = "particles_minimal")
    private boolean particlesMinimal = true;

    @JsonOption(name = "entity_distance")
    private boolean entityDistance = true;

    @JsonOption(name = "entity_shadows_off")
    private boolean entityShadowsOff = true;

    @JsonOption(name = "mipmap_off")
    private boolean mipmapOff = true;

    @JsonOption(name = "ao_off")
    private boolean aoOff = false;

    @JsonOption(name = "sound_off")
    private boolean soundOff = false;

    public PerformanceModule() {
        super(new ModuleData("performance", "\uD83D\uDE81", color,
            "Performance", "Applies a low-spec profile to the game's own graphics settings"));

        // Not a HUD element, it only changes settings.
        getData().setGuiElement(false);

        ClientLifecycleEvents.CLIENT_STARTED.register((client) -> {
            if(applyOnLaunch && getData().isEnabled()) {
                apply(client);
            }
        });
    }

    /**
     * Writes the profile into the game's options and saves them.
     */
    public void apply(MinecraftClient client) {
        var options = client.options;

        try {
            if(renderDistance > 0) {
                options.getViewDistance().setValue(renderDistance);
            }

            if(simulationDistance > 0) {
                options.getSimulationDistance().setValue(simulationDistance);
            }

            if(limitFps && maxFps > 0) {
                // vsync and a framerate cap fight each other, so vsync has to go first
                options.getEnableVsync().setValue(false);
                options.getMaxFps().setValue(Math.clamp(maxFps, 10, GameOptions.MAX_FPS_LIMIT));
            }

            if(graphicsFast) {
                options.getPreset().setValue(GraphicsMode.FAST);
            }

            if(cloudsOff) {
                options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
            }

            if(particlesMinimal) {
                options.getParticles().setValue(ParticlesMode.MINIMAL);
            }

            if(entityDistance) {
                // Entities past half the usual range stop being rendered and ticked
                options.getEntityDistanceScaling().setValue(0.5);
            }

            if(entityShadowsOff) {
                options.getEntityShadows().setValue(false);
            }

            if(aoOff) {
                options.getAo().setValue(false);
            }

            if(mipmapOff) {
                // Saves texture memory, which matters on a 4GB machine
                options.getMipmapLevels().setValue(0);
            }

            if(soundOff) {
                for(var category : SoundCategory.values()) {
                    options.getSoundVolumeOption(category).setValue(0.0);
                }
            }

            options.write();
        }
        catch(Exception e) {
            FireClient.LOGGER.error("Failed to apply the performance profile!", e);
        }
    }

    /**
     * Puts the settings back to values that will not fight the player, so their own
     * settings can be found again in the vanilla options screen.
     */
    public void revert(MinecraftClient client) {
        var options = client.options;

        try {
            options.getEnableVsync().setValue(true);
            options.getMaxFps().setValue(GameOptions.MAX_FPS_LIMIT);
            options.getEntityDistanceScaling().setValue(1.0);
            options.getEntityShadows().setValue(true);
            options.getMipmapLevels().setValue(4);

            options.write();
        }
        catch(Exception e) {
            FireClient.LOGGER.error("Failed to revert the performance profile!", e);
        }
    }

    @Override
    public void draw(DrawContext context, RenderTickCounter ticks) { }

    @Override
    public void openScreen(Screen screen) {
        FireClientside.saveConfig();
    }

    @Override
    public List<ClickableWidget> getConfigScreen(Screen base) {
        var widgets = new ArrayList<ClickableWidget>();

        var ui = new HudUi(base, "performance");

        ui.header("Profile")
            .toggle("Apply On Launch", () -> applyOnLaunch, (value) -> applyOnLaunch = value)
            .button("Apply Now", () -> apply(MinecraftClient.getInstance()))
            .button("Revert", () -> revert(MinecraftClient.getInstance()))

            .header("Distances")
            .slider("Render Distance", 2, 16, " chunks", () -> renderDistance, (value) -> renderDistance = value)
            .slider("Simulation Distance", 2, 16, " chunks", () -> simulationDistance, (value) -> simulationDistance = value)
            .toggle("Half Entity Range", () -> entityDistance, (value) -> entityDistance = value)

            .header("Graphics")
            .toggle("Fast Graphics", () -> graphicsFast, (value) -> graphicsFast = value)
            .toggle("Clouds Off", () -> cloudsOff, (value) -> cloudsOff = value)
            .toggle("Entity Shadows Off", () -> entityShadowsOff, (value) -> entityShadowsOff = value)
            .toggle("Smooth Lighting Off", () -> aoOff, (value) -> aoOff = value)
            .toggle("Mipmaps Off", () -> mipmapOff, (value) -> mipmapOff = value)

            .header("Effects")
            .toggle("Minimal Particles", () -> particlesMinimal, (value) -> particlesMinimal = value)
            .toggle("Mute Everything", () -> soundOff, (value) -> soundOff = value)

            .header("Framerate")
            .toggle("Cap Framerate", () -> limitFps, (value) -> limitFps = value)
            .slider("Max FPS", 20, 260, "", () -> maxFps, (value) -> maxFps = value);

        widgets.add(ui.build());
        return widgets;
    }
}