package net.jj.hollowbell.item;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A compass in a ring of glass with a bit of amethyst: it rings faintly towards him. Use it and it says how far he
 * is and which way, or, if none of him is out there yet, how long until one comes down and where his ground is.
 * It glints while he is close.
 */
public class FinderItem extends Item {
    public FinderItem(Properties p) { super(p); }

    private static final String[] WAY = {"south", "south-east", "east", "north-east", "north", "north-west", "west", "south-west"};

    /** eight ways round, from you towards that spot */
    public static String way(double dx, double dz) {
        return WAY[(int) Math.round(Math.atan2(dx, dz) / (Math.PI / 4)) & 7];
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level lvl, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (lvl instanceof ServerLevel sl) {
            answer(sl, player);
            float vol = 0.7f * HollowbellConfig.V.soundVolume;
            if (vol > 0) lvl.playSound(null, player.blockPosition(), ModSounds.ECHO, SoundSource.PLAYERS, vol, 1.4f);
            player.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(stack, lvl.isClientSide);
    }

    /** what the finder says to this player (the tests call this straight) */
    public static Component answer(ServerLevel sl, Player player) {
        Component msg = tell(sl, player);
        player.displayClientMessage(msg, false);
        return msg;
    }

    private static Component tell(ServerLevel sl, Player player) {
        // one that is right here and loaded beats any note of where he was
        HollowbellEntity near = null;
        double bd = Double.MAX_VALUE;
        for (HollowbellEntity h : sl.getEntities(ModEntities.HOLLOWBELL, h -> !h.isRemoved() && !h.isDeadOrDying())) {
            double d = h.distanceToSqr(player);
            if (d < bd) { bd = d; near = h; }
        }
        if (near != null) return spot(player, near.getX(), near.getZ(), false);
        // one out of the world, drifting on his own in this dimension
        Away.Rec r = Away.get(sl.getServer()).nearest(sl, player.position());
        if (r != null) {
            Vec3 s = r.spot(sl.getGameTime());
            return spot(player, s.x, s.z, true);
        }
        // the world's notes are about the overworld: from the nether or the end they would mean nothing
        boolean over = sl.dimension() == Level.OVERWORLD;
        WorldOne w = WorldOne.get(sl.getServer());
        if (!over) return Component.translatable("message.hollowbell.finder_wrong_world");
        BlockPos at = w.where();
        if (at != null && w.aliveNow()) return spot(player, at.getX() + 0.5, at.getZ() + 0.5, true);
        int days = w.daysLeft(sl);
        if (at != null && HollowbellConfig.V.oneInTheWorld && days >= 0) {
            // where the next one comes down (his new ground is claimed there when he does)
            double hx = at.getX() + 0.5 - player.getX(), hz = at.getZ() + 0.5 - player.getZ();
            String far = Math.sqrt(hx * hx + hz * hz) > 2000 ? "far to the " : "to the ";
            return days > 0
                    ? Component.translatable("message.hollowbell.finder_wait", days, far + way(hx, hz))
                    : Component.translatable("message.hollowbell.finder_soon", far + way(hx, hz));
        }
        return Component.translatable("message.hollowbell.finder_none");
    }

    private static Component spot(Player p, double x, double z, boolean noted) {
        double dx = x - p.getX(), dz = z - p.getZ();
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        if (dist < 150) return Component.translatable("message.hollowbell.finder_close", dist, way(dx, dz));
        return Component.translatable(noted ? "message.hollowbell.finder_far" : "message.hollowbell.finder",
                dist, way(dx, dz), (int) Math.floor(x), (int) Math.floor(z));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, java.util.List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        tip.add(Component.translatable("item.hollowbell.hollowbell_finder.tip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ a glint while he is near

    @Override
    public void inventoryTick(ItemStack stack, Level lvl, Entity holder, int slot, boolean selected) {
        if (lvl.isClientSide || lvl.getGameTime() % 20 != 0) return;
        boolean close = !lvl.getEntities(ModEntities.HOLLOWBELL, holder.getBoundingBox().inflate(300, 400, 300),
                h -> !h.isRemoved() && !h.isDeadOrDying()).isEmpty();
        boolean has = Boolean.TRUE.equals(stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        if (close != has) {
            if (close) stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            else stack.remove(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
    }
}
