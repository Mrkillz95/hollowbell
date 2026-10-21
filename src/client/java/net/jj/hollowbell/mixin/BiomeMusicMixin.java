package net.jj.hollowbell.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.jj.hollowbell.HollowbellMod;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * In his biome its own music is what the game plays, in creative too (the game would play its creative music
 * there), and it takes over from whatever was playing within a few seconds of walking in.
 */
@Mixin(Minecraft.class)
public abstract class BiomeMusicMixin {
    @ModifyReturnValue(method = "getSituationalMusic", at = @At("RETURN"))
    private Music hollowbell$biomeMusic(Music was) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || mc.level == null) return was;
        var biome = mc.level.getBiome(mc.player.blockPosition());
        boolean ours = biome.unwrapKey().map(k -> HollowbellMod.MOD_ID.equals(k.location().getNamespace()))
                .orElse(false);
        if (!ours) return was;
        // a second to five before it starts, and as soon again after each track
        return biome.value().getBackgroundMusic().map(m -> new Music(m.getEvent(), 20, 100, true)).orElse(was);
    }
}
