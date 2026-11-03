package net.jj.hollowbell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.jj.hollowbell.world.GroundRules;
import net.jj.hollowbell.world.NoWait;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * (JJ answers 2, ADMIN_BRIDGE.md 22.16 and 23.6) Two of the Giants Guide's all-giants settings his own spawner keeps
 * to, sent with the others in "adminset" (absent = as always; nothing saved, the Guide sends it all again):
 * <ul>
 *   <li>"Giants in the Overworld / Nether / End" ({@code all.dimOverworld} ... {@code nogiants}): the world's own one
 *       doesn't come there while it says so; his time keeps running, and he comes as usual once it's allowed.</li>
 *   <li>"Giants in each other's grounds" ({@code all.spawnMix}): each time the world's own one comes, his ground or
 *       another giant's is drawn (each the same chance). In another's he comes once the land there is loaded
 *       (someone is there), on a spot he can live on (his {@link GiantsFun#PLACES}: dry land, or deep water if he
 *       swims); none there, or nobody comes within a day: drawn again.</li>
 * </ul>
 */
public final class GiantsMix {
    private GiantsMix() {}

    public static final String MIX = "all.spawnMix";
    /** each dimension's rule */
    private static final Map<String, String> DIMS = Map.of("all.dimOverworld", "minecraft:overworld",
            "all.dimNether", "minecraft:the_nether", "all.dimEnd", "minecraft:the_end");
    /** how long he waits for someone to come to the other ground before it's drawn again: a game day */
    public static final long WAIT = 24000;

    private static volatile Map<String, Boolean> shut = Map.of();
    private static volatile boolean mix;

    /** out of adminset's "all" (GiantsPlayers.receiveAll) */
    public static void receive(JsonObject all) {
        Map<String, Boolean> m = new HashMap<>();
        for (var e : DIMS.entrySet()) {
            JsonElement v = all.get(e.getKey());
            if (v != null && v.isJsonPrimitive() && v.getAsString().toLowerCase(Locale.ROOT).equals("nogiants"))
                m.put(e.getValue(), true);
        }
        shut = Map.copyOf(m);
        JsonElement x = all.get(MIX);
        mix = x != null && x.isJsonPrimitive() && x.getAsString().equalsIgnoreCase("true");
    }

    public static void clear() {
        shut = Map.of();
        mix = false;
    }

    /** giants may not be in this world now (its "no giants" rule) */
    public static boolean held(Level level) {
        return shut.containsKey(level.dimension().location().toString());
    }

    /** the dimension holding him back, or null */
    public static @Nullable String heldIn(Level level) {
        return held(level) ? level.dimension().location().toString() : null;
    }

    public static boolean mix() {
        return mix;
    }

    /** another giant's ground he may come in */
    public record Pick(String giant, int x, int z, int r) {}

    /** (the tests) the next draw */
    public static @Nullable Pick testPick;
    public static boolean testOwn;

    /** his own ground (null) or another giant's, each the same chance */
    public static @Nullable Pick choose(MinecraftServer server, RandomSource random) {
        if (testOwn) return null;
        if (testPick != null) return testPick;
        List<Pick> all = new ArrayList<>();
        for (GroundRules.Ground g : GroundRules.others(server, GroundRules.ME))
            if (g.r() > 0) all.add(new Pick(g.giant(), g.x(), g.z(), g.r()));
        int i = random.nextInt(all.size() + 1);
        return i == all.size() ? null : all.get(i);
    }

    /** the land in the middle of that ground is loaded (someone is there) */
    public static boolean ready(ServerLevel level, Pick p) {
        return NoWait.loaded(level, p.x(), p.z());
    }

    /** where he can live (his "fun" places: land, sea, cave, sky) */
    public static List<String> places() {
        return GiantsFun.PLACES;
    }

    /** a spot in that ground where he can live, by his own places */
    public static @Nullable BlockPos spot(ServerLevel level, Pick p, RandomSource random) {
        return spot(level, p, GiantsFun.PLACES, random);
    }

    /**
     * a spot in that ground he can live on, in loaded land: dry land (a "land" giant), or water at least three deep
     * (one that swims, "sea"); null if none of a few tries finds one
     */
    public static @Nullable BlockPos spot(ServerLevel level, Pick p, List<String> places, RandomSource random) {
        boolean land = places.contains("land"), sea = places.contains("sea");
        double reach = Math.max(1, p.r() * 0.6);
        for (int i = 0; i < 24; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = i == 0 ? 0 : Math.sqrt(random.nextDouble()) * reach;
            int x = Mth.floor(p.x() + Math.cos(a) * d), z = Mth.floor(p.z() + Math.sin(a) * d);
            if (!NoWait.loaded(level, x, z) || !level.getWorldBorder().isWithinBounds(x, z)) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            if (top <= level.getMinBuildHeight()) continue;
            boolean wet = top > floor || !level.getFluidState(new BlockPos(x, top - 1, z)).isEmpty();
            if (!wet && land) return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (wet && sea && top - floor >= 3
                    && level.getFluidState(new BlockPos(x, top - 1, z)).is(net.minecraft.tags.FluidTags.WATER))
                return new BlockPos(x, floor, z);
        }
        return null;
    }
}
