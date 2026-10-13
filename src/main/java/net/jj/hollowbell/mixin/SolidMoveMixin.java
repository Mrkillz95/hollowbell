package net.jj.hollowbell.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.jj.hollowbell.solid.Solid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The solid kit: riding on a big one as he crashes down moves you further in a tick than the server allows a player
 * to move (10 blocks), and it would put you back where you were. On him, the allowance is as far as he can carry you.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SolidMoveMixin {
    @Shadow
    public ServerPlayer player;

    // ModifyExpressionValue, not ModifyConstant: the other giants change the same limit and these chain.
    @ModifyExpressionValue(method = "handleMovePlayer", at = @At(value = "CONSTANT", args = "floatValue=100.0F"))
    private float hollowbell$carried(float allowed) {
        double most = Solid.carryAllowance(player);
        return most <= 0 ? allowed : (float) Math.max(allowed, (most + 2) * (most + 2));
    }
}
