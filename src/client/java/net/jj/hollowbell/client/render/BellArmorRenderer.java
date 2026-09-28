package net.jj.hollowbell.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;

/**
 * Bell glass is drawn the way his own glass is: see-through. Vanilla draws every armor solid (a cutout), which
 * would turn the pale glass into flat paint; here it goes through the translucent pass instead, with the glint
 * and any trim drawn over it just as vanilla does.
 */
public final class BellArmorRenderer implements ArmorRenderer {
    private static final ResourceLocation OUTER = ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "textures/models/armor/bell_glass_layer_1.png");
    private static final ResourceLocation INNER = ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "textures/models/armor/bell_glass_layer_2.png");

    private HumanoidModel<LivingEntity> outer, inner;

    public static void register() {
        ArmorRenderer.register(new BellArmorRenderer(), ModItems.BELL_HELMET, ModItems.BELL_CHESTPLATE, ModItems.BELL_LEGGINGS, ModItems.BELL_BOOTS);
    }

    private HumanoidModel<LivingEntity> model(boolean legs) {
        if (outer == null) {
            var models = Minecraft.getInstance().getEntityModels();
            outer = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
            inner = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        }
        return legs ? inner : outer;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, ItemStack stack, LivingEntity entity, EquipmentSlot slot, int light,
                       HumanoidModel<LivingEntity> context) {
        if (!(stack.getItem() instanceof ArmorItem armor)) return;
        boolean legs = slot == EquipmentSlot.LEGS;
        HumanoidModel<LivingEntity> m = model(legs);
        context.copyPropertiesTo(m);
        m.setAllVisible(false);
        switch (slot) {
            case HEAD -> { m.head.visible = true; m.hat.visible = true; }
            case CHEST -> { m.body.visible = true; m.rightArm.visible = true; m.leftArm.visible = true; }
            case LEGS -> { m.body.visible = true; m.rightLeg.visible = true; m.leftLeg.visible = true; }
            case FEET -> { m.rightLeg.visible = true; m.leftLeg.visible = true; }
            default -> { return; }
        }
        // the glass, see-through, with the glint over it when enchanted
        var vc = ItemRenderer.getArmorFoilBuffer(buffers, RenderType.entityTranslucent(legs ? INNER : OUTER), stack.hasFoil());
        m.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY);
        // a smithing trim, if it has one, the same way vanilla puts it on
        ArmorTrim trim = stack.get(DataComponents.TRIM);
        if (trim != null) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(Sheets.ARMOR_TRIMS_SHEET)
                    .getSprite(legs ? trim.innerTexture(armor.getMaterial()) : trim.outerTexture(armor.getMaterial()));
            var tvc = sprite.wrap(buffers.getBuffer(Sheets.armorTrimsSheet(trim.pattern().value().decal())));
            m.renderToBuffer(pose, tvc, light, OverlayTexture.NO_OVERLAY);
        }
    }
}
