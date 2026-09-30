package net.jj.hollowbell.mixin;

import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** what the level remembers about structures, so a painted-over one is forgotten there too */
@Mixin(StructureManager.class)
public interface StructureManagerAccess {
    @Accessor("structureCheck")
    StructureCheck hollowbell$check();
}
