package net.jj.giants.fightsmixin;

import net.jj.giants.fights.Fights;
import net.jj.giants.tools.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (Part 10B) A block a fighting giant's own entity changes while it ticks: kept as it is when the fight says no blocks
 * break (or only some), or noted to grow back after. Nothing is looked at unless a giant is ticking and a fight that
 * needs it is going on.
 */
@Mixin(Level.class)
public abstract class FightBlocksMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void jjGiants$fightBlock(BlockPos pos, BlockState state, int flags, int left,
            CallbackInfoReturnable<Boolean> cir) {
        if (Stats.currentEntity == null || !Fights.watchingBlocks) return;
        Level self = (Level) (Object) this;
        if (self.isClientSide()) return;
        if (!Fights.blockChange(self, pos, state, Stats.currentEntity)) cir.setReturnValue(false);
    }
}
