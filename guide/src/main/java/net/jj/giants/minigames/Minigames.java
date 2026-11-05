package net.jj.giants.minigames;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminCommand;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.FunGiants;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26) The minigames: one at a time on a server, each round in a world of its own made for
 * it and thrown away after, so the main world is never touched. An admin (or a moderator, while Moderators run
 * minigames is on) opens one from the book's Minigames page or /giants minigame start, for everyone online (they are
 * all taken) or open (players join from the Guide's This world page, the chat's [Join], or /giants join; [Watch] and
 * /giants spectate to watch). Everyone's things in the main world are kept safe on the way in and put back exactly on
 * the way out: on leaving, logging out, the server stopping, and after a crash (on their next login).
 */
public final class Minigames {
    private Minigames() {}

    /** every mode, in the book's order */
    public static final Map<String, MgMode> MODES = new LinkedHashMap<>();

    static {
        for (MgMode m : List.of(new Royale(), new BossRush(), new Siege(), new Escort(), new Legends(),
                new Possession(), new RivalTamers(), new Bounty(), new KaijuWar(), new OneLife(), new Speedrun()))
            MODES.put(m.id(), m);
    }

    /** the minigame open or running now (null: none) */
    static @Nullable MgGame current;
    /** players the minigames are moving between worlds right now (not "leaving by other means") */
    static final Set<UUID> moving = ConcurrentHashMap.newKeySet();
    private static boolean changed;
    private static int cleanAt = -1;

