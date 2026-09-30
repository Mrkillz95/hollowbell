package net.jj.hollowbell.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jj.hollowbell.world.BellGen;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.StructureAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A structure that would stand in his ground (a witch hut, a village, an outpost, a ruin...) is never made: its start
 * isn't kept, so it has no pieces, no mobs and /locate doesn't find it. Deep ones stay. Other mods wrap this call too.
 */
@Mixin(ChunkGenerator.class)
public abstract class StructureStartMixin {
    @WrapOperation(method = "tryGenerateStructure", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/StructureManager;setStartForStructure(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/levelgen/structure/Structure;Lnet/minecraft/world/level/levelgen/structure/StructureStart;Lnet/minecraft/world/level/chunk/StructureAccess;)V"))
    private void hollowbell$noneInHisGround(StructureManager manager, SectionPos pos, Structure structure, StructureStart start,
                                            StructureAccess access, Operation<Void> original) {
        if (BellGen.refuses(this, start)) return;
        original.call(manager, pos, structure, start, access);
    }
}
