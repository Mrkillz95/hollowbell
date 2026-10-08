package net.jj.hollowbell.mixin;

import net.jj.hollowbell.solid.client.SolidClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The solid kit: standing on him, each frame puts you where he's drawn, before the camera is set (and back after). */
@Mixin(GameRenderer.class)
public abstract class SolidFrameMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void hollowbell$solidFrame(DeltaTracker dt, CallbackInfo ci) { SolidClient.beforeFrame(dt.getGameTimeDeltaPartialTick(true)); }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void hollowbell$solidFrameEnd(DeltaTracker dt, CallbackInfo ci) { SolidClient.afterFrame(); }
}
