package net.jj.giants.toolsmixin;

import net.jj.giants.tools.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (Part 6, the stats page and the griefing alert) A block turned to air while a giant's own entity ticks counts as
 * broken by that giant. Nothing is looked at unless a giant is ticking.
 */
@Mixin(Level.class)
public abstract class BlockBreakMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"))
    private void jjGiants$blockSet(BlockPos pos, BlockState state, int flags, int left,
            CallbackInfoReturnable<Boolean> cir) {
        if (Stats.current == null || !state.isAir()) return;
        Level self = (Level) (Object) this;
        if (self.isClientSide() || self.getBlockState(pos).isAir()) return;
        Stats.blockBroken();
    }
}
