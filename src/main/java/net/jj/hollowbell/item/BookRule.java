package net.jj.hollowbell.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jj.hollowbell.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * One Hollowbell book at a time. Whoever has it (in their pack, on their cursor or crafting grid, online or not) is
 * its holder, kept in the world's data with the book's own id. A book in an ender chest is nobody's. Nobody else can
 * craft one, pick one up or take one out of a container while it's held, and any other copy has gone quiet: it gives
 * no orders. The holder only changes when the book is seen leaving them (they're online then), or when an op frees it.
 */
public final class BookRule extends SavedData {
    private static final String NAME = "hollowbell_book";
    /** the id written into each book, and the mark the client reads to know a copy has gone quiet */
    private static final String ID = "HollowbellBook", QUIET = "HollowbellQuiet";

    private @Nullable UUID holder, bookId;
    private String holderName = "";

    public static BookRule get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(BookRule::new, BookRule::load, null), NAME);
    }

    private BookRule() {}

    private static BookRule load(CompoundTag tag, HolderLookup.Provider p) {
        BookRule r = new BookRule();
        if (tag.hasUUID("Holder") && tag.hasUUID("Book")) {
            r.holder = tag.getUUID("Holder");
            r.bookId = tag.getUUID("Book");
            r.holderName = tag.getString("Name");
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        if (holder != null && bookId != null) {
            tag.putUUID("Holder", holder);
            tag.putUUID("Book", bookId);
            tag.putString("Name", holderName);
        }
        return tag;
    }

    public @Nullable UUID holder() {
        return holder;
    }

    public String holderName() {
        return holderName;
    }

    private void hold(Player p, UUID id) {
        holder = p.getUUID();
        holderName = p.getGameProfile().getName();
        bookId = id;
        setDirty();
    }

    /** /hollowbell book free: nobody holds it now */
    public void free() {
        holder = null;
        bookId = null;
        holderName = "";
        setDirty();
    }

    // a book's own id

    public static @Nullable UUID idOf(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        CompoundTag t = d.copyTag();
        return t.hasUUID(ID) ? t.getUUID(ID) : null;
    }

    private static UUID giveId(ItemStack s) {
        UUID id = idOf(s);
        if (id != null) return id;
        UUID fresh = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putUUID(ID, fresh));
        return fresh;
    }

    /** this game's own view of a copy: marked quiet by the server when it isn't the working one */
    public static boolean quiet(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getBoolean(QUIET);
    }

    private static void markQuiet(ItemStack s, boolean on) {
        if (quiet(s) == on) return;
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> {
            if (on) t.putBoolean(QUIET, true);
            else t.remove(QUIET);
        });
    }

    // the rule

    /** this copy, in this player's hands, gives orders */
    public boolean works(ServerPlayer p, ItemStack s) {
        if (!s.is(ModItems.CODEX)) return false;
        // (a copy with no id yet is a new one an op or the creative menu gave: whoever has it holds the book now)
        if (idOf(s) == null) {
            hold(p, giveId(s));
            markQuiet(s, false);
            return true;
        }
        return p.getUUID().equals(holder) && idOf(s).equals(bookId);
    }

    /**
     * May this player get this book (crafting it, picking it up, taking it out of a container)? Only when nobody
     * holds it, or it's their own working copy moving about. A yes for a free book makes them its holder at once, so
     * two can't both grab it.
     */
    public boolean mayTake(ServerPlayer p, ItemStack s) {
        if (holder == null) {
            hold(p, giveId(s));
            markQuiet(s, false);
            return true;
        }
        UUID id = idOf(s);
        return p.getUUID().equals(holder) && id != null && id.equals(bookId);
    }

    private final Map<UUID, Long> toldAt = new HashMap<>();

    /** the plain line for a refusal (not more than once in two seconds, as a pickup is tried every tick) */
    public void refuse(ServerPlayer p) {
        long now = p.serverLevel().getGameTime();
        Long last = toldAt.get(p.getUUID());
        if (last != null && now - last < 40) return;
        toldAt.put(p.getUUID(), now);
        p.displayClientMessage(Component.translatable("message.hollowbell.book_taken", holderName), true);
    }

    public void sayQuiet(ServerPlayer p) {
        p.displayClientMessage(Component.translatable("message.hollowbell.book_quiet", holderName), true);
    }

    /** a slot of a container that isn't the player's own pack (their ender chest is outside, like any chest) */
    public static boolean outside(Slot slot, Player p) {
        return slot.container != p.getInventory();
    }

    /** every copy of the book a player has: pack, cursor, crafting grids (not their ender chest) */
    static List<ItemStack> booksOf(ServerPlayer p) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            add(out, p.getInventory().getItem(i));
        add(out, p.containerMenu.getCarried());
        for (int i = 0; i < p.inventoryMenu.getCraftSlots().getContainerSize(); i++)
            add(out, p.inventoryMenu.getCraftSlots().getItem(i));
        if (p.containerMenu != p.inventoryMenu)
            for (Slot sl : p.containerMenu.slots) if (sl.container instanceof CraftingContainer) add(out, sl.getItem());
        return out;
    }

    private static void add(List<ItemStack> out, ItemStack s) {
        if (s.is(ModItems.CODEX)) out.add(s);
    }

    /** once a second: who has it, seen on the players who are on */
    public static void serverTick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        get(server).look(server.getPlayerList().getPlayers());
    }

    /** a player leaving: looked at one last time (a book dropped just before is seen gone) */
    public static void leaving(ServerPlayer p) {
        get(p.server).look(List.of(p));
    }

    public void look(List<ServerPlayer> players) {
        // new copies (an op's /give, the creative menu) make whoever has them the holder
        for (ServerPlayer p : players) for (ItemStack s : booksOf(p)) if (idOf(s) == null) hold(p, giveId(s));
        // the holder, on and seen without the working copy: it has left them
        for (ServerPlayer p : players) {
            if (!p.getUUID().equals(holder)) continue;
            boolean has = false;
            for (ItemStack s : booksOf(p)) has |= bookId.equals(idOf(s));
            if (!has) free();
        }
        // nobody holds it (it left them, or a world from before this rule): the first seen with one has it
        if (holder == null)
            for (ServerPlayer p : players) {
                List<ItemStack> bs = booksOf(p);
                if (bs.isEmpty()) continue;
                hold(p, idOf(bs.get(0)));
                break;
            }
        for (ServerPlayer p : players)
            for (ItemStack s : booksOf(p)) markQuiet(s, !(p.getUUID().equals(holder) && idOf(s).equals(bookId)));
    }
}
