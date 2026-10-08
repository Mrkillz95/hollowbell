package net.jj.hollowbell.lab;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.jj.hollowbell.entity.HollowSolid;
import net.jj.hollowbell.rig.BellAnim;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellPieces;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.rig.BellState;
import net.jj.hollowbell.solid.SolidShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The solid kit in the pose lab. Development only (never in the jar): ./gradlew solidLab -Plab="solid overlap"
 *  - solid: how his solid shape came out (frames, cells, what was filled in), and that the inside of the dome stays
 *    hollow (cells of the dome, rim or crown in the open space under the dome);
 *  - overlap: how much his parts pass into each other at the key moments of every move and of drifting, climbing,
 *    sinking, turning, sleeping and dying (the same scenes as the pose lab): model cells where one part is right
 *    inside another, away from where the two join. Groups: the dome (with the four glowing spots), the vase, each arm,
 *    each pod, each egg clump, each strand. Written to build/solidlab/overlap.txt, with pictures of the worst poses.
 */
public final class SolidLab {
    static BellRig rig;
    static BellPieces pc;
    static SolidShape shape;
    static File out = new File(System.getProperty("lab.out", "build/solidlab"));

    public static void main(String[] args) throws Exception {
        out.mkdirs();
        PoseLab.rig = rig = BellRig.get();
        PoseLab.model = BellModel.get();
        pc = BellPieces.get();
        long t0 = System.currentTimeMillis();
        shape = HollowSolid.shape();
        System.out.println("solid shape built in " + (System.currentTimeMillis() - t0) + " ms");
        List<String> want = Arrays.asList(args);
        try (PrintWriter log = new PrintWriter(new File(out, "solid.txt"))) {
            if (want.contains("solid") || want.contains("all")) check(log);
        }
        if (want.contains("overlap") || want.contains("all")) {
            try (PrintWriter log = new PrintWriter(new File(out, "overlap.txt"))) { overlapAll(log, want); }
        }
    }

    static void say(PrintWriter log, String s) { System.out.println(s); log.println(s); log.flush(); }

    // ------------------------------------------------------------------ the shape

