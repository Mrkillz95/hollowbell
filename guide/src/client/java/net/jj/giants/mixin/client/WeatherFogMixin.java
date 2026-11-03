package net.jj.giants.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.jj.giants.client.WeatherClient;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (Part 9, ADMIN_BRIDGE.md 25.5) A giant's weather on the player's game: the land's fog closes in and takes his colour.
 * Out of every giant's weather (nearly always) both return at once and nothing changes.
 */
@Mixin(FogRenderer.class)
public abstract class WeatherFogMixin {
    @Shadow
    private static float fogRed;

    @Shadow
    private static float fogGreen;

    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void giants$weatherColour(Camera camera, float partialTick, ClientLevel level, int renderDistance,
            float darken, CallbackInfo ci) {
        float[] c = WeatherClient.colour(camera, fogRed, fogGreen, fogBlue);
        if (c == null) return;
        fogRed = c[0];
        fogGreen = c[1];
        fogBlue = c[2];
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void giants$weatherFog(Camera camera, FogRenderer.FogMode mode, float far, boolean thick,
            float partialTick, CallbackInfo ci) {
        if (mode != FogRenderer.FogMode.FOG_TERRAIN) return;
        float[] f = WeatherClient.fog(camera, RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd());
        if (f == null) return;
        RenderSystem.setShaderFogStart(f[0]);
        RenderSystem.setShaderFogEnd(f[1]);
    }
}
