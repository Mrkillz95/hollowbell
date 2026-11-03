package net.jj.giants.toolsmixin;

import net.jj.giants.tools.Stats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (Part 6, the stats page) How long each giant's entities take to tick (with what rides them), and which giant is
 * ticking while blocks are broken. Only a giant mod's own entities are timed; any other costs one map lookup.
 */
@Mixin(ServerLevel.class)
public abstract class TickCostMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void jjGiants$tickStarts(Entity e, CallbackInfo ci) {
        Stats.entityStarts(e);
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void jjGiants$tickEnds(Entity e, CallbackInfo ci) {
        Stats.entityEnds();
    }
}
