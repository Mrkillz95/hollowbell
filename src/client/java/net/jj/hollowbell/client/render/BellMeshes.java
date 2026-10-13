package net.jj.hollowbell.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellPieces;
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Hollowbell baked into GPU meshes, one set per bone, from the game's own block models, so every block looks just
 * like it does in the world (and follows the resource pack). Built on a worker thread the first time he is seen.
 *
 * Each bone has up to three meshes: solid blocks, glowing blocks (drawn at full light), and see-through ones
 * (stained and tinted glass). The see-through ones keep what they need to be
 * re-sorted back to front as you move around, so the dome looks right from any side.
 * There is also a far-away version with every 2x2x2 lump of blocks merged into one box, cut in the same slices as the
 * full one, and a tiny one (8x8x8 lumps, a bone at a time) for the Bellings and for him when he is very small or very
 * far. Each lump's box only spans the blocks it has, so a strand one block thick stays one block thick.
 */
public final class BellMeshes {
    public static final BellMeshes INSTANCE = new BellMeshes();
    private static final Direction[] DIRS = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final int[][] OFF = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    public static final int FULL = 0, FAR = 1, TINY = 2;
    private static final int[] LUMP = {1, 2, 8};
    /** mesh kinds */
    public static final int SOLID = 0, GLOW = 1, CLEAR = 2, CLEAR_RED = 3;

    public static final class Mesh {
        public final VertexBuffer vb;
        final @Nullable MeshData.SortState sort;
        final int quads;
        /** where the camera was (in the bone's rest space) when it was last sorted */
        final Vector3f sortedFor = new Vector3f(Float.NaN, 0, 0);

        @Nullable
        CompletableFuture<SortJob> sorting;

        Mesh(VertexBuffer vb, @Nullable MeshData.SortState sort, int quads) {
            this.vb = vb;
            this.sort = sort;
            this.quads = quads;
        }
    }

    record SortJob(ByteBufferBuilder mem, ByteBufferBuilder.Result result) {}

    /** [lod][kind][slice] (see {@link BellPieces}), except the tiny version: [TINY][kind][bone] */
    private Mesh[][][] meshes;

    private CompletableFuture<Built> building;
    private boolean ready;
    public long quadCount, farQuadCount;

    private record Part(MeshData data, @Nullable MeshData.SortState sort, int quads) {}

    private record Built(Part[][][] parts, ByteBufferBuilder[] memory) {}

    private BellMeshes() {}

    public boolean ensureReady() {
        if (ready) return true;
        if (building == null) {
            building = CompletableFuture.supplyAsync(this::build, Util.backgroundExecutor());
            return false;
        }
        if (!building.isDone()) return false;
        Built b;
        try {
            b = building.join();
        } catch (Exception e) {
            HollowbellMod.LOG.error("Mesh build failed", e);
            building = null;
            return false;
        }
        meshes = new Mesh[3][4][];
        for (int l = 0; l < 3; l++) for (int k = 0; k < 4; k++) meshes[l][k] = new Mesh[b.parts[l][k].length];
        int nb = b.parts[TINY][0].length;
        long q = 0, fq = 0;
        for (int l = 0; l < 3; l++)
            for (int k = 0; k < 4; k++)
                for (int i = 0; i < b.parts[l][k].length; i++) {
                    Part p = b.parts[l][k][i];
                    if (p == null) continue;
                    VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    vb.bind();
                    vb.upload(p.data);
                    meshes[l][k][i] = new Mesh(vb, p.sort, p.quads);
                    if (l == FULL) q += p.quads;
                    else if (l == FAR) fq += p.quads;
                }
        VertexBuffer.unbind();
        for (ByteBufferBuilder m : b.memory) if (m != null) m.close();
        quadCount = q;
        farQuadCount = fq;
        ready = true;
        HollowbellMod.LOG.info("On the GPU: {} faces in {} bones ({} faces far away)", q, nb, fq);
        return true;
    }

    public boolean isReady() {
        return ready;
    }

    /** a mesh: for FULL and FAR, of one slice (see {@link BellPieces}); for TINY, of one bone */
    public @Nullable Mesh mesh(int lod, int kind, int unit) {
        return meshes == null ? null : meshes[lod][kind][unit];
    }

