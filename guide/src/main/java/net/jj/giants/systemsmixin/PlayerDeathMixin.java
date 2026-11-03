package net.jj.giants.systemsmixin;

import net.jj.giants.systems.PlayerDeaths;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (Part 8, a player dying near a giant) With "keep" their things and levels stay with them; with "swallow" their
 * things go into the giant. "normal" (as shipped): nothing changes.
 */
@Mixin(Player.class)
public abstract class PlayerDeathMixin {
    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void jjGiants$dropEquipment(CallbackInfo ci) {
        if (PlayerDeaths.dropping((Player) (Object) this)) ci.cancel();
    }

    @Inject(method = "getBaseExperienceReward", at = @At("HEAD"), cancellable = true)
    private void jjGiants$experience(CallbackInfoReturnable<Integer> cir) {
        if (PlayerDeaths.keeps((Player) (Object) this)) cir.setReturnValue(0);
    }
}
