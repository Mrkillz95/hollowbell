package net.jj.mountain.client.render;

import net.jj.mountain.MountainMod;
import net.jj.mountain.entity.ShadowOfYou;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/** Your own shape in wet black goo. Nothing but a silhouette, because that is the whole idea. */
public class ShadowRenderer extends HumanoidMobRenderer<ShadowOfYou, HumanoidModel<ShadowOfYou>> {
    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "textures/entity/shadow.png");

    public ShadowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowOfYou e) { return TEX; }
}
