package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;

import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.item.CodexItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/** One Hollowbell book at a time: whoever has it holds it, online or not, and every other copy has gone quiet. */
public class BookTests implements FabricGameTest {
    private static Vec3 at(GameTestHelper h, int dx) {
        return Vec3.atCenterOf(h.absolutePos(new BlockPos(1 + dx, 2, 1)));
    }

    private static BookRule rule(GameTestHelper h) {
        return BookRule.get(h.getLevel().getServer());
    }

    /** a player who has the book, seen holding it */
    private static ServerPlayer holder(GameTestHelper h) {
        ServerPlayer p = player(h, at(h, 0));
        p.getInventory().add(new ItemStack(ModItems.CODEX));
        rule(h).look(List.of(p));
        h.assertTrue(p.getUUID().equals(rule(h).holder()), "the first one with the book doesn't hold it");
        return p;
    }

    private static int books(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(ModItems.CODEX)) n++;
        if (p.containerMenu.getCarried().is(ModItems.CODEX)) n++;
        return n;
    }

    private static int bookSlot(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(ModItems.CODEX)) return i;
        return -1;
    }

    private static ItemEntity onGround(GameTestHelper h, ServerPlayer p, ItemStack s) {
        ItemEntity it = new ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), s);
        it.setNoPickUpDelay();
        h.getLevel().addFreshEntity(it);
        return it;
    }

    private static ItemStack oldCopy() {
        ItemStack s = new ItemStack(ModItems.CODEX);
        UUID id = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putUUID("HollowbellBook", id));
        return s;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "book_held")
    public void nobodyElseGetsTheBookWhileItsHeldEvenOffline(GameTestHelper h) {
        rule(h).free();
        ServerPlayer a = holder(h), b = player(h, at(h, 2));
        // picking one up
        ItemEntity it = onGround(h, b, oldCopy());
        it.playerTouch(b);
        h.assertTrue(books(b) == 0 && !it.isRemoved(), "the second player picked a book up");
        // taking one out of a chest
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, oldCopy());
        ChestMenu menu = ChestMenu.threeRows(1, b.getInventory(), chest);
        b.containerMenu = menu;
        menu.clicked(0, 0, ClickType.QUICK_MOVE, b);
        menu.clicked(0, 0, ClickType.PICKUP, b);
        h.assertTrue(books(b) == 0 && chest.getItem(0).is(ModItems.CODEX), "the second player took a book out");
        // crafting one
        CraftingMenu craft =
                new CraftingMenu(2, b.getInventory(), ContainerLevelAccess.create(h.getLevel(), b.blockPosition()));
        b.containerMenu = craft;
        ItemStack[] grid = {
            ItemStack.EMPTY,
            new ItemStack(ModItems.POD),
            ItemStack.EMPTY,
            new ItemStack(ModItems.BELL_GLASS),
            new ItemStack(Items.BOOK),
            new ItemStack(ModItems.BELL_GLASS),
            ItemStack.EMPTY,
            new ItemStack(ModItems.BELL_GLASS),
            ItemStack.EMPTY
        };
        for (int i = 0; i < 9; i++) craft.getSlot(1 + i).set(grid[i]);
        craft.slotsChanged(craft.getSlot(1).container);
        h.assertTrue(craft.getSlot(0).getItem().is(ModItems.CODEX), "the book recipe didn't come up");
        craft.clicked(0, 0, ClickType.QUICK_MOVE, b);
        craft.clicked(0, 0, ClickType.PICKUP, b);
        h.assertTrue(books(b) == 0, "the second player crafted a book");
        b.containerMenu = b.inventoryMenu;
        // the holder logs out: still theirs
        drop(a);
        rule(h).look(List.of(b));
        it.playerTouch(b);
        h.assertTrue(books(b) == 0 && a.getUUID().equals(rule(h).holder()), "the book was free once its holder left");
        it.discard();
        drop(b);
        rule(h).free();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "book_dropped")
    public void aDroppedBookIsAnyonesToPickUp(GameTestHelper h) {
        rule(h).free();
        ServerPlayer a = holder(h), b = player(h, at(h, 2));
        int slot = bookSlot(a);
        ItemStack book = a.getInventory().removeItemNoUpdate(slot);
        ItemEntity it = onGround(h, b, book);
        rule(h).look(List.of(a, b));
        h.assertTrue(rule(h).holder() == null, "the book was still held once dropped");
        it.playerTouch(b);
        h.assertTrue(books(b) == 1 && b.getUUID().equals(rule(h).holder()), "the second player couldn't pick it up");
        h.assertTrue(CodexItem.carriedBy(b), "the book they picked up doesn't work");
        drop(a);
        drop(b);
        rule(h).free();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "book_death")
    public void dyingWithItFreesIt(GameTestHelper h) {
        rule(h).free();
        ServerPlayer a = holder(h);
        a.kill();
        rule(h).look(List.of(a));
        h.assertTrue(rule(h).holder() == null, "the book was still held after its holder died and dropped it");
        drop(a);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "book_free")
    public void bookFreeFreesIt(GameTestHelper h) {
        rule(h).free();
        ServerPlayer a = holder(h);
        drop(a);
        var src = h.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
        h.getLevel().getServer().getCommands().performPrefixedCommand(src, "hollowbell book free");
        h.assertTrue(rule(h).holder() == null, "book free didn't free it");
        ServerPlayer b = player(h, at(h, 2));
        ItemEntity it = onGround(h, b, oldCopy());
        it.playerTouch(b);
        h.assertTrue(books(b) == 1 && b.getUUID().equals(rule(h).holder()), "after book free it couldn't be picked up");
        drop(b);
        rule(h).free();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "book_dupe")
    public void aDuplicateGoesQuiet(GameTestHelper h) {
        rule(h).free();
        ServerPlayer a = holder(h), b = player(h, at(h, 2));
        int slot = bookSlot(a);
        b.getInventory().add(a.getInventory().getItem(slot).copy());
        rule(h).look(List.of(a, b));
        h.assertTrue(CodexItem.carriedBy(a), "the holder's book stopped working");
        h.assertTrue(!CodexItem.carriedBy(b) && CodexItem.onlyQuiet(b), "the duplicate works");
        int bs = bookSlot(b);
        h.assertTrue(BookRule.quiet(b.getInventory().getItem(bs)), "the duplicate isn't marked quiet");
        drop(a);
        drop(b);
        rule(h).free();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "book_old_world")
    public void anOldWorldWithTwoBooksKeepsOneWorking(GameTestHelper h) {
        rule(h).free();
        ServerPlayer a = player(h, at(h, 0)), b = player(h, at(h, 2));
        a.getInventory().add(oldCopy());
        b.getInventory().add(oldCopy());
        rule(h).look(List.of(a, b));
        int working = (CodexItem.carriedBy(a) ? 1 : 0) + (CodexItem.carriedBy(b) ? 1 : 0);
        h.assertTrue(working == 1, "working books: " + working);
        drop(a);
        drop(b);
        rule(h).free();
        h.succeed();
    }

    /** the holder's book goes from their pack into their ender chest */
    private static void putAway(ServerPlayer p) {
        p.getEnderChestInventory().setItem(0, p.getInventory().removeItemNoUpdate(bookSlot(p)));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "book_ender_in")
    public void puttingItInYourEnderChestFreesIt(GameTestHelper h) {
        rule(h).free();
        ServerPlayer a = holder(h), b = player(h, at(h, 2));
        putAway(a);
        rule(h).look(List.of(a, b));
        h.assertTrue(rule(h).holder() == null, "the book was still held from the ender chest");
        h.assertTrue(!CodexItem.carriedBy(a), "the book in the ender chest still gives orders");
        ItemEntity it = onGround(h, b, oldCopy());
        it.playerTouch(b);
        h.assertTrue(books(b) == 1 && b.getUUID().equals(rule(h).holder()), "the second player couldn't pick one up");
        drop(a);
        drop(b);
        rule(h).free();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "book_ender_out")
    public void noTakingItOutOfAnEnderChestWhileSomeoneElseHasIt(GameTestHelper h) {
        rule(h).free();
        ServerPlayer a = holder(h), b = player(h, at(h, 2));
        putAway(a);
        rule(h).look(List.of(a, b));
        onGround(h, b, oldCopy()).playerTouch(b);
        h.assertTrue(b.getUUID().equals(rule(h).holder()), "the second player doesn't hold the book");
        Slot inChest = new Slot(a.getEnderChestInventory(), 0, 0, 0);
        h.assertFalse(inChest.mayPickup(a), "the first took theirs out of the ender chest");
        drop(b);
        rule(h).look(List.of(a));
        h.assertFalse(inChest.mayPickup(a), "the first took it out while the holder is away");
        drop(a);
        rule(h).free();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "book_ender_login")
    public void aHolderWithItInTheirEnderChestIsFreedOnLogin(GameTestHelper h) {
        rule(h).free();
        ServerPlayer a = holder(h);
        drop(a);
        // from a world where the ender chest still counted: the working book waits there while they are away
        putAway(a);
        h.assertTrue(a.getUUID().equals(rule(h).holder()), "the book was freed while its holder was away");
        rule(h).look(List.of(a));
        h.assertTrue(rule(h).holder() == null, "back on with it only in the ender chest, still held");
        rule(h).free();
        h.succeed();
    }
}