    static void check(PrintWriter log) {
        say(log, String.format("shape: %d frames, %d cells (%s), %d KB of look-up", shape.frames(), shape.cells, HollowSolid.report, shape.denseBytes / 1024));
        Map<String, int[]> by = new TreeMap<>();
        for (int f = 0; f < shape.frames(); f++) {
            BellRig.Kind k = rig.kind[pc.bone[shape.bone[f]]];
            String n = k.name().toLowerCase() + (k == BellRig.Kind.ARM ? " piece " + rig.seg[pc.bone[shape.bone[f]]] : "") + (shape.kind[f] == SolidShape.SOFT ? " (soft)" : "");
            int[] c = by.computeIfAbsent(n, q -> new int[2]);
            c[0]++;
            final int[] cc = {0};
            shape.cells(f, (x, y, z) -> cc[0]++);
            c[1] += cc[0];
        }
        for (var e : by.entrySet()) say(log, String.format("  %-22s %4d frames %8d cells", e.getKey(), e.getValue()[0], e.getValue()[1]));
        // the hollow under the dome: from just over the rim up to under the crown, inside the dome's inner wall (2 blocks
        // in), away from the glowing spots and the vase
        int domeCells = 0, spotCells = 0, open = 0;
        Map<String, Integer> inBy = new TreeMap<>();
        Map<Integer, Integer> inY = new TreeMap<>();
        for (int y = rig.rimY + 2; y < rig.crownY - 12; y++) {
            float ri = rig.domeInner(y) - 2;
            for (int x = (int) -ri; x <= ri; x++) for (int z = (int) -ri; z <= ri; z++) {
                if (x * x + z * z > ri * ri) continue;
                int who = -1;
                for (int f = 0; f < shape.frames() && who < 0; f++) {
                    float[] b = shape.bounds[f];
                    if (x < b[0] || x >= b[3] || y < b[1] || y >= b[4] || z < b[2] || z >= b[5]) continue;
                    if (shape.has(f, x, y, z)) who = f;
                }
                if (who < 0) { open++; continue; }
                BellRig.Kind k = rig.kind[pc.bone[shape.bone[who]]];
                if (k == BellRig.Kind.SPOT) spotCells++;
                else { domeCells++; inBy.merge(k.name().toLowerCase() + (shape.kind[who] == SolidShape.SOFT ? " (soft)" : ""), 1, Integer::sum); inY.merge(y / 10 * 10, 1, Integer::sum); }
            }
        }
        // a cut through him, front to back through the middle (x = 0), and one across at the height of the spots
        int S = 4, W = 200 * S, H = 215 * S;
        BufferedImage img = new BufferedImage(W * 2, H, BufferedImage.TYPE_INT_RGB);
        for (int f = 0; f < shape.frames(); f++) {
            final int ff = f;
            BellRig.Kind k = rig.kind[pc.bone[shape.bone[f]]];
            int col = shape.kind[f] == SolidShape.SOFT ? 0x3a78c8 : k == BellRig.Kind.SPOT ? 0x60d070 : k == BellRig.Kind.POD ? 0x90a040 : k == BellRig.Kind.ARM ? 0xb09060 : 0xb8b8c0;
            shape.cells(f, (x, y, z) -> {
                if (x == 0) for (int a2 = 0; a2 < S; a2++) for (int b2 = 0; b2 < S; b2++) { int px = (z + 100) * S + a2, py = H - 10 - y * S + b2; if (px >= 0 && px < W && py >= 0 && py < H) img.setRGB(px, py, col); }
                if (y == 160) for (int a2 = 0; a2 < S; a2++) for (int b2 = 0; b2 < S; b2++) { int px = W + (x + 100) * S + a2, py = H / 2 + z * S + b2; if (px >= W && px < 2 * W && py >= 0 && py < H) img.setRGB(px, py, col); }
            });
        }
        Graphics2D gg = img.createGraphics();
        gg.setColor(Color.YELLOW);
        gg.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        gg.drawString("solid as built: cut front to back | across at the spots (grey solid, blue soft, green spots)", 10, 24);
        gg.dispose();
        try { ImageIO.write(img, "png", new File(out, "solid_cut.png")); } catch (java.io.IOException e) { throw new RuntimeException(e); }
        say(log, String.format("inside the dome: %d cells open, %d of the glowing spots and the vase, %d of anything else %s by height %s", open, spotCells, domeCells, inBy, inY));
    }

    // ------------------------------------------------------------------ overlap

    /** per slice: its group (-1 none) */
    static int[] group;
    static final List<String> groups = new ArrayList<>();
    static boolean[] strandGroup;
    /** joints between groups: x y z (rest), the two groups, the slice it moves with */
    static final List<float[]> joints = new ArrayList<>();
    /** where two groups touch as built: slice, x y z, the two groups */
    static final List<int[]> contacts = new ArrayList<>();
    static float JOINT_R = Float.parseFloat(System.getProperty("lab.jointR", "7"));
    static float CONTACT_R = Float.parseFloat(System.getProperty("lab.contactR", "4"));

    static String groupName(int b) {
        return switch (rig.kind[b]) {
            case BELL, RIM, CROWN -> "dome";
            case SPOT -> rig.part[b] == 4 ? "vase" : "dome";
            case ARM -> "arm " + rig.part[b];
            case STRAND -> "strand " + rig.part[b];
            case POD -> "pod " + rig.part[b];
            case EGG -> "egg " + rig.part[b];
        };
    }

