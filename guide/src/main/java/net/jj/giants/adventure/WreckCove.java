package net.jj.giants.adventure;

import java.util.ArrayList;
import java.util.List;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.fun.MgShapes;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * (Part 11, ADMIN_BRIDGE.md §28.6) The built-in adventure map, "The Wreck Cove Rescue": a young giant is lost on the
 * beach of Wreck Cove, where a Wreckback guards the wreck. The players come from the harbour to the west; once they're
 * near, the lost giant follows the shore road home (a scripted walk) while the guard patrols the lagoon, and goes for
 * them the moment a player sets foot in the cove. Hooks end it: the lost giant reaching the harbour wins, his falling
 * loses; the guard's half health and fall are told to everyone. Made entirely of the tools an admin has (paths, areas,
 * scripts, hooks), so it shows how to build one.
 */
final class WreckCove {
    private WreckCove() {}

    static final int[] COVE = {36, 44, 52};
    /** the map's own giant size (1.16 integration): small enough that both giants fit the cove; a smaller size picked
     *  for the game still wins */
    static final double SIZE = 0.08;
    /** the order the lost giant's kind is picked in (the first installed that isn't the guard) */
    static final List<String> LOST = List.of("pitchgut", "cerberus", "gloamwick", "lanternwillow", "swarmforge",
            "hollowbell", "furrowmaw", "wreckback");

