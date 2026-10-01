package org.loveroo.fireclient.modules.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.loveroo.fireclient.screen.base.ScrollableWidget;
import org.loveroo.fireclient.screen.widgets.ToggleButtonWidget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Builds the scrollable settings list used by the HUD modules.
 *
 * Labels are plain English text. They double as the fallback for a language key
 * (<code>fireclient.module.&lt;prefix&gt;.&lt;label as snake_case&gt;</code>), so the page always shows
 * readable text, even if a language file has no entry for it.
 *
 * Widgets are packed two per row. Headers, colors and buttons take a full row.
 */
public class HudUi {

    private static final int LIST_WIDTH = 340;
    private static final int HALF_WIDTH = 160;
    private static final int FULL_WIDTH = 330;
    private static final int ROW_HEIGHT = 24;

    /**
     * Lets an enum give the text shown on its cycle button
     */
    public interface Labeled {

        String label();
    }

    private final Screen base;
    private final String prefix;
    private final MinecraftClient client = MinecraftClient.getInstance();

    private final ArrayList<ScrollableWidget.ElementEntry> entries = new ArrayList<>();
    private final ArrayList<ClickableWidget> pending = new ArrayList<>();

    public HudUi(Screen base, String prefix) {
        this.base = base;
        this.prefix = prefix;
    }

    private MutableText t(String label) {
        var key = label.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return Text.translatableWithFallback("fireclient.module." + prefix + "." + key, label);
    }

    private int centerX() {
        return base.width / 2;
    }

    private int leftX() {
        return centerX() - 165;
    }

    private int rightX() {
        return centerX() + 5;
    }

    private int nextHalfX() {
        return (pending.isEmpty()) ? leftX() : rightX();
    }

    private void addHalf(ClickableWidget widget) {
        pending.add(widget);

        if(pending.size() >= 2) {
            flush();
        }
    }

    private void flush() {
        if(pending.isEmpty()) {
            return;
        }

        entries.add(new ScrollableWidget.ElementEntry(new ArrayList<>(pending)));
        pending.clear();
    }

    public HudUi header(String label) {
        flush();

        var text = new TextWidget(t(label).formatted(Formatting.GOLD), client.textRenderer);
        text.setPosition(leftX(), 7);

        entries.add(new ScrollableWidget.ElementEntry(List.<ClickableWidget>of(text)));
        return this;
    }

    public HudUi toggle(String label, ToggleButtonWidget.ToggleButtonBuilder.GetValue get, ToggleButtonWidget.ToggleButtonBuilder.SetValue set) {
        var button = new ToggleButtonWidget.ToggleButtonBuilder(t(label))
            .getValue(get)
            .setValue(set)
            .dimensions(nextHalfX(), 0, HALF_WIDTH, 20)
            .build();

        addHalf(button);
        return this;
    }

    public <E extends Enum<E> & Labeled> HudUi cycle(String label, E[] values, Supplier<E> get, Consumer<E> set) {
        var button = ButtonWidget.builder(cycleText(label, get.get()), (pressed) -> {
                var next = values[(get.get().ordinal() + 1) % values.length];

                set.accept(next);
                pressed.setMessage(cycleText(label, next));
            })
            .dimensions(nextHalfX(), 0, HALF_WIDTH, 20)
            .build();

        addHalf(button);
        return this;
    }

    private MutableText cycleText(String label, Labeled value) {
        return t(label).append(": ").append(Text.literal(value.label()));
    }

    public HudUi slider(String label, int min, int max, String suffix, IntSupplier get, IntConsumer set) {
        addHalf(new IntSlider(nextHalfX(), 0, HALF_WIDTH, 20, t(label), suffix, min, max, get.getAsInt(), set));
        return this;
    }

    /**
     * A full row: label on the left, a color preview and a 6 digit hex code (RRGGBB) on the right
     */
    public HudUi color(String label, Supplier<String> get, Consumer<String> set) {
        flush();

        var text = new TextWidget(t(label), client.textRenderer);
        text.setPosition(leftX(), 7);

        var swatch = new Swatch(rightX() + 105, 2, 16, 16, HudUtil.parseColor(get.get(), 0xFFFFFFFF));

        var field = new TextFieldWidget(client.textRenderer, 64, 18, t(label));
        field.setPosition(rightX() + 128, 1);
        field.setMaxLength(6);
        field.setTextPredicate((value) -> value.matches("[0-9a-fA-F]*"));
        field.setText(get.get());

        field.setChangedListener((value) -> {
            if(value.length() == 6) {
                set.accept(value.toUpperCase());
                swatch.setColor(HudUtil.parseColor(value, 0xFFFFFFFF));
            }
        });

        entries.add(new ScrollableWidget.ElementEntry(List.<ClickableWidget>of(text, swatch, field)));
        return this;
    }

    public HudUi button(String label, Runnable action) {
        flush();

        var button = ButtonWidget.builder(t(label), (pressed) -> action.run())
            .dimensions(leftX(), 0, FULL_WIDTH, 20)
            .build();

        entries.add(new ScrollableWidget.ElementEntry(List.<ClickableWidget>of(button)));
        return this;
    }

    public ScrollableWidget build() {
        flush();

        var height = Math.max(60, base.height - 80);

        var list = new ScrollableWidget(base, LIST_WIDTH, height, 0, ROW_HEIGHT, entries);
        list.setPosition(centerX() - (LIST_WIDTH / 2), 40);

        return list;
    }

    /**
     * A small filled square that previews a color. Not clickable.
     */
    private static class Swatch extends ClickableWidget {

        private int color;

        public Swatch(int x, int y, int width, int height, int color) {
            super(x, y, width, height, Text.empty());

            this.color = color;
            this.active = false;
        }

        public void setColor(int color) {
            this.color = color;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            var x = getX();
            var y = getY();

            context.fill(x, y, x + width, y + height, 0xFFFFFFFF);
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { }
    }

    private static class IntSlider extends SliderWidget {

        private final Text label;
        private final String suffix;
        private final int min;
        private final int max;
        private final IntConsumer set;

        public IntSlider(int x, int y, int width, int height, Text label, String suffix, int min, int max, int value, IntConsumer set) {
            super(x, y, width, height, Text.empty(), (double)(value - min) / (double)(max - min));

            this.label = label;
            this.suffix = suffix;
            this.min = min;
            this.max = max;
            this.set = set;

            updateMessage();
        }

        private int current() {
            return min + (int)Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            // called from the super constructor, before the fields exist
            if(label == null) {
                return;
            }

            setMessage(label.copy().append(": " + current() + suffix));
        }

        @Override
        protected void applyValue() {
            if(set == null) {
                return;
            }

            set.accept(current());
        }
    }
}
