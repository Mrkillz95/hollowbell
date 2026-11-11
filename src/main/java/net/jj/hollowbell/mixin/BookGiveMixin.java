package net.jj.hollowbell.mixin;

import java.util.Collection;
import net.jj.hollowbell.GiantsPlayers;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.item.BookRule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An op's /give of his book: whoever gets it holds the book now (see BookRule; the once-a-second look does the same
 * for the creative menu). (ADMIN_BRIDGE.md 20.18) One at their Max giants per player keeps a quiet copy, and the op is
 * told why.
 */
@Mixin(GiveCommand.class)
public abstract class BookGiveMixin {
    @Inject(method = "giveItem", at = @At("RETURN"))
    private static void hollowbell$givenBook(
            CommandSourceStack src,
            ItemInput item,
            Collection<ServerPlayer> targets,
            int count,
            CallbackInfoReturnable<Integer> cir) {
        if (item.getItem() != ModItems.CODEX) return;
        BookRule r = BookRule.get(src.getServer());
        for (ServerPlayer p : targets)
            if (!r.givenNew(p) && GiantsPlayers.mayOwn(p)) src.sendFailure(GiantsPlayers.opGiveLine(p));
    }
}
