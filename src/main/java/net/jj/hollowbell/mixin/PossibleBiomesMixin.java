package net.jj.hollowbell.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.jj.hollowbell.world.BellGen;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Set;

/** the overworld can hold the Bell Hollows, so /locate biome looks for it */
@Mixin(BiomeSource.class)
public abstract class PossibleBiomesMixin {
    @ModifyReturnValue(method = "possibleBiomes", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private Set<Holder<Biome>> hollowbell$withHollows(Set<Holder<Biome>> original) {
        return BellGen.possible(this, original);
    }
}