    /**
     * Re-sorts a see-through mesh for the camera at camRest (the bone's rest space), off the render thread,
     * if the camera has moved far enough since the last time. Picks up a finished sort and uploads it.
     */
    public void keepSorted(Mesh m, Vector3f camRest, float moveBy) {
        if (m.sort == null) return;
        if (m.sorting != null) {
            if (!m.sorting.isDone()) return;
            SortJob j;
            try {
                j = m.sorting.join();
            } catch (CompletionException | CancellationException e) {
                HollowbellMod.LOG.debug("Glass sort failed", e);
                m.sorting = null;
                return;
            }
            m.sorting = null;
            if (j != null) {
                m.vb.bind();
                m.vb.uploadIndexBuffer(j.result);
                j.mem.close();
            }
            return;
        }
        if (!Float.isNaN(m.sortedFor.x) && m.sortedFor.distanceSquared(camRest) < moveBy * moveBy) return;
        m.sortedFor.set(camRest);
        Vector3f at = new Vector3f(camRest);
        MeshData.SortState st = m.sort;
        int bytes = m.quads * 6 * 4 + 256;
        m.sorting = CompletableFuture.supplyAsync(
                () -> {
                    ByteBufferBuilder mem = new ByteBufferBuilder(bytes);
                    ByteBufferBuilder.Result r = st.buildSortedIndexBuffer(mem, VertexSorting.byDistance(at));
                    return new SortJob(mem, r);
                },
                Util.backgroundExecutor());
    }

    // building

