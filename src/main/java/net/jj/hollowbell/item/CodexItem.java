package net.jj.hollowbell.item;

import java.util.List;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Hollowbell Codex. Whoever carries it owns the nearest Hollowbell: he leaves you alone, drifts where you point and
 * grabs what you point at. Right-click to open the pages; right-click a creature with it to send him after it.
 */
public class CodexItem extends Item {
    public CodexItem(Properties p) {
        super(p);
    }

    /** anywhere in the pack counts, not only the hand */
    public static boolean carriedBy(@Nullable Entity e) {
        if (!(e instanceof Player p)) return false;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(ModItems.CODEX)) return true;
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.hollowbell.hollowbell_codex.tip1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.hollowbell.hollowbell_codex.tip2").withStyle(ChatFormatting.DARK_AQUA));
    }

    /** set by the client: opens the pages */
    public static Runnable openPages = () -> {};

    @Override
    public InteractionResultHolder<ItemStack> use(Level lvl, Player player, InteractionHand hand) {
        if (lvl.isClientSide) openPages.run();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), lvl.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel sl) || target instanceof HollowbellEntity)
            return InteractionResult.PASS;
        // the same as "go after what I look at", with the book held right on it
        String said = CodexOrders.attackLooked(
                (ServerPlayer) player, new CodexPayload(CodexPayload.ATTACK_THAT, target.getId()), true);
        if (!said.startsWith("look_going")) return InteractionResult.FAIL;
        sl.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.6f);
        return InteractionResult.SUCCESS;
    }
}
