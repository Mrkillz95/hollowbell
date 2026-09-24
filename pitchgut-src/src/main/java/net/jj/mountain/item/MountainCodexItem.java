package net.jj.mountain.item;

import net.jj.mountain.ModSounds;
import net.jj.mountain.entity.MountainEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Written on his own hide in his own goo. Whoever carries it owns him: he will not touch you, he goes where you
 * point, and he kills what you point at.
 *
 * Right-click with it and the pages open: everything he can be told to do is a line in the book. Right-clicking
 * a creature with it is the one shortcut, because pointing at a thing and saying "that one" wants no menu.
 */
public class MountainCodexItem extends Item {
    public MountainCodexItem(Properties p) { super(p); }

    /**
     * Has this player got the book on them? Anywhere in their pack counts, not just in their hand: you own him
     * while you are carrying it, and you stop owning him when it leaves you, not when you swap to a pickaxe.
     */
    public static boolean heldBy(@Nullable Entity e) {
        if (!(e instanceof Player p)) return false;
        // a copy that is not the one the world knows about owns nothing, even in the second before it crumbles
        net.jj.mountain.world.MountainWorld w = null;
        if (p.level().getServer() != null) w = net.jj.mountain.world.MountainWorld.get(p.level().getServer().overworld());
        net.minecraft.world.entity.player.Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(net.jj.mountain.ModItems.CODEX)) continue;
            if (w == null || w.bookId() == null || theRealOne(w, st)) return true;
        }
        return false;
    }

    static @Nullable MountainEntity nearest(ServerLevel sl, Player who) {
        MountainEntity best = null;
        double bd = Double.MAX_VALUE;
        for (MountainEntity m : sl.getEntities(net.jj.mountain.ModEntities.MOUNTAIN, m -> !m.isDeadOrDying())) {
            double d = m.distanceToSqr(who);
            if (d < bd) { bd = d; best = m; }
        }
        return best;
    }

    private static void note(Player p, String key, Object... args) {
        p.displayClientMessage(Component.translatable("message.mountain_breathes." + key, args), true);
    }

    private static void wake(MountainEntity m, Player p) {
        if (m.sleeping()) m.wakeUp(p);
    }

    // ------------------------------------------------------------------ which one is the real one
    /** the mark the one true book carries, written into the item itself */
    private static final String MARK = "MountainBookId";

    public static @Nullable java.util.UUID markOf(ItemStack stack) {
        if (!stack.is(net.jj.mountain.ModItems.CODEX)) return null;
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        net.minecraft.nbt.CompoundTag t = data.copyTag();
        return t.hasUUID(MARK) ? t.getUUID(MARK) : null;
    }

    public static void markAs(ItemStack stack, java.util.UUID id) {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        var had = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (had != null) t = had.copyTag();
        t.putUUID(MARK, id);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(t));
    }

    /** is this stack the one the world knows about? */
    public static boolean theRealOne(net.jj.mountain.world.MountainWorld w, ItemStack stack) {
        java.util.UUID want = w.bookId();
        return want != null && want.equals(markOf(stack));
    }

    /** There is one of these in the world. A second one written falls apart in your hands. */
    @Override
    public void onCraftedBy(ItemStack stack, Level lvl, Player player) {
        super.onCraftedBy(stack, lvl, player);
        if (lvl.isClientSide || player.level().getServer() == null) return;
        ServerLevel over = player.level().getServer().overworld();
        var w = net.jj.mountain.world.MountainWorld.get(over);
        if (w.marked(player.getUUID())) {
            stack.setCount(0);
            note(player, "codex_will_not_stay");
            return;
        }
        if (w.bookExists()) {
            stack.setCount(0);
            note(player, "codex_only_one");
            return;
        }
        markAs(stack, w.claimBookAs());      // this is now the one, and the world knows its mark
    }

    /**
     * Whoever asked a dying Mountain to finish it can never carry this again. It goes up the moment it touches
     * them, wherever it came from, and the world lets another be written so nobody else loses theirs for it.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level lvl, net.minecraft.world.entity.Entity holder, int slot, boolean selected) {
        super.inventoryTick(stack, lvl, holder, slot, selected);
        if (lvl.isClientSide || !(lvl instanceof ServerLevel sl) || !(holder instanceof net.minecraft.server.level.ServerPlayer p)) return;
        if (lvl.getGameTime() % 10 != 0) return;
        var w = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());
        if (!w.marked(p.getUUID())) return;
        // only the real one going up lets another be written; a copy burning frees nothing
        boolean real = w.bookId() == null || theRealOne(w, stack);
        stack.setCount(0);
        if (real) w.forgetBook();
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, p.getX(), p.getY() + 1.2, p.getZ(), 60, 0.4, 0.6, 0.4, 0.05);
        sl.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.5f);
        note(p, "codex_will_not_stay");
    }

    /** set by the client: opens the book's pages on screen */
    public static Runnable openPages = () -> {};

    @Override
    public InteractionResultHolder<ItemStack> use(Level lvl, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (lvl.isClientSide) openPages.run();
        return InteractionResultHolder.sidedSuccess(stack, lvl.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (c.getLevel().isClientSide) openPages.run();
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level() instanceof ServerLevel sl && target instanceof MountainEntity him) {
            if (him.ridden()) { him.dropRider(); return InteractionResult.SUCCESS; }
            if (him.possess(player)) return InteractionResult.SUCCESS;
            return InteractionResult.FAIL;
        }
        if (player.level() instanceof ServerLevel sl && !(target instanceof MountainEntity)) {
            MountainEntity m = nearest(sl, player);
            if (m == null) { note(player, "codex_none"); return InteractionResult.FAIL; }
            wake(m, player);
            m.sendAfter(java.util.List.of(target));
            note(player, "codex_kill", target.getDisplayName().getString());
            sl.playSound(null, player.blockPosition(), ModSounds.ROAR, SoundSource.PLAYERS, ModSounds.vol(1.4f), 0.9f);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
