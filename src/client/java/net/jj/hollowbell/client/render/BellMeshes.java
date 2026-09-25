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
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.rig.BellModel;
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
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Hollowbell baked into GPU meshes, one set per bone, from the game's own block models, so every block looks just
 * like it does in the world (and follows the resource pack). Built on a worker thread the first time he is seen.
 *
 * Each bone has up to three meshes: solid blocks, glowing blocks (drawn at full light), and see-through ones
 * (stained and tinted glass). The see-through ones keep what they need to be
 * re-sorted back to front as you move around, so the dome looks right from any side.
 * There is also a far-away version with every 2x2x2 lump of blocks merged into one, and a tiny one (8x8x8) for the
 * Bellings and for him when he is very small or very far.
 */
public final class BellMeshes {
    public static final BellMeshes INSTANCE = new BellMeshes();
    private static final Direction[] DIRS = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
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
        @Nullable CompletableFuture<SortJob> sorting;
        Mesh(VertexBuffer vb, @Nullable MeshData.SortState sort, int quads) { this.vb = vb; this.sort = sort; this.quads = quads; }
    }

    record SortJob(ByteBufferBuilder mem, ByteBufferBuilder.Result result) {}

    /** [lod][kind][bone] */
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
        try { b = building.join(); }
        catch (Exception e) { HollowbellMod.LOG.error("Hollowbell mesh build failed", e); building = null; return false; }
        int nb = b.parts[0][0].length;
        meshes = new Mesh[3][4][nb];
        long q = 0, fq = 0;
        for (int l = 0; l < 3; l++) for (int k = 0; k < 4; k++) for (int i = 0; i < nb; i++) {
            Part p = b.parts[l][k][i];
            if (p == null) continue;
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(p.data);
            meshes[l][k][i] = new Mesh(vb, p.sort, p.quads);
            if (l == FULL) q += p.quads; else if (l == FAR) fq += p.quads;
        }
        VertexBuffer.unbind();
        for (ByteBufferBuilder m : b.memory) if (m != null) m.close();
        quadCount = q; farQuadCount = fq;
        ready = true;
        HollowbellMod.LOG.info("Hollowbell on the GPU: {} faces in {} bones ({} faces far away)", q, nb, fq);
        return true;
    }

    public boolean isReady() { return ready; }

    public @Nullable Mesh mesh(int lod, int kind, int bone) { return meshes == null ? null : meshes[lod][kind][bone]; }

    /**
     * Re-sorts a see-through mesh for the camera at camRest (the bone's rest space), off the render thread,
     * if the camera has moved far enough since the last time. Picks up a finished sort and uploads it.
     */
    public void keepSorted(Mesh m, Vector3f camRest, float moveBy) {
        if (m.sort == null) return;
        if (m.sorting != null) {
            if (!m.sorting.isDone()) return;
            SortJob j;
            try { j = m.sorting.join(); } catch (Exception e) { m.sorting = null; return; }
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
        m.sorting = CompletableFuture.supplyAsync(() -> {
            ByteBufferBuilder mem = new ByteBufferBuilder(bytes);
            ByteBufferBuilder.Result r = st.buildSortedIndexBuffer(mem, VertexSorting.byDistance(at));
            return new SortJob(mem, r);
        }, Util.backgroundExecutor());
    }

    // ------------------------------------------------------------------ building

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
            try { st = BlockStateParser.parseForBlock(lookup, model.palette[i], false).blockState(); }
            catch (Exception e) { throw new IllegalStateException("Hollowbell: unknown block " + model.palette[i], e); }
            states[i] = st;
            models[i] = mc.getBlockRenderer().getBlockModel(st);
            kindOf[i] = st.getLightEmission() > 0 ? GLOW : ItemBlockRenderTypes.getChunkRenderType(st) == RenderType.translucent() ? CLEAR : SOLID;
        }
        // the red loops: lime glass on his arms turns to red glass below half health
        int limeIdx = -1;
        for (int i = 0; i < np; i++) if (model.palette[i].equals("minecraft:lime_stained_glass")) limeIdx = i;
        BlockState red = net.minecraft.world.level.block.Blocks.RED_STAINED_GLASS.defaultBlockState();
        BakedModel redModel = mc.getBlockRenderer().getBlockModel(red);

        int nb = model.boneCount();
        Part[][][] parts = new Part[3][4][nb];
        java.util.List<ByteBufferBuilder> mem = new java.util.ArrayList<>();
        RandomSource rnd = RandomSource.create(42);
        PoseStack ps = new PoseStack();
        PoseStack.Pose pose = ps.last();
        int lightSolid = LightTexture.pack(0, 15), lightGlow = LightTexture.FULL_BRIGHT;

        for (int lod = 0; lod < 3; lod++) {
            int L = LUMP[lod];
            for (int b = 0; b < nb; b++) {
                int n = model.count(b);
                if (n == 0) continue;
                // voxels (or merged lumps) of this bone: x y z palette faces alpha
                int[] vx, vy, vz, vp, vf, va;
                if (L == 1) {
                    vx = new int[n]; vy = new int[n]; vz = new int[n]; vp = new int[n]; vf = new int[n]; va = new int[n];
                    for (int i = 0; i < n; i++) {
                        vx[i] = model.x[b][i]; vy[i] = model.y[b][i]; vz[i] = model.z[b][i]; vp[i] = model.pal[b][i];
                        vf[i] = model.faces[b][i] & 0x3f; va[i] = model.alpha[b] == null ? 255 : model.alpha[b][i] & 0xff;
                    }
                } else {
                    Long2IntOpenHashMap pal = new Long2IntOpenHashMap(n / 4 + 8), best = new Long2IntOpenHashMap(n / 4 + 8);
                    Long2IntOpenHashMap alp = new Long2IntOpenHashMap(n / 4 + 8);
                    for (int i = 0; i < n; i++) {
                        long key = BellModel.key(Math.floorDiv(model.x[b][i], L), Math.floorDiv(model.y[b][i], L), Math.floorDiv(model.z[b][i], L));
                        int score = Integer.bitCount(model.faces[b][i] & 0x3f) + 1;
                        if (score > best.get(key)) { best.put(key, score); pal.put(key, model.pal[b][i]); }
                        int a = model.alpha[b] == null ? 255 : model.alpha[b][i] & 0xff;
                        alp.put(key, Math.max(alp.get(key), a));
                    }
                    long[] keys = pal.keySet().toLongArray();
                    int m = keys.length;
                    vx = new int[m]; vy = new int[m]; vz = new int[m]; vp = new int[m]; vf = new int[m]; va = new int[m];
                    for (int i = 0; i < m; i++) {
                        long k = keys[i];
                        int cx = (int) ((k >>> 26) & 0x1fff) - 4096, cy = (int) ((k >>> 13) & 0x1fff) - 4096, cz = (int) (k & 0x1fff) - 4096;
                        int f = 0;
                        for (int d = 0; d < 6; d++) if (!pal.containsKey(BellModel.key(cx + OFF[d][0], cy + OFF[d][1], cz + OFF[d][2]))) f |= 1 << d;
                        vx[i] = cx; vy[i] = cy; vz[i] = cz; vp[i] = pal.get(k); vf[i] = f; va[i] = alp.get(k);
                    }
                }
                boolean armBone = rig.kind[b] == BellRig.Kind.ARM;
                int[] count = new int[4];
                for (int i = 0; i < vx.length; i++) {
                    int k = va[i] < 255 ? CLEAR : kindOf[vp[i]];
                    int f = Integer.bitCount(vf[i]);
                    count[k] += f;
                    if (armBone && k == CLEAR && L == 1) count[CLEAR_RED] += f;
                }
                for (int k = 0; k < 4; k++) {
                    if (count[k] == 0) continue;
                    ByteBufferBuilder memK = new ByteBufferBuilder(Math.max(256, count[k] * 4 * 36 + 1024));
                    mem.add(memK);
                    BufferBuilder out = new BufferBuilder(memK, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
                    int quads = 0;
                    for (int i = 0; i < vx.length; i++) {
                        int kk = va[i] < 255 ? CLEAR : kindOf[vp[i]];
                        if (k == CLEAR_RED ? kk != CLEAR : kk != k) continue;
                        int p = vp[i];
                        BlockState st = states[p];
                        BakedModel bm = models[p];
                        if (k == CLEAR_RED && p == limeIdx) { st = red; bm = redModel; }
                        pose.pose().identity().translate(vx[i] * L, vy[i] * L, vz[i] * L);
                        if (L != 1) pose.pose().scale(L);
                        float a = va[i] / 255f;
                        int light = k == GLOW ? lightGlow : lightSolid;
                        for (int d = 0; d < 6; d++) {
                            if ((vf[i] & (1 << d)) == 0) continue;
                            List<BakedQuad> qs = bm.getQuads(st, DIRS[d], rnd);
                            for (BakedQuad q : qs) { out.putBulkData(pose, q, 1f, 1f, 1f, a, light, OverlayTexture.NO_OVERLAY); quads++; }
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
                    parts[lod][k][b] = new Part(md, sort, quads);
                }
            }
        }
        HollowbellMod.LOG.info("Hollowbell meshes built in {} ms", System.currentTimeMillis() - t0);
        return new Built(parts, mem.toArray(new ByteBufferBuilder[0]));
    }

    /** textures changed (resource pack switch): throw the meshes away, they rebuild on the next frame */
    public void invalidate() {
        RenderSystem.recordRenderCall(() -> {
            if (meshes != null) for (Mesh[][] a : meshes) for (Mesh[] b : a) for (Mesh m : b) if (m != null) m.vb.close();
            meshes = null; ready = false; building = null;
        });
    }
}
