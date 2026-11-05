package net.jj.giants.fights;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.systems.Systems;
import net.jj.giants.tools.Areas;
import net.jj.giants.tools.ToolsData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10B, ADMIN_BRIDGE.md §27.4) Ordinary giant fights (wild ones, and owners' fights), followed while one of the
 * giant-fight settings needs it: told by the giants ("fightnews"), looked at once a second. What happens to the
 * loser (chased, caught, spared, following his winner), healing and rest after, the fight kept to its area and out of
 * villages, players near kept safe, both bars, fight music, news for the owners, and the wins.
 */
public final class FightWatch {
    private FightWatch() {}

    /** one fight going on: the two, whether it is an owner's, where it began, its show */
    static final class Fight {
        final UUID a, b;
        final boolean owned;
        final ServerLevel level;
        final Vec3 start;
        final long began;
        final FightShow.Show show = new FightShow.Show();
        /** pulled back: walking back until this game time (then taken up again) */
        long pulledUntil = -1;
        /** both seen with neither after the other since this game time */
        long apartSince = -1;
        boolean over;

        Fight(UUID a, UUID b, boolean owned, ServerLevel level, Vec3 start, long began) {
            this.a = a;
            this.b = b;
            this.owned = owned;
            this.level = level;
            this.start = start;
            this.began = began;
        }

        String side() {
            return owned ? "owned" : "wild";
        }

        boolean has(UUID id) {
            return a.equals(id) || b.equals(id);
        }
    }

    /** a loser being chased down */
    static final class Chase {
        final UUID winner, loser;
        final boolean owned;
        final Vec3 from;
        final long began;
        final double loserHp;
        boolean caught;

        Chase(UUID winner, UUID loser, boolean owned, Vec3 from, long began, double loserHp) {
            this.winner = winner;
            this.loser = loser;
            this.owned = owned;
            this.from = from;
            this.began = began;
            this.loserHp = loserHp;
        }
    }

    static final Map<String, Fight> fights = new LinkedHashMap<>();
    static final List<Chase> chases = new ArrayList<>();

    static void forget() {
        fights.clear();
        chases.clear();
    }

    static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    static @Nullable Fight of(UUID id) {
        for (Fight f : fights.values()) if (!f.over && f.has(id)) return f;
        return null;
    }

    /** how many ordinary fights go on now in this world */
    static int count(ServerLevel l) {
        int n = 0;
        for (Fight f : fights.values()) if (!f.over && f.level == l) n++;
        return n;
    }

    // what the giants tell

    static void started(MinecraftServer server, LivingEntity e, LivingEntity other, boolean owned) {
        if (FightScript.in(e.getUUID()) || FightScript.in(other.getUUID())) return;
        String k = key(e.getUUID(), other.getUUID());
        Fight f = fights.get(k);
        if (f != null && !f.over) return;
        ServerLevel l = (ServerLevel) e.level();
        f = new Fight(e.getUUID(), other.getUUID(), owned, l, e.position().add(other.position()).scale(0.5),
                l.getGameTime());
        fights.put(k, f);
        if (Fights.on(server, f.side() + "News")) news(server, f, e, other);
    }

    /** a loser backed down from his winner */
    static void yielded(MinecraftServer server, LivingEntity loser, @Nullable LivingEntity winner, boolean owned) {
        if (FightScript.in(loser.getUUID())) return;
        Fight f = winner == null ? of(loser.getUUID()) : fights.get(key(loser.getUUID(), winner.getUUID()));
        String side = f != null ? f.side() : owned ? "owned" : "wild";
        if (winner == null && f != null) winner = FunGiants.find(server, f.a.equals(loser.getUUID()) ? f.b : f.a);
        if (winner != null) won(server, winner, loser, side, false);
        if (winner != null && Fights.option(server, side + "Escape").equals("chased")) {
            Chase c = new Chase(winner.getUUID(), loser.getUUID(), side.equals("owned"), loser.position(),
                    loser.level().getGameTime(), FightGiants.hp(server, loser).now());
            chases.add(c);
            FightGiants.act(server, winner, "chase", FightGiants.foe(loser));
            Fights.tellNear(server, loser, FightGiants.called(winner, true) + " goes after "
                    + FightGiants.called(loser, false) + "!", side);
        } else spared(server, loser, winner, side);
        if (f != null) end(server, f, winner, loser);
    }

    /** a giant fell: the fight he was in is over, the other one won */
    static void fell(MinecraftServer server, LivingEntity dead) {
        chases.removeIf(c -> c.loser.equals(dead.getUUID()) || c.winner.equals(dead.getUUID()));
        Fight f = of(dead.getUUID());
        if (f == null) return;
        LivingEntity winner = FunGiants.find(server, f.a.equals(dead.getUUID()) ? f.b : f.a);
        if (winner != null) won(server, winner, dead, f.side(), true);
        end(server, f, winner, dead);
    }

    /** the win: growth, loyalty, the owners' board */
    static void won(MinecraftServer server, LivingEntity winner, LivingEntity loser, String side, boolean fell) {
        if (!fell && Fights.on(server, side + "Grow")) Fights.growthWin(server, winner);
        if (Fights.on(server, side + "Loyalty")) Fights.loyaltyUp(server, winner, 10);
        String owner = FightGiants.owner(server, winner.getStringUUID());
        if (!owner.isEmpty()) {
            FightData d = FightData.get(server);
            d.board(d.s.wins, owner, FightGiants.ownerName(server, winner.getStringUUID())).wins++;
        }
    }

    /** the loser goes home as he always does, or (a wild one, "follow") follows his winner from now on */
    static void spared(MinecraftServer server, LivingEntity loser, @Nullable LivingEntity winner, String side) {
        if (winner == null || !Fights.option(server, side + "Spared").equals("follow")) return;
        if (!FightGiants.owner(server, loser.getStringUUID()).isEmpty()) return;
        FightData d = FightData.get(server);
        d.s.follows.put(loser.getStringUUID(), winner.getStringUUID());
        d.setDirty();
        Fights.tellNear(server, loser, FightGiants.called(loser, true) + " follows "
                + FightGiants.called(winner, false) + " now.", side);
    }

    /** the fight is over: healing, rest, the show taken down */
    static void end(MinecraftServer server, Fight f, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
        if (f.over) return;
        f.over = true;
        FightShow.end(f.show, server);
        String heal = Fights.option(server, f.side() + "Heal");
        long rest = (long) Fights.num(server, f.side() + "Rest") * 1200L;
        FightData d = FightData.get(server);
        for (UUID id : List.of(f.a, f.b)) {
            LivingEntity e = FunGiants.find(server, id);
            if (e == null) continue;
            if (!heal.equals("none")) {
                JsonObject o = new JsonObject();
                o.addProperty("share", heal.equals("full") ? 1.0 : 0.34);
                FightGiants.act(server, e, "health", o);
            }
            if (rest > 0) {
                d.s.rest.put(id.toString(), f.level.getGameTime() + rest);
                d.setDirty();
            }
        }
        if (Fights.on(server, f.side() + "News") && winner != null && loser != null)
            for (UUID id : List.of(winner.getUUID(), loser.getUUID())) {
                ServerPlayer p = Fights.player(server, FightGiants.owner(server, id.toString()));
                if (p != null) p.sendSystemMessage(Component.literal(id.equals(winner.getUUID())
                        ? "Your " + FightGiants.name(winner) + " won the fight against " + FightGiants.called(loser,
                                false) + "."
                        : "Your " + FightGiants.name(loser) + " lost the fight against " + FightGiants.called(winner,
                                false) + ".").withStyle(ChatFormatting.YELLOW));
            }
        Fights.blocksEnded(server, List.of(f.a, f.b), f.side());
    }

    /** "Your Cerberus is fighting the Wreckback" to each owner (with Stand down), and a line to players near */
    static void news(MinecraftServer server, Fight f, LivingEntity a, LivingEntity b) {
        for (LivingEntity[] pair : new LivingEntity[][] {{a, b}, {b, a}}) {
            ServerPlayer p = Fights.player(server, FightGiants.owner(server, pair[0].getStringUUID()));
            if (p == null) continue;
            p.sendSystemMessage(Component.literal("Your " + FightGiants.name(pair[0]) + " is fighting "
                    + FightGiants.called(pair[1], false) + ". ").withStyle(ChatFormatting.YELLOW)
                    .append(standDown(pair[0])));
        }
        Fights.tellNear(server, a, FightGiants.called(a, true) + " and " + FightGiants.called(b, false)
                + " are fighting!", f.side());
    }

    /** the chat button an owner presses to call his giant off */
    static Component standDown(LivingEntity g) {
        return Component.literal("[Stand down]").withStyle(s -> s.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/giants fight standdown "
                        + g.getStringUUID()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Call your " + FightGiants.name(g) + " off this fight."))));
    }

    // once a second

    static void tick(MinecraftServer server) {
        for (Fight f : List.copyOf(fights.values())) {
            if (f.over) {
                fights.values().remove(f);
                continue;
            }
            LivingEntity a = FunGiants.find(server, f.a), b = FunGiants.find(server, f.b);
            if (a == null || b == null) {
                end(server, f, a != null ? a : b, null);
                continue;
            }
            look(server, f, a, b);
        }
        for (Chase c : List.copyOf(chases)) chase(server, c);
        follow(server);
    }

    private static void look(MinecraftServer server, Fight f, LivingEntity a, LivingEntity b) {
        long now = f.level.getGameTime();
        FightGiants.Hp ha = FightGiants.hp(server, a), hb = FightGiants.hp(server, b);
        // over without a word: neither after the other for half a minute (orders, a player's hit, one got away)
        boolean at = ha.target().equals(f.b.toString()) || hb.target().equals(f.a.toString());
        if (!at && f.pulledUntil < 0) {
            if (f.apartSince < 0) f.apartSince = now;
            else if (now - f.apartSince > 600) {
                end(server, f, null, null);
                return;
            }
        } else f.apartSince = -1;
        Vec3 mid = a.position().add(b.position()).scale(0.5);
        String s = f.side();
        double r = Math.max(96, (FightGiants.reach(a) + FightGiants.reach(b)) * 2);
        // the area: pulled back, then taken up again as the same kind of fight
        if (f.pulledUntil >= 0) {
            if (now >= f.pulledUntil || mid.distanceTo(f.start) < 12) {
                f.pulledUntil = -1;
                JsonObject o = FightGiants.foe(b);
                o.addProperty("wild", !f.owned);
                FightGiants.act(server, a, "fight", o);
                o = FightGiants.foe(a);
                o.addProperty("wild", !f.owned);
                FightGiants.act(server, b, "fight", o);
            }
        } else {
            int drift = (int) Fights.num(server, s + "Drift");
            boolean far = drift > 0 && mid.distanceTo(f.start) > drift;
            boolean village = Fights.on(server, s + "SafeAreas") && unsafe(f.level, mid, Math.max(FightGiants.reach(a),
                    FightGiants.reach(b)));
            if (far || village) pullBack(server, f, a, b, village ? "away from the village" : "back");
        }
        // the show
        Map<UUID, Double> share = new HashMap<>();
        share.put(f.a, ha.share());
        share.put(f.b, hb.share());
        if (Fights.on(server, s + "Bars"))
            FightShow.bars(f.show, f.level, mid, r, List.of(a, b), share, Map.of(f.a, "a", f.b, "b"));
        String music = Fights.option(server, s + "Music");
        FightShow.music(f.show, server, f.level, mid, r, music.equals("usual") ? "" : music);
    }

    /** a village (villagers near) or a safe area where the fight is going */
    static boolean unsafe(ServerLevel l, Vec3 at, double reach) {
        String dim = l.dimension().location().toString();
        for (ToolsData.Area ar : ToolsData.get(l.getServer()).s.areas)
            if (Areas.inside(ar, dim, at.x, at.z) || Areas.inside(ar, dim, at.x + Math.signum(ar.x - at.x) * reach,
                    at.z + Math.signum(ar.z - at.z) * reach)) return true;
        double r = 48 + reach;
        return !l.getEntitiesOfClass(Villager.class, new AABB(at, at).inflate(r, 64, r), v -> v.isAlive()).isEmpty();
    }

    /** both break off and walk back toward where it began (then it is taken up again) */
    static void pullBack(MinecraftServer server, Fight f, LivingEntity a, LivingEntity b, String why) {
        FightGiants.act(server, a, "stop", null);
        FightGiants.act(server, b, "stop", null);
        for (LivingEntity e : List.of(a, b)) {
            Giant g = FightGiants.kind(e);
            if (g == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("x", f.start.x);
            o.addProperty("z", f.start.z);
            Fights.sys(server, g, e, "go", o);
        }
        f.pulledUntil = f.level.getGameTime() + 400;
        Fights.tellNear(server, a, FightGiants.called(a, true) + " and " + FightGiants.called(b, false)
                + " break off and circle " + why + ".", f.side());
    }

    /** a chase: caught (killed or spared), or he got away */
    private static void chase(MinecraftServer server, Chase c) {
        LivingEntity w = FunGiants.find(server, c.winner), l = FunGiants.find(server, c.loser);
        if (w == null || l == null) {
            chases.remove(c);
            if (w != null) FightGiants.act(server, w, "stop", null);
            return;
        }
        String side = c.owned ? "owned" : "wild";
        long now = l.level().getGameTime();
        if (c.caught) {
            // killing him: until he falls (or a minute)
            if (now - c.began > 2400) {
                chases.remove(c);
                FightGiants.act(server, w, "stop", null);
            }
            return;
        }
        double gap = Math.hypot(w.getX() - l.getX(), w.getZ() - l.getZ());
        boolean caught = gap < FightGiants.reach(w) * 0.6 + 6 || FightGiants.hp(server, l).now() < c.loserHp - 0.5;
        double gone = Math.hypot(l.getX() - c.from.x, l.getZ() - c.from.z);
        if (caught) {
            if (Fights.option(server, side + "Caught").equals("killed")) {
                c.caught = true;
                Fights.tellNear(server, l, FightGiants.called(w, true) + " has caught "
                        + FightGiants.called(l, false) + "!", side);
                return;
            }
            chases.remove(c);
            FightGiants.act(server, w, "stop", null);
            Fights.tellNear(server, l, FightGiants.called(w, true) + " lets " + FightGiants.called(l, false)
                    + " go.", side);
            spared(server, l, w, side);
        } else if (gone > Fights.num(server, side + "ChaseBlocks") || now - c.began > 1800) {
            chases.remove(c);
            FightGiants.act(server, w, "stop", null);
            Fights.tellNear(server, l, FightGiants.called(l, true) + " got away.", side);
            spared(server, l, w, side);
        }
    }

    /** wild losers who follow their winner: walked back to him when they stray (every ten seconds) */
    private static void follow(MinecraftServer server) {
        if (server.getTickCount() % 200 != 0) return;
        FightData d = FightData.get(server);
        if (d.s.follows.isEmpty()) return;
        for (var e : List.copyOf(d.s.follows.entrySet())) {
            LivingEntity l = FunGiants.find(server, e.getKey()), w = FunGiants.find(server, e.getValue());
            if (l == null || w == null) {
                if (l == null && w == null) continue;
                // one fell (or is gone for long): the other's record goes when he is next seen without him
                if ((l == null ? w : l).isAlive()) {
                    d.s.follows.remove(e.getKey());
                    d.setDirty();
                }
                continue;
            }
            if (l.level() != w.level()) continue;
            double far = Math.max(48, FightGiants.reach(w) * 1.5);
            if (l.distanceTo(w) <= far) continue;
            Giant g = FightGiants.kind(l);
            if (g == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("x", w.getX() + (l.getX() - w.getX()) / Math.max(1, l.distanceTo(w)) * far * 0.5);
            o.addProperty("z", w.getZ() + (l.getZ() - w.getZ()) / Math.max(1, l.distanceTo(w)) * far * 0.5);
            Fights.sys(server, g, l, "go", o);
        }
    }

    /** an owner (or an admin) calls his giant off: he stands down, and so does the one he was fighting */
    static boolean standDown(MinecraftServer server, LivingEntity g) {
        Fight f = of(g.getUUID());
        chases.removeIf(c -> c.winner.equals(g.getUUID()));
        FightGiants.act(server, g, "stop", null);
        if (f == null) return true;
        LivingEntity other = FunGiants.find(server, f.a.equals(g.getUUID()) ? f.b : f.a);
        if (other != null) FightGiants.act(server, other, "stop", null);
        end(server, f, null, null);
        return true;
    }

    /** the book: each fight going on, {text, a, b} */
    static com.google.gson.JsonArray list(MinecraftServer server) {
        com.google.gson.JsonArray out = new com.google.gson.JsonArray();
        for (Fight f : fights.values()) {
            if (f.over) continue;
            LivingEntity a = FunGiants.find(server, f.a), b = FunGiants.find(server, f.b);
            JsonObject j = new JsonObject();
            j.addProperty("text", (a == null ? "?" : FightGiants.called(a, true)) + " against " + (b == null ? "?"
                    : FightGiants.called(b, false)) + (f.owned ? " (an owner's fight)" : " (wild)"));
            j.addProperty("a", f.a.toString());
            j.addProperty("b", f.b.toString());
            out.add(j);
        }
        return out;
    }

    /** (the tests and the book) the fights going on */
    public static List<String> describe(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        for (Fight f : fights.values()) {
            if (f.over) continue;
            LivingEntity a = FunGiants.find(server, f.a), b = FunGiants.find(server, f.b);
            out.add((a == null ? "?" : FightGiants.called(a, true)) + " against " + (b == null ? "?"
                    : FightGiants.called(b, false)) + (f.owned ? " (an owner's fight)" : " (wild)"));
        }
        return out;
    }

    /** (the tests) Stand down */
    public static void standDownForTests(MinecraftServer server, LivingEntity g) {
        standDown(server, g);
    }

    /** (the tests) a fight between these two is being followed */
    public static boolean followingForTests(UUID a, UUID b) {
        Fight f = fights.get(key(a, b));
        return f != null && !f.over;
    }

    /** (the tests) a chase of this loser goes on, caught or not */
    public static @Nullable Boolean chaseForTests(UUID loser) {
        for (Chase c : chases) if (c.loser.equals(loser)) return c.caught;
        return null;
    }
}
