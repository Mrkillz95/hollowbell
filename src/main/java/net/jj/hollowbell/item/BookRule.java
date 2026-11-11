package net.jj.hollowbell.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jj.hollowbell.GiantsPlayers;
import net.jj.hollowbell.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    /** books still to burn: the player whose Hollowbell was killed, and that book's id */
    private final Map<UUID, UUID> toBurn = new HashMap<>();

    /**
     * (1.11.0, My Giant: BOOK_RULE.md's exception) a picker's own book, outside the one-book rule: its id, their name,
     * and the one of him bound to them (his id as last seen; null until he has come)
     */
    public record Own(UUID book, String name, @Nullable UUID giant) {}

    private final Map<UUID, Own> own = new HashMap<>();
    /** the mark in an own book: whose it is (and their name, for the line when somebody else tries to take it) */
    private static final String OWNER = "HollowbellOwn", OWNER_NAME = "HollowbellOwnName";

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
        ListTag burn = tag.getList("ToBurn", Tag.TAG_COMPOUND);
        for (int i = 0; i < burn.size(); i++) {
            CompoundTag c = burn.getCompound(i);
            if (c.hasUUID("Who") && c.hasUUID("Book")) r.toBurn.put(c.getUUID("Who"), c.getUUID("Book"));
        }
        ListTag own = tag.getList("Own", Tag.TAG_COMPOUND);
        for (int i = 0; i < own.size(); i++) {
            CompoundTag c = own.getCompound(i);
            if (c.hasUUID("Who") && c.hasUUID("Book"))
                r.own.put(c.getUUID("Who"), new Own(c.getUUID("Book"), c.getString("Name"),
                        c.hasUUID("Giant") ? c.getUUID("Giant") : null));
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
        if (!toBurn.isEmpty()) {
            ListTag burn = new ListTag();
            for (Map.Entry<UUID, UUID> e : toBurn.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("Who", e.getKey());
                c.putUUID("Book", e.getValue());
                burn.add(c);
            }
            tag.put("ToBurn", burn);
        }
        if (!own.isEmpty()) {
            ListTag list = new ListTag();
            for (Map.Entry<UUID, Own> e : own.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("Who", e.getKey());
                c.putUUID("Book", e.getValue().book());
                c.putString("Name", e.getValue().name());
                if (e.getValue().giant() != null) c.putUUID("Giant", e.getValue().giant());
                list.add(c);
            }
            tag.put("Own", list);
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

    /**
     * A fresh copy given to this player, as an op's /give: they hold the book from now on and any other copy goes
     * quiet (the Giants Guide's live control)
     */
    public boolean given(ServerPlayer p, ItemStack s) {
        // (Part 5) an admin's rule: this player may not own giants, so the copy stays quiet; (20.18) the same at their
        // Max giants per player, unless they hold it already (no gain)
        if (!GiantsPlayers.mayOwn(p) || !p.getUUID().equals(holder) && !GiantsPlayers.mayGain(p, false)) {
            giveId(s);
            markQuiet(s, true);
            return false;
        }
        hold(p, giveId(s));
        markQuiet(s, false);
        return true;
    }

    /**
     * (20.18) an op's /give: every new copy (no id yet) in their pack is given as above; false when one stayed quiet
     * because they're at their Max giants per player
     */
    public boolean givenNew(ServerPlayer p) {
        boolean all = true;
        for (ItemStack s : booksOf(p))
            if (idOf(s) == null && ownerOf(s) == null && GiantsPlayers.mayOwn(p)) all &= given(p, s);
        return all;
    }

    /** /hollowbell book free: nobody holds it now */
    public void free() {
        holder = null;
        bookId = null;
        holderName = "";
        setDirty();
    }

    /**
     * His holder's Hollowbell was killed: their book burns (now, or the moment they are next on), and the book is
     * nobody's. Whoever else carries a copy takes it up next, so the next one is theirs.
     */
    public void burn(MinecraftServer server) {
        if (holder != null && bookId != null) toBurn.put(holder, bookId);
        free();
        look(server.getPlayerList().getPlayers());
    }

    /** this player's book of a killed Hollowbell is still to burn */
    public boolean burning(UUID who) {
        return toBurn.containsKey(who);
    }

    /** their copy of the dead one's book goes up in flames in their hands, with a line saying why */
    private void burnUp(ServerPlayer p) {
        UUID id = toBurn.remove(p.getUUID());
        if (id == null) return;
        setDirty();
        int burnt = 0;
        for (ItemStack s : booksOf(p))
            if (id.equals(idOf(s))) {
                burnt += s.getCount();
                s.setCount(0);
            }
        if (burnt == 0) return;
        ServerLevel sl = p.serverLevel();
        sl.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.35, 0.5, 0.35, 0.03);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 16, 0.3, 0.4, 0.3, 0.02);
        sl.sendParticles(ParticleTypes.ASH, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 0.6, 0.5, 0.0);
        sl.playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
        sl.playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.6f);
        p.sendSystemMessage(Component.translatable("message.hollowbell.book_burns"));
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
        // (Part 5) an admin's rule: this player may not own giants, so no copy works for them
        if (!net.jj.hollowbell.GiantsPlayers.mayOwn(p)) return false;
        // (My Giant) an own book: only its owner's, only while My Giant is on
        if (ownerOf(s) != null) return ownWorks(p, s);
        // (a copy with no id yet is a new one an op or the creative menu gave: whoever has it holds the book now; at
        // their Max giants per player it stays quiet)
        if (idOf(s) == null) {
            if (!p.getUUID().equals(holder) && !GiantsPlayers.mayGain(p, false)) {
                giveId(s);
                markQuiet(s, true);
                return false;
            }
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
        // (Part 5) an admin's rule: this player may not own giants
        if (!net.jj.hollowbell.GiantsPlayers.mayOwn(p)) return false;
        // (My Giant) an own book: its owner may always take it, nobody else ever; the main holder slot is untouched
        UUID mark = ownerOf(s);
        if (mark != null) return mark.equals(p.getUUID());
        if (holder == null) {
            // (20.18) taking up a free book is a gain: not past their Max giants per player
            if (!GiantsPlayers.mayGain(p, true)) return false;
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
        refuse(p, ItemStack.EMPTY);
    }

    /** the same, for this book: somebody else's own book says whose it is */
    public void refuse(ServerPlayer p, ItemStack s) {
        long now = p.serverLevel().getGameTime();
        Long last = toldAt.get(p.getUUID());
        if (last != null && now - last < 40) return;
        toldAt.put(p.getUUID(), now);
        if (!net.jj.hollowbell.GiantsPlayers.mayOwn(p)) {
            net.jj.hollowbell.GiantsPlayers.say(p, "no_own");
            return;
        }
        UUID mark = ownerOf(s);
        if (mark != null && !mark.equals(p.getUUID())) {
            p.displayClientMessage(Component.translatable("message.hollowbell.own_book_theirs", ownerName(s)), true);
            return;
        }
        // (20.18) nobody holds it: refused for their Max giants per player
        String cap = holder == null ? GiantsPlayers.capLine(p.getUUID()) : null;
        if (cap != null) {
            p.displayClientMessage(Component.literal(cap), true);
            return;
        }
        p.displayClientMessage(Component.translatable("message.hollowbell.book_taken", holderName), true);
    }

    public void sayQuiet(ServerPlayer p) {
        List<ItemStack> bs = booksOf(p);
        sayQuiet(p, bs.isEmpty() ? ItemStack.EMPTY : bs.get(0));
    }

    /** why this copy gives no orders */
    public void sayQuiet(ServerPlayer p, ItemStack s) {
        // (Part 5) quiet because an admin ruled they may not own giants: that's what they're told
        if (!net.jj.hollowbell.GiantsPlayers.mayOwn(p)) {
            net.jj.hollowbell.GiantsPlayers.say(p, "no_own");
            return;
        }
        UUID mark = ownerOf(s);
        if (mark != null) {
            String key = !mark.equals(p.getUUID())
                    ? "message.hollowbell.own_book_theirs"
                    : !ownCurrent(mark, idOf(s)) ? "message.hollowbell.own_book_fell" : "message.hollowbell.own_book_off";
            p.displayClientMessage(Component.translatable(key, ownerName(s)), true);
            return;
        }
        // (20.18) quiet because they're at their Max giants per player (nobody holds it)
        String cap = holder == null ? GiantsPlayers.capLine(p.getUUID()) : null;
        if (cap != null) {
            p.displayClientMessage(Component.literal(cap), true);
            return;
        }
        p.displayClientMessage(Component.translatable("message.hollowbell.book_quiet", holderName), true);
    }

    // (1.11.0) My Giant: own books (BOOK_RULE.md "Exception: My Giant gives every picker their own book")

    /** whose own book this is (null: the main book, or any copy of it) */
    public static @Nullable UUID ownerOf(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        CompoundTag t = d.copyTag();
        return t.hasUUID(OWNER) ? t.getUUID(OWNER) : null;
    }

    private static String ownerName(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? "" : d.copyTag().getString(OWNER_NAME);
    }

    /** this own book is its owner's current one (they have an entry and the ids match) */
    private boolean ownCurrent(UUID owner, @Nullable UUID id) {
        Own o = own.get(owner);
        return o != null && id != null && o.book().equals(id);
    }

    /** an own book that works: in its owner's hands, while My Giant is on and they may own giants */
    public boolean ownWorks(ServerPlayer p, ItemStack s) {
        UUID mark = ownerOf(s);
        return mark != null
                && mark.equals(p.getUUID())
                && net.jj.hollowbell.GiantsPlayers.myGiant()
                && net.jj.hollowbell.GiantsPlayers.mayOwn(p)
                && ownCurrent(mark, idOf(s));
    }

    public @Nullable Own own(UUID who) {
        return own.get(who);
    }

    /** every own book, by owner (for GiantsBridge "owners") */
    public Map<UUID, Own> owns() {
        return java.util.Collections.unmodifiableMap(own);
    }

    /** every own book's owner, by name (for book who) */
    public List<String> ownNames() {
        List<String> out = new ArrayList<>();
        for (Own o : own.values()) out.add(o.name());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /**
     * (a My Giant pick) a fresh own book for this player, theirs alone: it never takes or frees the main holder slot.
     * Their entry keeps the bound one's id (null until he has come).
     */
    public ItemStack makeOwn(ServerPlayer p, @Nullable UUID giant) {
        ItemStack s = new ItemStack(ModItems.CODEX);
        UUID id = UUID.randomUUID();
        String name = p.getGameProfile().getName();
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> {
            t.putUUID(ID, id);
            t.putUUID(OWNER, p.getUUID());
            t.putString(OWNER_NAME, name);
        });
        own.put(p.getUUID(), new Own(id, name, giant));
        setDirty();
        return s;
    }

    /** another copy of their current own book (they lost theirs; it works only in their hands anyway) */
    public @Nullable ItemStack ownCopy(ServerPlayer p) {
        Own o = own.get(p.getUUID());
        if (o == null) return null;
        ItemStack s = new ItemStack(ModItems.CODEX);
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> {
            t.putUUID(ID, o.book());
            t.putUUID(OWNER, p.getUUID());
            t.putString(OWNER_NAME, o.name());
        });
        return s;
    }

    /** they carry their current own book (working or not) */
    public boolean carriesOwn(ServerPlayer p) {
        Own o = own.get(p.getUUID());
        if (o == null) return false;
        for (ItemStack s : booksOf(p)) if (p.getUUID().equals(ownerOf(s)) && o.book().equals(idOf(s))) return true;
        return false;
    }

    /** the bound one was seen under this id (he can come back from out of the world under a new one) */
    public void boundSeen(UUID owner, UUID giant) {
        Own o = own.get(owner);
        if (o == null || giant.equals(o.giant())) return;
        own.put(owner, new Own(o.book(), o.name(), giant));
        setDirty();
    }

    /** the owner of the one bound under this id, by the entries (for one not loaded) */
    public @Nullable UUID ownerOfGiant(UUID giant) {
        for (Map.Entry<UUID, Own> e : own.entrySet()) if (giant.equals(e.getValue().giant())) return e.getKey();
        return null;
    }

    /**
     * A bound one fell: his owner's own book goes quiet and their entry is dropped (the main book isn't touched).
     * They are told if they are on.
     */
    public void boundFell(MinecraftServer server, UUID owner, UUID giant) {
        Own o = own.get(owner);
        if (o == null || (o.giant() != null && !o.giant().equals(giant))) return;
        own.remove(owner);
        setDirty();
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p == null) return;
        for (ItemStack s : booksOf(p)) if (owner.equals(ownerOf(s))) markQuiet(s, true);
        p.sendSystemMessage(Component.translatable("message.hollowbell.own_book_fell"));
    }

    /** (the tests) every own book forgotten */
    public void forgetOwn() {
        if (own.isEmpty()) return;
        own.clear();
        setDirty();
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
        if (!toBurn.isEmpty()) for (ServerPlayer p : players) burnUp(p);
        // new copies (an op's /give, the creative menu) make whoever has them the holder
        for (ServerPlayer p : players)
            for (ItemStack s : booksOf(p))
                if (idOf(s) == null) {
                    UUID id = giveId(s);
                    // (Part 5: one who may not own giants keeps a quiet copy; 20.18: so does one at their Max giants
                    // per player, unless they hold it already)
                    if (GiantsPlayers.mayOwn(p) && (p.getUUID().equals(holder) || GiantsPlayers.mayGain(p, false)))
                        hold(p, id);
                }
        // (Part 5) an admin ruled the holder may not own giants: the book is let go of
        if (holder != null && !net.jj.hollowbell.GiantsPlayers.mayOwn(holder)) free();
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
                List<ItemStack> bs = new ArrayList<>(booksOf(p));
                bs.removeIf(s -> ownerOf(s) != null); // (an own book never becomes the main one)
                // (20.18: one at their Max giants per player is passed over like one who may not own giants)
                if (bs.isEmpty() || !GiantsPlayers.mayOwn(p) || !GiantsPlayers.mayGain(p, false)) continue;
                hold(p, idOf(bs.get(0)));
                break;
            }
        for (ServerPlayer p : players)
            for (ItemStack s : booksOf(p))
                markQuiet(s, ownerOf(s) != null
                        ? !ownWorks(p, s)
                        : !(p.getUUID().equals(holder) && idOf(s).equals(bookId)));
    }
}
