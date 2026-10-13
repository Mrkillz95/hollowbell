package net.jj.hollowbell.mixin;

import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.item.BookRule;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** one Hollowbell book at a time: a book on the ground stays there for anyone but its holder while it's held */
@Mixin(ItemEntity.class)
public abstract class BookPickupMixin {
    @Shadow
    public abstract ItemStack getItem();

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void hollowbell$oneBook(Player player, CallbackInfo ci) {
        ItemStack s = getItem();
        if (!(player instanceof ServerPlayer sp) || !s.is(ModItems.CODEX)) return;
        BookRule r = BookRule.get(sp.server);
        if (r.mayTake(sp, s)) return;
        r.refuse(sp);
        ci.cancel();
    }
}
