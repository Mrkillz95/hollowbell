package net.jj.giants.mixin.client;

import net.jj.giants.client.admin.CreationClient;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Create New World pressed: the giants' choice for it waits for the new world's first load (ADMIN_BRIDGE.md §18.7) */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
    @Inject(method = "onCreate", at = @At("HEAD"))
    private void giants$arm(CallbackInfo ci) {
        CreationClient.arm((CreateWorldScreen) (Object) this);
    }
}
