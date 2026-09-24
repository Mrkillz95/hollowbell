package net.jj.mountain.mixin;

import net.jj.mountain.entity.MountainCollision;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Standing on his back has no blocks around it, and a server without flight allowed would kick you for flying. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerFloatingMixin {
    @Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true)
    private void mountain_breathes$onHisBack(Entity e, CallbackInfoReturnable<Boolean> cir) {
        if (MountainCollision.overHisBack(e)) cir.setReturnValue(false);
    }
}
