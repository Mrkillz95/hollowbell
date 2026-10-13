package net.jj.hollowbell.mixin;

import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.item.BookRule;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * one Hollowbell book at a time: it won't come out of a crafting result, a chest or any other container that isn't the
 * player's own while somebody else holds it
 */
@Mixin(Slot.class)
public abstract class BookSlotMixin {
    @Shadow
    public abstract ItemStack getItem();

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void hollowbell$oneBook(Player player, CallbackInfoReturnable<Boolean> cir) {
        ItemStack s = getItem();
        if (!(player instanceof ServerPlayer sp) || !s.is(ModItems.CODEX)) return;
        if (!BookRule.outside((Slot) (Object) this, player)) return;
        BookRule r = BookRule.get(sp.server);
        if (r.mayTake(sp, s)) return;
        r.refuse(sp);
        cir.setReturnValue(false);
    }
}
