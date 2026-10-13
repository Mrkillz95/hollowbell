package net.jj.hollowbell.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.concurrent.CompletableFuture;
import net.jj.hollowbell.world.BellGen;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** as soon as the generator has filled a chunk with land, his ground notes how high it is (see BellGen.noised) */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class HollowsNoiseMixin {
    @ModifyReturnValue(method = "fillFromNoise", at = @At("RETURN"))
    private CompletableFuture<ChunkAccess> hollowbell$noteLand(CompletableFuture<ChunkAccess> made) {
        NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) (Object) this;
        return made.thenApply(c -> {
            BellGen.noised(c, gen);
            return c;
        });
    }
}
