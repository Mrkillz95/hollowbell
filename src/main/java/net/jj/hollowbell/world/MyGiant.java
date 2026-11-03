package net.jj.hollowbell.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.jj.hollowbell.GiantsPlayers;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.item.CodexItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Giants Guide's My Giant and starting kit (ADMIN_BRIDGE.md 20.5): "mygiant" takes a player who picked him to
 * the middle of his ground (the "Getting the land ready" way) and there gives his finder, his book when nobody holds
 * it, and one of him near them (within the world's limit); "give" puts his book, finder or armour in a new player's
 * pack. What happened goes back to the Guide by GuideBridge "mygiantdone".
 */
public final class MyGiant {
    private MyGiant() {}

    public static final String NAME = "Hollowbell";

    /** (the tests) the last outcome told to the Guide: "outcome spawned holder" */
    public static volatile String lastDone = "";

    /** GiantsBridge "mygiant" */
    public static @Nullable List<String> pick(MinecraftServer server, String arg) {
        UUID who;
        boolean book, finder, giant;
        try {
            JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("players").getAsInt() > GiantsPlayers.VERSION) return null;
            who = UUID.fromString(o.get("player").getAsString());
            book = !o.has("book") || o.get("book").getAsBoolean();
            finder = !o.has("finder") || o.get("finder").getAsBoolean();
            giant = !o.has("giant") || o.get("giant").getAsBoolean();
        } catch (RuntimeException e) {
            return null;
        }
        if (server == null) return null;
        ServerPlayer p = server.getPlayerList().getPlayer(who);
        if (p == null) return List.of("no", "That player isn't online.");
        WorldOne w = WorldOne.get(server);
        if (!w.homeClaimed()) return List.of("no", NAME + " has no ground yet.");
        ServerLevel l = server.overworld();
        int bx = w.homeX(), bz = w.homeZ();
        Function<ServerPlayer, Component> arrive = q -> {
            // (as /giants home hollowbell: onto safe ground at the middle of his ground)
            BlockPos at = TakeMe.safeNear(l, bx, bz, 48);
            q.teleportTo(l, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, q.getYRot(), 0f);
            q.fallDistance = 0;
            return Component.literal(arrived(q, book, finder, giant, new Vec3(bx + 0.5, at.getY(), bz + 0.5)));
        };
        if (NoWait.loadedAround(l, bx, bz)) {
            Component said = arrive.apply(p);
            return List.of("ok", said.getString());
        }
        NoWait.go(p, l, bx, bz, q -> {
            Component c = arrive.apply(q);
            return c;
        });
        NoWait.onGiveUp(p.getUUID(), () -> done(server, who, "failed", false, ""));
        return List.of("ok", Component.translatable("command.hollowbell.tp_soon").getString());
    }

    /** at his ground: the finder, the book, one of him; the line to tell them */
    static String arrived(ServerPlayer p, boolean book, boolean finder, boolean giant, Vec3 home) {
        MinecraftServer server = p.server;
        if (finder) put(p, new ItemStack(ModItems.FINDER));
        BookRule r = BookRule.get(server);
        boolean mayOwn = GiantsPlayers.mayOwn(p) && GiantsPlayers.books();
        String outcome;
        String holder = "";
        boolean spawned = false;
        String line;
        if (!book || !mayOwn) {
            outcome = "placed";
            line = "Welcome to the " + NAME + "'s ground.";
            if (!GiantsPlayers.mayOwn(p)) line += " You may not own giants in this world.";
            else if (!GiantsPlayers.books()) line += " Giants' books are turned off in this world.";
        } else if (r.holder() != null && !r.holder().equals(p.getUUID())) {
            // (1.11.0, My Giant on: BOOK_RULE.md's exception) their own book and their own one of him, while there's
            // room under his Most at once; at the cap, or with nowhere to put him, they share as before
            boolean full = false;
            String me = p.getGameProfile().getName();
            String own = null;
            if (GiantsPlayers.myGiant()) {
                int cap = BellConfig.V.maxInWorld;
                if (r.own(p.getUUID()) != null) {
                    // (picked again while their own book and giant still stand: kept; a lost book comes back)
                    if (!r.carriesOwn(p)) put(p, r.ownCopy(p));
                    own = "The " + NAME + " is yours: his book is in your pack.";
                } else if (giant && (cap <= 0 || WorldOne.standing(server) < cap)) {
                    HollowbellEntity e = WorldOne.companionNear(p.serverLevel(), p.position(),
                            Math.max(12, 110 * BellConfig.V.worldScale), home, null);
                    if (e != null) {
                        e.setMyOwner(p.getUUID());
                        put(p, r.makeOwn(p, e.getUUID()));
                        own = "The " + NAME + " is yours: his book is in your pack, and he's coming to you.";
                    }
                } else full = giant;
            }
            if (own != null) {
                outcome = "own";
                holder = me;
                spawned = true;
                line = own;
            } else {
                outcome = "shared";
                holder = r.holderName();
                BellWorld ww = BellWorld.get(server);
                ww.addFriend(r.holder(), p.getUUID());
                ww.rememberName(p.getUUID(), me);
                line = full
                        ? "This world already has as many of the " + NAME + " as it allows, so you share " + holder
                                + "'s: you're on " + holder + "'s safe list and he won't hurt you."
                        : holder + " has the " + NAME + " book, so you share theirs: you're on " + holder
                                + "'s safe list and he won't hurt you.";
            }
        } else {
            if (r.holder() == null) giveBook(p);
            holder = p.getGameProfile().getName();
            int cap = BellConfig.V.maxInWorld;
            if (giant && (cap <= 0 || WorldOne.standing(server) < cap)) {
                HollowbellEntity e = WorldOne.companionNear(p.serverLevel(), p.position(),
                        Math.max(12, 110 * BellConfig.V.worldScale), home, null);
                spawned = e != null;
            }
            if (spawned || !giant) {
                outcome = "own";
                line = "The " + NAME + " is yours: his book is in your pack" + (spawned ? ", and he's coming to you."
                        : ".");
            } else {
                outcome = "own_cap";
                line = "This world already has as many of the " + NAME + " as it allows, so you take charge of the ones"
                        + " out there: his book is in your pack.";
            }
        }
        done(server, p.getUUID(), outcome, spawned, holder);
        return line;
    }

    /** a fresh book into their pack, held by them (BOOK_RULE.md) */
    static void giveBook(ServerPlayer p) {
        ItemStack b = new ItemStack(ModItems.CODEX);
        BookRule.get(p.server).given(p, b);
        put(p, b);
    }

    private static void put(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    /** tells the Guide what happened (GuideBridge "mygiantdone"), if it is there */
    static void done(MinecraftServer server, UUID who, String outcome, boolean spawned, String holder) {
        lastDone = outcome + " " + spawned + " " + holder;
        JsonObject o = new JsonObject();
        o.addProperty("players", GiantsPlayers.VERSION);
        o.addProperty("player", who.toString());
        o.addProperty("giant", "hollowbell");
        o.addProperty("outcome", outcome);
        o.addProperty("holder", holder);
        o.addProperty("spawned", spawned);
        try {
            Class<?> c = Class.forName("net.jj.giants.GuideBridge", true, MyGiant.class.getClassLoader());
            Method m = c.getMethod("guide", MinecraftServer.class, String.class, String.class);
            m.invoke(null, server, "mygiantdone", o.toString());
        } catch (ClassNotFoundException e) {
            // (no Guide: nobody to tell)
        } catch (ReflectiveOperationException | RuntimeException e) {
            HollowbellMod.LOG.debug("Couldn't tell the Guide about a pick: {}", e.toString());
        }
    }

    /** GiantsBridge "give": {"players":1,"player":"<uuid>","what":"book"|"finder"|"armour"} */
    public static @Nullable List<String> give(MinecraftServer server, String arg) {
        UUID who;
        String what;
        try {
            JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("players").getAsInt() > GiantsPlayers.VERSION) return null;
            who = UUID.fromString(o.get("player").getAsString());
            what = o.get("what").getAsString();
        } catch (RuntimeException e) {
            return null;
        }
        if (server == null) return null;
        ServerPlayer p = server.getPlayerList().getPlayer(who);
        if (p == null) return List.of("no", "That player isn't online.");
        switch (what) {
            case "finder" -> put(p, new ItemStack(ModItems.FINDER));
            case "armour" -> {
                wear(p, ModItems.BELL_HELMET, EquipmentSlot.HEAD);
                wear(p, ModItems.BELL_CHESTPLATE, EquipmentSlot.CHEST);
                wear(p, ModItems.BELL_LEGGINGS, EquipmentSlot.LEGS);
                wear(p, ModItems.BELL_BOOTS, EquipmentSlot.FEET);
            }
            case "book" -> {
                if (!GiantsPlayers.mayOwn(p)) return List.of("no", "You may not own giants in this world.");
                if (!GiantsPlayers.books()) return List.of("no", "Giants' books are turned off in this world.");
                BookRule r = BookRule.get(server);
                if (r.holder() != null && !r.holder().equals(p.getUUID()))
                    return List.of("no", r.holderName() + " has the " + NAME + " book.");
                if (r.holder() == null) giveBook(p);
            }
            default -> {
                return null;
            }
        }
        return List.of("ok");
    }

    private static void wear(ServerPlayer p, Item item, EquipmentSlot slot) {
        if (p.getItemBySlot(slot).isEmpty()) p.setItemSlot(slot, new ItemStack(item));
        else put(p, new ItemStack(item));
    }

    public static void forget() {
        lastDone = "";
    }

    // (1.11.0) who answers whom (BOOK_RULE.md "Exception: My Giant gives every picker their own book")

    /**
     * The player this one answers to alone, while My Giant is on (null: he answers the main book, as any other). In
     * the world: his own mark; out of it: the mark in his saved body; in land not loaded: the owners' entries.
     */
    public static @Nullable UUID ownerOf(MinecraftServer server, UUID id, @Nullable HollowbellEntity h) {
        if (h != null) return h.boundOwner();
        if (!GiantsPlayers.myGiant()) return null;
        Away.Rec r = Away.get(server).get(id);
        if (r != null && r.body != null && r.body.hasUUID("MyOwner")) return r.body.getUUID("MyOwner");
        return BookRule.get(server).ownerOfGiant(id);
    }

    /**
     * He answers this player's book: one bound to somebody answers his owner alone (their own book or the main one);
     * every other one answers the main book only. With My Giant off: whoever carries the working book, as shipped.
     */
    public static boolean answers(ServerPlayer p, @Nullable UUID boundOwner) {
        if (boundOwner != null) return boundOwner.equals(p.getUUID()) && CodexItem.carriedBy(p);
        return CodexItem.carriedMainBy(p);
    }

    public static boolean answers(ServerPlayer p, HollowbellEntity h) {
        return answers(p, h.boundOwner());
    }
}
