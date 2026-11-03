package net.jj.giants.players;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminInsight;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The "Giants: Challenges" advancements (ADMIN_BRIDGE.md 20.10): one or two for each preset with a clear goal, granted
 * only while challenges are on and the world's preset is exactly that one.
 */
public final class Challenges {
    private Challenges() {}

    public static final String ROOT = "root";

    /** the preset the world is on now, by id ("" when Custom, Classic or changed since) */
    public static String presetNow(MinecraftServer server) {
        AdminWorld w = AdminWorld.get(server);
        String name = net.jj.giants.tools.Tools.presetName(server);
        if (name.endsWith(", changed")) return "";
        String id = AdminTransfer.builtInId(name);
        return id == null ? "" : id;
    }

    public static boolean active(MinecraftServer server, String preset) {
        return Players.on(server, "achievements") && preset.equals(presetNow(server));
    }

    /** grants one if its preset is on now; true if it was granted now */
    public static boolean award(ServerPlayer p, String id, String preset) {
        MinecraftServer server = p.server;
        if (!active(server, preset)) return false;
        return grant(p, id);
    }

    static boolean grant(ServerPlayer p, String id) {
        AdvancementHolder a = p.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "challenges/" + id));
        if (a == null) return false;
        if (p.getAdvancements().getOrStartProgress(a).isDone()) return false;
        if (!id.equals(ROOT)) grant(p, ROOT);
        boolean any = false;
        for (String c : a.value().criteria().keySet()) any |= p.getAdvancements().award(a, c);
        return any;
    }

    /** joining: the tab is there while challenges are on */
    public static void joined(ServerPlayer p) {
        if (!Players.on(p.server, "achievements")) return;
        AdvancementHolder root = p.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "challenges/" + ROOT));
        if (root == null) return;
        for (String c : root.value().criteria().keySet()) p.getAdvancements().award(root, c);
    }

    public static boolean done(ServerPlayer p, String id) {
        AdvancementHolder a = p.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "challenges/" + id));
        return a != null && p.getAdvancements().getOrStartProgress(a).isDone();
    }

    // a giant fell

    public static void fell(MinecraftServer server, String kind, @Nullable ServerPlayer killer) {
        long now = server.overworld().getGameTime();
        PlayerData d = PlayerData.get(server);
        PlayerData.Challenges c = d.s.challenges;
        if (active(server, "one_life")) {
            c.oneLifeFallen.add(kind);
            if (killer != null) c.oneLifeSlayers.add(killer.getStringUUID());
            d.setDirty();
            boolean all = true;
            for (Giant g : AdminServer.giants()) all &= c.oneLifeFallen.contains(g.key());
            if (all && !AdminServer.giants().isEmpty())
                for (String id : c.oneLifeSlayers) {
                    ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(id));
                    if (p != null) grant(p, "last_giant_standing");
                }
        }
        if (killer == null) return;
        award(killer, "titan_slayer", "titans");
        award(killer, "against_the_odds", "hardcore");
        if (active(server, "night_terrors") && night(server.overworld())) grant(killer, "dawn_breaker");
        if (active(server, "boss_rush")) {
            List<Long> t = c.rush.computeIfAbsent(killer.getStringUUID(), k -> new ArrayList<>());
            t.add(now);
            t.removeIf(x -> now - x > 24000);
            d.setDirty();
            if (t.size() >= 3) grant(killer, "rush_hour");
        }
        if (active(server, "pocket_giants")) {
            int n = c.pocket.merge(killer.getStringUUID(), 1, Integer::sum);
            d.setDirty();
            if (n >= 4) grant(killer, "pocket_full");
        }
        if (active(server, "speedrun")) {
            PlayerData.Stats st = d.s.stats.get(killer.getStringUUID());
            if (st != null && st.first >= 0 && now - st.first <= 36000) grant(killer, "any_percent");
        }
    }

    static boolean night(ServerLevel l) {
        long t = l.getDayTime() % 24000;
        return !l.dimensionType().hasFixedTime() && t >= 13000 && t < 23000;
    }

    // Siege: on from dusk to dawn without dying (counted while the server runs)

    private static final Set<UUID> NIGHT = new HashSet<>();
    private static boolean wasNight, counting;

    /** every five seconds */
    public static void tick(MinecraftServer server) {
        ServerLevel l = server.overworld();
        boolean n = night(l);
        if (n && !wasNight) {
            NIGHT.clear();
            counting = active(server, "siege");
            if (counting) for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (p.isAlive() && !p.isSpectator()) NIGHT.add(p.getUUID());
        } else if (n && counting) {
            // (left the game, or died: out)
            NIGHT.removeIf(u -> {
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                return p == null || !p.isAlive();
            });
        } else if (!n && wasNight && counting) {
            for (UUID u : NIGHT) {
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p != null && p.isAlive()) award(p, "held_the_line", "siege");
            }
            NIGHT.clear();
            counting = false;
        }
        wasNight = n;
    }

    /** a player died: out of tonight's count */
    public static void playerDied(ServerPlayer p) {
        NIGHT.remove(p.getUUID());
    }

    public static void forget() {
        NIGHT.clear();
        wasNight = false;
        counting = false;
    }
}
