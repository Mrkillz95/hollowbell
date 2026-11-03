package net.jj.giants.systemsmixin;

import java.util.function.Supplier;
import net.jj.giants.systems.Grief;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (1.13.0, grief healing) While grief healing is on, a block a giant breaks (while his own entity ticks: him, his
 * creatures, his robots, his shots and blasts) drops nothing: no item, no experience. Its block grows back, so a drop
 * would be a copy. Every block's drops (broken, blown up, or popped off when what held it went) come through
 * {@code popResource}; what a container held still spills (it grows back empty). Off: nothing changes.
 */
@Mixin(Block.class)
public abstract class GriefDropsMixin {
    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void jjGiants$griefNoDrop(Level level, Supplier<ItemEntity> make, ItemStack stack, CallbackInfo ci) {
        if (Grief.noDrops(level)) ci.cancel();
    }

    @Inject(method = "popExperience", at = @At("HEAD"), cancellable = true)
    private void jjGiants$griefNoExperience(ServerLevel level, BlockPos pos, int amount, CallbackInfo ci) {
        if (Grief.noDrops(level)) ci.cancel();
    }
}
