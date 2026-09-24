package net.jj.mountain.item;

import net.jj.mountain.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** One of his, still working. Hold it up and for a while you see what he sees: everything alive, through anything. */
public class MountainEyeItem extends Item {
    public MountainEyeItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level lvl, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!lvl.isClientSide) {
            double r = 140;
            int seen = 0;
            for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r))) {
                if (e == player) continue;
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0, false, false));
                seen++;
            }
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 500, 0, false, false));
            lvl.playSound(null, player.blockPosition(), ModSounds.WEAK, SoundSource.PLAYERS, ModSounds.vol(0.8f), 1.3f);
            player.displayClientMessage(Component.translatable("message.mountain_breathes.eye_used", seen), true);
            player.getCooldowns().addCooldown(this, 400);
            final InteractionHand h = hand;
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(h));
        }
        return InteractionResultHolder.sidedSuccess(stack, lvl.isClientSide);
    }
}
