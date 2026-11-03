package net.jj.giants.mixin.client;

import net.jj.giants.client.admin.GiantsBigButton;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (Part 2C, JJ Nov 2: "make it easier to find and very obvious") the Create World screen's first tab (Game) gets a big
 * "Giants: Classic — Change…" button under Allow Commands, the same as the More tab's Giants… (ADMIN_BRIDGE.md 21.4).
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
public abstract class CreateWorldGameTabMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void giants$addBigButton(CreateWorldScreen screen, CallbackInfo ci) {
        GridLayout layout = ((GridLayoutTabAccess) this).giants$layout();
        // (one child a row: the next row is under the last)
        int[] rows = {0};
        layout.visitChildren(e -> rows[0]++);
        layout.addChild(new GiantsBigButton(screen), rows[0], 0, layout.newCellSettings().alignHorizontallyCenter());
    }
}
