package net.jj.hollowbell.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.jj.hollowbell.block.LootCacheBlock;
import net.jj.hollowbell.block.LootCacheBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** the pale green beam standing up out of his loot cache while anything is left in it, seen from far off */
public class LootCacheRenderer implements BlockEntityRenderer<LootCacheBlockEntity> {
    public LootCacheRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(LootCacheBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(LootCacheBlock.LIT) || !be.getBlockState().getValue(LootCacheBlock.LIT)) return;
        long t = be.getLevel().getGameTime();
        int top = be.getLevel().getMaxBuildHeight() - be.getBlockPos().getY();
        BeaconRenderer.renderBeaconBeam(ps, buf, BeaconRenderer.BEAM_LOCATION, partial, 1f, t, 1, top, 0xFFA8F0B4, 0.25f, 0.32f);
    }

    @Override public boolean shouldRenderOffScreen(LootCacheBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 320; }
    @Override public boolean shouldRender(LootCacheBlockEntity be, Vec3 cam) {
        return Vec3.atCenterOf(be.getBlockPos()).multiply(1, 0, 1).closerThan(cam.multiply(1, 0, 1), getViewDistance());
    }
}
