package net.jj.giants.systemsmixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (Part 9) a phantom following a giant circles over him: his own spot to circle round */
@Mixin(Phantom.class)
public interface PhantomAccess {
    @Accessor("anchorPoint")
    void jjGiants$setAnchorPoint(BlockPos at);
}
