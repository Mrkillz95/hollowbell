package net.jj.mountain.item;

import net.jj.mountain.ModSounds;
import net.jj.mountain.entity.MountainEntity;
import net.jj.mountain.world.MountainWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A drop of his goo behind bone. It knows where he is, always, however far that is. */
public class MountainCompassItem extends Item {
    public MountainCompassItem(Properties p) { super(p); }

    private static final String[] WAY = {"south", "south-east", "east", "north-east", "north", "north-west", "west", "south-west"};

    @Override
    public InteractionResultHolder<ItemStack> use(Level lvl, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (lvl instanceof ServerLevel sl) {
            // one that is right here and loaded beats the world's note of where he was
            MountainEntity near = null;
            double bd = Double.MAX_VALUE;
            for (MountainEntity m : sl.getEntities(net.jj.mountain.ModEntities.MOUNTAIN, m -> !m.isDeadOrDying())) {
                double d = m.distanceToSqr(player);
                if (d < bd) { bd = d; near = m; }
            }
            MountainWorld w = MountainWorld.get(sl.getServer().overworld());   // the world's notes live in the overworld
            BlockPos at = near != null ? near.blockPosition() : w.where(sl.getServer().overworld());
            if (at == null || (near == null && !w.aliveNow())) {
                int days = w.daysLeft(sl);
                player.displayClientMessage(days > 0
                        ? Component.translatable("message.mountain_breathes.compass_wait", days)
                        : Component.translatable("message.mountain_breathes.compass_none"), true);
            } else {
                double dx = at.getX() - player.getX(), dz = at.getZ() - player.getZ();
                int dist = (int) Math.sqrt(dx * dx + dz * dz);
                int seg = (int) Math.round(Math.atan2(dx, dz) / (Math.PI / 4)) & 7;
                player.displayClientMessage(dist < 200
                        ? Component.translatable("message.mountain_breathes.compass_close", dist, WAY[seg])
                        : Component.translatable("message.mountain_breathes.compass", dist, WAY[seg], at.getX(), at.getZ()), true);
            }
            lvl.playSound(null, player.blockPosition(), ModSounds.HEART, SoundSource.PLAYERS, ModSounds.vol(0.7f), 1.2f);
            player.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(stack, lvl.isClientSide);
    }
}
