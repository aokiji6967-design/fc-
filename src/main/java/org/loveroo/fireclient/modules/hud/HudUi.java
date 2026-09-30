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
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Builds the scrollable settings list used by the Armor HUD and Potion HUD config pages.
 * Widgets are packed two per row; headers, colors and buttons take a full row.
 *
 * Language keys are looked up as <code>fireclient.module.&lt;prefix&gt;.&lt;key&gt;</code>
 */
public class HudUi {

    private static final int LIST_WIDTH = 340;
    private static final int HALF_WIDTH = 160;
    private static final int FULL_WIDTH = 330;
    private static final int ROW_HEIGHT = 24;

    private final Screen base;
    private final String prefix;
    private final MinecraftClient client = MinecraftClient.getInstance();

    private final ArrayList<ScrollableWidget.ElementEntry> entries = new ArrayList<>();
    private final ArrayList<ClickableWidget> pending = new ArrayList<>();

    public HudUi(Screen base, String prefix) {
        this.base = base;
        this.prefix = prefix;
    }

    public MutableText t(String key) {
        return Text.translatable("fireclient.module." + prefix + "." + key);
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

    public HudUi header(String key) {
        flush();

        var text = new TextWidget(t(key).formatted(Formatting.GOLD), client.textRenderer);
        text.setPosition(leftX(), 7);

        entries.add(new ScrollableWidget.ElementEntry(List.<ClickableWidget>of(text)));
        return this;
    }

    public HudUi toggle(String key, ToggleButtonWidget.ToggleButtonBuilder.GetValue get, ToggleButtonWidget.ToggleButtonBuilder.SetValue set) {
        var button = new ToggleButtonWidget.ToggleButtonBuilder(t(key))
            .getValue(get)
            .setValue(set)
            .dimensions(nextHalfX(), 0, HALF_WIDTH, 20)
            .build();

        addHalf(button);
        return this;
    }

    public <E extends Enum<E>> HudUi cycle(String key, E[] values, Supplier<E> get, Consumer<E> set) {
        var button = ButtonWidget.builder(cycleText(key, get.get()), (pressed) -> {
                var next = values[(get.get().ordinal() + 1) % values.length];

                set.accept(next);
                pressed.setMessage(cycleText(key, next));
            })
            .dimensions(nextHalfX(), 0, HALF_WIDTH, 20)
            .build();

        addHalf(button);
        return this;
    }

    private MutableText cycleText(String key, Enum<?> value) {
        return t(key).append(": ").append(t(key + "." + value.name().toLowerCase()));
    }

    public HudUi slider(String key, int min, int max, IntSupplier get, IntConsumer set) {
        addHalf(new IntSlider(nextHalfX(), 0, HALF_WIDTH, 20, t(key), min, max, get.getAsInt(), set));
        return this;
    }

    /**
     * A full row with a label and an AARRGGBB hex field
     */
    public HudUi color(String key, Supplier<String> get, Consumer<String> set, int fallback) {
        flush();

        var label = new TextWidget(t(key), client.textRenderer);
        label.setPosition(leftX(), 7);

        var field = new TextFieldWidget(client.textRenderer, 100, 18, t(key));
        field.setPosition(rightX() + 60, 1);
        field.setMaxLength(8);
        field.setTextPredicate((text) -> text.matches("[0-9a-fA-F]*"));
        field.setText(get.get());
        field.setEditableColor(HudUtil.parseColor(get.get(), fallback, true));

        field.setChangedListener((text) -> {
            if(text.length() == 8 || text.length() == 6) {
                set.accept(text.toUpperCase());
                field.setEditableColor(HudUtil.parseColor(text, fallback, true));
            }
        });

        entries.add(new ScrollableWidget.ElementEntry(List.<ClickableWidget>of(label, field)));
        return this;
    }

    public HudUi button(String key, Runnable action) {
        flush();

        var button = ButtonWidget.builder(t(key), (pressed) -> action.run())
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

    private static class IntSlider extends SliderWidget {

        private final Text label;
        private final int min;
        private final int max;
        private final IntConsumer set;

        public IntSlider(int x, int y, int width, int height, Text label, int min, int max, int value, IntConsumer set) {
            super(x, y, width, height, Text.empty(), (double)(value - min) / (double)(max - min));

            this.label = label;
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
            if(label == null) {
                return;
            }

            setMessage(label.copy().append(": " + current()));
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
