package net.jj.hollowbell.mixin;

import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/** who is being sent an entity right now: far sight leaves those out, they already see the real one */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccess {
    @Accessor("seenBy")
    Set<ServerPlayerConnection> hollowbell$seenBy();
}
