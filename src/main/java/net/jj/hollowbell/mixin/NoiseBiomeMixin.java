package net.jj.hollowbell.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.jj.hollowbell.world.BellGen;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;

/** the overworld's biome source says bell_hollows for his ground's columns (the other giants stack here too) */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class NoiseBiomeMixin {
    @ModifyReturnValue(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private Holder<Biome> hollowbell$bellHollows(Holder<Biome> original, int qx, int qy, int qz, Climate.Sampler sampler) {
        return BellGen.biome(this, original, qx, qy, qz);
    }
}
