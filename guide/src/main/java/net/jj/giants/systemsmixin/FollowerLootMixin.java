package net.jj.giants.systemsmixin;

import net.jj.giants.systems.Followers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (Part 9) a giant's follower drops nothing when it dies: no loot, no experience (no follower farms) */
@Mixin(LivingEntity.class)
public abstract class FollowerLootMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void giants$followerLoot(ServerLevel level, DamageSource src, CallbackInfo ci) {
        if (((LivingEntity) (Object) this).getTags().contains(Followers.MARK)) ci.cancel();
    }

    @Inject(method = "dropExperience", at = @At("HEAD"), cancellable = true)
    private void giants$followerXp(@Nullable Entity killer, CallbackInfo ci) {
        if (((LivingEntity) (Object) this).getTags().contains(Followers.MARK)) ci.cancel();
    }
}
