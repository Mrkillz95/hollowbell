package net.jj.hollowbell.mixin;

import net.jj.hollowbell.solid.Sitting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaving the game while sitting on him: set down on the ground beside him first, so you're saved standing there. */
@Mixin(PlayerList.class)
public abstract class SitLeaveMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void hollowbell$sitterLeaves(ServerPlayer p, CallbackInfo ci) {
        Sitting.leaving(p);
    }
}