    static void groups() {
        if (group != null) return;
        Map<String, Integer> idx = new LinkedHashMap<>();
        int[] gOfBone = new int[rig.boneCount()];
        for (int b = 0; b < rig.boneCount(); b++) { String g = groupName(b); gOfBone[b] = idx.computeIfAbsent(g, q -> { groups.add(q); return groups.size() - 1; }); }
        group = new int[pc.count];
        for (int s = 0; s < pc.count; s++) group[s] = gOfBone[pc.bone[s]];
        strandGroup = new boolean[groups.size()];
        for (int g = 0; g < groups.size(); g++) strandGroup[g] = groups.get(g).startsWith("strand");
        // where one part hangs from another: the root of each arm and strand, the top of each pod and egg clump
        for (int b = 0; b < rig.boneCount(); b++) {
            int p = rig.parent[b];
            if (p < 0 || gOfBone[p] == gOfBone[b]) continue;
            Vector3f at = rig.chainOf[b] >= 0 && rig.seg[b] == 0 ? rig.chains[rig.chainOf[b]].joints[0] : rig.pivot[b];
            joints.add(new float[]{at.x, at.y, at.z, gOfBone[b], gOfBone[p], pc.first[b]});
        }
        // where two parts touch as built: one point per 3-block patch
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap at = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        at.defaultReturnValue(-1);
        for (int f = 0; f < shape.frames(); f++) { int s = shape.bone[f]; shape.cells(f, (x, y, z) -> at.putIfAbsent(key(x, y, z), s)); }
        java.util.Set<Long> seen = new java.util.HashSet<>();
        int[][] off = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (var en : at.long2IntEntrySet()) {
            long k = en.getLongKey();
            int s = en.getIntValue();
            int x = kx(k), y = ky(k), z = kz(k);
            for (int[] o : off) {
                int ns = at.get(key(x + o[0], y + o[1], z + o[2]));
                if (ns < 0 || group[ns] == group[s]) continue;
                long patch = (((long) Math.floorDiv(x, 3) & 0xFFF) << 48) | (((long) Math.floorDiv(y, 3) & 0xFFF) << 36) | (((long) Math.floorDiv(z, 3) & 0xFFF) << 24)
                        | ((long) group[s] << 12) | group[ns];
                if (seen.add(patch)) contacts.add(new int[]{s, x, y, z, group[s], group[ns]});
            }
        }
        System.out.println("groups " + groups.size() + ", joints " + joints.size() + ", parts touching as built: " + contacts.size() + " points");
    }

    static long key(int x, int y, int z) { return ((long) (x + 0x8000) << 32) | ((long) (y + 0x8000) << 16) | (long) (z + 0x8000); }
    static int kx(long k) { return (int) (k >>> 32) - 0x8000; }
    static int ky(long k) { return (int) ((k >>> 16) & 0xFFFF) - 0x8000; }
    static int kz(long k) { return (int) (k & 0xFFFF) - 0x8000; }

    /** a cell holds up to four groups, 16 bits each (group + 1) */
    static long addGroup(long m, int g) {
        int v = g + 1;
        for (int i = 0; i < 4; i++) {
            int have = (int) ((m >>> (16 * i)) & 0xFFFF);
            if (have == v) return m;
            if (have == 0) return m | ((long) v << (16 * i));
        }
        return m;
    }
    static boolean hasGroup(long m, int g) {
        int v = g + 1;
        for (int i = 0; i < 4; i++) if (((m >>> (16 * i)) & 0xFFFF) == v) return true;
        return false;
    }

    static List<Vector3f> collect;
    /** the solid cells of the last overlap() (model space, for pictures), and their groups */
    static List<float[]> drawn;

