package net.jj.giants.minigames;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.minigamesmixin.MgServerAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.2) A world of its own for each minigame round, made while the server runs and thrown
 * away when the round ends, so the main world is never touched.
 *
 * <p>How: a new {@link ServerLevel} under the key {@code jj_giants:mg_<id>} is put into the server's own map of worlds
 * (the way the game makes the Nether and the End at start, only later), with the Overworld's kind of sky and a flat
 * generator (grass on stone, no structures, no lakes, no mobs coming on their own). Its clock and weather are the
 * Overworld's, except a fixed time of day and never rain. Nothing of it is ever written to disk (mixins on the chunk
 * and entity savers), so throwing it away is cheap: the world is closed, taken out of the map, and its folder (which
 * holds at most empty region files) deleted. A world left behind by a crash is deleted when the server next starts.
 * Players' clients need nothing: the game sends a world key and the Overworld's kind of sky, which every client knows.
 */
public final class MgLevels {
    private MgLevels() {}

    public static final String NS = GiantsGuideMod.ID, PREFIX = "mg_";
    /** the height of the grass in a minigame's world (players stand at GROUND + 1) */
    public static final int GROUND = 63;
    /** keeps a minigame's map loaded (and its giants moving) for the whole round */
    public static final TicketType<ChunkPos> TICKET =
            TicketType.create("jj_giants_minigame", Comparator.comparingLong(ChunkPos::toLong));

    /** the worlds made this run that still stand */
    private static final Set<ResourceKey<Level>> made = ConcurrentHashMap.newKeySet();

    /** a minigame's world (made now, or one a crash left behind) */
    public static boolean temporary(@Nullable Level l) {
        return l != null && temporary(l.dimension().location());
    }

    public static boolean temporary(ResourceKey<Level> k) {
        return temporary(k.location());
    }

    public static boolean temporary(@Nullable ResourceLocation id) {
        return id != null && id.getNamespace().equals(NS) && id.getPath().startsWith(PREFIX);
    }

    public static boolean temporary(@Nullable String dim) {
        return dim != null && temporary(ResourceLocation.tryParse(dim));
    }

    /** the Overworld's clock and weather, but a time of day of its own and no rain */
    static final class Data extends DerivedLevelData {
        long day;

        Data(WorldData w, ServerLevelData o, long day) {
            super(w, o);
            this.day = day;
        }

        @Override
        public long getDayTime() {
            return day;
        }

        @Override
        public boolean isRaining() {
            return false;
        }

        @Override
        public boolean isThundering() {
            return false;
        }
    }

    private static final ChunkProgressListener QUIET = new ChunkProgressListener() {
        @Override
        public void updateSpawnPos(ChunkPos p) {}

        @Override
        public void onStatusChange(ChunkPos p, @Nullable ChunkStatus s) {}

        @Override
        public void start() {}

        @Override
        public void stop() {}
    };

