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
import org.loveroo.fireclient.modules.keystrokes.ClickTracker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Keystrokes
 *
 * Draws the keys you are holding right now: WASD, space, sneak, sprint and the
 * two mouse buttons, with clicks per second on the mouse keys.
 *
 * The press animation is what makes it feel smooth. Each key owns a value that
 * eases towards 1 while held and back to 0 while released, instead of snapping
 * on the frame the key goes down. That value drives the background, the text
 * color and a small shrink of the key, so a tap reads as a quick pulse rather
 * than a flicker.
 *
 * The easing is frame rate independent: the amount moved per frame is derived
 * from the frame delta, so the animation takes the same wall clock time at
 * 30fps as it does at 240fps.
 */
public class KeystrokesModule extends ModuleBase {

    private static final Color color = Color.fromRGB(0x8FD3FF);

    // key ids, also the index into the animation array
    private static final int W = 0;
    private static final int A = 1;
    private static final int S = 2;
    private static final int D = 3;
    private static final int SPACE = 4;
    private static final int SNEAK = 5;
    private static final int SPRINT = 6;
    private static final int LMB = 7;
    private static final int RMB = 8;
    private static final int KEY_COUNT = 9;

    private static final int KEY_SIZE = 20;
    private static final int GAP = 2;
    private static final int SPACE_HEIGHT = 8;
    private static final float CPS_SCALE = 0.5f;

    private static final String[] LABELS = { "W", "A", "S", "D", "SPACE", "SNEAK", "SPRINT", "LMB", "RMB" };

    /**
     * Pre-built strings for the click counters.
     *
     * CPS is a small number and the same handful of values are redrawn every
     * frame, so the strings are built once instead of allocating a new one per
     * key per frame. Anything past the table falls back to String.valueOf.
     */
    private static final int CPS_CACHE_MAX = 99;
    private static final String[] CPS_STRINGS = new String[CPS_CACHE_MAX + 1];

    static {
        for(var i = 0; i <= CPS_CACHE_MAX; i++) {
            CPS_STRINGS[i] = Integer.toString(i);
        }
    }

    private static String cpsString(int cps) {
        if(cps >= 0 && cps <= CPS_CACHE_MAX) {
            return CPS_STRINGS[cps];
        }

        return String.valueOf(cps);
    }

    // fraction of the remaining distance covered per frame at 20fps
    private static final float PRESS_SPEED = 0.55f;
    private static final float RELEASE_SPEED = 0.35f;

    // below this the key is treated as settled, so it stops nudging forever
    private static final float SNAP_EPSILON = 0.004f;

    @JsonOption(name = "show_space")
    private boolean showSpace = true;

    @JsonOption(name = "show_sneak_sprint")
    private boolean showSneakSprint = false;

    @JsonOption(name = "show_mouse")
    private boolean showMouse = true;

    @JsonOption(name = "show_cps")
    private boolean showCps = true;

    @JsonOption(name = "animate")
    private boolean animate = true;

    @JsonOption(name = "press_scale")
    private boolean pressScale = true;

    @JsonOption(name = "rainbow")
    private boolean rainbow = false;

    @JsonOption(name = "rainbow_speed")
    private int rainbowSpeed = 1;

    @JsonOption(name = "animation_speed")
    private int animationSpeed = 100;

    @JsonOption(name = "show_background")
    private boolean showBackground = true;

    @JsonOption(name = "show_border")
    private boolean showBorder = true;

    @JsonOption(name = "show_shadow")
    private boolean showShadow = false;

    @JsonOption(name = "show_text_shadow")
    private boolean showTextShadow = true;

    @JsonOption(name = "bg_opacity")
    private int backgroundOpacity = 70;

    @JsonOption(name = "bg_color")
    private String backgroundColor = "000000";

    @JsonOption(name = "pressed_bg_color")
    private String pressedBackgroundColor = "8FD3FF";

    @JsonOption(name = "border_color")
    private String borderColor = "3C3C46";

    @JsonOption(name = "text_color")
    private String textColor = "FFFFFF";

    @JsonOption(name = "pressed_text_color")
    private String pressedTextColor = "000000";

    /**
     * Eased press amount per key, 0 (released) to 1 (fully held).
     *
     * Allocated once: the key set is fixed, so the draw loop never allocates.
     */
    private final float[] press = new float[KEY_COUNT];

    private int cachedLeftCps = 0;
    private int cachedRightCps = 0;

    // Colors resolved once per frame. Rainbow shifts them with time, so they
    // cannot be cached across frames, but within a frame they are constant and
    // every key would otherwise re-parse the same config strings 5 times.
    private int releasedBackground = 0;
    private int heldBackground = 0;
    private int borderColorArgb = 0;
    private int releasedText = 0;
    private int heldText = 0;
    private int backgroundAlpha = 0;

