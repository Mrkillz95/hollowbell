package net.jj.giants.systemsmixin;

import net.jj.giants.systems.Grief;
import net.jj.giants.tools.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (Part 8, grief healing) A block a giant's own entity sets while it ticks is noted, so it can grow back. Nothing is
 * looked at unless grief healing is on and a giant is ticking.
 */
@Mixin(Level.class)
public abstract class GriefMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"))
    private void jjGiants$griefSet(BlockPos pos, BlockState state, int flags, int left,
            CallbackInfoReturnable<Boolean> cir) {
        if (!Grief.on || Stats.current == null) return;
        Grief.changing((Level) (Object) this, pos, state);
    }
}
