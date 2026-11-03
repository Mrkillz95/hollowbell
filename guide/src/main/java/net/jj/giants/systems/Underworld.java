package net.jj.giants.systems;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.tools.Watch;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 9, ADMIN_BRIDGE.md 25.6) Underworld: the giants live in the Nether and the End instead of the Overworld. While
 * it is on the giants are told the Overworld has no giants (their own spawners hold the world's own ones back there:
 * JJ answers 2, #13), a wild world's own one standing in the Overworld goes below, and the Guide keeps one of each kind
 * in the Nether, the End or both: he comes near a player who is there (48-128 blocks off, in loaded land, through his
 * own summon), calm, sized to fit (in the Nether under its roof: his height at most nine tenths of the room there),
 * and when he falls the next comes after his own "Days before he comes back" (never, with "Comes back after he falls"
 * off). Where he can live is his own (his "places"): every giant walks the land; nobody is put in the lava.
 */
public final class Underworld {
    private Underworld() {}

    public static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether",
            END = "minecraft:the_end";
    public static final String MARK = "jj_giants.under";

    /** the dimensions the giants live in for a setting */
    public static List<String> dims(String mode) {
        return switch (mode) {
            case "nether" -> List.of(NETHER);
            case "end" -> List.of(END);
            case "both" -> List.of(NETHER, END);
            default -> List.of();
        };
    }

    /**
     * the giants who can't live under a roof of rock (their own rule: Furrowmaw burrows and comes up under the open
     * sky, and sinks away in the Nether): they live in the End instead of the Nether
     */
    public static final java.util.Set<String> NO_ROOF = java.util.Set.of("furrowmaw");

    /** where one kind lives for a setting */
    public static List<String> dimsFor(String kind, String mode) {
        List<String> d = dims(mode);
        if (!NO_ROOF.contains(kind) || !d.contains(NETHER)) return d;
        return List.of(END);
    }

    public static boolean on(MinecraftServer server) {
        return !dims(Systems.option(server, "underworld")).isEmpty();
    }

    /** one summon on its way: who was standing before, where, how many looks left */
    private record Pending(String key, String kind, ServerLevel level, BlockPos at, Set<UUID> before, int[] looks) {}

    private static final List<Pending> pending = new ArrayList<>();
    /** how far from a player there he comes (the tests bring it nearer) */
    public static double nearMin = 48, nearMax = 128;
    /** (the tests) only this kind is brought below (null: every kind) */
    public static @Nullable String onlyForTests;
    /** the server tick each one was last tried (a try every ten seconds at most) */
    private static final Map<String, Integer> tried = new HashMap<>();
    /** world's own ones already sent below (told once) */
    private static final Set<String> sentBelow = new HashSet<>();
    public static int sentBelowCount;

    static void forget() {
        pending.clear();
        tried.clear();
        sentBelow.clear();
        sentBelowCount = 0;
    }

    /** every second */
    public static void tick(MinecraftServer server) {
        String mode = Systems.option(server, "underworld");
        List<String> dims = dims(mode);
        if (dims.isEmpty()) {
            pending.clear();
            return;
        }
        if (server.getPlayerList().getPlayers().isEmpty() && !forced) return;
        Map<String, List<Watch.Row>> rows = Watch.rows(server);
        goBelow(server, rows);
        for (Pending q : List.copyOf(pending)) found(server, q);
        SystemsData d = SystemsData.get(server);
        long now = server.overworld().getGameTime();
        int tick = server.getTickCount();
        List<String> every = new ArrayList<>(dims);
        if (!every.contains(END)) every.add(END);
        for (String dim : every) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim)));
            if (level == null) continue;
            List<ServerPlayer> there = new ArrayList<>();
            for (ServerPlayer p : level.players()) if (!p.isSpectator()) there.add(p);
            for (Giant g : AdminServer.giants()) {
                if (!dimsFor(g.key(), mode).contains(dim)) continue;
                if (onlyForTests != null && !onlyForTests.equals(g.key())) continue;
                String key = g.key() + "|" + dim;
                SystemsData.Under u = d.s.under.computeIfAbsent(key, k -> new SystemsData.Under());
                if (!u.id.isEmpty()) {
                    LivingEntity kept = FunGiants.find(server, u.id);
                    // (under a roof: one who ended up on the roof is put back on his spot below it)
                    if (kept != null && kept.level() == level && level.dimensionType().hasCeiling() && u.sy > 0
                            && kept.getY() >= level.getMinBuildHeight() + level.getLogicalHeight() - 0.5) {
                        kept.teleportTo(u.sx + 0.5, u.sy, u.sz + 0.5);
                        kept.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                        Systems.log("the {} in the {} was on the roof: back under it", g.key(), dim);
                    }
                    if (kept != null || listed(rows, g.key(), u.id)) continue;
                    // he fell (or was taken away): the next after his own days, or never
                    u.id = "";
                    u.fell = now;
                    d.setDirty();
                    Systems.log("the {} in the {} is gone", g.key(), dim);
                }
                if (pendingFor(key)) continue;
                Integer last = tried.get(key);
                if (last != null && tick - last < 200 && tick >= last && !forced) continue;
                // (every ten seconds at most: his own "comes back" settings, as they are now)
                if (u.fell > 0) {
                    Respawn rs = respawn(server, g);
                    boolean never = !rs.on();
                    long due = u.fell + Math.round(rs.days() * 24000);
                    if (never != u.never || due != u.due) {
                        u.never = never;
                        u.due = due;
                        d.setDirty();
                    }
                    if (u.never || now < u.due) {
                        tried.put(key, tick);
                        continue;
                    }
                }
                if (there.isEmpty()) continue;
                tried.put(key, tick);
                place(server, g, key, level, there.get(level.random.nextInt(there.size())));
            }
        }
    }

    private static boolean pendingFor(String key) {
        for (Pending q : pending) if (q.key().equals(key)) return true;
        return false;
    }

    private static boolean listed(Map<String, List<Watch.Row>> rows, String kind, String id) {
        List<Watch.Row> rs = rows.get(kind);
        if (rs == null) return true; // (his mod doesn't list: leave him be)
        for (Watch.Row r : rs) if (r.id().equals(id)) return true;
        return false;
    }

    /** a wild world's own one standing in the Overworld goes below (taken away: his spawner holds the next back) */
    private static void goBelow(MinecraftServer server, Map<String, List<Watch.Row>> rows) {
        for (Map.Entry<String, List<Watch.Row>> e : rows.entrySet())
            for (Watch.Row r : e.getValue()) {
                if (!"here".equals(r.state()) || !OVERWORLD.equals(r.dim()) || !Look.flag(r.r(), "own")
                        || !AdminJson.str(r.r(), "ownerId", 40).isEmpty())
                    continue;
                Giant g = AdminServer.giant(e.getKey());
                if (g == null) continue;
                LivingEntity body = FunGiants.find(server, r.id());
                if (body != null && sentBelow.add(r.id()))
                    Look.tellNear(body, Component.literal("The " + AdminServer.nameOf(g) + " sinks into the dark:"
                            + " in this world the giants live below.").withStyle(ChatFormatting.DARK_PURPLE), 160);
                Systems.live(server, g, r.id(), "remove", "");
                sentBelowCount++;
            }
    }

    /** his own summon at a spot near this player, sized to fit */
    private static void place(MinecraftServer server, Giant g, String key, ServerLevel level, ServerPlayer near) {
        double want = spawnSize(server, g);
        double[] range = sizeRange(server, g);
        double tall = FunGiants.height(g.key(), 1);
        boolean roof = level.dimensionType().hasCeiling();
        for (int i = 0; i < 16; i++) {
            double a = level.random.nextDouble() * Math.PI * 2,
                    dist = nearMin + level.random.nextDouble() * (nearMax - nearMin);
            int x = (int) Math.floor(near.getX() + Math.cos(a) * dist), z = (int) Math.floor(near.getZ()
                    + Math.sin(a) * dist);
            if (!level.hasChunkAt(new BlockPos(x, near.getBlockY(), z)) || !level.getWorldBorder().isWithinBounds(x, z))
                continue;
            int[] col = roof ? roomUnderRoof(level, x, z, near.getBlockY()) : open(level, x, z);
            if (col == null) continue;
            double size = Math.min(want, range[1]);
            if (roof) size = Math.min(size, col[1] * 0.9 / tall);
            if (size < range[0] || size * tall < 2) continue;
            size = Math.round(size * 1000) / 1000.0;
            Set<UUID> before = new HashSet<>();
            for (LivingEntity e : FunGiants.standing(server, g.key())) before.add(e.getUUID());
            List<String> moods = moods(server, g);
            String arg = level.dimension().location() + " " + (x + 0.5) + " " + col[0] + " " + (z + 0.5) + " -"
                    + (moods.isEmpty() || moods.contains("calm") ? " calm" : "") + " " + AdminSetting.plain(size);
            List<String> said = AdminServer.call(server, g, "summon", arg);
            pending.add(new Pending(key, g.key(), level, new BlockPos(x, col[0], z), before, new int[] {15}));
            Systems.log("bringing a {} into the {} at {} {} {}, size {}: {}", g.key(), level.dimension().location(), x,
                    col[0], z, size, said);
            return;
        }
    }

    /** the summon came to something: the new one near the spot is the one kept there */
    private static void found(MinecraftServer server, Pending q) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : FunGiants.standing(server, q.kind())) {
            if (q.before().contains(e.getUUID()) || e.level() != q.level()) continue;
            double dd = e.blockPosition().distSqr(q.at());
            if (dd < bd) {
                bd = dd;
                best = e;
            }
        }
        if (best == null) {
            if (--q.looks()[0] <= 0) {
                pending.remove(q);
                Systems.log("no {} came in the {} (his own summon left none: the world may hold as many as it"
                        + " allows)", q.kind(), q.level().dimension().location());
            }
            return;
        }
        pending.remove(q);
        // (his own summon looks for the ground its own way: under a roof it may not be the room chosen; put him there)
        if (best.blockPosition().distSqr(q.at()) > 16) {
            best.teleportTo(q.at().getX() + 0.5, q.at().getY(), q.at().getZ() + 0.5);
            best.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
        best.addTag(MARK);
        SystemsData d = SystemsData.get(server);
        SystemsData.Under u = d.s.under.computeIfAbsent(q.key(), k -> new SystemsData.Under());
        u.id = best.getStringUUID();
        u.sx = q.at().getX();
        u.sy = q.at().getY();
        u.sz = q.at().getZ();
        u.fell = 0;
        u.due = 0;
        d.setDirty();
        Giant g = AdminServer.giant(q.kind());
        String name = g == null ? q.kind() : AdminServer.nameOf(g);
        Look.tellNear(best, Component.literal("A " + name + " stirs in the " + (q.level().dimension().location()
                .toString().equals(END) ? "End" : "Nether") + ".").withStyle(ChatFormatting.DARK_PURPLE), 200);
        SystemsAdmin.changed();
    }

    /** the most room under the roof in this column near this height: {the standing block, how tall the room is} */
    static int @Nullable [] roomUnderRoof(ServerLevel l, int x, int z, int nearY) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = Math.min(l.getMaxBuildHeight() - 1, l.getMinBuildHeight() + l.getLogicalHeight() - 2);
        int bottom = Math.max(l.getMinBuildHeight() + 1, nearY - 64);
        top = Math.min(top, nearY + 64);
        int best = -1, bestRoom = 0, run = 0;
        for (int y = top; y >= bottom; y--) {
            BlockState s = l.getBlockState(p.set(x, y, z));
            boolean air = s.getCollisionShape(l, p).isEmpty() && s.getFluidState().isEmpty();
            if (air) {
                run++;
                continue;
            }
            // something solid: a floor under the run above it (not lava, not a fluid)
            if (run >= 3 && s.isFaceSturdy(l, p, Direction.UP) && s.getFluidState().isEmpty() && run > bestRoom) {
                bestRoom = run;
                best = y + 1;
            }
            run = 0;
        }
        return best < 0 ? null : new int[] {best, bestRoom};
    }

    /** open land (the End's islands): the ground there, never over the void */
    static int @Nullable [] open(ServerLevel l, int x, int z) {
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= l.getMinBuildHeight() + 2) return null;
        BlockPos under = new BlockPos(x, y - 1, z);
        BlockState s = l.getBlockState(under);
        if (!s.isFaceSturdy(l, under, Direction.UP) || !s.getFluidState().isEmpty()
                || l.getFluidState(under.above()).is(FluidTags.LAVA)) return null;
        return new int[] {y, l.getMaxBuildHeight() - y};
    }

    // his own settings

    record Respawn(boolean on, double days) {}

    static Respawn respawn(MinecraftServer server, Giant g) {
        AdminJson.Report rep = AdminServer.report(server, g);
        boolean on = true;
        double days = 1;
        if (rep != null) {
            AdminSetting r = rep.setting("respawn");
            if (r != null) try {
                on = r.value().getAsBoolean();
            } catch (RuntimeException e) {
                on = true;
            }
            AdminSetting dd = rep.setting("worldRespawnDays");
            if (dd != null) try {
                days = dd.value().getAsDouble();
            } catch (RuntimeException e) {
                days = 1;
            }
        }
        return new Respawn(on, Math.max(0, days));
    }

    /** the size he comes at on his own: the world's own size times a draw in his size spread, times all-giants size */
    static double spawnSize(MinecraftServer server, Giant g) {
        AdminJson.Report rep = AdminServer.report(server, g);
        double base = 1, lo = 1, hi = 1;
        if (rep != null) {
            AdminSetting w = rep.setting("worldScale");
            if (w != null) try {
                base = w.value().getAsDouble();
            } catch (RuntimeException e) {
                base = 1;
            }
            AdminSetting sp = rep.setting("spawnSize");
            if (sp != null && sp.value() instanceof JsonArray a && a.size() == 2) {
                lo = a.get(0).getAsDouble();
                hi = a.get(1).getAsDouble();
            }
        }
        double all = 1;
        JsonElement v = AdminWorld.get(server).values(AdminCatalog.ALL).get("all.size");
        if (v != null) try {
            all = v.getAsDouble();
        } catch (RuntimeException e) {
            all = 1;
        }
        return base * (lo + Math.random() * (hi - lo)) * all;
    }

    /** his smallest and biggest size (his live header) */
    static double[] sizeRange(MinecraftServer server, Giant g) {
        List<String> l = AdminServer.call(server, g, "live", "1");
        JsonObject h = l == null || l.isEmpty() ? null : AdminJson.object(l.get(0));
        if (h != null && h.get("size") instanceof JsonArray a && a.size() == 2)
            return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble()};
        return new double[] {0.01, 4};
    }

    static List<String> moods(MinecraftServer server, Giant g) {
        List<String> l = AdminServer.call(server, g, "live", "1");
        JsonObject h = l == null || l.isEmpty() ? null : AdminJson.object(l.get(0));
        return h == null ? List.of() : Systems.names(h.getAsJsonArray("moods"));
    }

    /** (the book) each one kept below: kind, dimension, and standing / when the next comes / never */
    static JsonArray json(MinecraftServer server) {
        JsonArray a = new JsonArray();
        if (!on(server)) return a;
        long now = server.overworld().getGameTime();
        for (var e : SystemsData.get(server).s.under.entrySet()) {
            String[] k = e.getKey().split("\\|", 2);
            if (k.length < 2 || !dimsFor(k[0], Systems.option(server, "underworld")).contains(k[1])) continue;
            JsonObject j = new JsonObject();
            j.addProperty("kind", k[0]);
            j.addProperty("dim", k[1]);
            SystemsData.Under u = e.getValue();
            j.addProperty("state", !u.id.isEmpty() ? "standing" : u.never ? "never" : u.fell > 0 && now < u.due
                    ? "waits" : "comes");
            if (u.id.isEmpty() && !u.never && u.fell > 0 && now < u.due)
                j.addProperty("minutes", Math.max(1, (u.due - now) / 1200));
            a.add(j);
        }
        return a;
    }

    // (the tests)

    private static boolean forced;

    public static void lookForTests(MinecraftServer server) {
        forced = true;
        try {
            Watch.forget();
            tick(server);
        } finally {
            forced = false;
        }
    }
}
