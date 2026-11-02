package net.jj.giants.admin;

import java.util.List;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Giants Admin Book (/giants adminbook, operators only): every giant's settings for this world, on pages
 * (AdminBook, ADMIN_BOOK.md). In anybody else's hands it does nothing but say it is for admins.
 */
public class AdminBookItem extends Item {
    public static final AdminBookItem BOOK =
            new AdminBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    public AdminBookItem(Properties p) {
        super(p);
    }

    public static void register() {
        Registry.register(
                BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "admin_book"), BOOK);
    }

    /** one into their pack (or at their feet if it's full) */
    public static void give(ServerPlayer p) {
        ItemStack book = new ItemStack(BOOK);
        if (!p.getInventory().add(book)) p.drop(book, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // (the server decides: an operator's game opens the book, anyone else is told it is for admins)
        if (!level.isClientSide && player instanceof ServerPlayer p) AdminBook.open(p);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.jj_giants.admin_book.tip").withStyle(ChatFormatting.GRAY));
    }
}