    /**
     * A fresh world for one round. Called between ticks only (never while the server walks its worlds): the server's
     * map of worlds is changed here.
     */
    public static ServerLevel create(MinecraftServer server, String id, long dayTime) {
        MgServerAccess a = (MgServerAccess) server;
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(NS, PREFIX + id));
        if (a.jjGiants$levels().containsKey(key)) throw new IllegalStateException("there is a world " + key.location());
        RegistryAccess ra = server.registryAccess();
        Holder<DimensionType> type =
                ra.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD);
        Holder<Biome> plains = ra.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        int bottom = type.value().minY();
        FlatLevelGeneratorSettings flat = new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct()), plains,
                List.of()).withBiomeAndLayers(List.of(new FlatLayerInfo(1, Blocks.BEDROCK),
                        new FlatLayerInfo(GROUND - bottom - 4, Blocks.STONE), new FlatLayerInfo(3, Blocks.DIRT),
                        new FlatLayerInfo(1, Blocks.GRASS_BLOCK)), Optional.of(HolderSet.direct()), plains);
        WorldData wd = server.getWorldData();
        ServerLevel l = new ServerLevel(server, a.jjGiants$executor(), a.jjGiants$storage(),
                new Data(wd, wd.overworldData(), dayTime), key, new LevelStem(type, new FlatLevelSource(flat)), QUIET,
                false, BiomeManager.obfuscateSeed(wd.worldGenOptions().seed()), List.of(), false, null);
        l.noSave = true;
        l.getChunkSource().setSpawnSettings(false, false);
        // its own border: the vanilla link to the Overworld's is only made for the Nether and the End at start
        a.jjGiants$levels().put(key, l);
        made.add(key);
        ServerWorldEvents.LOAD.invoker().onWorldLoad(server, l);
        GiantsGuideMod.LOG.info("Giants minigames: made the world {}", key.location());
        return l;
    }

    /** the time of day in a minigame's world */
    public static void setDayTime(ServerLevel l, long day) {
        if (l.getLevelData() instanceof Data d) d.day = day;
    }

    /** a world going away: its tickets let go first, so its land unloads (nothing is written), then it closes */
    private record Going(ServerLevel level, int[] ticks) {}

    private static final List<Going> going = new ArrayList<>();

    /** let it go: closed and deleted within a few seconds (once its land has unloaded) */
    public static void retire(ServerLevel l) {
        for (Going g : going) if (g.level() == l) return;
        for (ServerPlayer p : List.copyOf(l.players())) sendOut(l.getServer(), p);
        l.getChunkSource().removeTicketsOnClosing();
        going.add(new Going(l, new int[] {0}));
    }

    /** each tick (between ticks): the worlds let go are closed once quiet, or after ten seconds */
    static void tick(MinecraftServer server) {
        if (going.isEmpty()) return;
        for (Going g : List.copyOf(going)) {
            ServerLevel l = g.level();
            for (ServerPlayer p : List.copyOf(l.players())) sendOut(server, p);
            if (++g.ticks()[0] < 200 && l.getChunkSource().chunkMap.hasWork()) continue;
            going.remove(g);
            close(server, l);
        }
    }

    /** someone still in a world being thrown away (it shouldn't happen: the game sends everyone home first) */
    private static void sendOut(MinecraftServer server, ServerPlayer p) {
        ServerLevel o = server.overworld();
        var sp = o.getSharedSpawnPos();
        GiantsGuideMod.LOG.warn("Giants minigames: {} was still in {} as it closed: sent to spawn",
                p.getGameProfile().getName(), p.level().dimension().location());
        p.teleportTo(o, sp.getX() + 0.5, o.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types
                .MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ()), sp.getZ() + 0.5, p.getYRot(), p.getXRot());
    }

    /** closed now and its folder deleted (the server stopping, or a retired one gone quiet) */
    public static void close(MinecraftServer server, ServerLevel l) {
        MgServerAccess a = (MgServerAccess) server;
        ResourceKey<Level> key = l.dimension();
        going.removeIf(g -> g.level() == l);
        for (ServerPlayer p : List.copyOf(l.players())) sendOut(server, p);
        if (a.jjGiants$levels().get(key) == l) {
            a.jjGiants$levels().remove(key);
            ServerWorldEvents.UNLOAD.invoker().onWorldUnload(server, l);
        }
        try {
            l.close();
        } catch (Exception e) {
            GiantsGuideMod.LOG.warn("Giants minigames: closing {}: {}", key.location(), e.toString());
        }
        made.remove(key);
        delete(a.jjGiants$storage().getDimensionPath(key));
        GiantsGuideMod.LOG.info("Giants minigames: threw away the world {}", key.location());
    }

    /** every world of ours still standing (the server stopping) */
    public static List<ServerLevel> standing(MinecraftServer server) {
        List<ServerLevel> out = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) if (temporary(l)) out.add(l);
        return out;
    }

    public static boolean madeNow(ResourceKey<Level> k) {
        return made.contains(k);
    }

    /** the server starting: folders a crash left behind are deleted (none of them is loaded) */
    public static int cleanOnStart(MinecraftServer server) {
        Path dims = server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(NS);
        int n = 0;
        if (!Files.isDirectory(dims)) return 0;
        try (Stream<Path> s = Files.list(dims)) {
            for (Path p : s.toList())
                if (p.getFileName().toString().startsWith(PREFIX) && delete(p)) n++;
        } catch (IOException e) {
            GiantsGuideMod.LOG.warn("Giants minigames: couldn't look for old minigame worlds: {}", e.toString());
        }
        if (n > 0) GiantsGuideMod.LOG.info("Giants minigames: deleted {} minigame world(s) left from before", n);
        return n;
    }

    /** a folder and everything in it */
    static boolean delete(Path dir) {
        if (!Files.exists(dir)) return true;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            return true;
        } catch (IOException e) {
            GiantsGuideMod.LOG.warn("Giants minigames: couldn't delete {} (it goes at the next start): {}", dir,
                    e.toString());
            return false;
        }
    }

    static void forget() {
        going.clear();
        made.clear();
    }
}
