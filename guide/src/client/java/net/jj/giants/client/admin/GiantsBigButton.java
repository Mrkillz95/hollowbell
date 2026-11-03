package net.jj.giants.client.admin;

import net.jj.giants.admin.AdminBookItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * (Part 2C) The Create World screen's big Giants button on the Game tab: the admin book's picture, the giants' preset
 * as it is now and "Change…", drawn in the admin book's leather and gold so it can't be missed. Opens the same picker
 * as the More tab's Giants… button.
 */
public final class GiantsBigButton extends Button {
    private final Screen createWorld;
    private static final ItemStack BOOK = new ItemStack(AdminBookItem.BOOK);

    public GiantsBigButton(Screen createWorld) {
        super(0, 0, 210, 24, label(createWorld),
                b -> Minecraft.getInstance().setScreen(new CreationScreen(createWorld)), DEFAULT_NARRATION);
        this.createWorld = createWorld;
        setTooltip(Tooltip.create(Component.translatable("admin.jj_giants.creation.big.tip")));
    }

    static Component label(Screen createWorld) {
        return Component.translatable("admin.jj_giants.creation.big", CreationClient.presetLabel(createWorld));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        setMessage(label(createWorld));
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hot = isHoveredOrFocused() && active;
        // leather, a gold rule, brighter when pointed at
        g.fill(x, y, x + w, y + h, 0xFF140A0C);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hot ? 0xFF6A2440 : 0xFF4A1426);
        g.renderOutline(x + 2, y + 2, w - 4, h - 4, hot ? 0xFFFFF0A0 : 0xFFE8B840);
        g.fill(x + 3, y + 3, x + w - 3, y + 4, 0x30FFFFFF);
        g.renderItem(BOOK, x + 5, y + (h - 16) / 2);
        var font = Minecraft.getInstance().font;
        int tx = x + 25, tw = w - 30;
        String s = getMessage().getString();
        if (font.width(s) > tw) s = font.plainSubstrByWidth(s, tw - font.width("…")) + "…";
        g.drawString(font, s, tx + Math.max(0, (tw - font.width(s)) / 2), y + (h - 8) / 2, hot ? 0xFFFFF4C0
                : 0xFFF0D070, true);
    }
}
