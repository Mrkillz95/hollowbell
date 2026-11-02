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
 * The Giants Admin Book (/giants adminbook, operators only). Its pages come in Part 2 (ADMIN_BOOK.md); for now it
 * points to /giants admin. In anybody else's hands it does nothing but say it is for admins.
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
        if (!level.isClientSide && player instanceof ServerPlayer p) {
            p.displayClientMessage(
                    Component.literal(p.hasPermissions(AdminCommand.OPS)
                            ? "Its pages are still being written. For now: /giants admin."
                            : "This book is for admins."),
                    true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.jj_giants.admin_book.tip").withStyle(ChatFormatting.GRAY));
    }
}
