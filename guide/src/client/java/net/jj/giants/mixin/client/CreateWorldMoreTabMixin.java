package net.jj.giants.mixin.client;

import net.jj.giants.client.admin.CreationClient;
import net.jj.giants.client.admin.CreationScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Create World screen's More tab gets "Giants…" under Game Rules, Experiments and Data Packs (ADMIN_BRIDGE.md
 * §18.6): the giants' preset picker and every one of their settings, before the world is made.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$MoreTab")
public abstract class CreateWorldMoreTabMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void giants$addButton(CreateWorldScreen screen, CallbackInfo ci) {
        Button b = Button.builder(CreationClient.buttonLabel(screen),
                        x -> Minecraft.getInstance().setScreen(new CreationScreen(screen)))
                .width(210)
                .build();
        b.setTooltip(Tooltip.create(Component.translatable("admin.jj_giants.creation.button.tip")));
        ((GridLayoutTabAccess) this).giants$layout().addChild(b, 3, 0);
    }
}
