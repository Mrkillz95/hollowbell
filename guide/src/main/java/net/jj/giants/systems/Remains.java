package net.jj.giants.systems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * (Part 8, ADMIN_BRIDGE.md §24.3, §24.5) What stays where a giant fell: his skeleton (bone, left for good, mined like
 * any block) or his corpse (flesh in his colours over the bones, rotting away in four stages). Each kind has his own
 * body plan, sized to him. Built a few hundred blocks a tick, only in loaded land, only into air or plants, never
 * where a player stands.
 */
public final class Remains {
    private Remains() {}

    public static final int PER_TICK = 400, MOST_BLOCKS = 60000;
    /** layers: bones last; inner flesh; outer flesh (rots first) */
    static final int BONE = 0, INNER = 1, OUTER = 2;

    /** the remains still being put down: index of the next block to place */
    private static final Map<SystemsData.Remains, Integer> building = new LinkedHashMap<>();

    static void forget() {
        building.clear();
    }

    /** one kind's body: his frame, his colours */
    record Plan(double ribH, double ribW, int legs, double legLen, boolean ring, int heads, double neck, double skull,
            double tail, boolean antlers, boolean wings, boolean shell, boolean roots, BlockState bone,
            BlockState frame, BlockState flesh, BlockState flesh2, BlockState inner, BlockState rotten) {}

    static Plan plan(String kind) {
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        return switch (kind) {
            case "pitchgut" -> new Plan(0.24, 0.2, 4, 0.22, false, 1, 0.12, 0.09, 0.15, false, false, false, false,
                    bone, bone, Blocks.BLACK_TERRACOTTA.defaultBlockState(), Blocks.BLACK_TERRACOTTA.defaultBlockState(),
                    Blocks.NETHER_WART_BLOCK.defaultBlockState(), Blocks.MUD.defaultBlockState());
            case "furrowmaw" -> new Plan(0.05, 0.05, 0, 0, true, 1, 0.0, 0.06, 0.05, false, false, false, false,
                    bone, bone, Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(),
                    Blocks.NETHER_WART_BLOCK.defaultBlockState(), Blocks.ROOTED_DIRT.defaultBlockState());
            case "cerberus" -> new Plan(0.16, 0.13, 4, 0.25, false, 3, 0.16, 0.06, 0.25, false, false, false, false,
                    bone, bone, Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState(),
                    Blocks.NETHER_WART_BLOCK.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState());
            case "hollowbell" -> new Plan(0.12, 0.1, 2, 0.2, false, 1, 0.22, 0.05, 0.12, false, true, false, false,
                    bone, bone, Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(),
                    Blocks.PINK_TERRACOTTA.defaultBlockState(), Blocks.MUD.defaultBlockState());
            case "lanternwillow" -> new Plan(0.14, 0.12, 0, 0, false, 1, 0.1, 0.06, 0.0, false, false, false, true,
                    bone, Blocks.STRIPPED_OAK_LOG.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(),
                    Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState(),
                    Blocks.MUD.defaultBlockState());
            case "wreckback" -> new Plan(0.14, 0.2, 8, 0.2, false, 1, 0.02, 0.06, 0.05, false, false, true, false,
                    bone, Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.WARPED_WART_BLOCK.defaultBlockState(),
                    Blocks.WARPED_WART_BLOCK.defaultBlockState(), Blocks.PRISMARINE.defaultBlockState(),
                    Blocks.MUD.defaultBlockState());
            case "swarmforge" -> new Plan(0.18, 0.16, 4, 0.22, false, 1, 0.06, 0.07, 0.0, false, false, false, false,
                    Blocks.IRON_BLOCK.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState(),
                    Blocks.WEATHERED_COPPER.defaultBlockState(), Blocks.WEATHERED_COPPER.defaultBlockState(),
                    Blocks.CUT_COPPER.defaultBlockState(), Blocks.OXIDIZED_COPPER.defaultBlockState());
            case "gloamwick" -> new Plan(0.16, 0.12, 4, 0.3, false, 1, 0.16, 0.07, 0.04, true, false, false, false,
                    bone, bone, Blocks.SCULK.defaultBlockState(), Blocks.SCULK.defaultBlockState(),
                    Blocks.SOUL_SOIL.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState());
            default -> new Plan(0.15, 0.12, 4, 0.2, false, 1, 0.1, 0.06, 0.1, false, false, false, false, bone, bone,
                    Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(),
                    Blocks.NETHER_WART_BLOCK.defaultBlockState(), Blocks.MUD.defaultBlockState());
        };
    }