    public static @Nullable MgGame current() {
        return current;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Minigames::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            MgLevels.cleanOnStart(s);
            cleanAt = s.getTickCount() + 100;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(Minigames::stopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> forget());
        ServerPlayConnectionEvents.JOIN.register((h, sender, s) -> joined(h.player));
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> {
            if (current != null) current.gone(h.player);
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register(Minigames::allowDeath);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(Minigames::allowDamage);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(Minigames::afterDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
            MgGame g = current;
            if (g != null && e.level() == g.level && !(e instanceof ServerPlayer) && g.giants.of(e.getUUID()) == null
                    && g.phase == MgGame.Phase.PLAY) g.mode.creatureDied(g, e, src);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(Minigames::changedWorld);
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> MgCommand.register(d));
    }

    /** a world stopped (and the tests) */
    public static void forget() {
        current = null;
        moving.clear();
        MgLevels.forget();
        MgGiants.forgetMoods();
        MgAdmin.forget();
        cleanAt = -1;
    }

    // who may

    /** may open and stop minigames: an admin, or a moderator while the admins allow it */
    public static boolean staff(ServerPlayer p) {
        if (p.hasPermissions(AdminCommand.OPS)) return true;
        return AdminWorld.get(p.server).isMod(p.getUUID()) && MgCatalog.on(p.server, "mgMods");
    }

    static boolean quiet(MinecraftServer server) {
        return net.jj.giants.tools.Tools.quiet(server);
    }

    // starting and stopping

    public static AdminServer.Result start(MinecraftServer server, String who, String mode, boolean force,
            int rounds) {
        MgMode m = MODES.get(mode.toLowerCase(java.util.Locale.ROOT));
        if (m == null) return AdminServer.Result.fail("No minigame called " + mode + ". Try one of: "
                + String.join(", ", MODES.keySet()) + ".");
        if (current != null) return AdminServer.Result.fail(current.mode.name() + " is on already. Stop it first"
                + " (/giants minigame stop).");
        if (net.jj.giants.tools.Tools.on(server, "paused"))
            return AdminServer.Result.fail("All giants are paused right now. Unpause them first (Server tools).");
        String why = m.cannot(server);
        if (why != null) return AdminServer.Result.fail(m.name() + " can't be played now: " + why);
        int r = rounds > 0 ? Math.min(5, rounds) : Math.max(1, Math.min(5, MgCatalog.num(server, "mgRounds")));
        MgGame g = new MgGame(server, m, force, who, r);
        current = g;
        g.open();
        changed();
        String line = m.name() + (force ? " starts in " + g.lobbyLeft / 20 + " s for everyone online."
                : " is open: players have " + g.lobbyLeft / 20 + " s to join.");
        return new AdminServer.Result(true, List.of(line), List.of(who + " opened " + m.name() + (force
                ? " for everyone online." : ".")));
    }

    public static AdminServer.Result stop(MinecraftServer server, String who) {
        MgGame g = current;
        if (g == null) return AdminServer.Result.fail("No minigame is on.");
        g.stop(who, g.mode.name() + " was stopped by " + who + ".");
        return new AdminServer.Result(true, List.of(g.mode.name() + " is stopped: everyone is back where they were."),
                List.of(who + " stopped " + g.mode.name() + "."));
    }

    /** the lobby's countdown cut short: it starts now (once the map is ready) */
    public static AdminServer.Result now(MinecraftServer server, String who) {
        MgGame g = current;
        if (g == null) return AdminServer.Result.fail("No minigame is on.");
        if (g.phase != MgGame.Phase.LOBBY) return AdminServer.Result.fail(g.mode.name() + " has started already.");
        g.lobbyLeft = 0;
        return AdminServer.Result.said(List.of(g.mode.name() + " starts as soon as its map is ready."));
    }

    /** a game finished (or was stopped): nothing on any more */
    static void ended(MgGame g) {
        if (current == g) current = null;
        changed();
    }

    // players

    public static AdminServer.Result join(ServerPlayer p, boolean watch) {
        MgGame g = current;
        if (g == null) return AdminServer.Result.fail("No minigame is open right now.");
        return g.join(p, watch);
    }

    public static AdminServer.Result leave(ServerPlayer p) {
        MgGame g = current;
        if (g == null) return AdminServer.Result.fail("You aren't in a minigame.");
        return g.leave(p);
    }

    /** someone logged in: anything kept from a minigame put back at once (a crash or a stop left it waiting) */
    static void joined(ServerPlayer p) {
        MinecraftServer server = p.server;
        MgGame g = current;
        MgGame.P in = g == null ? null : g.of(p.getUUID());
        if (MgSnap.waiting(server, p.getUUID()) && (in == null || !in.in)) restore(p);
        if (g == null) return;
        if (g.force && g.phase == MgGame.Phase.LOBBY) g.addForced(p);
        else if (g.phase == MgGame.Phase.LOBBY && (g.tell && !quiet(server)))
            p.sendSystemMessage(g.invite(g.mode.name() + " is open: it starts in " + g.lobbyLeft / 20 + " s."));
        else if (g.phase != MgGame.Phase.LOBBY && g.phase != MgGame.Phase.DONE && g.watching && g.tell && !quiet(server))
            p.sendSystemMessage(Component.literal("[Giants] ").withStyle(ChatFormatting.GOLD).append(Component.literal(
                    g.mode.name() + " is being played. ").withStyle(ChatFormatting.YELLOW)).append(MgGame.click("Watch",
                    "/giants spectate", "Watch " + g.mode.name(), ChatFormatting.AQUA)));
    }

    /** put back from the kept file (and the file gone once their player file is saved) */
    static boolean restore(ServerPlayer p) {
        CompoundTag t = MgSnap.read(p.server, p.getUUID());
        if (t == null) return false;
        moving.add(p.getUUID());
        try {
            MgSnap.apply(p, t);
        } finally {
            moving.remove(p.getUUID());
        }
        MgSnap.savePlayer(p);
        MgSnap.delete(p.server, p.getUUID());
        p.sendSystemMessage(Component.literal("You were in a minigame when it ended without you: you're back where"
                + " you were, with everything you had.").withStyle(ChatFormatting.GOLD));
        GiantsGuideMod.LOG.info("Giants minigames: put {} back from their kept things", p.getGameProfile().getName());
        return true;
    }

    // the events

    private static void tick(MinecraftServer server) {
        MgLevels.tick(server);
        if (cleanAt >= 0 && server.getTickCount() >= cleanAt) {
            cleanAt = -1;
            cleanGiants(server);
        }
        MgGame g = current;
        if (g != null) {
            try {
                g.tick();
            } catch (RuntimeException e) {
                GiantsGuideMod.LOG.error("Giants minigames: {} broke; everyone is sent back", g.mode.name(), e);
                try {
                    g.finish("Something went wrong in " + g.mode.name() + ", so it ended. You're back where you"
                            + " were.");
                } catch (RuntimeException e2) {
                    GiantsGuideMod.LOG.error("Giants minigames: sending everyone back", e2);
                    current = null;
                }
            }
        }
        if (changed && server.getTickCount() % 10 == 0) {
            changed = false;
            AdminBook.changed(MgAdmin.KIND);
            MgAdmin.flush(server);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) MgAdmin.guidePage(p);
        }
        if (g != null && server.getTickCount() % 20 == 0) MgAdmin.flush(server);
    }