    private Built build() {
        long t0 = System.currentTimeMillis();
        BellModel model = BellModel.get();
        BellRig rig = BellRig.get();
        var mc = Minecraft.getInstance();
        var lookup = BuiltInRegistries.BLOCK.asLookup();
        int np = model.palette.length;
        BlockState[] states = new BlockState[np];
        BakedModel[] models = new BakedModel[np];
        int[] kindOf = new int[np];
        for (int i = 0; i < np; i++) {
            BlockState st;
            try {
                st = BlockStateParser.parseForBlock(lookup, model.palette[i], false)
                        .blockState();
            } catch (CommandSyntaxException e) {
                throw new IllegalStateException("Hollowbell: unknown block " + model.palette[i], e);
            }
            states[i] = st;
            models[i] = mc.getBlockRenderer().getBlockModel(st);
            kindOf[i] = st.getLightEmission() > 0
                    ? GLOW
                    : ItemBlockRenderTypes.getChunkRenderType(st) == RenderType.translucent() ? CLEAR : SOLID;
        }
        // the red loops: lime glass on his arms turns to red glass below half health
        int limeIdx = -1;
        for (int i = 0; i < np; i++) if (model.palette[i].equals("minecraft:lime_stained_glass")) limeIdx = i;
        BlockState red = Blocks.RED_STAINED_GLASS.defaultBlockState();
        BakedModel redModel = mc.getBlockRenderer().getBlockModel(red);

        int nb = model.boneCount();
        BellPieces pieces = BellPieces.get();
        // each arm and strand is drawn in slices that bend smoothly; very far off, a piece at a time
        Part[][][] parts = new Part[3][4][];
        for (int l = 0; l < 3; l++) for (int k = 0; k < 4; k++) parts[l][k] = new Part[l != TINY ? pieces.count : nb];
        List<ByteBufferBuilder> mem = new ArrayList<>();
        RandomSource rnd = RandomSource.create(42);
        PoseStack ps = new PoseStack();
        PoseStack.Pose pose = ps.last();
        int lightSolid = LightTexture.pack(0, 15), lightGlow = LightTexture.FULL_BRIGHT;

        for (int lod = 0; lod < 3; lod++) {
            int L = LUMP[lod];
            boolean sliced = lod != TINY;
            int units = sliced ? pieces.count : nb;
            for (int unit = 0; unit < units; unit++) {
                int b = sliced ? pieces.bone[unit] : unit;
                int n = model.count(b);
                if (n == 0) continue;
                int[] of = pieces.sliceOf[b];
                // voxels (or merged lumps) of this slice of a bone (or of the whole bone): x y z palette faces alpha,
                // and for a lump the box it fills inside its cube (see grow())
                int[] vx, vy, vz, vp, vf, va, vb = null;
                if (L == 1) {
                    int m = 0;
                    for (int i = 0; i < n; i++) if (of[i] == unit) m++;
                    if (m == 0) continue;
                    vx = new int[m];
                    vy = new int[m];
                    vz = new int[m];
                    vp = new int[m];
                    vf = new int[m];
                    va = new int[m];
                    for (int i = 0, j = 0; i < n; i++) {
                        if (of[i] != unit) continue;
                        vx[j] = model.x[b][i];
                        vy[j] = model.y[b][i];
                        vz[j] = model.z[b][i];
                        vp[j] = model.pal[b][i];
                        vf[j] = model.faces[b][i] & 0x3f;
                        va[j] = model.alpha[b] == null ? 255 : model.alpha[b][i] & 0xff;
                        j++;
                    }
                } else {
                    Long2IntOpenHashMap pal = new Long2IntOpenHashMap(n / 4 + 8),
                            best = new Long2IntOpenHashMap(n / 4 + 8);
                    Long2IntOpenHashMap alp = new Long2IntOpenHashMap(n / 4 + 8);
                    Long2IntOpenHashMap box = new Long2IntOpenHashMap(n / 4 + 8);
                    box.defaultReturnValue(EMPTY_BOX);
                    for (int i = 0; i < n; i++) {
                        if (sliced && of[i] != unit) continue;
                        int x = model.x[b][i], y = model.y[b][i], z = model.z[b][i];
                        long key = BellModel.key(Math.floorDiv(x, L), Math.floorDiv(y, L), Math.floorDiv(z, L));
                        box.put(key, grow(box.get(key), Math.floorMod(x, L), Math.floorMod(y, L), Math.floorMod(z, L)));
                        int score = Integer.bitCount(model.faces[b][i] & 0x3f) + 1;
                        if (score > best.get(key)) {
                            best.put(key, score);
                            pal.put(key, model.pal[b][i]);
                        }
                        int a = model.alpha[b] == null ? 255 : model.alpha[b][i] & 0xff;
                        alp.put(key, Math.max(alp.get(key), a));
                    }
                    long[] keys = pal.keySet().toLongArray();
                    int m = keys.length;
                    if (m == 0) continue;
                    vb = new int[m];
                    vx = new int[m];
                    vy = new int[m];
                    vz = new int[m];
                    vp = new int[m];
                    vf = new int[m];
                    va = new int[m];
                    for (int i = 0; i < m; i++) {
                        long k = keys[i];
                        int cx = (int) ((k >>> 26) & 0x1fff) - 4096,
                                cy = (int) ((k >>> 13) & 0x1fff) - 4096,
                                cz = (int) (k & 0x1fff) - 4096;
                        int bx = box.get(k);
                        int f = 0;
                        for (int d = 0; d < 6; d++) {
                            long nk = BellModel.key(cx + OFF[d][0], cy + OFF[d][1], cz + OFF[d][2]);
                            if (!pal.containsKey(nk) || !covered(bx, box.get(nk), d, L)) f |= 1 << d;
                        }
                        vb[i] = bx;
                        vx[i] = cx;
                        vy[i] = cy;
                        vz[i] = cz;
                        vp[i] = pal.get(k);
                        vf[i] = f;
                        va[i] = alp.get(k);
                    }
                }
                boolean armBone = rig.kind[b] == BellRig.Kind.ARM;
                int[] count = new int[4];
                for (int i = 0; i < vx.length; i++) {
                    int k = va[i] < 255 ? CLEAR : kindOf[vp[i]];
                    int f = Integer.bitCount(vf[i]);
                    count[k] += f;
                    if (armBone && k == CLEAR && sliced) count[CLEAR_RED] += f;
                }
                for (int k = 0; k < 4; k++) {
                    if (count[k] == 0) continue;
                    ByteBufferBuilder memK = new ByteBufferBuilder(Math.max(256, count[k] * 4 * 36 + 1024));
                    mem.add(memK);
                    BufferBuilder out =
                            new BufferBuilder(memK, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
                    int quads = 0;
                    for (int i = 0; i < vx.length; i++) {
                        int kk = va[i] < 255 ? CLEAR : kindOf[vp[i]];
                        if (k == CLEAR_RED ? kk != CLEAR : kk != k) continue;
                        int p = vp[i];
                        BlockState st = states[p];
                        BakedModel bm = models[p];
                        if (k == CLEAR_RED && p == limeIdx) {
                            st = red;
                            bm = redModel;
                        }
                        pose.pose().identity().translate(vx[i] * L, vy[i] * L, vz[i] * L);
                        if (vb != null) {
                            int bx = vb[i];
                            pose.pose()
                                    .translate(lo(bx, 0), lo(bx, 1), lo(bx, 2))
                                    .scale(size(bx, 0), size(bx, 1), size(bx, 2));
                        }
                        float a = va[i] / 255f;
                        int light = k == GLOW ? lightGlow : lightSolid;
                        for (int d = 0; d < 6; d++) {
                            if ((vf[i] & (1 << d)) == 0) continue;
                            List<BakedQuad> qs = bm.getQuads(st, DIRS[d], rnd);
                            for (BakedQuad q : qs) {
                                out.putBulkData(pose, q, 1f, 1f, 1f, a, light, OverlayTexture.NO_OVERLAY);
                                quads++;
                            }
                        }
                    }
                    MeshData md = out.build();
                    if (md == null) continue;
                    MeshData.SortState sort = null;
                    if (k == CLEAR || k == CLEAR_RED) {
                        ByteBufferBuilder memI = new ByteBufferBuilder(quads * 6 * 4 + 256);
                        mem.add(memI);
                        // sorted once from above and outside; re-sorted for the real camera once he is drawn
                        sort = md.sortQuads(memI, VertexSorting.byDistance(0f, 400f, 300f));
                    }
                    parts[lod][k][unit] = new Part(md, sort, quads);
                }
            }
        }
        HollowbellMod.LOG.info("Hollowbell meshes built in {} ms", System.currentTimeMillis() - t0);
        return new Built(parts, mem.toArray(new ByteBufferBuilder[0]));
    }

    /**
     * A lump's box inside its cube: the lowest and highest block it has on each axis, 3 bits each (x y z lows, then
     * x y z highs).
     */
    private static final int EMPTY_BOX = 7 | 7 << 3 | 7 << 6;

    private static int grow(int box, int x, int y, int z) {
        int[] at = {x, y, z};
        for (int a = 0; a < 3; a++) {
            int lo = Math.min(lo(box, a), at[a]), hi = Math.max(hi(box, a), at[a]);
            box = box & ~(7 << 3 * a) & ~(7 << 9 + 3 * a) | lo << 3 * a | hi << 9 + 3 * a;
        }
        return box;
    }

    private static int lo(int box, int axis) {
        return box >> 3 * axis & 7;
    }

    private static int hi(int box, int axis) {
        return box >> 9 + 3 * axis & 7;
    }

    private static int size(int box, int axis) {
        return hi(box, axis) - lo(box, axis) + 1;
    }

    /**
     * Is face d of a lump's box hidden by the next lump that way: both reach the face between them, and the next one's
     * box covers all of this face (anything less would leave a see-through gap).
     */
    private static boolean covered(int box, int next, int d, int lump) {
        int axis = d < 2 ? 1 : d < 4 ? 2 : 0;
        boolean up = (d & 1) == 1;
        boolean touch = up
                ? hi(box, axis) == lump - 1 && lo(next, axis) == 0
                : lo(box, axis) == 0 && hi(next, axis) == lump - 1;
        if (!touch) return false;
        for (int a = 0; a < 3; a++)
            if (a != axis && (lo(next, a) > lo(box, a) || hi(next, a) < hi(box, a))) return false;
        return true;
    }

    /** textures changed (resource pack switch): throw the meshes away, they rebuild on the next frame */
    public void invalidate() {
        RenderSystem.recordRenderCall(() -> {
            if (meshes != null)
                for (Mesh[][] a : meshes) for (Mesh[] b : a) for (Mesh m : b) if (m != null) m.vb.close();
            meshes = null;
            ready = false;
            building = null;
        });
    }
}
