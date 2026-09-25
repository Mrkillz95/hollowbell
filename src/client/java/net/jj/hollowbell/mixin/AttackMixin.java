package net.jj.hollowbell.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.hollowbell.client.BellPick;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.net.HitPayload;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A swing at him goes to the server as a hit on the block you were looking at, not as the game's own attack: the
 * game would measure your reach to the small box at his middle and throw the swing away.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class AttackMixin {
    @Shadow protected abstract void ensureHasSentCarriedItem();

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void hollowbell$swingAtHim(Player player, Entity target, CallbackInfo ci) {
        if (!(target instanceof HollowbellEntity h)) return;
        ensureHasSentCarriedItem();
        ClientPlayNetworking.send(new HitPayload(h.getId(), BellPick.bellId == h.getId() ? BellPick.bone : -1));
        player.resetAttackStrengthTicker();
        ci.cancel();
    }
}