    /**
     * Resolves the four config colors for this frame.
     *
     * Called once at the top of draw() rather than per key: the parse is a
     * memoized hash lookup, but doing it 45 times a frame for 9 keys that all
     * share the same config strings is pure waste.
     */
    private void resolveFrameColors() {
        releasedBackground = color(backgroundColor, 0xFF000000);
        heldBackground = color(pressedBackgroundColor, 0xFF8FD3FF);
        borderColorArgb = color(borderColor, 0xFF3C3C46);
        releasedText = color(textColor, 0xFFFFFFFF);
        heldText = color(pressedTextColor, 0xFF000000);

        backgroundAlpha = Math.round(Math.clamp(backgroundOpacity, 0, 100) * 2.55f);
    }

    public KeystrokesModule() {
        super(new ModuleData("keystrokes", "\uD83D\uDD18", color,
            "Keystrokes", "Shows the keys you are pressing, with a smooth animation and clicks per second"));

        getData().setVisible(true);
        getData().setWidth(gridWidth());
        getData().setHeight(gridHeight());

        // Positions are stored as a fraction of the screen, so these are
        // (x / 640, y / 360) on a reference 640x360 window. Bottom left, sitting
        // just above the hotbar.
        getData().setDefaultPosX(2, 640);
        getData().setDefaultPosY(280, 360);

        var toggleBind = new Keybind("toggle_keystrokes",
                Text.translatable("fireclient.keybind.generic.toggle.name"),
                Text.translatable("fireclient.keybind.generic.toggle_visibility.description", getData().getShownName()),
                true, null,
                () -> getData().setVisible(!getData().isVisible()), null);

        FireClientside.getKeybindManager().registerKeybind(toggleBind);
    }

    @Override
    public void postLoad() {
        ClickTracker.reset();
    }

    // ------------------------------------------------------------------
    // layout
    // ------------------------------------------------------------------

    private static int gridWidth() {
        return (KEY_SIZE * 3) + (GAP * 2);
    }

    private static int halfWidth() {
        return (gridWidth() - GAP) / 2;
    }

    private int gridHeight() {
        // WASD block, space, then the optional sneak/sprint and mouse rows
        var rows = (KEY_SIZE * 2) + (GAP * 2);

        if(showSpace) {
            rows += SPACE_HEIGHT + GAP;
        }

        if(showSneakSprint) {
            rows += KEY_SIZE + GAP;
        }

        if(showMouse) {
            rows += KEY_SIZE + GAP;
        }

        return rows - GAP;
    }

    private static boolean pressed(KeyBinding key) {
        return key != null && key.isPressed();
    }

    /**
     * Eases one key towards its target.
     *
     * {@code speed} is the fraction of the remaining distance covered in a
     * 1/20s frame, converted to this frame's delta so the animation runs at the
     * same speed regardless of frame rate.
     */
    private float ease(int id, boolean isPressed, float delta) {
        var target = (isPressed) ? 1.0f : 0.0f;
        var current = press[id];

        if(!animate) {
            return press[id] = target;
        }

        // a delta of 0 (paused game) would freeze the animation, so floor it
        var frames = Math.max(delta, 1.0f / 240.0f);
        frames *= (animationSpeed / 100.0f);

        var speed = (isPressed) ? PRESS_SPEED : RELEASE_SPEED;
        var amount = 1.0f - (float)Math.pow(1.0f - speed, frames);

        var next = current + (target - current) * amount;

        if(Math.abs(next - target) < SNAP_EPSILON) {
            next = target;
        }

        return press[id] = next;
    }

    // ------------------------------------------------------------------
    // colors
    // ------------------------------------------------------------------

    /**
     * Rainbow shifts the hue over time, everything else reads the config.
     *
     * Both pressed and released colors shift by the same amount, so a key still
     * reads as "held" while the whole thing cycles.
     */
    private int color(String hex, int fallback) {
        if(!rainbow) {
            return HudUtil.parseColor(hex, fallback);
        }

        var base = HudUtil.parseColor(hex, fallback);
        var time = Util.getMeasuringTimeMs() / 1000.0f;

        var hue = (time * Math.max(0.05f, rainbowSpeed * 0.1f)) % 1.0f;
        if(hue < 0.0f) {
            hue += 1.0f;
        }

        return (base & 0xFF000000) | (rainbowColor(hue) & 0xFFFFFF);
    }

