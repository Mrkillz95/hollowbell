package net.jj.giants.mixin.client;

import net.jj.giants.client.admin.GiantsButton;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
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
        ((GridLayoutTabAccess) this).giants$layout().addChild(new GiantsButton(screen), 3, 0);
    }
}
