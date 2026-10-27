package net.jj.hollowbell.item;

import java.util.List;
import net.jj.hollowbell.Adv;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.entity.StingerHook;
import net.jj.hollowbell.fx.BigFx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Stinger: a strand end off the Hollowbell. A hit poisons (Poison II) and slows. Hold right-click and let go to
 * throw it as a barbed harpoon on its strand, up to 36 blocks: what it sticks in takes 8, is pinned where it stands
 * for three seconds and gets Poison III. Sneak as you let go and the strand reels you to where it stuck.
 */
public class StingerItem extends SwordItem {
    /** ticks of holding before a throw goes, and the wait after one */
    public static final int CHARGE = 6, COOLDOWN = 120;
    /** what the barb does to what it sticks in */
    public static final float HARPOON = 8f;

    public StingerItem(Tier tier, Properties p) {
        super(tier, p);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.hollowbell.stinger.tip").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("item.hollowbell.stinger.tip2").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("item.hollowbell.stinger.tip3").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity by) {
        target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), by);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), by);
        // the barb goes in: a sting and a spurt of his green poison
        Level l = target.level();
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
        l.playSound(null, at.x, at.y, at.z, ModSounds.STING, SoundSource.PLAYERS, 0.8f, 1.2f);
        BigFx.send(l, BigFx.SPARKS, at, 1.2f, Vec3.ZERO, 0x9CFF6A);
        return super.hurtEnemy(stack, target, by);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity e) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity by, int left) {
        int held = getUseDuration(stack, by) - left;
        if (held < CHARGE || !(by instanceof Player p)) return;
        if (!level.isClientSide) throwIt(p, stack, Math.min(1f, held / 14f), p.isShiftKeyDown());
    }

    /** the throw itself; power 0..1 from how long it was held */
    public static StingerHook throwIt(Player p, ItemStack stack, float power, boolean meAlways) {
        Level level = p.level();
        // one strand out at a time
        for (StingerHook old : level.getEntitiesOfClass(
                StingerHook.class, p.getBoundingBox().inflate(StingerHook.RANGE + 8), h -> h.getOwner() == p))
            old.discard();
        StingerHook h = new StingerHook(level, p, meAlways, HARPOON);
        h.setDeltaMovement(p.getLookAngle().scale(1.6 + 1.4 * power));
        level.addFreshEntity(h);
        level.playSound(
                null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1f, 1.2f);
        p.getCooldowns().addCooldown(stack.getItem(), COOLDOWN);
        if (p instanceof ServerPlayer sp) Adv.grant(sp, "stinger_throw");
        if (!p.getAbilities().instabuild)
            stack.hurtAndBreak(1, p, p.getMainHandItem() == stack ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        return h;
    }
}