    /**
     * Hue (0 to 1) to a packed 0xRRGGBB color, the usual six sector hue wheel.
     */
    private static int rainbowColor(float hue) {
        var h = (hue - (float)Math.floor(hue)) * 6.0f;
        var sector = (int)h;
        var f = h - sector;

        var rising = Math.round(255 * f);
        var falling = 255 - rising;

        return switch(sector % 6) {
            case 0 -> (rising << 16);
            case 1 -> (rising << 8) | falling;
            case 2 -> falling;
            case 3 -> (falling << 16);
            case 4 -> (falling << 8) | rising;
            default -> rising;
        } & 0xFFFFFF;
    }

    /**
     * Background color for a key at a given press amount, with the configured
     * opacity applied last so it stays constant across the whole animation.
     */
    private int backgroundFor(float amount) {
        var blended = HudUtil.lerpColor(releasedBackground, heldBackground, amount);

        return HudUtil.withAlpha(blended, backgroundAlpha);
    }

    private int textFor(float amount) {
        return HudUtil.lerpColor(releasedText, heldText, amount);
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
        var options = client.options;

        var delta = ticks.getDynamicDeltaTicks();

        resolveFrameColors();

        // read the counters once per frame, both keys show the same reading
        cachedLeftCps = ClickTracker.getLeftCps();
        cachedRightCps = ClickTracker.getRightCps();

        var w = ease(W, pressed(options.forwardKey), delta);
        var a = ease(A, pressed(options.leftKey), delta);
        var s = ease(S, pressed(options.backKey), delta);
        var d = ease(D, pressed(options.rightKey), delta);
        var space = ease(SPACE, pressed(options.jumpKey), delta);
        var sneak = ease(SNEAK, pressed(options.sneakKey), delta);
        var sprint = ease(SPRINT, pressed(options.sprintKey), delta);
        var lmb = ease(LMB, pressed(options.attackKey), delta);
        var rmb = ease(RMB, pressed(options.useKey), delta);

        transform(context.getMatrices());

        var col1 = KEY_SIZE + GAP;
        var row = 0;

        // WASD
        drawKey(context, W, w, col1, row, KEY_SIZE, KEY_SIZE, false, -1);
        row += KEY_SIZE + GAP;

        drawKey(context, A, a, 0, row, KEY_SIZE, KEY_SIZE, false, -1);
        drawKey(context, S, s, col1, row, KEY_SIZE, KEY_SIZE, false, -1);
        drawKey(context, D, d, col1 + KEY_SIZE + GAP, row, KEY_SIZE, KEY_SIZE, false, -1);
        row += KEY_SIZE + GAP;

        if(showSpace) {
            drawKey(context, SPACE, space, 0, row, gridWidth(), SPACE_HEIGHT, true, -1);
            row += SPACE_HEIGHT + GAP;
        }

        if(showSneakSprint) {
            drawKey(context, SNEAK, sneak, 0, row, halfWidth(), KEY_SIZE, false, -1);
            drawKey(context, SPRINT, sprint, halfWidth() + GAP, row, halfWidth(), KEY_SIZE, false, -1);
            row += KEY_SIZE + GAP;
        }

        if(showMouse) {
            // mouse keys are never a bare strip: they show the button name, and
            // the click counter underneath when that is turned on
            var leftCps = (showCps) ? cachedLeftCps : -1;
            var rightCps = (showCps) ? cachedRightCps : -1;

            drawKey(context, LMB, lmb, 0, row, halfWidth(), KEY_SIZE, false, leftCps);
            drawKey(context, RMB, rmb, halfWidth() + GAP, row, halfWidth(), KEY_SIZE, false, rightCps);
        }

        endTransform(context.getMatrices());

        // The rows are toggleable at runtime, so the height has to follow them.
        // Refreshing here (rather than only in the constructor) keeps the HUD
        // editor hitbox and outline matching what is actually drawn.
        getData().setHeight(gridHeight());
    }

