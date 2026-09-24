package net.jj.mountain.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.jj.mountain.entity.GooGlob;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** A tumbling lump of goo: a black core with a couple of smaller lumps stuck to it. */
public class GooGlobRenderer extends EntityRenderer<GooGlob> {
    private static final BlockState CORE = Blocks.BLACK_CONCRETE_POWDER.defaultBlockState();
    private static final BlockState LUMP = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final BlockState SKIN = Blocks.MAGENTA_TERRACOTTA.defaultBlockState();

    public GooGlobRenderer(EntityRendererProvider.Context ctx) { super(ctx); this.shadowRadius = 0.3f; }

    @Override public ResourceLocation getTextureLocation(GooGlob e) { return TextureAtlas.LOCATION_BLOCKS; }

    @Override
    public void render(GooGlob e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        BlockRenderDispatcher br = Minecraft.getInstance().getBlockRenderer();
        float s = e.size(), spin = (e.tickCount + partial) * (e.dart() ? 25f : 9f);
        ps.pushPose();
        ps.translate(0, s * 0.5f, 0);
        ps.mulPose(Axis.YP.rotationDegrees(spin + e.getId() * 37f));
        ps.mulPose(Axis.XP.rotationDegrees(spin * 0.7f));
        ps.scale(s, s, s);
        block(br, ps, buf, light, CORE, 0f, 0f, 0f, 1f);
        block(br, ps, buf, light, LUMP, 0.45f, 0.3f, 0.1f, 0.55f);
        if (!e.dart()) {
            block(br, ps, buf, light, SKIN, -0.4f, -0.2f, 0.35f, 0.5f);
            block(br, ps, buf, light, CORE, 0.1f, -0.45f, -0.4f, 0.6f);
        }
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, light);
    }

    private static void block(BlockRenderDispatcher br, PoseStack ps, MultiBufferSource buf, int light, BlockState st, float x, float y, float z, float k) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.scale(k, k, k);
        ps.translate(-0.5f, -0.5f, -0.5f);
        br.renderSingleBlock(st, ps, buf, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }
}
