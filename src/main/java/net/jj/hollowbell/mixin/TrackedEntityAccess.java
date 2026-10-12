package net.jj.hollowbell.mixin;

import java.util.Set;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** who is being sent an entity right now: far sight leaves those out, they already see the real one */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccess {
    @Accessor("seenBy")
    Set<ServerPlayerConnection> hollowbell$seenBy();
}