    /** his body as blocks, in the world, lying where he fell facing the way he faced */
    static final class Body {
        final Map<Long, BlockState> state = new LinkedHashMap<>();
        final Map<Long, Integer> layer = new HashMap<>();
        final double ox, oz, fx, fz, rx, rz;
        final int baseY;

        Body(double ox, int baseY, double oz, float yaw) {
            this.ox = ox;
            this.oz = oz;
            this.baseY = baseY;
            double r = Math.toRadians(yaw);
            fx = -Math.sin(r);
            fz = Math.cos(r);
            rx = Math.cos(r);
            rz = Math.sin(r);
        }

        BlockPos at(double a, double b, double y) {
            return BlockPos.containing(ox + fx * a + rx * b, baseY + y, oz + fz * a + rz * b);
        }

        /** a block of this layer (bones win over flesh: a lower layer is never covered by a higher one) */
        void put(double a, double b, double y, BlockState s, int lay) {
            if (y < 0) return;
            long k = at(a, b, y).asLong();
            Integer was = layer.get(k);
            if (was != null && was <= lay) return;
            state.put(k, s);
            layer.put(k, lay);
        }

        /** a line of blocks, this thick, along its own way (bone blocks turned to lie along it) */
        void line(double a1, double b1, double y1, double a2, double b2, double y2, double thick, BlockState s,
                int lay) {
            double da = a2 - a1, db = b2 - b1, dy = y2 - y1;
            double len = Math.sqrt(da * da + db * db + dy * dy);
            int steps = Math.max(1, (int) Math.ceil(len * 2));
            BlockState t = s;
            if (s.hasProperty(RotatedPillarBlock.AXIS)) {
                double wx = Math.abs(fx * da + rx * db), wz = Math.abs(fz * da + rz * db), wy = Math.abs(dy);
                Direction.Axis ax = wy >= wx && wy >= wz ? Direction.Axis.Y : wx >= wz ? Direction.Axis.X
                        : Direction.Axis.Z;
                t = s.setValue(RotatedPillarBlock.AXIS, ax);
            }
            int r = (int) Math.floor(thick / 2);
            for (int i = 0; i <= steps; i++) {
                double f = i / (double) steps;
                double a = a1 + da * f, b = b1 + db * f, y = y1 + dy * f;
                for (int x = -r; x <= r; x++)
                    for (int z = -r; z <= r; z++)
                        for (int h = -r; h <= r; h++)
                            if (x * x + z * z + h * h <= r * r + 0.5) put(a + x, b + z, y + h, t, lay);
            }
        }

        void ball(double a, double b, double y, double r, BlockState s, int lay) {
            int R = (int) Math.ceil(r);
            for (int x = -R; x <= R; x++)
                for (int z = -R; z <= R; z++)
                    for (int h = -R; h <= R; h++)
                        if (x * x + z * z + h * h <= r * r) put(a + x, b + z, y + h, s, lay);
        }
    }

