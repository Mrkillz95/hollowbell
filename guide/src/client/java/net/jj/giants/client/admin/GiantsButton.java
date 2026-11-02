package net.jj.giants.client.admin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Create World screen's "Giants…" button (More tab): opens the picker, and always reads the choice as it is now
 * ("Giants: Far Frontier…"), even when the screen keeps its tabs while the picker was open.
 */
public final class GiantsButton extends Button {
    private final Screen createWorld;

    public GiantsButton(Screen createWorld) {
        super(0, 0, 210, 20, CreationClient.buttonLabel(createWorld),
                b -> Minecraft.getInstance().setScreen(new CreationScreen(createWorld)), DEFAULT_NARRATION);
        this.createWorld = createWorld;
        setTooltip(Tooltip.create(Component.translatable("admin.jj_giants.creation.button.tip")));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        setMessage(CreationClient.buttonLabel(createWorld));
        super.renderWidget(g, mx, my, partial);
    }
}
