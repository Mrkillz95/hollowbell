package net.jj.hollowbell.mixin;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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

    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), require = 0)
    private int hollowbell$farEnough(int ownRange, int viewRange) {
        int base = Math.min(ownRange, viewRange);
        double want = hollowbell$range(entity);
        return want < 0 ? base : Math.max(base, (int) Math.ceil(want));
    }

    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"), require = 0)
    private boolean hollowbell$middleNeedNotBeLoaded(ChunkMap map, ServerPlayer player, int x, int z) {
        if (hollowbell$range(entity) >= 0) return true;
        return ((ChunkMapAccess) map).hollowbell$isChunkTracked(player, x, z);
    }
}
