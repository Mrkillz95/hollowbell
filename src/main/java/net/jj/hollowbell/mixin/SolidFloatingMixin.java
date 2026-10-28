package net.jj.hollowbell.mixin;

import net.jj.hollowbell.solid.Solid;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The solid kit: standing on him (or jumping on him) has no blocks round it, and isn't flying. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SolidFloatingMixin {
    @Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true)
    private void hollowbell$onHim(Entity e, CallbackInfoReturnable<Boolean> cir) {
        if (Solid.overAny(e)) cir.setReturnValue(false);
    }
}