    /** the body plan, at this length (blocks along his spine) */
    static Body body(String kind, double len, double ox, int baseY, double oz, float yaw, boolean corpse) {
        Plan p = plan(kind);
        Body b = new Body(ox, baseY, oz, yaw);
        double L = len, half = L / 2;
        double th = L >= 40 ? 2 : 1;
        double H = Math.max(2, p.ribH() * L), W = Math.max(1.5, p.ribW() * L);
        BlockState bone = p.bone(), frame = p.frame();
        if (p.ring()) {
            // a long worm: the spine down the middle, a ring of ribs round it all the way, a ring of teeth at the front
            double R = Math.max(1.5, Math.max(H, W));
            b.line(-half, 0, R, half, 0, R, th, bone, BONE);
            double gap = Math.max(3, L / 24);
            for (double a = -half + gap / 2; a < half - 1; a += gap)
                for (int i = 0; i < 24; i++) {
                    double t = i / 24.0 * Math.PI * 2;
                    b.put(a, Math.cos(t) * R, R + Math.sin(t) * R, bone, BONE);
                }
            for (int i = 0; i < 12; i++) {
                double t = i / 12.0 * Math.PI * 2;
                b.line(half, Math.cos(t) * R * 1.2, R + Math.sin(t) * R * 1.2, half + R * 0.6, Math.cos(t) * R * 0.5,
                        R + Math.sin(t) * R * 0.5, 1, bone, BONE);
            }
            if (corpse) shell(b, p, 0, R, half, R * 1.2, R * 1.2);
            return b;
        }
        // the spine
        b.line(-half, 0, H, half, 0, H, th, frame == bone ? bone : frame, BONE);
        // the ribs, arching down to the ground each side
        double gap = Math.max(2, L / 12);
        for (double a = -half * 0.6; a <= half * 0.5; a += gap) {
            int n = (int) Math.max(8, Math.PI * (H + W));
            double pa = 0, pb = W, py = 0;
            for (int i = 1; i <= n; i++) {
                double t = Math.PI * i / n;
                double nb = W * Math.cos(t), ny = H * Math.sin(t);
                b.line(a, pb, py, a, nb, ny, 1, frame, BONE);
                pb = nb;
                py = ny;
            }
        }
        // a crab's shell: timbers and bone over the ribs
        if (p.shell()) {
            for (double a = -half * 0.7; a <= half * 0.7; a += 1)
                for (int i = 0; i <= 16; i++) {
                    double t = Math.PI * i / 16;
                    if ((int) Math.round(a) % 3 == 0 || i % 4 == 0)
                        b.put(a, W * 1.15 * Math.cos(t), H * 1.25 * Math.sin(t), (int) Math.round(a) % 6 == 0 ? bone
                                : frame, BONE);
                }
        }
        // legs (or a willow's roots), splayed out on the ground
        if (p.roots()) {
            for (int i = 0; i < 8; i++) {
                double t = i / 8.0 * Math.PI * 2;
                double ca = -half * 0.8, len2 = L * 0.35;
                b.line(ca, 0, H * 0.6, ca + Math.cos(t) * len2, Math.sin(t) * len2, 0, 1, frame, BONE);
            }
        }
        if (p.legs() > 0) {
            double legL = Math.max(3, p.legLen() * L);
            int pairs = p.legs() / 2;
            for (int i = 0; i < pairs; i++) {
                double a = pairs == 1 ? half * 0.3 : -half * 0.55 + i * (half * 1.1 / (pairs - 1));
                for (int s : new int[] {-1, 1}) {
                    double kneeB = s * (W + legL * 0.5), footB = s * (W + legL);
                    b.line(a, s * W * 0.6, H * 0.4, a + legL * 0.1, kneeB, H * 0.5, th, bone, BONE);
                    b.line(a + legL * 0.1, kneeB, H * 0.5, a + legL * 0.3, footB, 0, 1, bone, BONE);
                }
            }
        }
        // wings: long arms laid out to the sides, finger bones fanning back
        if (p.wings()) {
            double wl = L * 0.7;
            for (int s : new int[] {-1, 1}) {
                double ea = half * 0.2, eb = s * (W + wl * 0.5);
                b.line(half * 0.3, s * W * 0.5, H * 0.8, ea, eb, H * 0.3, th, bone, BONE);
                b.line(ea, eb, H * 0.3, ea - wl * 0.1, s * (W + wl), 0, 1, bone, BONE);
                for (int f = 0; f < 4; f++)
                    b.line(ea, eb, H * 0.3, ea - wl * (0.3 + f * 0.15), s * (W + wl * (0.8 - f * 0.18)), 0, 1, bone,
                            BONE);
            }
        }
        // the tail
        if (p.tail() > 0) {
            double tl = p.tail() * L;
            b.line(-half, 0, H, -half - tl * 0.5, 0, H * 0.4, th, bone, BONE);
            b.line(-half - tl * 0.5, 0, H * 0.4, -half - tl, 0, 0, 1, bone, BONE);
        }
        // the neck(s) and skull(s); a moose's antlers
        double nl = Math.max(1, p.neck() * L), sk = Math.max(1.2, p.skull() * L);
        for (int hIdx = 0; hIdx < p.heads(); hIdx++) {
            double spread = p.heads() == 1 ? 0 : (hIdx - 1) * W * 0.9;
            double na = half + nl, nb = spread, ny = H + nl * 0.4;
            b.line(half, 0, H, na, nb, ny, th, bone, BONE);
            b.ball(na + sk * 0.6, nb, ny, sk, bone, BONE);
            // the jaw, open on the ground
            b.line(na + sk * 0.2, nb, ny - sk, na + sk * 2.2, nb, Math.max(0, ny - sk * 1.8), 1, bone, BONE);
            if (p.antlers()) {
                double aw = L * 0.25;
                for (int s : new int[] {-1, 1}) {
                    double ta = na + sk * 0.3, tb = nb + s * sk, ty = ny + sk;
                    double ea = ta - aw * 0.2, eb = tb + s * aw, ey = ty + aw * 0.5;
                    b.line(ta, tb, ty, ea, eb, ey, 1, bone, BONE);
                    for (int t = 1; t <= 4; t++) {
                        double f = t / 5.0;
                        double pa = ta + (ea - ta) * f, pb = tb + (eb - tb) * f, py = ty + (ey - ty) * f;
                        b.line(pa, pb, py, pa + aw * 0.12, pb + s * aw * 0.1, py + aw * 0.3, 1, bone, BONE);
                    }
                }
            }
        }
        if (corpse) shell(b, p, 0, H, half, W * 1.15, H * 1.2);
        return b;
    }

