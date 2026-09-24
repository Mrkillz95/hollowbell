package net.jj.mountain.item;

import net.jj.mountain.ModSounds;
import net.jj.mountain.entity.MountainEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Cut from him and hollowed out. Blow it and he hears it, wherever he is, and starts walking. */
public class MountainHornItem extends Item {
    public MountainHornItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level lvl, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (lvl instanceof ServerLevel sl) {
            lvl.playSound(null, player.blockPosition(), ModSounds.ROAR, SoundSource.PLAYERS, ModSounds.vol(4f), 0.55f);
            MountainEntity best = null;
            double bd = Double.MAX_VALUE;
            for (MountainEntity m : sl.getEntities(net.jj.mountain.ModEntities.MOUNTAIN, m -> !m.isDeadOrDying())) {
                double d = m.distanceToSqr(player);
                if (d < bd) { bd = d; best = m; }
            }
            if (best == null) {
                player.displayClientMessage(Component.translatable("message.mountain_breathes.horn_empty"), true);
            } else {
                if (best.sleeping()) best.wakeUp(player);
                best.setGoal(player.position());
                player.displayClientMessage(Component.translatable("message.mountain_breathes.horn", (int) Math.sqrt(bd)), true);
            }
            player.getCooldowns().addCooldown(this, 600);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, lvl.isClientSide);
    }
}
