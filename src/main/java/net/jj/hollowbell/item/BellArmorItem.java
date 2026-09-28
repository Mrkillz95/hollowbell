package net.jj.hollowbell.item;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jj.hollowbell.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Bell glass armor. As tough as diamond and light, and the full set carries two of his own gifts: poison never
 * touches you (his stings taught the glass), and falling fast wakes slow falling, the way he lets things down.
 */
public class BellArmorItem extends ArmorItem {
    public BellArmorItem(Holder<ArmorMaterial> material, Type type, Properties p) { super(material, type, p); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.hollowbell.bell_glass.tip").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("item.hollowbell.bell_glass.tip2").withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack, ctx, tip, flag);
    }

    /** wearing all four pieces */
    public static boolean fullSet(LivingEntity p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.BELL_HELMET)
                && p.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.BELL_CHESTPLATE)
                && p.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.BELL_LEGGINGS)
                && p.getItemBySlot(EquipmentSlot.FEET).is(ModItems.BELL_BOOTS);
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BellArmorItem::tick);
        // poison's own bite (it lands as magic) never reaches somebody in the full set
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amount) -> {
            if (e instanceof ServerPlayer p && src.is(DamageTypes.MAGIC) && p.hasEffect(MobEffects.POISON) && fullSet(p)) {
                p.removeEffect(MobEffects.POISON);
                return false;
            }
            return true;
        });
    }

    private static void tick(MinecraftServer server) {
        boolean slow = server.getTickCount() % 10 == 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // the fall is checked every tick (a fall is quick); the rest every half second
            if (p.fallDistance > 3f || slow) abilities(p);
        }
    }

    /** one pass of the set's gifts (the tests call this straight) */
    public static void abilities(ServerPlayer p) {
        if (p.isSpectator() || !fullSet(p)) return;
        if (p.hasEffect(MobEffects.POISON)) p.removeEffect(MobEffects.POISON);
        // falling fast (more than three blocks, not gliding): he lets you down the way he lets things down
        if (p.fallDistance > 3f && !p.isFallFlying() && !p.getAbilities().flying && !p.isInWater()) {
            MobEffectInstance now = p.getEffect(MobEffects.SLOW_FALLING);
            if (now == null || now.getDuration() < 40)
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 70, 0, true, false, true));
            p.resetFallDistance();
        }
    }
}
