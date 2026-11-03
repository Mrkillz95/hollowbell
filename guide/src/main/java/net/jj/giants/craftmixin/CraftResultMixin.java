package net.jj.giants.craftmixin;

import net.jj.giants.players.Players;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * (Part 5, ADMIN_BRIDGE.md 20.13) the crafting grid's result (the table and the pack's own 2x2): a giant's finder
 * while finders aren't craftable in this world, or a giant's book while books are off, stays empty.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftResultMixin {
    @ModifyVariable(
            method = "slotChangedCraftingGrid",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V"),
            ordinal = 0)
    private static ItemStack jj_giants$craftable(ItemStack result) {
        return Players.craftable(result) ? result : ItemStack.EMPTY;
    }
}