    /** the overlap at one pose (slices): count per group pair (a * 4096 + b, a < b) */
    static Map<Integer, Integer> overlap(Matrix4f[] slices, BellState st) {
        groups();
        Long2LongOpenHashMap at = new Long2LongOpenHashMap(1 << 20);
        Vector3f v = new Vector3f();
        for (int f = 0; f < shape.frames(); f++) {
            int s = shape.bone[f], b = pc.bone[s];
            if (!rig.shown(st, b) || (rig.kind[b] == BellRig.Kind.POD && st.podPopped[rig.part[b]])) continue;
            int g = group[s];
            Matrix4f m = slices[s];
            shape.cells(f, (x, y, z) -> {
                m.transformPosition(x + 0.5f, y + 0.5f, z + 0.5f, v);
                long k = key((int) Math.floor(v.x), (int) Math.floor(v.y), (int) Math.floor(v.z));
                at.put(k, addGroup(at.get(k), g));
            });
        }
        if (drawn != null) for (var en : at.long2LongEntrySet()) {
            long k = en.getLongKey();
            drawn.add(new float[]{kx(k), ky(k), kz(k), (int) (en.getLongValue() & 0xFFFF) - 1});
        }
        // joints and touching points where they are now
        List<float[]> jp = new ArrayList<>();
        for (float[] j : joints) {
            Vector3f p = slices[(int) j[5]].transformPosition(new Vector3f(j[0], j[1], j[2]));
            jp.add(new float[]{p.x, p.y, p.z, j[3], j[4]});
        }
        it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<List<float[]>> near = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        for (int[] c : contacts) {
            Vector3f p = slices[c[0]].transformPosition(new Vector3f(c[1] + 0.5f, c[2] + 0.5f, c[3] + 0.5f));
            near.computeIfAbsent(key(Math.floorDiv((int) Math.floor(p.x), 4), Math.floorDiv((int) Math.floor(p.y), 4), Math.floorDiv((int) Math.floor(p.z), 4)), q -> new ArrayList<>())
                    .add(new float[]{p.x, p.y, p.z, Math.min(c[4], c[5]) * 4096 + Math.max(c[4], c[5])});
        }
        Map<Integer, Integer> res = new TreeMap<>();
        int[][] off = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        int[] gs = new int[4];
        for (var en : at.long2LongEntrySet()) {
            long m = en.getLongValue();
            if ((m >>> 16) == 0) continue;                 // one group only
            int n = 0;
            for (int i = 0; i < 4; i++) { int q = (int) ((m >>> (16 * i)) & 0xFFFF); if (q != 0) gs[n++] = q - 1; }
            long k = en.getLongKey();
            int x = kx(k), y = ky(k), z = kz(k);
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
                int a = Math.min(gs[i], gs[j]), c = Math.max(gs[i], gs[j]);
                // deep in one of the two: the cells next to it all that one's (parts that only touch don't count)
                boolean deepA = true, deepC = true;
                for (int[] o : off) {
                    long nm = at.get(key(x + o[0], y + o[1], z + o[2]));
                    if (!hasGroup(nm, a)) deepA = false;
                    if (!hasGroup(nm, c)) deepC = false;
                }
                if (!deepA && !deepC) continue;
                boolean joint = false;
                for (float[] jj : jp) {
                    int ga = (int) jj[3], gb = (int) jj[4];
                    if (!((ga == a && gb == c) || (ga == c && gb == a))) continue;
                    float dx = x + 0.5f - jj[0], dy = y + 0.5f - jj[1], dz = z + 0.5f - jj[2];
                    if (dx * dx + dy * dy + dz * dz < JOINT_R * JOINT_R) { joint = true; break; }
                }
                if (!joint) {
                    int bx = Math.floorDiv(x, 4), by = Math.floorDiv(y, 4), bz = Math.floorDiv(z, 4), pk = a * 4096 + c;
                    search:
                    for (int i2 = -1; i2 <= 1; i2++) for (int j2 = -1; j2 <= 1; j2++) for (int l = -1; l <= 1; l++) {
                        List<float[]> L = near.get(key(bx + i2, by + j2, bz + l));
                        if (L == null) continue;
                        for (float[] q : L) {
                            if ((int) q[3] != pk) continue;
                            float dx = x + 0.5f - q[0], dy = y + 0.5f - q[1], dz = z + 0.5f - q[2];
                            if (dx * dx + dy * dy + dz * dz < CONTACT_R * CONTACT_R) { joint = true; break search; }
                        }
                    }
                }
                if (joint) continue;
                res.merge(a * 4096 + c, 1, Integer::sum);
                if (collect != null) collect.add(new Vector3f(x + 0.5f, y + 0.5f, z + 0.5f));
            }
        }
        return res;
    }

    static String pair(int k) { return groups.get(k / 4096) + " / " + groups.get(k % 4096); }

    /** the kind of a group pair: "strand/strand", "with a strand" or "solid parts" */
    static String pairKind(int k) {
        boolean a = strandGroup[k / 4096], b = strandGroup[k % 4096];
        return a && b ? "strand/strand" : a || b ? "with a strand" : "solid parts";
    }

    static String describe(Map<Integer, Integer> o, int top) {
        List<Map.Entry<Integer, Integer>> l = new ArrayList<>(o.entrySet());
        l.sort((p, q) -> q.getValue() - p.getValue());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(top, l.size()); i++) sb.append(pair(l.get(i).getKey())).append(' ').append(l.get(i).getValue()).append("; ");
        return sb.toString();
    }

    /** a pose of the scene at its sample ticks: (tick, slices, state) for each */
    interface PoseVisitor { void at(int t, Matrix4f[] slices, BellState st) throws Exception; }

    static long steps;

    static void runScene(PoseLab.Scene s, PoseVisitor v) throws Exception {
        BellAnim anim = new BellAnim(rig);
        BellAnim.In in = new BellAnim.In();
        BellState st = new BellState(rig);
        Matrix4f[] pose = rig.newPose(), slices = pc.newPose(), hang = pc.newHang();
        int si = 0;
        for (int t = 0; t <= s.ticks() && si < s.samples().length; t++) {
            in.time = 1000 + t;
            in.speedRef = 0.3f;
            in.groundUnder = PoseLab.GROUND;
            in.ground = (mx, mz) -> PoseLab.GROUND;
            in.pulse = net.jj.hollowbell.entity.Moves.pulseCurve(t % 60, 1f);
            in.sleep = 0f; in.sunk = 0f; in.dying = -1f; in.tired = false; in.red = false;
            PoseLab.move(in, 0f, 0f, 0f);
            st.clearMoves();
            in.clearAsks();
            s.d().tick(t, in, anim.now());
            anim.step(in);
            steps++;
            if (t == s.samples()[si]) {
                anim.fill(st, 1f);
                rig.computePose(st, pose, hang);
                pc.pose(st, pose, hang, slices);
                v.at(t, slices, st);
                si++;
            }
        }
    }

    static void overlapAll(PrintWriter log, List<String> want) throws Exception {
        groups();
        Map<String, PoseLab.Scene> scenes = PoseLab.scenes();
        // as built (the rest pose) first: should be all but nothing
        {
            BellState st = new BellState(rig);
            Matrix4f[] pose = rig.newPose(), slices = pc.newPose(), hang = pc.newHang();
            rig.computePose(st, pose, hang);
            pc.pose(st, pose, hang, slices);
            Map<Integer, Integer> o = overlap(slices, st);
            say(log, "as built: " + sum(o) + " " + describe(o, 6));
        }
        String only = System.getProperty("lab.scenes", "");
        Map<String, Integer> kindTot = new TreeMap<>();
        long total = 0;
        String worstAt = "";
        int worstN = -1;
        Map<String, Integer> perScene = new LinkedHashMap<>();
        for (PoseLab.Scene s : scenes.values()) {
            if (!only.isEmpty() && Arrays.stream(only.split(",")).noneMatch(w -> s.name().startsWith(w))) continue;
            int[] sceneWorst = {0};
            String[] sceneWho = {""};
            int[] sceneT = {0};
            Map<String, Integer> sceneKinds = new TreeMap<>();
            runScene(s, (t, slices, st) -> {
                Map<Integer, Integer> o = overlap(slices, st);
                int n = sum(o);
                for (var e : o.entrySet()) sceneKinds.merge(pairKind(e.getKey()), e.getValue(), Integer::sum);
                if (n > sceneWorst[0]) { sceneWorst[0] = n; sceneWho[0] = describe(o, 3); sceneT[0] = t; }
            });
            for (var e : sceneKinds.entrySet()) kindTot.merge(e.getKey(), e.getValue(), Integer::sum);
            int sceneSum = sceneKinds.values().stream().mapToInt(Integer::intValue).sum();
            total += sceneSum;
            perScene.put(s.name(), sceneWorst[0]);
            say(log, String.format("  %-16s all poses %6d (%s) | worst pose %5d at t%d: %s", s.name(), sceneSum, sceneKinds, sceneWorst[0], sceneT[0] - PoseLab.SETTLE, sceneWho[0]));
            if (sceneWorst[0] > worstN) { worstN = sceneWorst[0]; worstAt = s.name() + "@t" + (sceneT[0] - PoseLab.SETTLE); }
            if (Boolean.getBoolean("lab.shots") && sceneWorst[0] > 0) picture(s, sceneT[0]);
        }
        say(log, String.format("ALL: %d cells over every key pose (%s); worst pose %d (%s)", total, kindTot, worstN, worstAt));
        say(log, String.format("keeping his parts apart: %.3f ms a tick on average (%d ticks)", net.jj.hollowbell.rig.BellAnim.apartNanos / 1e6 / Math.max(1, steps), steps));
    }

    static int sum(Map<Integer, Integer> m) { int t = 0; for (int v : m.values()) t += v; return t; }

    /** the worst pose of a scene: his solid cells from the front and from above, the cells inside another part bright */
    static void picture(PoseLab.Scene s, int tt) throws Exception {
        PoseLab.Scene one = new PoseLab.Scene(s.name(), tt, new int[]{tt}, s.d());
        runScene(one, (t, slices, st) -> {
            collect = new ArrayList<>(); drawn = new ArrayList<>();
            Map<Integer, Integer> o = overlap(slices, st);
            int S = 3, W = 240 * S, H = 240 * S;
            BufferedImage img = new BufferedImage(W * 2, H, BufferedImage.TYPE_INT_RGB);
            for (int view = 0; view < 2; view++) {
                float[] zb = new float[W * H];
                Arrays.fill(zb, Float.MAX_VALUE);
                int x0 = view * W;
                for (float[] c : drawn) {
                    // front: x across, y up, nearest z in front; top: x across, z down, highest y in front
                    float u = c[0] * S + W / 2f, vv = view == 0 ? H - 20 - c[1] * S : H / 2f + c[2] * S, d = view == 0 ? c[2] : -c[1];
                    int g = (int) c[3];
                    boolean strand = g >= 0 && strandGroup[g];
                    int col = strand ? 0x4a5560 : groups.get(Math.max(0, g)).startsWith("arm") ? 0x6a6050 : groups.get(Math.max(0, g)).startsWith("pod") ? 0x507a48 : 0x8a8a90;
                    float shade = Math.max(0.4f, Math.min(1.1f, 0.85f - d / 300f));
                    col = shadeOf(col, shade);
                    plot(img, zb, x0, W, H, (int) u, (int) vv, S, d, col);
                }
                for (Vector3f c : collect) {
                    float u = c.x * S + W / 2f, vv = view == 0 ? H - 20 - c.y * S : H / 2f + c.z * S, d = (view == 0 ? c.z : -c.y) - 1000;
                    plot(img, zb, x0, W, H, (int) u, (int) vv, S, d, 0xff3030);
                }
            }
            Graphics2D g = img.createGraphics();
            g.setColor(Color.YELLOW);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            g.drawString(s.name() + " t" + (t - PoseLab.SETTLE) + "  parts inside each other: " + sum(o) + "  (front | from above)", 10, 24);
            g.dispose();
            ImageIO.write(img, "png", new File(out, "overlap_" + s.name() + ".png"));
            collect = null; drawn = null;
        });
    }

    static int shadeOf(int col, float k) {
        int r = Math.min(255, (int) (((col >> 16) & 255) * k)), g = Math.min(255, (int) (((col >> 8) & 255) * k)), b = Math.min(255, (int) ((col & 255) * k));
        return (r << 16) | (g << 8) | b;
    }

    static void plot(BufferedImage img, float[] zb, int x0, int W, int H, int u, int v, int S, float d, int col) {
        for (int a = 0; a < S; a++) for (int b = 0; b < S; b++) {
            int px = u + a, py = v + b;
            if (px < 0 || py < 0 || px >= W || py >= H) continue;
            int o = py * W + px;
            if (d < zb[o]) { zb[o] = d; img.setRGB(x0 + px, py, col); }
        }
    }

    /**
     * For the game test: the worst overlap of solid parts (no strands) over a scene's key poses, and of all of it.
     * Returns {worst solid, worst all, who}.
     */
    public static synchronized Object[] worst(String scene) throws Exception {
        if (rig == null) { PoseLab.rig = rig = BellRig.get(); PoseLab.model = BellModel.get(); pc = BellPieces.get(); shape = HollowSolid.shape(); }
        groups();
        PoseLab.Scene s = PoseLab.scenes().get(scene);
        if (s == null) throw new IllegalArgumentException("no scene " + scene);
        int[] w = {0, 0};
        String[] who = {""};
        runScene(s, (t, slices, st) -> {
            Map<Integer, Integer> o = overlap(slices, st);
            int solid = 0, all = 0;
            for (var e : o.entrySet()) { all += e.getValue(); if (pairKind(e.getKey()).equals("solid parts")) solid += e.getValue(); }
            if (solid > w[0]) { w[0] = solid; who[0] = describe(o, 2) + " at t" + (t - PoseLab.SETTLE); }
            w[1] = Math.max(w[1], all);
        });
        return new Object[]{w[0], w[1], who[0]};
    }
}
