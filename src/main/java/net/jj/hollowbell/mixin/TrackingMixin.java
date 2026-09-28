package net.jj.hollowbell.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * He is far wider and taller than the game expects an entity to be. A player is only sent an entity while they are
 * within view distance of its one position (the middle of the ground under him), so standing under his rim, or
 * watching his dome from a way off, he vanished. Here the range he is sent at is stretched to cover all of him.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class TrackingMixin {
    @Shadow @Final Entity entity;

    private static double hollowbell$range(Entity e) {
        if (!(e instanceof HollowbellEntity h)) return -1;
        return Math.max(160.0, Math.min(HollowbellConfig.V.renderDistance, 300.0 * Math.max(0.15f, h.bellScale()) + 140.0));
    }

    @WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), require = 0)
    private int hollowbell$farEnough(int ownRange, int viewRange, Operation<Integer> original) {
        int base = original.call(ownRange, viewRange);
        double want = hollowbell$range(entity);
        return want < 0 ? base : Math.max(base, (int) Math.ceil(want));
    }

    @WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"), require = 0)
    private boolean hollowbell$middleNeedNotBeLoaded(ChunkMap map, ServerPlayer player, int x, int z, Operation<Boolean> original) {
        if (hollowbell$range(entity) >= 0) return true;
        return original.call(map, player, x, z);
    }
}
