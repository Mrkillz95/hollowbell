package net.jj.hollowbell.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/** The Stinger: a whip-like strand end off the Hollowbell. What it hits is poisoned and slowed. */
public class StingerItem extends SwordItem {
    public StingerItem(Tier tier, Properties p) { super(tier, p); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, java.util.List<net.minecraft.network.chat.Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        tip.add(net.minecraft.network.chat.Component.translatable("item.hollowbell.stinger.tip").withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity by) {
        target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), by);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), by);
        return super.hurtEnemy(stack, target, by);
    }
}
