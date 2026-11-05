package net.jj.giants.mixin.client;

import net.jj.giants.client.editor.EditorClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (1.16, ADMIN_BRIDGE.md §29) A giant's followers (Systems: Mob followers, ordinary creatures) wear his look: drawn in
 * its colour. Only creatures the server named; everything else is drawn as always.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityTintMixin {
    @Unique
    private static LivingEntity jj_giants$drawing;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), require = 0)
    private void jj_giants$whoIsDrawn(LivingEntity e, float yaw, float partial, com.mojang.blaze3d.vertex.PoseStack ps,
            MultiBufferSource buffers, int light, CallbackInfo ci) {
        jj_giants$drawing = e;
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"), index = 4, require = 0)
    private int jj_giants$tint(int argb) {
        return EditorClient.tint(jj_giants$drawing, argb);
    }
}
