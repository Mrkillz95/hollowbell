package net.jj.hollowbell.mixin;

import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaving the game while he has you (riding his crown, being carried up, held by a strand or an arm, or inside his
 * dome): you're let go and put down on the ground beside him first, so you're saved standing there and not up in
 * the air, where you'd fall when you came back.
 */
@Mixin(PlayerList.class)
public abstract class LogOffMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void hollowbell$putDownFirst(ServerPlayer p, CallbackInfo ci) {
        HollowbellEntity.putDownOnLeaving(p);
    }
}
