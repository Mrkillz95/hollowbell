package net.jj.hollowbell.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.Set;
import net.jj.hollowbell.world.BellGen;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** the overworld can hold the Bell Hollows, so /locate biome looks for it */
@Mixin(BiomeSource.class)
public abstract class HollowsLocateMixin {
    @ModifyReturnValue(method = "possibleBiomes", at = @At("RETURN"))
    private Set<Holder<Biome>> hollowbell$withHollows(Set<Holder<Biome>> original) {
        return BellGen.possible(this, original);
    }
}
