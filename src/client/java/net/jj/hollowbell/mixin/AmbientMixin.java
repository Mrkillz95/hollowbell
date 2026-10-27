package net.jj.hollowbell.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.jj.hollowbell.GiantsPerf;
import net.jj.hollowbell.HollowbellMod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.AmbientParticleSettings;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The motes drifting in the air of his biome, as many as the Giants Guide's ambient level asks on this game (at
 * 100%, the default, nothing changes; other biomes are never touched).
 */
@Mixin(ClientLevel.class)
public abstract class AmbientMixin {
    @WrapOperation(
            method = "doAnimateTick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/biome/Biome;getAmbientParticle()Ljava/util/Optional;"))
    private Optional<AmbientParticleSettings> hollowbell$ambient(Biome biome, Operation<Optional<AmbientParticleSettings>> op) {
        Optional<AmbientParticleSettings> was = op.call(biome);
        float k = GiantsPerf.f("fx.ambient");
        if (k >= 1f || was.isEmpty()) return was;
        ClientLevel level = (ClientLevel) (Object) this;
        var key = level.registryAccess().registryOrThrow(Registries.BIOME).getResourceKey(biome);
        if (key.isEmpty() || !HollowbellMod.MOD_ID.equals(key.get().location().getNamespace())) return was;
        return level.random.nextFloat() < k ? was : Optional.empty();
    }
}
