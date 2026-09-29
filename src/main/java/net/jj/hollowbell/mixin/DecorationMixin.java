package net.jj.hollowbell.mixin;

import net.jj.hollowbell.world.BellGen;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** after the vanilla decoration of a chunk, his land is made in it if it is his */
@Mixin(ChunkGenerator.class)
public abstract class DecorationMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void hollowbell$bellHollows(WorldGenLevel level, ChunkAccess chunk, StructureManager structures, CallbackInfo ci) {
        BellGen.decorated(this, level, chunk, structures);
    }
}
