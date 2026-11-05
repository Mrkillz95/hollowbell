package net.jj.giants.adventure;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.fun.Marks;
import net.jj.giants.fun.MgShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 11, ADMIN_BRIDGE.md §28.5) Adventure maps: a box of the world saved (its blocks, chests and signs, the giants
 * standing in it with their scripts, sizes, moods and tags, the paths and areas inside it, and the hooks aimed at
 * those giants' tags), to be played as a minigame round in a world of its own (minigames.AdvMode). Saving reads a few
 * thousand columns a tick from loaded land only. The blocks go to <world>/jj_giants_adventures/<name>.nbt; the rest
 * is described in the world's adventure data. One built-in map ships ready to play: The Wreck Cove Rescue (WreckCove).
 */
public final class AdvMaps {
    private AdvMaps() {}

    public static final int MOST_SIDE = 192, MOST_TALL = 128;
    public static final String BUILT_IN = "wreck_cove";

    /** one giant of a map: his kind, where (map blocks from its middle; y unused), size, mood, script, tags */
    public static final class MapGiant {
        public String kind = "", mood = "", script = "", wears = "", name = "";
        public double x, z, size = 0.15;
        public List<String> tags = new ArrayList<>();
        /** players' blows don't land on him (one to keep alive) */
        public boolean protect;
        /** any kind will do when his own isn't installed */
        public List<String> or = new ArrayList<>();
    }

    /** what a map file describes besides its blocks */
    public static final class Meta {
        public List<MapGiant> giants = new ArrayList<>();
        public AdvData.State state = new AdvData.State();
    }

    /** a map ready to build into a round's world: its pieces, its giants and its own paths, areas, scripts, hooks */
    public static final class Loaded {
        public String id = "", title = "", goal = "", timeUp = "lose", winArea = "";
        public int half = 64;
        public final List<Object[]> pieces = new ArrayList<>();
        public final List<MapGiant> giants = new ArrayList<>();
        /** coordinates are the round world's (dim filled in when the round begins) */
        public AdvData.State state = new AdvData.State();
        public final List<Vec3> starts = new ArrayList<>();
        public Vec3 watch = new Vec3(0, 30, 0);
        /** chests, signs...: {x, y, z} and what they hold */
        public final List<Object[]> blockEntities = new ArrayList<>();
    }

    static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("jj_giants_adventures");
    }

    static Path file(MinecraftServer server, String name) {
        return folder(server).resolve(name + ".nbt");
    }

    // saving

    /** a save going on (one at a time): the box, read a few columns a tick */
    static final class Job {
        String name, by;
        ServerLevel level;
        int x0, y0, z0, sx, sy, sz, col;
        BlockState[] blocks;
        final List<CompoundTag> bes = new ArrayList<>();
        final int[] tops;
        @Nullable ServerPlayer asker;

        Job(int sx, int sz) {
            tops = new int[sx * sz];
        }
    }

    static @Nullable Job job;

    static void forget() {
        job = null;
    }

    public static boolean saving() {
        return job != null;
    }

    /** saves the box between two corners as the adventure map called name (replacing one of that name) */
    public static AdminServer.Result save(MinecraftServer server, @Nullable ServerPlayer p, String name, BlockPos a,
            BlockPos b) {
        String n = AdvData.name(name);
        if (n == null) return AdminServer.Result.fail("A map's name is letters, digits, _ and - (24 at most).");
        if (n.equals(BUILT_IN)) return AdminServer.Result.fail("That name is the built-in map's. Pick another.");
        if (job != null) return AdminServer.Result.fail("A map is being saved already. Wait for it to finish.");
        ServerLevel l = p != null ? p.serverLevel() : server.overworld();
        if (net.jj.giants.minigames.Minigames.inMinigame(p)) return AdminServer.Result.fail(
                "Save maps from the main world, not from inside a minigame.");
        int x0 = Math.min(a.getX(), b.getX()), y0 = Math.min(a.getY(), b.getY()), z0 = Math.min(a.getZ(), b.getZ());
        int sx = Math.abs(a.getX() - b.getX()) + 1, sy = Math.abs(a.getY() - b.getY()) + 1,
                sz = Math.abs(a.getZ() - b.getZ()) + 1;
        if (sx > MOST_SIDE || sz > MOST_SIDE || sy > MOST_TALL)
            return AdminServer.Result.fail("That box is " + sx + " x " + sy + " x " + sz + ": a map may be at most "
                    + MOST_SIDE + " x " + MOST_TALL + " x " + MOST_SIDE + " (wide x tall x deep).");
        if (sx < 8 || sz < 8 || sy < 4) return AdminServer.Result.fail("That box is too small for a map (8 x 4 x 8 at"
                + " least).");
        for (int cx = x0 >> 4; cx <= (x0 + sx - 1) >> 4; cx++)
            for (int cz = z0 >> 4; cz <= (z0 + sz - 1) >> 4; cz++)
                if (l.getChunkSource().getChunkNow(cx, cz) == null) return AdminServer.Result.fail(
                        "Part of that box isn't loaded. Stand near its middle (all of it must be loaded) and try again.");
        Job j = new Job(sx, sz);
        j.name = n;
        j.by = p == null ? "Server" : p.getGameProfile().getName();
        j.level = l;
        j.x0 = x0;
        j.y0 = y0;
        j.z0 = z0;
        j.sx = sx;
        j.sy = sy;
        j.sz = sz;
        j.blocks = new BlockState[sx * sy * sz];
        j.asker = p;
        job = j;
        return AdminServer.Result.said(List.of("Saving the map " + n + " (" + sx + " x " + sy + " x " + sz
                + " blocks)…"));
    }

    static void tick(MinecraftServer server) {
        Job j = job;
        if (j == null) return;
        long until = System.nanoTime() + 6_000_000L;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int cols = j.sx * j.sz;
        while (j.col < cols && System.nanoTime() < until) {
            int lx = j.col % j.sx, lz = j.col / j.sx;
            int x = j.x0 + lx, z = j.z0 + lz;
            LevelChunk c = j.level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (c == null) {
                finish(server, j, "Part of the box was unloaded while it was being saved. Stand near it and try again.");
                return;
            }
            // (the column's top block inside the box: what is above the box doesn't count)
            int topIn = 0;
            for (int ly = 0; ly < j.sy; ly++) {
                m.set(x, j.y0 + ly, z);
                BlockState s = c.getBlockState(m);
                if (!s.isAir() && s.blocksMotion()) topIn = ly;
                j.blocks[(ly * j.sz + lz) * j.sx + lx] = s;
                if (s.hasBlockEntity()) {
                    BlockEntity be = c.getBlockEntity(m);
                    if (be != null) {
                        CompoundTag t = be.saveWithFullMetadata(server.registryAccess());
                        t.putInt("ax", lx);
                        t.putInt("ay", ly);
                        t.putInt("az", lz);
                        j.bes.add(t);
                    }
                }
            }
            j.tops[j.col] = topIn;
            j.col++;
        }
        if (j.col >= cols) finish(server, j, null);
    }

    private static void finish(MinecraftServer server, Job j, @Nullable String failed) {
        job = null;
        AdminServer.Result r;
        if (failed != null) r = AdminServer.Result.fail(failed);
        else {
            try {
                r = write(server, j);
            } catch (IOException | RuntimeException e) {
                GiantsGuideMod.LOG.warn("Giants adventure: couldn't save the map {}", j.name, e);
                r = AdminServer.Result.fail("The map couldn't be saved: " + e.getMessage());
            }
        }
        if (j.asker != null && !j.asker.hasDisconnected())
            for (String line : r.lines()) j.asker.sendSystemMessage(net.minecraft.network.chat.Component.literal(line)
                    .withStyle(r.ok() ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.RED));
        if (!r.announce().isEmpty()) AdminServer.announce(server, j.asker, r.announce());
        Adventure.changed();
    }

    private static AdminServer.Result write(MinecraftServer server, Job j) throws IOException {
        // the ground: the middle height of the box's top blocks (it goes on the round world's grass)
        int[] tops = j.tops.clone();
        Arrays.sort(tops);
        int ground = tops[tops.length / 2];
        // the palette
        Map<BlockState, Integer> index = new LinkedHashMap<>();
        int[] cells = new int[j.blocks.length];
        int solid = 0;
        for (int i = 0; i < j.blocks.length; i++) {
            BlockState s = j.blocks[i] == null ? Blocks.AIR.defaultBlockState() : j.blocks[i];
            Integer k = index.get(s);
            if (k == null) index.put(s, k = index.size());
            cells[i] = k;
            if (!s.isAir()) solid++;
        }
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 1);
        root.putInt("sx", j.sx);
        root.putInt("sy", j.sy);
        root.putInt("sz", j.sz);
        root.putInt("ground", ground);
        ListTag pal = new ListTag();
        for (BlockState s : index.keySet()) pal.add(NbtUtils.writeBlockState(s));
        root.put("palette", pal);
        root.put("blocks", new IntArrayTag(cells));
        ListTag bes = new ListTag();
        bes.addAll(j.bes);
        root.put("bes", bes);
        // the giants in the box, their scripts and hooks, the paths and areas inside it
        Meta meta = new Meta();
        AdvData d = AdvData.get(server);
        String dim = j.level.dimension().location().toString();
        double cx = j.x0 + j.sx / 2.0, cz = j.z0 + j.sz / 2.0;
        Map<String, String> rows = new HashMap<>();
        for (LivingEntity e : FunGiants.standing(server, null)) {
            if (e.level() != j.level || !inBox(j, e.getX(), e.getY(), e.getZ())) continue;
            MapGiant g = new MapGiant();
            g.kind = FunGiants.kindOf(e);
            g.x = e.getX() - cx;
            g.z = e.getZ() - cz;
            g.size = FunGiants.sizeOf(e);
            Giant k = AdminServer.giant(g.kind);
            JsonObject row = k == null ? null : AdvGiants.row(server, e, new HashMap<>());
            g.mood = row == null ? "" : AdminJson.str(row, "mood", 16);
            AdvData.Run run = d.s.runs.get(e.getStringUUID());
            if (run != null) {
                g.script = run.script;
                AdvData.Script sc = d.s.scripts.get(run.script);
                if (sc != null) meta.state.scripts.put(run.script, AdvData.copy(sc, AdvData.Script.class));
            }
            String wears = Marks.wears(e);
            g.wears = wears == null ? "" : wears;
            for (String t : e.getTags()) if (!t.startsWith("jj_giants.")) g.tags.add(t);
            g.protect = e.getTags().contains(PROTECT);
            meta.giants.add(g);
            // the hooks aimed at him by a tag he wears (not "all" or his kind: those stay the world's)
            for (AdvData.Hook h : d.s.hooks) {
                String t = h.target.toLowerCase(Locale.ROOT);
                if (t.equals("all") || t.equals(g.kind) || t.equals(e.getStringUUID())) continue;
                if (Hooks.matches(h.target, e) && meta.state.hooks.stream().noneMatch(x -> x.id == h.id))
                    meta.state.hooks.add(AdvData.copy(h, AdvData.Hook.class));
            }
        }
        // (the scripts those scripts' steps name: paths and areas inside the box, moved to the map's middle)
        for (var p : d.s.paths.entrySet()) {
            if (!p.getValue().dim.equals(dim)) continue;
            boolean in = !p.getValue().pts.isEmpty();
            for (double[] pt : p.getValue().pts) in &= inBox(j, pt[0], pt[1], pt[2]);
            if (!in) continue;
            AdvData.Path q = new AdvData.Path();
            for (double[] pt : p.getValue().pts) q.pts.add(new double[] {pt[0] - cx, pt[1] - j.y0 - ground, pt[2] - cz});
            meta.state.paths.put(p.getKey(), q);
        }
        for (var a : d.s.areas.entrySet()) {
            AdvData.Area v = a.getValue();
            if (!v.dim.equals(dim) || !inBox(j, v.x1, v.y1, v.z1) || !inBox(j, v.x2, v.y2, v.z2)) continue;
            AdvData.Area q = new AdvData.Area();
            q.x1 = (int) Math.floor(v.x1 - cx);
            q.x2 = (int) Math.floor(v.x2 - cx);
            q.z1 = (int) Math.floor(v.z1 - cz);
            q.z2 = (int) Math.floor(v.z2 - cz);
            q.y1 = v.y1 - j.y0 - ground;
            q.y2 = v.y2 - j.y0 - ground;
            meta.state.areas.put(a.getKey(), q);
        }
        root.putString("meta", AdvData.json(meta));
        Files.createDirectories(folder(server));
        Path tmp = folder(server).resolve(j.name + ".nbt.tmp");
        NbtIo.writeCompressed(root, tmp);
        Files.move(tmp, file(server, j.name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        AdvData.MapInfo info = d.s.maps.getOrDefault(j.name, new AdvData.MapInfo());
        info.name = j.name;
        info.by = j.by;
        info.time = System.currentTimeMillis();
        info.sx = j.sx;
        info.sy = j.sy;
        info.sz = j.sz;
        info.giants = meta.giants.size();
        info.blocks = solid;
        info.dim = dim;
        if (info.goal.isEmpty()) info.goal = "Find your way through " + j.name + ".";
        d.s.maps.put(j.name, info);
        d.setDirty();
        Adventure.log(server, j.by, "map", j.name, "saved");
        String line = "Saved the map " + j.name + ": " + j.sx + " x " + j.sy + " x " + j.sz + ", " + solid + " blocks, "
                + meta.giants.size() + (meta.giants.size() == 1 ? " giant" : " giants") + ", "
                + meta.state.scripts.size() + " scripts, " + meta.state.hooks.size() + " hooks, "
                + meta.state.paths.size() + " paths, " + meta.state.areas.size() + " areas.";
        return new AdminServer.Result(true, List.of(line), List.of(j.by + " saved the adventure map " + j.name + "."));
    }

    private static boolean inBox(Job j, double x, double y, double z) {
        return x >= j.x0 && x < j.x0 + j.sx && z >= j.z0 && z < j.z0 + j.sz && y >= j.y0 - 2 && y < j.y0 + j.sy + 2;
    }

    public static final String PROTECT = "adv_protect";

    public static AdminServer.Result delete(MinecraftServer server, String who, String name) {
        AdvData d = AdvData.get(server);
        String n = AdvData.name(name);
        if (n == null || !d.s.maps.containsKey(n)) return AdminServer.Result.fail("There's no saved map called " + name + ".");
        d.s.maps.remove(n);
        d.setDirty();
        try {
            Files.deleteIfExists(file(server, n));
        } catch (IOException e) {
            GiantsGuideMod.LOG.warn("Giants adventure: couldn't delete the map file {}", n, e);
        }
        Adventure.log(server, who, "map", n, "deleted");
        Adventure.changed();
        return new AdminServer.Result(true, List.of("Deleted the map " + n + "."), List.of(who + " deleted the"
                + " adventure map " + n + "."));
    }

    // loading (for a round)

    /** the map ready to build (sizeIndex: the world's map size, for the built-in one), or null with why */
    public static Object load(MinecraftServer server, String name, int sizeIndex, double giantSize) {
        if (name.equals(BUILT_IN)) return WreckCove.make(server, sizeIndex, giantSize);
        AdvData.MapInfo info = AdvData.get(server).s.maps.get(name);
        if (info == null) return "There's no adventure map called " + name + ".";
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(file(server, name), NbtAccounter.unlimitedHeap());
        } catch (IOException | RuntimeException e) {
            GiantsGuideMod.LOG.warn("Giants adventure: couldn't read the map {}", name, e);
            return "The map's file couldn't be read.";
        }
        int sx = root.getInt("sx"), sy = root.getInt("sy"), sz = root.getInt("sz"), ground = root.getInt("ground");
        ListTag pal = root.getList("palette", Tag.TAG_COMPOUND);
        var blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
        BlockState[] states = new BlockState[pal.size()];
        for (int i = 0; i < pal.size(); i++) states[i] = NbtUtils.readBlockState(blocks, pal.getCompound(i));
        int[] cells = root.getIntArray("blocks");
        if (cells.length != sx * sy * sz) return "The map's file is damaged.";
        Meta meta;
        try {
            meta = new com.google.gson.Gson().fromJson(root.getString("meta"), Meta.class);
        } catch (RuntimeException e) {
            meta = null;
        }
        if (meta == null) meta = new Meta();
        if (meta.state == null) meta.state = new AdvData.State();
        meta.state.fill();
        Loaded m = new Loaded();
        m.id = name;
        m.title = title(name);
        m.goal = info.goal;
        m.timeUp = info.timeUp;
        m.winArea = info.winArea;
        int hx = sx / 2, hz = sz / 2;
        int reach = Math.max(hx, hz) + 1;
        m.half = reach + 6;
        final int g0 = ground;
        m.pieces.add(new Object[] {MgShapes.custom(new MgShapes.Columns() {
            @Override
            public int reach() {
                return reach;
            }

            @Override
            public void column(int dx, int dz, int f, MgShapes.Out out) {
                int lx = dx + hx, lz = dz + hz;
                if (lx < 0 || lz < 0 || lx >= sx || lz >= sz) return;
                for (int ly = 0; ly < sy; ly++) {
                    BlockState s = states[cells[(ly * sz + lz) * sx + lx]];
                    int y = f + ly - g0;
                    if (s.isAir() && y > f) continue;
                    out.set(y, s);
                }
            }
        }), 0, 0});
        for (Tag t : root.getList("bes", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            m.blockEntities.add(new Object[] {c.getInt("ax") - hx, c.getInt("ay") - ground, c.getInt("az") - hz, c});
        }
        m.giants.addAll(meta.giants);
        // (map coordinates: x, z from the middle, y from the ground)
        m.state = meta.state;
        AdvData.Area start = m.state.areas.get("start");
        if (start != null)
            for (int i = 0; i < 8; i++)
                m.starts.add(new Vec3(Math.min(start.x1, start.x2) + 0.5 + (Math.abs(start.x2 - start.x1)) * ((i * 37) % 8)
                        / 8.0, 0, Math.min(start.z1, start.z2) + 0.5 + Math.abs(start.z2 - start.z1) * ((i * 53) % 8) / 8.0));
        else for (int i = 0; i < 8; i++) m.starts.add(new Vec3(-hx + 3 + (i % 4) * 2, 0, (i / 4) * 2 - 1));
        m.watch = new Vec3(0, Math.max(20, sy), 0);
        return m;
    }

    /** a map's name as a title ("wreck_cove" -> "Wreck Cove") */
    public static String title(String name) {
        if (name.equals(BUILT_IN)) return "The Wreck Cove Rescue";
        StringBuilder b = new StringBuilder();
        for (String w : name.split("[_\\-]+")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }
}