    /** a corpse's flesh: an outer and an inner layer round his middle (half of each colour for the Cerberus) */
    private static void shell(Body b, Plan p, double ca, double cy, double ra, double rb, double ry) {
        int A = (int) Math.ceil(ra), B = (int) Math.ceil(rb), Y = (int) Math.ceil(cy + ry);
        for (int a = -A; a <= A; a++)
            for (int s = -B; s <= B; s++)
                for (int y = 0; y <= Y; y++) {
                    double d = (a * a) / (ra * ra) + (s * s) / (rb * rb) + ((y - cy) * (y - cy)) / (ry * ry);
                    if (d > 1) continue;
                    double rad = Math.sqrt(d);
                    if (rad > 0.8) b.put(ca + a, s, y, s < 0 ? p.flesh2() : p.flesh(), OUTER);
                    else if (rad > 0.6) b.put(ca + a, s, y, p.inner(), INNER);
                }
    }

    /** he fell: his remains go down where he lies */
    static void build(ServerLevel l, String kind, LivingEntity e, String rule, double corpseDays) {
        double size = FunGiants.sizeOf(e);
        double len = Math.max(6, Math.min(96, Math.max(FunGiants.height(kind, size), 2 * FunGiants.reach(kind, size))
                * (kind.equals("furrowmaw") ? 0.6 : 0.55)));
        int bx = e.getBlockX(), bz = e.getBlockZ();
        int ground = l.isLoaded(e.blockPosition())
                ? l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) : e.getBlockY();
        int baseY = Math.min(ground, e.getBlockY() + 2);
        if (baseY < l.getMinBuildHeight() + 1) baseY = e.getBlockY();
        boolean corpse = rule.equals("corpse");
        Body b = body(kind, len, e.getX(), baseY, e.getZ(), e.getYRot(), corpse);
        SystemsData.Remains r = new SystemsData.Remains();
        r.kind = kind;
        r.dim = l.dimension().location().toString();
        r.rule = corpse ? "corpse" : "skeleton";
        r.x = bx;
        r.y = baseY;
        r.z = bz;
        r.fell = l.getGameTime();
        r.rotTicks = (long) (Math.max(0.5, corpseDays) * 24000);
        int n = 0;
        for (var en : b.state.entrySet()) {
            if (n++ >= MOST_BLOCKS) break;
            r.pos.add(en.getKey());
            r.layer.add(b.layer.get(en.getKey()));
            r.block.add(NbtUtils.writeBlockState(en.getValue()).toString());
        }
        SystemsData d = SystemsData.get(l.getServer());
        d.s.remains.add(r);
        d.setDirty();
        building.put(r, 0);
        String name = AdminServer.giant(kind) == null ? kind : AdminServer.nameOf(AdminServer.giant(kind));
        Systems.log("a {} fell at {}: his {} goes down ({} blocks)", name, e.blockPosition().toShortString(), r.rule,
                r.pos.size());
        SystemsAdmin.changed();
    }

    /** a placed block, read back */
    private static BlockState state(ServerLevel l, String s) {
        try {
            CompoundTag t = TagParser.parseTag(s);
            return NbtUtils.readBlockState(l.holderLookup(Registries.BLOCK), t);
        } catch (Exception bad) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    private static boolean free(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return (s.isAir() || s.canBeReplaced()) && !s.hasBlockEntity();
    }

    /** every tick: remains going down; once a second, corpses rotting */
    public static void tick(MinecraftServer server) {
        if (!building.isEmpty()) place(server);
        if (server.getTickCount() % 20 != 0) return;
        SystemsData d = SystemsData.get(server);
        if (d.s.remains.isEmpty()) return;
        // (after a restart: what wasn't down yet carries on)
        for (SystemsData.Remains r : d.s.remains) if (r.stage < 0) building.putIfAbsent(r, -r.stage - 1);
        boolean changed = false;
        for (SystemsData.Remains r : new ArrayList<>(d.s.remains)) {
            if (!r.rule.equals("corpse") || building.containsKey(r)) continue;
            ServerLevel l = level(server, r.dim);
            if (l == null) continue;
            int want = (int) Math.min(4, 4 * (l.getGameTime() - r.fell) / Math.max(1, r.rotTicks));
            if (want <= r.stage) continue;
            if (!l.isLoaded(new BlockPos(r.x, r.y, r.z))) continue;
            Plan p = plan(r.kind);
            BlockState rotten = p.rotten();
            while (r.stage < want) {
                r.stage++;
                for (int i = 0; i < r.pos.size(); i++) {
                    BlockPos pos = BlockPos.of(r.pos.get(i));
                    if (!l.isLoaded(pos)) continue;
                    int lay = r.layer.get(i);
                    BlockState cur = l.getBlockState(pos), was = state(l, r.block.get(i));
                    boolean ours = cur == was || r.stage >= 2 && lay == OUTER && cur == rotten;
                    if (!ours) continue;
                    if (r.stage == 1 && lay == OUTER) l.setBlock(pos, rotten, Block.UPDATE_CLIENTS);
                    else if (r.stage == 2 && lay == OUTER || r.stage == 3 && lay == INNER || r.stage == 4 && lay == BONE)
                        l.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            if (r.stage >= 4) d.s.remains.remove(r);
            changed = true;
        }
        if (changed) {
            d.setDirty();
            SystemsAdmin.changed();
        }
    }

    private static void place(MinecraftServer server) {
        int budget = PER_TICK;
        SystemsData d = SystemsData.get(server);
        for (var it = building.entrySet().iterator(); it.hasNext() && budget > 0; ) {
            var en = it.next();
            SystemsData.Remains r = en.getKey();
            ServerLevel l = level(server, r.dim);
            if (l == null) {
                it.remove();
                continue;
            }
            List<ServerPlayer> near = l.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(r.x, r.y, r.z, r.x + 1, r.y + 1, r.z + 1).inflate(120));
            int i = en.getValue();
            for (; i < r.pos.size() && budget > 0; i++, budget--) {
                BlockPos pos = BlockPos.of(r.pos.get(i));
                if (!l.isLoaded(pos) || !free(l, pos)) {
                    r.block.set(i, "");
                    continue;
                }
                AABB box = new AABB(pos);
                boolean player = false;
                for (ServerPlayer p : near) if (p.getBoundingBox().intersects(box)) player = true;
                if (player) {
                    r.block.set(i, "");
                    continue;
                }
                l.setBlock(pos, state(l, r.block.get(i)), Block.UPDATE_CLIENTS);
            }
            if (i >= r.pos.size()) {
                // (what couldn't go down isn't ours to take away later)
                for (int k = r.pos.size() - 1; k >= 0; k--)
                    if (r.block.get(k).isEmpty()) {
                        r.pos.remove(k);
                        r.layer.remove(k);
                        r.block.remove(k);
                    }
                r.stage = 0;
                it.remove();
                if (r.rule.equals("skeleton")) d.s.remains.remove(r);
                SystemsAdmin.changed();
            } else {
                en.setValue(i);
                r.stage = -i - 1;
            }
            d.setDirty();
        }
    }

    private static ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        return rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
    }

    /** the remains rotting now */
    public static int rotting(MinecraftServer server) {
        int n = 0;
        for (SystemsData.Remains r : SystemsData.get(server).s.remains) if (r.rule.equals("corpse")) n++;
        return n;
    }

    /** (the tests) a body's blocks, as it would go down */
    public static Map<Long, BlockState> bodyForTests(String kind, double len, boolean corpse) {
        return body(kind, len, 0.5, 64, 0.5, 0, corpse).state;
    }
}
