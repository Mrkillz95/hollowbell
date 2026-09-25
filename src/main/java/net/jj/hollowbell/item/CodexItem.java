package net.jj.hollowbell.item;

import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Hollowbell Codex. Whoever carries it owns the nearest Hollowbell: he leaves you alone, drifts where you
 * point and grabs what you point at. Right-click to open the pages; right-click a creature with it to send him after it.
 */
public class CodexItem extends Item {
    public CodexItem(Properties p) { super(p); }

    /** anywhere in the pack counts, not only the hand */
    public static boolean carriedBy(@Nullable Entity e) {
        if (!(e instanceof Player p)) return false;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(ModItems.CODEX)) return true;
        return false;
    }

    /** set by the client: opens the pages */
    public static Runnable openPages = () -> {};

    @Override
    public InteractionResultHolder<ItemStack> use(Level lvl, Player player, InteractionHand hand) {
        if (lvl.isClientSide) openPages.run();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), lvl.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel sl) || target instanceof HollowbellEntity) return InteractionResult.PASS;
        HollowbellEntity m = net.jj.hollowbell.net.CodexOrders.his(player);
        if (m == null) { player.displayClientMessage(Component.translatable("message.hollowbell.codex_none"), true); return InteractionResult.FAIL; }
        m.sendAfter(target);
        player.displayClientMessage(Component.translatable("message.hollowbell.codex_kill", target.getDisplayName().getString()), true);
        sl.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.6f);
        return InteractionResult.SUCCESS;
    }
}
