package net.jj.hollowbell.item;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
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
import net.minecraft.util.Mth;

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
            float vol = HollowbellConfig.V.soundVolume;
            if (vol > 0) lvl.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, vol, 1f);
            player.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(stack, lvl.isClientSide);
    }

    /** what the finder says to this player, in chat (the tests call this straight) */
    public static Component answer(ServerLevel sl, Player player) {
        Component msg = tell(sl, player.position());
        player.displayClientMessage(msg, false);
        return msg;
    }

    /** a distance the way the finder says it: to the nearest ten blocks */
    public static int tens(double d) { return (int) (Math.round(d / 10.0) * 10); }

    /**
     * The one sentence all five finders use (the same words in the book's "Where is he?" and /hollowbell natural):
     * where he is from here, or when and where the next one comes down.
     */
    public static Component tell(ServerLevel sl, Vec3 from) {
        // one that is right here and loaded beats any note of where he was
        HollowbellEntity near = null;
        double bd = Double.MAX_VALUE;
        for (HollowbellEntity h : sl.getEntities(ModEntities.HOLLOWBELL, h -> !h.isRemoved() && !h.isDeadOrDying())) {
            double d = h.distanceToSqr(from);
            if (d < bd) { bd = d; near = h; }
        }
        if (near != null) return found(sl, from, near.getX(), near.getZ());
        // one out of the world, drifting on his own in this dimension
        Away.Rec r = Away.get(sl.getServer()).nearest(sl, from);
        if (r != null) {
            Vec3 s = r.spot(sl.getGameTime());
            return found(sl, from, s.x, s.z);
        }
        // the world's notes are about the overworld: from the nether or the end they would mean nothing
        if (sl.dimension() != Level.OVERWORLD) return Component.translatable("message.hollowbell.finder_wrong_world");
        WorldOne w = WorldOne.get(sl.getServer());
        BlockPos at = w.where();
        if (at != null && w.aliveNow()) return found(sl, from, at.getX() + 0.5, at.getZ() + 0.5);
        int days = w.daysLeft(sl);
        if (at != null && HollowbellConfig.V.oneInTheWorld && days >= 0) {
            // where the next one comes down: the middle of his ground
            double dx = at.getX() + 0.5 - from.x, dz = at.getZ() + 0.5 - from.z;
            int n = tens(Math.sqrt(dx * dx + dz * dz));
            if (days > 0) return Component.translatable(days == 1 ? "message.hollowbell.finder_dead_day" : "message.hollowbell.finder_dead", days, n, way(dx, dz));
            return Component.translatable("message.hollowbell.finder_due", n, way(dx, dz));
        }
        return Component.translatable("message.hollowbell.finder_none");
    }

    /** he is there: how far, which way, where, and whether that is in his ground */
    public static Component found(ServerLevel sl, Vec3 from, double x, double z) {
        double dx = x - from.x, dz = z - from.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 64) return Component.translatable("message.hollowbell.finder_close", tens(d), way(dx, dz));
        boolean inGround = sl.dimension() == Level.OVERWORLD && net.jj.hollowbell.world.BellGen.inGround(Mth.floor(x), Mth.floor(z));
        return Component.translatable(inGround ? "message.hollowbell.finder_ground" : "message.hollowbell.finder",
                tens(d), way(dx, dz), Mth.floor(x), Mth.floor(z));
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