    /**
     * Draws one key at a local position.
     *
     * {@code amount} is the eased press value and drives the colors, while
     * {@code shrink} scales the key down slightly around its center so a press
     * has some weight to it.
     */
    private void drawKey(DrawContext context, int id, float amount, int x, int y, int w, int h, boolean showBar, int cps) {
        var scale = 1.0f;

        if(pressScale && amount > 0.0f) {
            // shrink to 92% at full press
            scale = 1.0f - (0.08f * amount);
        }

        var matrix = context.getMatrices();
        var scaled = (scale != 1.0f);

        // The matrix does the shrinking for the whole key, box and text alike,
        // so everything below draws at the unscaled rect. Shrinking the rect as
        // well would apply the scale twice (a 20px key would render at ~17px
        // and the label would shrink with it).
        if(scaled) {
            var centerX = x + (w / 2);
            var centerY = y + (h / 2);

            matrix.pushMatrix();
            matrix.translate(centerX, centerY);
            matrix.scale(scale, scale);
            matrix.translate(-centerX, -centerY);
        }

        if(showBackground) {
            HudUtil.drawBox(context, x, y, w, h, true, backgroundFor(amount),
                    showBorder, borderColorArgb);
        }
        else if(showBorder) {
            HudUtil.drawBorder(context, x, y, w, h, borderColorArgb);
        }

        if(showShadow) {
            context.fill(x + 1, y + h, x + w + 1, y + h + 1, 0x40000000);
        }

        var text = MinecraftClient.getInstance().textRenderer;
        var label = LABELS[id];
        var labelColor = textFor(amount);

        if(showBar) {
            // the space bar is drawn as a strip rather than a centered label
            var stripX = x + 4;
            var stripW = w - 8;
            var stripY = y + (h / 2) - 1;

            if(showTextShadow) {
                context.fill(stripX + 1, stripY + 1, stripX + stripW + 1, stripY + 2, shadowOf(labelColor));
            }

            context.fill(stripX, stripY, stripX + stripW, stripY + 1, labelColor);
        }
        else if(cps >= 0) {
            // mouse keys stack the name above the click counter
            var nameX = x + ((w - text.getWidth(label)) / 2);
            var nameY = y + 2;

            context.drawText(text, label, nameX, nameY, labelColor, showTextShadow);

            var value = cpsString(cps);
            var valueW = Math.round(text.getWidth(value) * CPS_SCALE);
            var cpsY = y + h - (Math.round(text.fontHeight * CPS_SCALE)) - 2;

            HudUtil.drawScaledText(context, text, value,
                x + ((w - valueW) / 2.0f), cpsY, CPS_SCALE, labelColor);
        }
        else {
            var centeredX = x + ((w - text.getWidth(label)) / 2);
            var centeredY = y + (h / 2) - (text.fontHeight / 2);

            context.drawText(text, label, centeredX, centeredY, labelColor, showTextShadow);
        }

        if(scaled) {
            matrix.popMatrix();
        }
    }

    /**
     * A dimmed copy of a color, used for the offset shadow on the space bar.
     */
    private static int shadowOf(int argb) {
        return HudUtil.darken(HudUtil.withAlpha(argb, 0xFF), 0.55f);
    }

    // ------------------------------------------------------------------
    // config screen
    // ------------------------------------------------------------------

    @Override
    public List<ClickableWidget> getConfigScreen(Screen base) {
        var widgets = new ArrayList<ClickableWidget>();

        widgets.add(FireClientside.getKeybindManager().getKeybind("toggle_keystrokes").getRebindButton(5, base.height - 25, 120, 20));

        var ui = new HudUi(base, "keystrokes");

        ui.header("Layout")
            .toggle("Visible", getData()::isVisible, getData()::setVisible)
            .toggle("Space", () -> showSpace, (value) -> showSpace = value)
            .toggle("Sneak / Sprint", () -> showSneakSprint, (value) -> showSneakSprint = value)
            .toggle("Mouse Keys", () -> showMouse, (value) -> showMouse = value)
            .toggle("Clicks Per Second", () -> showCps, (value) -> showCps = value)

            .header("Animation")
            .toggle("Animate", () -> animate, (value) -> animate = value)
            .toggle("Press Scale", () -> pressScale, (value) -> pressScale = value)
            .slider("Speed", 25, 300, "%", () -> animationSpeed, (value) -> animationSpeed = value)

            .header("Style")
            .toggle("Background", () -> showBackground, (value) -> showBackground = value)
            .toggle("Border", () -> showBorder, (value) -> showBorder = value)
            .toggle("Drop Shadow", () -> showShadow, (value) -> showShadow = value)
            .toggle("Text Shadow", () -> showTextShadow, (value) -> showTextShadow = value)
            .slider("Opacity", 0, 100, "%", () -> backgroundOpacity, (value) -> backgroundOpacity = value)
            .toggle("Rainbow", () -> rainbow, (value) -> rainbow = value)
            .slider("Rainbow Speed", 1, 20, "", () -> rainbowSpeed, (value) -> rainbowSpeed = value)

            .header("Colors (hex)")
            .color("Background", () -> backgroundColor, (value) -> backgroundColor = value)
            .color("Pressed Background", () -> pressedBackgroundColor, (value) -> pressedBackgroundColor = value)
            .color("Border", () -> borderColor, (value) -> borderColor = value)
            .color("Text", () -> textColor, (value) -> textColor = value)
            .color("Pressed Text", () -> pressedTextColor, (value) -> pressedTextColor = value);

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
}
