package net.jj.mountain.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.mountain.net.CodexPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The one line in the book that is asked twice. The first page says plainly what it costs and what it does; the
 * second will not light up until the word is typed out. Nothing is sent until both have been got through, and
 * the server checks the lot again before he so much as lifts a leg.
 */
public class DoomScreen extends Screen {
    private final @Nullable Screen back;
    private int held;
    private @Nullable Button go;

    public DoomScreen(@Nullable Screen back) {
        super(Component.translatable("codex.mountain_breathes.end"));
        this.back = back;
    }

    private static String key(String what) { return "codex.mountain_breathes.end" + what; }

    @Override
    protected void init() {
        int w = 180, y = this.height / 2 + 54;
        addRenderableWidget(Button.builder(Component.translatable(key("_back")),
                b -> onClose()).bounds(this.width / 2 - w - 4, y, w, 20).build());
        go = Button.builder(Component.translatable(key("_on")).withStyle(ChatFormatting.RED),
                b -> Minecraft.getInstance().setScreen(new Word(back))).bounds(this.width / 2 + 4, y, w, 20).build();
        go.active = held >= 60;
        addRenderableWidget(go);
    }

    @Override
    public void tick() {
        held++;
        if (go != null) go.active = held >= 60;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g, mx, my, partial);
        super.render(g, mx, my, partial);
        int y = this.height / 2 - 98;
        g.drawCenteredString(this.font, Component.translatable(key("")).withStyle(ChatFormatting.DARK_RED),
                this.width / 2, y, 0xFFFF4040);
        y += 18;
        for (int i = 1; i <= 7; i++) {
            Component line = Component.translatable(key("_warn") + i);
            // the last line is the one that says none of this happens if you just kill him
            g.drawCenteredString(this.font, line, this.width / 2, y + (i == 7 ? 6 : 0),
                    i == 7 ? 0xFF88CC88 : i >= 5 ? 0xFFFF6666 : 0xFFDDDDDD);
            y += 13;
        }
        if (go != null && !go.active)
            g.drawCenteredString(this.font, Component.translatable("codex.mountain_breathes.end_wait",
                    (60 - held) / 20 + 1), this.width / 2, this.height / 2 + 36, 0xFF999999);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(back); }
    @Override public boolean isPauseScreen() { return false; }

    /** the second asking: the word has to be typed out before the line will light */
    public static class Word extends Screen {
        private final @Nullable Screen back;
        private @Nullable EditBox box;
        private @Nullable Button go;

        public Word(@Nullable Screen back) {
            super(Component.translatable("codex.mountain_breathes.end"));
            this.back = back;
        }

        private static String key(String what) { return "codex.mountain_breathes.end" + what; }

        private static String word() { return Component.translatable(key("_word")).getString(); }

        @Override
        protected void init() {
            box = new EditBox(this.font, this.width / 2 - 80, this.height / 2 - 4, 160, 20,
                    Component.translatable(key("_word")));
            box.setMaxLength(24);
            box.setResponder(t -> { if (go != null) go.active = t.trim().equalsIgnoreCase(word()); });
            addRenderableWidget(box);
            setInitialFocus(box);
            int w = 180, y = this.height / 2 + 54;
            addRenderableWidget(Button.builder(Component.translatable(key("_back")),
                    b -> onClose()).bounds(this.width / 2 - w - 4, y, w, 20).build());
            go = Button.builder(Component.translatable(key("_do")).withStyle(ChatFormatting.RED), b -> {
                ClientPlayNetworking.send(new CodexPayload(CodexPayload.UNMAKE, CodexPayload.UNMAKE_MEANT));
                Minecraft.getInstance().setScreen(null);
            }).bounds(this.width / 2 + 4, y, w, 20).build();
            go.active = false;
            addRenderableWidget(go);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partial) {
            renderBackground(g, mx, my, partial);
            super.render(g, mx, my, partial);
            g.drawCenteredString(this.font, Component.translatable(key("")).withStyle(ChatFormatting.DARK_RED),
                    this.width / 2, this.height / 2 - 60, 0xFFFF4040);
            g.drawCenteredString(this.font, Component.translatable("codex.mountain_breathes.end_type", word()),
                    this.width / 2, this.height / 2 - 34, 0xFFDDDDDD);
            g.drawCenteredString(this.font, Component.translatable(key("_last")).withStyle(ChatFormatting.RED),
                    this.width / 2, this.height / 2 + 28, 0xFFFF6666);
        }

        @Override public void onClose() { Minecraft.getInstance().setScreen(back); }
        @Override public boolean isPauseScreen() { return false; }
    }
}
