package net.jj.hollowbell.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.jj.hollowbell.block.LootCacheBlock;
import net.jj.hollowbell.block.LootCacheBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** the pale green beam standing up out of his loot cache while anything is left in it, seen from far off */
public class LootCacheRenderer implements BlockEntityRenderer<LootCacheBlockEntity> {
    public LootCacheRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(LootCacheBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(LootCacheBlock.LIT) || !be.getBlockState().getValue(LootCacheBlock.LIT)) return;
        // the same beam as the far-off one (LootBeamsClient), which stands aside while this one draws
        net.jj.hollowbell.client.LootBeamsClient.cacheDrew(be.getBlockPos());
        net.jj.hollowbell.client.LootBeamsClient.draw(new org.joml.Matrix4f(ps.last().pose()), be.getBlockPos(), partial, be.getLevel().getGameTime(),
                be.getLevel().getMaxBuildHeight(), net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera());
    }

    @Override public boolean shouldRenderOffScreen(LootCacheBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 320; }
    @Override public boolean shouldRender(LootCacheBlockEntity be, Vec3 cam) {
        return Vec3.atCenterOf(be.getBlockPos()).multiply(1, 0, 1).closerThan(cam.multiply(1, 0, 1), getViewDistance());
    }
}
