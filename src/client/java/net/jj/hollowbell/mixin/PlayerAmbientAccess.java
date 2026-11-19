package net.jj.hollowbell.mixin;

import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AmbientSoundHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (leak-fix) a player's ambient sound handlers, so the ones left behind on a respawn can be faded out (LeftBehind) */
@Mixin(LocalPlayer.class)
public interface PlayerAmbientAccess {
    @Accessor("ambientSoundHandlers")
    List<AmbientSoundHandler> hollowbell$ambientHandlers();
}
