package net.jj.giants.mixin.client;

import net.jj.giants.client.WeatherClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (Part 9, ADMIN_BRIDGE.md 25.5) in a giant's weather the sky takes his fog's colour too */
@Mixin(ClientLevel.class)
public abstract class WeatherSkyMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void giants$weatherSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (WeatherClient.strength() > 0) cir.setReturnValue(WeatherClient.sky(cir.getReturnValue()));
    }
}
