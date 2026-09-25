package net.jj.hollowbell.world;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;


/**
 * He's far bigger than the game expects anything to be. It only moves a creature while the chunk at its very
 * middle is near a player, so you could be standing under his rim, or watching him from a way off, and have him
 * freeze in the air. While a player is within sight of him, this keeps the chunk at his middle moving and the
 * ground under him loaded (the chunkLoading setting). It lets go by itself a few seconds after nobody's near.
 */
public final class KeepAwake {
    private KeepAwake() {}

    private static final TicketType<Integer> HOLLOWBELL = TicketType.create("hollowbell", Integer::compare, 100);

    public static void tick(ServerLevel level) {
        if (!HollowbellConfig.V.chunkLoading || level.getGameTime() % 20 != 0) return;
        for (HollowbellEntity h : level.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved())) {
            double r = 300 * Math.max(0.15f, h.bellScale()) + 140;
            boolean seen = false;
            for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.distanceToSqr(h.getX(), p.getY(), h.getZ()) < r * r) { seen = true; break; }
            if (!seen) continue;
            // 2 chunks out from his middle keeps the middle moving; more keeps the ground under all of him there
            int radius = Math.min(9, (int) Math.ceil(90 * h.bellScale() / 16.0) + 2);
            level.getChunkSource().addRegionTicket(HOLLOWBELL, new ChunkPos(h.blockPosition()), radius, h.getId());
        }
    }
}
