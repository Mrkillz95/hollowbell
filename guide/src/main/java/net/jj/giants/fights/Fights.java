package net.jj.giants.fights;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.systems.Systems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10B, ADMIN_BRIDGE.md §27) Giant fights: the fight script for staged fights ({@link FightScript}) and the
 * world's settings for ordinary giant fights ({@link FightWatch}). The giants ask ("fightok") and tell ("fightnews")
 * through GuideBridge only while a setting needs it; with every setting at the jar's nothing here runs.
 */
public final class Fights {
    private Fights() {}

    public static final int VERSION = 1;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Fights::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            FightShow.normal(s);
            if (FightScript.run != null) FightScript.stop(s, "The server");
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> forget());
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(Fights::allowDamage);
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> FightsCommand.register(d));
    }

    public static void forget() {
        FightScript.forget();
        FightWatch.forget();
        FightGiants.forget();
        blocks.clear();
        blockGiants.clear();
        pushed = null;
    }

    static void log(String what, Object... args) {
        GiantsGuideMod.LOG.debug("Giant fights: " + what, args);
    }

    // the settings (the world's value, else the server's default)

    static boolean on(MinecraftServer server, String id) {
        return Systems.on(server, id);
    }

    static String option(MinecraftServer server, String id) {
        return Systems.option(server, id);
    }

    static double num(MinecraftServer server, String id) {
        return Systems.num(server, id);
    }

    /** any of these settings off the jar's: the Guide follows ordinary fights */
    static boolean follows(MinecraftServer server) {
        for (AdminSetting s : FightCatalog.SETTINGS) {
            if (FightCatalog.GIANTS_APPLY.contains(s.id()) && !s.id().equals("all.ownedMayKill")) continue;
            if (!AdminSetting.same(Systems.value(server, s.id()), s.jar())) return true;
        }
        return false;
    }

    /** a rule the giants must ask about before a fight starts */
    static boolean asks(MinecraftServer server) {
        return !option(server, "ownedMayFight").equals("always") || num(server, "fightsMost") > 0
                || !option(server, "fightsWhen").equals("any") || !on(server, "fightsSleepers")
                || num(server, "wildRest") > 0 || num(server, "ownedRest") > 0 || FightScript.busy()
                || net.jj.giants.adventure.Adventure.roundOn();
    }

    /** what the giants are sent with their "all" (their own settings' server defaults, and the two flags) */
    public static Map<String, JsonElement> forGiants(@Nullable MinecraftServer server, Map<String, JsonElement> all) {
        if (server == null) return all;
        Map<String, JsonElement> out = null;
        for (String id : FightCatalog.GIANTS_APPLY) {
            if (all.containsKey(id)) continue;
            AdminSetting s = AdminCatalog.shared(id);
            if (s == null || AdminSetting.same(s.def(), s.jar())) continue;
            if (out == null) out = new LinkedHashMap<>(all);
            out.put(id, s.def());
        }
        boolean ask = asks(server), report = follows(server) || FightScript.busy();
        if (ask || report) {
            if (out == null) out = new LinkedHashMap<>(all);
            if (ask) out.put("all.fightAsk", new JsonPrimitive(true));
            if (report) out.put("all.fightReport", new JsonPrimitive(true));
        }
        return out == null ? all : out;
    }

    /** the flags the giants were last sent (sent again when they change) */
    private static @Nullable String pushed;

    /** the flags sent again now (a staged fight began) */
    public static void pushFlags(MinecraftServer server) {
        pushed = "";
        flags(server);
    }

    private static void flags(MinecraftServer server) {
        String now = asks(server) + "," + (follows(server) || FightScript.busy());
        if (now.equals(pushed)) return;
        boolean first = pushed == null;
        pushed = now;
        if (first && now.equals("false,false")) return;
        AdminServer.push(server, net.jj.giants.admin.AdminWorld.get(server));
    }

    // the clock

    private static void tick(MinecraftServer server) {
        FightShow.tick(server);
        FightScript.tick(server);
        int t = server.getTickCount();
        if (t % 20 == 0) {
            FightScript.cool(server);
            flags(server);
            watchingBlocks = blocksRule(server);
            if (t % 100 == 0) ownerTags(server);
            if (!FightWatch.fights.isEmpty() || !FightWatch.chases.isEmpty()
                    || !FightData.get(server).s.follows.isEmpty()) FightWatch.tick(server);
        }
        if (!healing.isEmpty()) healTick(server);
        if (t % 40 == 0 && (FightScript.busy() || !FightWatch.fights.isEmpty())) changed();
    }

    // the giants' calls (GuideBridge)

    /** "fightok": may this fight start now ["ok"] / ["no", why] */
    public static @Nullable List<String> ok(MinecraftServer server, String arg) {
        JsonObject o = AdminJson.object(arg);
        if (o == null || !o.has("id") || !o.has("other")) return null;
        try {
            if (o.get("fight").getAsInt() > VERSION) return null;
        } catch (RuntimeException e) {
            return null;
        }
        LivingEntity self = FunGiants.find(server, AdminJson.str(o, "id", 40)), other = FunGiants.find(server,
                AdminJson.str(o, "other", 40));
        if (self == null || other == null) return List.of("ok");
        String why = refusal(server, self, other, o.has("ordered") && o.get("ordered").getAsBoolean(),
                AdminJson.str(o, "by", 40));
        return why == null ? List.of("ok") : List.of("no", why);
    }

    /** why this fight may not start now, or null */
    static @Nullable String refusal(MinecraftServer server, LivingEntity self, LivingEntity other, boolean ordered,
            String by) {
        // (lagfix) an adventure map's own giants are on the same story: never each other, ordered or not
        if (net.jj.giants.adventure.Adventure.sameStory(self, other))
            return "They're on the same side in this adventure.";
        // a staged fight's giants fight only each other
        boolean sa = FightScript.in(self.getUUID()), sb = FightScript.in(other.getUUID());
        if (sa || sb) {
            String s1 = FightScript.sideOf(self.getUUID()), s2 = FightScript.sideOf(other.getUUID());
            return sa && sb && !s1.equals(s2) && FightScript.fightingNow() ? null : "That giant is in a staged fight.";
        }
        long now = self.level().getGameTime();
        FightData d = FightData.get(server);
        for (LivingEntity e : List.of(self, other)) {
            Long until = d.s.rest.get(e.getStringUUID());
            if (until != null && until > now) return "The " + FightGiants.name(e) + " is resting after a fight.";
        }
        if (!on(server, "fightsSleepers") && (FightGiants.asleep(server, other.getStringUUID())
                || FightGiants.asleep(server, self.getStringUUID()))) return "That giant is asleep.";
        String when = option(server, "fightsWhen");
        if (!when.equals("any") && self.level().dimensionType().hasFixedTime() == false) {
            boolean day = self.level().isDay();
            if (when.equals("day") && !day) return "Giants only fight by day here.";
            if (when.equals("night") && day) return "Giants only fight at night here.";
        }
        int most = (int) num(server, "fightsMost");
        if (most > 0 && FightWatch.of(self.getUUID()) == null && FightWatch.of(other.getUUID()) == null
                && FightWatch.count((ServerLevel) self.level()) >= most)
            return "As many giant fights as this world allows are going on.";
        String oa = FightGiants.owner(server, self.getStringUUID()), ob = FightGiants.owner(server,
                other.getStringUUID());
        if (!oa.isEmpty() && !ob.isEmpty() && !oa.equals(ob)) {
            switch (option(server, "ownedMayFight")) {
                case "never" -> {
                    return "Owned giants may not fight each other here.";
                }
                case "pvp" -> {
                    if (!server.isPvpAllowed()) return "Owned giants fight each other only where players may.";
                }
                case "both_ok" -> {
                    if (!d.s.allow.contains(oa) || !d.s.allow.contains(ob))
                        return "Both owners must agree first (/giants fight allow).";
                }
                default -> {}
            }
        }
        return null;
    }

    /** "fightnews": a giant's fight began, or he backed down */
    public static @Nullable List<String> news(MinecraftServer server, String arg) {
        JsonObject o = AdminJson.object(arg);
        if (o == null || !o.has("id")) return null;
        try {
            if (o.get("fight").getAsInt() > VERSION) return null;
        } catch (RuntimeException e) {
            return null;
        }
        LivingEntity self = FunGiants.find(server, AdminJson.str(o, "id", 40)), other = FunGiants.find(server,
                AdminJson.str(o, "other", 40));
        boolean ordered = o.has("ordered") && o.get("ordered").getAsBoolean();
        if (self == null) return List.of("ok");
        switch (AdminJson.str(o, "event", 16)) {
            case "start" -> {
                if (other != null && follows(server)) FightWatch.started(server, self, other, ordered);
            }
            case "yield" -> {
                if (follows(server)) FightWatch.yielded(server, self, other, ordered);
            }
            default -> {}
        }
        return List.of("ok");
    }

    /** a giant fell (from Systems.fell, the one catch every giant's death goes through) */
    public static void fell(LivingEntity e) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        FightWatch.fell(sl.getServer(), e);
    }

    // players near a fight

    /** a giant in a fight hurting a player who isn't in it: never while the fight keeps watchers safe */
    private static boolean allowDamage(LivingEntity victim, DamageSource src, float amount) {
        if (!(victim instanceof ServerPlayer p)) return true;
        if (FightScript.run == null && FightWatch.fights.isEmpty()) return true;
        LivingEntity g = giantBehind(p.serverLevel(), src);
        if (g == null) return true;
        if (FightScript.in(g.getUUID())) return FightScript.allow(p, g);
        FightWatch.Fight f = FightWatch.of(g.getUUID());
        if (f == null || !option(p.server, f.side() + "Watchers").equals("safe")) return true;
        // in the fight: their own giant's foe, or one of the two is after them
        LivingEntity a = FunGiants.find(p.server, f.a), b = FunGiants.find(p.server, f.b);
        for (LivingEntity x : new LivingEntity[] {a, b})
            if (x instanceof net.minecraft.world.entity.Mob m && m.getTarget() == p) return true;
        return false;
    }

    static @Nullable LivingEntity giantBehind(ServerLevel l, DamageSource src) {
        for (Entity x : new Entity[] {src.getEntity(), src.getDirectEntity()})
            if (x instanceof LivingEntity le && FunGiants.kindOf(le) != null) return le;
        String kind = net.jj.giants.tools.Tools.kindOf(src.getEntity());
        if (kind == null) kind = net.jj.giants.tools.Tools.kindOf(src.getDirectEntity());
        if (kind == null) return null;
        Entity at = src.getEntity() != null ? src.getEntity() : src.getDirectEntity();
        LivingEntity best = null;
        double bd = 400 * 400;
        for (LivingEntity le : FunGiants.standing(l.getServer(), kind)) {
            if (le.level() != l || at == null) continue;
            double d = le.distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = le;
            }
        }
        return best;
    }

    // blocks broken while giants fight

    /** a fight going on whose blocks rule isn't "usual" (the mixin looks at nothing otherwise) */
    public static volatile boolean watchingBlocks;

    /** a block change while a giant's own entity ticks (Stats.currentEntity): false = it doesn't happen */
    public static boolean blockChange(Level level, BlockPos pos, BlockState state, Entity ticking) {
        LivingEntity giant = root(ticking);
        if (giant == null) return true;
        String rule;
        String side = FightScript.sideOf(giant.getUUID());
        FightScript.Run r = FightScript.run;
        if (!side.isEmpty() && r != null) rule = switch (r.s.arena) {
            case "off" -> "off";
            case "some" -> "some";
            case "little" -> "little";
            default -> "usual";
        };
        else {
            FightWatch.Fight f = FightWatch.of(giant.getUUID());
            if (f == null) return true;
            rule = option(level.getServer(), f.side() + "Blocks");
        }
        boolean heal = rule.equals("heal") || (!side.isEmpty() && r != null && r.s.healBlocks);
        if (rule.equals("usual") && !heal) return true;
        BlockState old = level.getBlockState(pos);
        if (old.isAir() || !old.getFluidState().isEmpty() || old.is(BlockTags.FIRE) || old.equals(state)) return true;
        boolean keep = switch (rule) {
            case "off" -> true;
            case "some" -> level.random.nextFloat() < 0.5f;
            case "little" -> level.random.nextFloat() < 0.75f;
            default -> false;
        };
        if (keep) return false;
        if (heal && blocks.size() < 200_000) {
            long k = pos.asLong();
            Map<Long, BlockState> m = blocks.computeIfAbsent(level.dimension().location().toString(),
                    x -> new LinkedHashMap<>());
            m.putIfAbsent(k, old);
            blockGiants.add(giant.getUUID());
        }
        return true;
    }

    /** blocks the fighting giants broke, to put back after: per world, the first state at each spot */
    private static final Map<String, Map<Long, BlockState>> blocks = new HashMap<>();
    private static final java.util.Set<UUID> blockGiants = new java.util.HashSet<>();
    /** the ones being put back now */
    private static final List<Object[]> healing = new ArrayList<>();

    /** a fight with "heal" ended: what its giants broke grows back (a few hundred blocks a tick) */
    static void blocksEnded(MinecraftServer server, List<UUID> giants, String side) {
        if (option(server, side + "Blocks").equals("heal")) healBlocks(server);
    }

    static void healBlocks(MinecraftServer server) {
        for (var e : blocks.entrySet())
            for (var b : e.getValue().entrySet()) healing.add(new Object[] {e.getKey(), b.getKey(), b.getValue()});
        blocks.clear();
        blockGiants.clear();
    }

    private static void healTick(MinecraftServer server) {
        int n = 0;
        while (!healing.isEmpty() && n++ < 400) {
            Object[] h = healing.remove(healing.size() - 1);
            ServerLevel l = null;
            for (ServerLevel x : server.getAllLevels())
                if (x.dimension().location().toString().equals(h[0])) l = x;
            if (l == null) continue;
            BlockPos pos = BlockPos.of((long) h[1]);
            if (!l.isLoaded(pos)) continue;
            BlockState now = l.getBlockState(pos);
            if (now.isAir() || !now.getFluidState().isEmpty() || now.is(BlockTags.FIRE) || now.is(Blocks.SNOW))
                l.setBlock(pos, (BlockState) h[2], 2);
        }
    }

    /** (the tests) how many blocks wait to grow back after a fight */
    public static int blocksWaitingForTests() {
        int n = healing.size();
        for (var m : blocks.values()) n += m.size();
        return n;
    }

    /** a giant mod's entity: the giant himself, or the giant a part of him belongs to */
    static @Nullable LivingEntity root(Entity e) {
        if (e instanceof LivingEntity le && FunGiants.kindOf(le) != null) return le;
        try {
            Object o = e.getClass().getMethod("owner").invoke(e);
            if (o instanceof LivingEntity le && FunGiants.kindOf(le) != null) return le;
        } catch (ReflectiveOperationException | RuntimeException x) {
            // not a part
        }
        return null;
    }

    /** any fight now whose blocks aren't "usual" */
    private static boolean blocksRule(MinecraftServer server) {
        FightScript.Run r = FightScript.run;
        if (r != null && (!r.s.arena.equals("usual") || r.s.healBlocks)) return true;
        if (FightWatch.fights.isEmpty()) return false;
        return !option(server, "wildBlocks").equals("usual") || !option(server, "ownedBlocks").equals("usual");
    }

    /** while owned giants may not kill each other, each giant wears his owner (so his blows know it) */
    private static boolean ownerTagged;

    private static void ownerTags(MinecraftServer server) {
        boolean need = !on(server, "ownedMayKill");
        if (!need && !ownerTagged) return;
        ownerTagged = need;
        FightGiants.freshRows();
        for (LivingEntity e : FunGiants.standing(server, null)) {
            String want = need ? FightGiants.owner(server, e.getStringUUID()) : "";
            String head = "jj_giants.owner=";
            String has = null;
            for (String t : e.getTags()) if (t.startsWith(head)) has = t.substring(head.length());
            if (want.equals(has == null ? "" : has)) continue;
            if (has != null) e.removeTag(head + has);
            if (!want.isEmpty()) e.addTag(head + want);
        }
    }

    // what the fights ask of the giants and the systems

    static void sys(MinecraftServer server, Giant g, LivingEntity e, String what, JsonObject more) {
        JsonObject o = more == null ? new JsonObject() : more.deepCopy();
        o.addProperty("sys", 1);
        o.addProperty("do", what);
        o.addProperty("id", e.getStringUUID());
        AdminServer.call(server, g, "sysact", o.toString());
    }

    static void home(MinecraftServer server, Giant g, LivingEntity e) {
        JsonObject o = new JsonObject();
        o.addProperty("live", 1);
        o.addProperty("id", e.getStringUUID());
        o.addProperty("do", "home");
        o.addProperty("value", "");
        o.addProperty("by", "-");
        AdminServer.call(server, g, "liveact", o.toString());
    }

    static void kill(MinecraftServer server, LivingEntity e) {
        Giant g = FightGiants.kind(e);
        if (g == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("live", 1);
        o.addProperty("id", e.getStringUUID());
        o.addProperty("do", "kill");
        o.addProperty("value", "");
        o.addProperty("by", "-");
        AdminServer.call(server, g, "liveact", o.toString());
    }

    /** a fight's own speed factor on him (with the fun tag's and the systems' through SystemsTags) */
    static void speed(MinecraftServer server, LivingEntity e, double f) {
        net.jj.giants.systems.SystemsTags.fight(server, e, "speed", f);
        Giant g = FightGiants.kind(e);
        if (g != null) FunGiants.act(server, g, e.getStringUUID(), "stats", new JsonObject());
    }

    static void growthWin(MinecraftServer server, LivingEntity winner) {
        Systems.winFromFight(server, winner.getStringUUID());
    }

    static void loyaltyUp(MinecraftServer server, LivingEntity winner, int by) {
        Systems.loyaltyFromFight(server, winner.getStringUUID(), by);
    }

    static @Nullable ServerPlayer player(MinecraftServer server, String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** a line to the players near an ordinary fight (the giants' meeting lines' switch, not in quiet mode) */
    static void tellNear(MinecraftServer server, LivingEntity at, String line, String side) {
        if (net.jj.giants.tools.Tools.quiet(server) || !net.jj.giants.players.Players.on(server, "chatMeetings"))
            return;
        Component c = Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        double r = Math.max(256, FightGiants.reach(at) * 3);
        for (ServerPlayer p : ((ServerLevel) at.level()).players())
            if (p.distanceToSqr(at) < r * r) p.sendSystemMessage(c);
    }

    // emeralds for bets

    static boolean take(ServerPlayer p, int n) {
        var inv = p.getInventory();
        if (inv.countItem(Items.EMERALD) < n) return false;
        int left = n;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(Items.EMERALD)) continue;
            int t = Math.min(left, s.getCount());
            s.shrink(t);
            left -= t;
        }
        return true;
    }

    static void give(ServerPlayer p, int n) {
        while (n > 0) {
            int k = Math.min(64, n);
            ItemStack s = new ItemStack(Items.EMERALD, k);
            if (!p.getInventory().add(s)) p.drop(s, false);
            n -= k;
        }
    }

    // the book

    /** the book's requests on the Fun page's fight parts (Admins: the book checked the role) */
    public static @Nullable AdminServer.Result request(MinecraftServer server, ServerPlayer p, String who, String what,
            JsonObject o) {
        return switch (what) {
            case "fightscript" -> FightEdit.request(server, p, o);
            case "fightstart" -> FightEdit.start(server, p, AdminJson.str(o, "spot", 8).equals("look"));
            case "fightstop" -> FightScript.stop(server, who);
            case "fightsave" -> FightEdit.save(server, p, AdminJson.str(o, "name", 40));
            case "fightload" -> FightEdit.load(server, p, AdminJson.str(o, "name", 40));
            case "fightdelete" -> FightEdit.delete(server, who, AdminJson.str(o, "name", 40));
            case "fightreplay" -> FightScript.replay(server, who, p.getUUID(), AdminJson.str(o, "name", 40));
            case "fightstanddown" -> {
                LivingEntity e = FunGiants.find(server, AdminJson.str(o, "id", 40));
                if (e == null) yield AdminServer.Result.fail("He isn't there any more.");
                FightWatch.standDown(server, e);
                yield AdminServer.Result.said(List.of(FightGiants.called(e, true) + " stands down."));
            }
            default -> null;
        };
    }

    static void changed() {
        AdminBook.changed(net.jj.giants.fun.Fun.PAGE);
    }

    /** the Fun page's fight part: this admin's script, the saved setups, the staged fight now, fights going on */
    public static JsonObject state(MinecraftServer server, @Nullable ServerPlayer p) {
        JsonObject o = new JsonObject();
        FightData d = FightData.get(server);
        if (p != null) o.add("script", FightData.GSON.toJsonTree(d.draft(p.getStringUUID())));
        com.google.gson.JsonArray saved = new com.google.gson.JsonArray();
        for (FightData.Setup s : d.s.saved.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", s.name);
            j.addProperty("about", FightScript.sideNames(s) + (s.dim.isEmpty() ? "" : " · at " + (int) s.x + " "
                    + (int) s.y + " " + (int) s.z));
            saved.add(j);
        }
        o.add("saved", saved);
        JsonObject now = FightScript.state(server);
        if (now != null) o.add("now", now);
        o.add("goingList", FightWatch.list(server));
        JsonObject moves = new JsonObject();
        for (Giant g : AdminServer.giants()) {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (String m : FightGiants.moves(server, g)) a.add(m);
            moves.add(g.key(), a);
            moves.addProperty(g.key() + ".finisher", FightGiants.finisher(server, g));
        }
        o.add("moves", moves);
        return o;
    }
}