    /**
     * Giants a crash left in a minigame's world (out of the world, or noted there): each giant's own remove, so none
     * of them comes back into the main world where that world was.
     */
    static int cleanGiants(MinecraftServer server) {
        int n = 0;
        for (Giant k : AdminServer.giants()) {
            List<String> lines = AdminServer.call(server, k, AdminLive.LIST, String.valueOf(AdminLive.VERSION));
            if (lines == null) continue;
            for (int i = 1; i < lines.size(); i++) {
                JsonObject r = AdminJson.object(lines.get(i));
                if (r == null || !MgLevels.temporary(AdminJson.str(r, "dim", 128))) continue;
                ServerLevel l = server.getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        ResourceLocation.parse(AdminJson.str(r, "dim", 128))));
                if (l != null) continue;
                JsonObject o = new JsonObject();
                o.addProperty("live", AdminLive.VERSION);
                o.addProperty("id", AdminJson.str(r, "id", 40));
                o.addProperty("do", "remove");
                o.addProperty("value", "");
                o.addProperty("by", "-");
                AdminServer.call(server, k, AdminLive.ACT, o.toString());
                n++;
            }
        }
        if (n > 0) GiantsGuideMod.LOG.info("Giants minigames: took away {} giant(s) left in old minigame worlds", n);
        return n;
    }

    private static void stopping(MinecraftServer server) {
        MgGame g = current;
        current = null;
        if (g != null) {
            try {
                g.stopNow();
            } catch (RuntimeException e) {
                GiantsGuideMod.LOG.error("Giants minigames: sending everyone back as the server stops", e);
            }
        }
        for (ServerLevel l : MgLevels.standing(server)) MgLevels.close(server, l);
    }

    private static boolean allowDeath(LivingEntity e, DamageSource src, float amount) {
        if (!(e instanceof ServerPlayer sp)) return true;
        MgGame g = current;
        if (g == null) return true;
        MgGame.P p = g.of(sp.getUUID());
        if (p == null || !p.in) return true;
        sp.setHealth(sp.getMaxHealth());
        sp.clearFire();
        sp.removeAllEffects();
        sp.fallDistance = 0;
        if (p.watcher || !p.alive || p.down) return false;
        if (src.getEntity() instanceof ServerPlayer by && by != sp) {
            MgGame.P q = g.of(by.getUUID());
            if (q != null) q.kos++;
        }
        g.mode.knockedOut(g, p, sp, src);
        changed();
        return false;
    }

    private static boolean allowDamage(LivingEntity e, DamageSource src, float amount) {
        MgGame g = current;
        if (g == null || !MgLevels.temporary(e.level())) return true;
        if (e instanceof ServerPlayer sp) {
            MgGame.P p = g.of(sp.getUUID());
            if (p == null || !p.in) return true;
            if (p.watcher || !p.alive || p.down) return false;
            // nobody is hurt before the round starts or after it ends
            if (g.phase != MgGame.Phase.PLAY) return false;
            if (src.getEntity() instanceof ServerPlayer by && by != sp) {
                MgGame.P q = g.of(by.getUUID());
                if (q != null && !g.pvpNow()) return false;
                if (q != null && g.mode.teams() && q.team >= 0 && q.team == p.team) return false;
            }
            return g.mode.playerMayBeHurt(g, p, src);
        }
        // a player who is out (or watching) can't hit anything
        MgGame.P q = src.getEntity() instanceof ServerPlayer by ? g.of(by.getUUID()) : null;
        if (q != null && (q.watcher || !q.alive || q.down)) return false;
        // the mode's say over its giants (an escorted one, a team's own)
        MgGiants.G giant = g.giants.of(e.getUUID());
        if (giant != null && !g.mode.giantMayBeHurt(g, giant, src, q)) return false;
        return true;
    }

    private static void afterDamage(LivingEntity e, DamageSource src, float base, float taken, boolean blocked) {
        MgGame g = current;
        if (g == null || taken <= 0 || e.level() != g.level) return;
        if (!(src.getEntity() instanceof ServerPlayer by)) return;
        MgGame.P q = g.of(by.getUUID());
        if (q == null) return;
        MgGiants.G giant = g.giants.of(e.getUUID());
        if (giant != null) {
            g.giants.hurt(giant, by.getUUID(), taken);
            q.dealt += taken;
        }
    }

    private static void changedWorld(ServerPlayer p, ServerLevel from, ServerLevel to) {
        if (moving.contains(p.getUUID())) return;
        MgGame g = current;
        if (g == null || from != g.level || to == g.level) return;
        // (a giant's own place, Pitchgut's innards, is part of the round: they come back from there)
        if (!to.dimension().location().getNamespace().equals("minecraft")) return;
        MgGame.P q = g.of(p.getUUID());
        if (q == null || !q.in) return;
        // left the round's world some other way (a command, a portal): out of the round, and put back
        GiantsGuideMod.LOG.info("Giants minigames: {} left {}'s world another way: put back", p.getGameProfile()
                .getName(), g.mode.name());
        g.leave(p);
        p.sendSystemMessage(Component.literal("You left " + g.mode.name() + "'s world, so you're out of it and back"
                + " where you were.").withStyle(ChatFormatting.GOLD));
    }

    // telling

    /** a game's result: everyone online (Tell everyone on, not in quiet mode), else the players in it and staff */
    static void tellResult(MinecraftServer server, MgGame g, String line) {
        Component c = Component.literal("[Giants] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(line).withStyle(ChatFormatting.YELLOW));
        GiantsGuideMod.LOG.info("Giants minigames: {}", line);
        boolean all = g.tell && !quiet(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            MgGame.P q = g.of(p.getUUID());
            if (all || (q != null && !q.in) || staff(p)) p.sendSystemMessage(c);
        }
    }

    /** a minigame challenge (Giants: Challenges), while the world has challenges on */
    static boolean challenge(ServerPlayer p, String id) {
        if (!net.jj.giants.players.Players.on(p.server, "achievements")) return false;
        var adv = p.server.getAdvancements();
        AdvancementHolder a = adv.get(ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "challenges/" + id));
        if (a == null) return false;
        AdvancementHolder root = adv.get(ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "challenges/root"));
        if (root != null) for (String c : root.value().criteria().keySet()) p.getAdvancements().award(root, c);
        boolean any = false;
        for (String c : a.value().criteria().keySet()) any |= p.getAdvancements().award(a, c);
        return any;
    }

    /** the book's Minigames page and the players' This world page need sending again */
    static void changed() {
        changed = true;
    }

    // the tests

    /** (the tests) whatever is on, stopped: everyone home */
    public static void stopForTests(MinecraftServer server) {
        MgGame g = current;
        if (g != null) g.stop("Tester", "Stopped for a test.");
        current = null;
    }

    /** (the tests) the giants noted in minigame worlds that are gone, taken away (how many) */
    public static int cleanGiantsForTests(MinecraftServer server) {
        return cleanGiants(server);
    }

    /** (the tests) the server stopping */
    public static void stoppingForTests(MinecraftServer server) {
        stopping(server);
    }

    /** (the tests) a crash: the game and its worlds gone, nobody put back (only the kept files are left) */
    public static void crashForTests(MinecraftServer server) {
        MgGame g = current;
        current = null;
        if (g == null) return;
        for (MgGame.P p : g.people.values()) p.in = false;
        g.giants.removeAll(g.level);
        g.bar.removeAllPlayers();
        if (g.level != null) MgLevels.close(server, g.level);
    }

    // for the other parts

    /** an entity in a minigame's world (the stats, the leaderboards and the systems leave it out) */
    public static boolean inMinigame(@Nullable Entity e) {
        return e != null && MgLevels.temporary(e.level());
    }

    /** (the tests) a giant kind the mode would use: every installed one, in the Guide's order */
    static List<String> kinds() {
        List<String> out = new ArrayList<>();
        for (Giant g : AdminServer.giants()) out.add(g.key());
        return out;
    }

    /** a kind's plain name */
    static String name(String kind) {
        Giant g = AdminServer.giant(kind);
        return g == null ? kind : AdminServer.nameOf(g);
    }

    /** his height at a size (blocks) */
    static double height(String kind, double size) {
        return FunGiants.height(kind, size);
    }
}