    static Object make(MinecraftServer server, int sizeIndex, double gameSize) {
        double giantSize = Math.min(gameSize, SIZE);
        List<String> kinds = new ArrayList<>();
        for (var g : AdminServer.giants()) kinds.add(g.key());
        if (kinds.isEmpty()) return "No giants are installed.";
        String guard = kinds.contains("wreckback") ? "wreckback" : kinds.get(0);
        String lost = null;
        for (String k : LOST) if (kinds.contains(k) && !k.equals(guard)) {
            lost = k;
            break;
        }
        if (lost == null) for (String k : kinds) if (!k.equals(guard)) {
            lost = k;
            break;
        }
        if (lost == null) lost = guard;

        int r = COVE[Math.max(0, Math.min(2, sizeIndex))];
        int cx = 40;
        double[] cove = MgShapes.cove(r);
        double shore = cove[2];
        AdvMaps.Loaded m = new AdvMaps.Loaded();
        m.id = AdvMaps.BUILT_IN;
        m.title = AdvMaps.title(AdvMaps.BUILT_IN);
        m.goal = "Bring the lost giant home: reach him on the beach, then keep him safe on the shore road to the"
                + " harbour. Beware the guard of the cove!";
        m.timeUp = "lose";
        // the cove (Wreckback's own den), east of the middle
        MgShapes.Shape den = MgShapes.den("wreckback", r, FunGiants.height("wreckback", Math.max(0.15, giantSize)));
        if (den != null) m.pieces.add(new Object[] {den, cx, 0});
        // the shore road: from the cove's west beach to the harbour
        double bx = cx - (shore + (r - shore) * 0.5);
        double hx = -78;
        double[][] road = {{bx, 0}, {bx - 16, 8}, {bx - 34, 4}, {-30, -8}, {-52, -6}, {hx + 6, 0}};
        m.pieces.add(new Object[] {MgShapes.custom(new Road(road)), 0, 0});
        m.pieces.add(new Object[] {MgShapes.custom(new Harbour()), (int) hx, 0});
        m.half = Math.max(cx + r + 10, (int) -hx + 22);
        // where the players come in: on the harbour's quay
        for (int i = 0; i < 8; i++) m.starts.add(new Vec3(hx + 1 + (i % 4) * 1.5, 0, -2 + (i / 4) * 3));
        m.watch = new Vec3(-10, 34, 0);

        AdvData.State s = m.state;
        // paths (map coordinates: from the middle; y from the ground)
        AdvData.Path home = new AdvData.Path();
        for (double[] p : road) home.pts.add(new double[] {p[0], 1, p[1]});
        s.paths.put("shore_road", home);
        AdvData.Path watch = new AdvData.Path();
        double wr = shore * 0.45;
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            watch.pts.add(new double[] {cx + Math.cos(a) * wr, 0, Math.sin(a) * wr});
        }
        s.paths.put("lagoon_watch", watch);
        // areas: the cove (where the guard notices you) and the harbour (home)
        s.areas.put("cove", area(cx - r, -8, -r, cx + r, 40, r));
        s.areas.put("harbour", area((int) hx - 12, -4, -14, (int) hx + 16, 30, 14));
        // the scripts
        s.scripts.put("lost_giant", script(
                "title Lost in Wreck Cove",
                "mood calm",
                "say Is someone there? Please, help me get home!",
                "waitfor players 20",
                "say You came! The harbour is west, along the shore road. Stay close!",
                "walk shore_road",
                "say The harbour! I'm home!"));
        s.scripts.put("cove_guard", script(
                "title Guard of Wreck Cove",
                "mood calm",
                "patrol lagoon_watch until area cove",
                "say Who dares set foot in my cove?",
                "fight 80",
                "guard 60"));
        // the hooks (they end the round: /giants adventure win|lose)
        s.hooks.add(hook(1, "adv_lost", "enters:harbour", "giants adventure win {name} is home safe at the harbour!"));
        s.hooks.add(hook(2, "adv_lost", "dies", "giants adventure lose The lost giant fell on the way home."));
        s.hooks.add(hook(3, "adv_guard", "half", "tellraw @a {\"text\":\"The guard of Wreck Cove bellows, badly"
                + " hurt!\",\"color\":\"aqua\"}"));
        s.hooks.add(hook(4, "adv_guard", "dies", "tellraw @a {\"text\":\"The guard of Wreck Cove has fallen. The"
                + " way home is clear!\",\"color\":\"green\"}"));
        s.hooks.add(hook(5, "adv_guard", "sees", "playsound minecraft:event.raid.horn hostile @a ~ ~ ~ 3 0.8"));
        // the giants
        AdvMaps.MapGiant lg = new AdvMaps.MapGiant();
        lg.kind = lost;
        lg.x = bx + 2;
        lg.z = 0;
        lg.size = Math.max(0.06, giantSize * 0.75);
        lg.mood = "calm";
        lg.script = "lost_giant";
        lg.tags.add("adv_lost");
        lg.protect = true;
        m.giants.add(lg);
        AdvMaps.MapGiant gg = new AdvMaps.MapGiant();
        gg.kind = guard;
        gg.x = cx + cove[0] * 0.5;
        gg.z = cove[1] * 0.5;
        gg.size = giantSize;
        gg.mood = "calm";
        gg.script = "cove_guard";
        gg.tags.add("adv_guard");
        m.giants.add(gg);
        return m;
    }

    static AdvData.Area area(int x1, int y1, int z1, int x2, int y2, int z2) {
        AdvData.Area a = new AdvData.Area();
        a.x1 = x1;
        a.y1 = y1;
        a.z1 = z1;
        a.x2 = x2;
        a.y2 = y2;
        a.z2 = z2;
        return a;
    }

    static AdvData.Script script(String... steps) {
        AdvData.Script s = new AdvData.Script();
        s.steps.addAll(List.of(steps));
        return s;
    }

    static AdvData.Hook hook(int id, String target, String event, String command) {
        AdvData.Hook h = new AdvData.Hook();
        h.id = id;
        h.target = target;
        h.event = event;
        h.command = command;
        h.by = "The Wreck Cove Rescue";
        h.level = 2;
        return h;
    }

    /** the shore road: a worn path three blocks wide along the points, with gravel edges and lanterns now and then */
    static final class Road implements MgShapes.Columns {
        final double[][] pts;
        final int reach;

        Road(double[][] pts) {
            this.pts = pts;
            int r = 0;
            for (double[] p : pts) r = Math.max(r, (int) Math.ceil(Math.max(Math.abs(p[0]), Math.abs(p[1]))) + 4);
            reach = r;
        }

        @Override
        public int reach() {
            return reach;
        }

        @Override
        public void column(int dx, int dz, int f, MgShapes.Out out) {
            double best = Double.MAX_VALUE, along = 0, run = 0;
            for (int i = 0; i + 1 < pts.length; i++) {
                double[] a = pts[i], b = pts[i + 1];
                double vx = b[0] - a[0], vz = b[1] - a[1], len2 = vx * vx + vz * vz;
                double t = Math.max(0, Math.min(1, ((dx + 0.5 - a[0]) * vx + (dz + 0.5 - a[1]) * vz) / len2));
                double px = a[0] + vx * t - dx - 0.5, pz = a[1] + vz * t - dz - 0.5;
                double d = Math.sqrt(px * px + pz * pz);
                if (d < best) {
                    best = d;
                    along = run + Math.sqrt(len2) * t;
                }
                run += Math.sqrt(len2);
            }
            if (best > 3.2) return;
            double h = MgShapes.hash(dx, dz, 301);
            if (best < 1.6) out.set(f, Blocks.DIRT_PATH.defaultBlockState());
            else if (best < 2.4) out.set(f, h < 0.5 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
            else if (h < 0.35) out.set(f, Blocks.COARSE_DIRT.defaultBlockState());
            // a lantern post every 14 blocks, on the north side of the road
            if (best > 2.4 && best < 3.2 && ((int) along) % 14 == 0 && MgShapes.hash(dx, dz, 302) < 0.5) {
                out.set(f + 1, Blocks.SPRUCE_FENCE.defaultBlockState());
                out.set(f + 2, Blocks.SPRUCE_FENCE.defaultBlockState());
                out.set(f + 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
            }
        }
    }

    /** the harbour: a pond with a quay and a jetty, a boat house roof, barrels and lanterns */
    static final class Harbour implements MgShapes.Columns {
        @Override
        public int reach() {
            return 18;
        }

        @Override
        public void column(int dx, int dz, int f, MgShapes.Out out) {
            // the pond, west of the quay
            double pd = Math.hypot(dx + 9, dz * 0.8);
            boolean pond = pd < 9 + MgShapes.hash(dx, dz, 311) * 1.5;
            boolean quay = dx >= -2 && dx <= 6 && Math.abs(dz) <= 6;
            boolean jetty = dz >= -1 && dz <= 1 && dx >= -14 && dx < -2;
            BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
            if (pond && !quay) {
                int deep = pd < 5 ? 3 : pd < 7.5 ? 2 : 1;
                out.set(f - deep - 1, Blocks.SAND.defaultBlockState());
                for (int y = f - deep; y <= f - 1; y++) out.set(y, Blocks.WATER.defaultBlockState());
                out.set(f, jetty ? plank : Blocks.WATER.defaultBlockState());
                if (jetty && (dx % 4 == 0) && Math.abs(dz) == 1) {
                    for (int y = f - deep; y < f; y++) out.set(y, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                    out.set(f + 1, Blocks.SPRUCE_FENCE.defaultBlockState());
                    if (dx == -12) out.set(f + 2, Blocks.LANTERN.defaultBlockState());
                }
                if (!jetty && pd < 6 && MgShapes.hash(dx, dz, 312) < 0.06)
                    out.set(f - deep, Blocks.SEAGRASS.defaultBlockState());
                return;
            }
            if (pond && !quay) return;
            if (quay) {
                out.set(f, plank);
                boolean edge = Math.abs(dz) == 6 || dx == 6;
                if (edge && (dx + dz) % 3 == 0) {
                    out.set(f + 1, Blocks.SPRUCE_FENCE.defaultBlockState());
                    if ((dx == 6 && Math.abs(dz) == 6)) out.set(f + 2, Blocks.LANTERN.defaultBlockState());
                }
                // the boat house: posts and a slab roof over the back of the quay
                boolean post = (dx == 2 || dx == 6) && (dz == -5 || dz == 5);
                if (post) for (int y = f + 1; y <= f + 4; y++) out.set(y, Blocks.SPRUCE_LOG.defaultBlockState());
                if (dx >= 1 && dx <= 7 && Math.abs(dz) <= 6) out.set(f + 5, Blocks.SPRUCE_SLAB.defaultBlockState()
                        .setValue(BlockStateProperties.SLAB_TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
                if (dx == 5 && (dz == -3 || dz == -2)) out.set(f + 1, Blocks.BARREL.defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.UP));
                if (dx == 5 && dz == 3) out.set(f + 1, Blocks.CAMPFIRE.defaultBlockState()
                        .setValue(BlockStateProperties.LIT, true));
                if (dx == 3 && dz == -4) out.set(f + 1, Blocks.WHITE_BANNER.defaultBlockState());
                return;
            }
            // sand round the pond's rim
            if (pd < 11.5 && !quay) out.set(f, Blocks.SAND.defaultBlockState());
        }
    }
}
