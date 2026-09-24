package net.jj.mountain.mixin;

import net.jj.mountain.MountainConfig;
import net.jj.mountain.entity.MountainEntity;
import net.jj.mountain.entity.MountainPart;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * He is longer than the game expects an entity to be. A player is only sent an entity while they are within their
 * own view distance of that entity's one position, which for him sits under his neck; standing at his tail, or
 * anywhere down his side at a big size, put players past that and he simply vanished. Here the range he is sent at
 * is stretched to cover his whole body, and the chunk his neck happens to be in no longer has to be loaded.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class MountainTrackingMixin {
    @Shadow @Final Entity entity;

    /** how far away he should still be sent: his own length, plus room to see him coming */
    private static double mountain_breathes$range(Entity e) {
        float scale = e instanceof MountainEntity m ? m.mountainScale() : e instanceof MountainPart p ? p.ownerScale() : -1f;
        if (scale < 0f) return -1;
        return Math.max(160.0, Math.min(MountainConfig.V.renderDistance, 260.0 * Math.max(0.15f, scale) + 140.0));
    }

    /**
     * The game works out how far away a thing is still worth sending as the smaller of its own tracking range and
     * your view distance. For him that answer is always too small, so the smaller of the two is taken and then
     * pushed back up to what he needs.
     */
    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), require = 0)
    private int mountain_breathes$farEnoughToSeeHim(int ownRange, int viewRange) {
        int base = Math.min(ownRange, viewRange);
        double want = mountain_breathes$range(entity);
        return want < 0 ? base : Math.max(base, (int) Math.ceil(want));
    }

    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"), require = 0)
    private boolean mountain_breathes$neckChunkNeedNotBeLoaded(ChunkMap map, ServerPlayer player, int x, int z) {
        if (mountain_breathes$range(entity) >= 0) return true;
        return ((ChunkMapAccess) map).mountain_breathes$isChunkTracked(player, x, z);
    }
}
