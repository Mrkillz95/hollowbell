package net.jj.hollowbell.mixin;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (leak-fix) the biome loops a player's ambient handler is playing (LeftBehind) */
@Mixin(BiomeAmbientSoundsHandler.class)
public interface BiomeLoopsAccess {
    @Accessor("loopSounds")
    Object2ObjectArrayMap<Biome, BiomeAmbientSoundsHandler.LoopSoundInstance> hollowbell$loops();
}
