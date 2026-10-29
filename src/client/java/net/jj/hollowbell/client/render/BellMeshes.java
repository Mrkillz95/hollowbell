package net.jj.hollowbell.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
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
import net.jj.hollowbell.GiantsPerf;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.mixin.VertexBufferAccess;
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

        // Fast drawing: who the order above is for, and the glass of any other one of him drawn in his own order
        int owner = Integer.MIN_VALUE;
        long ownerFrame = Long.MIN_VALUE;

        @Nullable
        List<Order> others;

        Mesh(VertexBuffer vb, @Nullable MeshData.SortState sort, int quads) {
            this.vb = vb;
            this.sort = sort;
            this.quads = quads;
        }
    }

    record SortJob(ByteBufferBuilder mem, ByteBufferBuilder.Result result) {}

    /** (Fast drawing) another one of him's own back-to-front order for a see-through mesh, in its own index buffer */
    static final class Order {
        int who;
        long used;
        int ibo;
        boolean loaded;
        final Vector3f sortedFor = new Vector3f(Float.NaN, 0, 0);

        @Nullable
        CompletableFuture<SortJob> sorting;
    }

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
        // Fast drawing: sent to the graphics card a few milliseconds a frame, not all in one long frame
        if (uploading != null || GiantsPerf.fastDrawing()) return uploadSome(b);
        long t0 = System.nanoTime();
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
        long dt = System.nanoTime() - t0;
        RenderStats.uploadNs += dt;
        RenderStats.uploadMaxFrameNs = Math.max(RenderStats.uploadMaxFrameNs, dt);
        RenderStats.uploadFrames++;
        HollowbellMod.LOG.info("On the GPU: {} faces in {} bones ({} faces far away)", q, nb, fq);
        return true;
    }

    // Fast drawing: the upload spread over frames, and each one of him's own glass order

    /** what is left to send, while it goes a bit at a time: the next part (lod, kind, index) */
    private @Nullable Built uploading;

    private int upLod, upKind, upIndex;
    /** the frame count (see {@link #frameStart}) and how long this frame has spent sending so far */
    private long frame, budgetFrame = -1, budgetUsedNs;

    private static final long BUDGET_NS = 3_000_000L;
    /** see-through meshes another one of him has his own order for */
    private final List<Mesh> withOthers = new ArrayList<>();

    private boolean uploadSome(Built b) {
        long t0 = System.nanoTime();
        if (uploading == null) {
            uploading = b;
            meshes = new Mesh[3][4][];
            for (int l = 0; l < 3; l++) for (int k = 0; k < 4; k++) meshes[l][k] = new Mesh[b.parts[l][k].length];
            upLod = upKind = upIndex = 0;
        }
        if (budgetFrame != frame) {
            budgetFrame = frame;
            budgetUsedNs = 0;
        }
        // (back on Classic halfway through: the rest goes now, as Classic sends it)
        boolean all = !GiantsPerf.fastDrawing();
        boolean any = false;
        while (upLod < 3) {
            if (!all && budgetUsedNs + (System.nanoTime() - t0) >= BUDGET_NS) break;
            Part[] row = b.parts[upLod][upKind];
            if (upIndex >= row.length) {
                upIndex = 0;
                if (++upKind == 4) {
                    upKind = 0;
                    upLod++;
                }
                continue;
            }
            Part p = row[upIndex];
            if (p != null) {
                VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vb.bind();
                vb.upload(p.data);
                meshes[upLod][upKind][upIndex] = new Mesh(vb, p.sort, p.quads);
                any = true;
            }
            upIndex++;
        }
        VertexBuffer.unbind();
        long dt = System.nanoTime() - t0;
        budgetUsedNs += dt;
        RenderStats.uploadNs += dt;
        RenderStats.uploadMaxFrameNs = Math.max(RenderStats.uploadMaxFrameNs, budgetUsedNs);
        if (any) RenderStats.uploadFrames++;
        if (upLod < 3) return false;
        uploading = null;
        for (ByteBufferBuilder m : b.memory) if (m != null) m.close();
        long q = 0, fq = 0;
        for (int k = 0; k < 4; k++) {
            for (Mesh m : meshes[FULL][k]) if (m != null) q += m.quads;
            for (Mesh m : meshes[FAR][k]) if (m != null) fq += m.quads;
        }
        quadCount = q;
        farQuadCount = fq;
        ready = true;
        HollowbellMod.LOG.info(
                "On the GPU: {} faces in {} bones ({} faces far away), sent a bit a frame",
                q,
                b.parts[TINY][0].length,
                fq);
        return true;
    }

    /** a new frame (called as the world starts to draw) */
    public void frameStart() {
        frame++;
        // other ones' glass orders nobody has drawn with for a while (all of them, back on Classic) are let go
        if (withOthers.isEmpty()) return;
        boolean fast = GiantsPerf.fastDrawing();
        if (fast && frame % 240 != 0) return;
        long old = fast ? frame - 600 : Long.MAX_VALUE;
        for (int i = withOthers.size() - 1; i >= 0; i--) {
            Mesh m = withOthers.get(i);
            if (m.others != null) m.others.removeIf(o -> o.used < old && free(o));
            if (m.others == null || m.others.isEmpty()) {
                m.others = null;
                withOthers.remove(i);
            }
        }
    }

    /** lets an order's index buffer go; false (keep it for now) while a sort for it is still running */
    private static boolean free(Order o) {
        if (o.sorting != null) {
            if (!o.sorting.isDone()) return false;
            try {
                SortJob j = o.sorting.join();
                if (j != null) {
                    j.result.close();
                    j.mem.close();
                }
            } catch (CompletionException | CancellationException e) {
                HollowbellMod.LOG.debug("Glass sort failed", e);
            }
            o.sorting = null;
        }
        if (o.ibo != 0) GlStateManager._glDeleteBuffers(o.ibo);
        o.ibo = 0;
        o.loaded = false;
        return true;
    }

    /**
     * (Fast drawing) draws a see-through mesh for one of him (who), sorted back to front for where the camera is in
     * the bone's rest space. The first one to draw a mesh keeps its own index buffer; any other one of him on screen
     * gets an order of his own, so two of him no longer sort the same glass back and forth for each other.
     */
    public void drawSorted(Mesh m, int who, Vector3f camRest, float moveBy) {
        if (m.sort == null) {
            m.vb.bind();
            m.vb.draw();
            return;
        }
        if (m.owner == who || m.ownerFrame < frame - 20) {
            m.owner = who;
            m.ownerFrame = frame;
            keepSorted(m, camRest, moveBy);
            m.vb.bind();
            m.vb.draw();
            return;
        }
        Order o = order(m, who);
        keepSorted(m, o, camRest, moveBy);
        m.vb.bind();
        if (!o.loaded) {
            // (until his own order is ready: the first one's, as Classic draws it)
            m.vb.draw();
            return;
        }
        GlStateManager._glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, o.ibo);
        RenderSystem.drawElements(VertexFormat.Mode.QUADS.asGLMode, m.quads * 6, m.sort.indexType().asGLType);
        // (the mesh's own index buffer back in its vertex array, for whoever draws it next)
        GlStateManager._glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ((VertexBufferAccess) m.vb).hollowbell$indexBufferId());
    }

    private static final int GL_ELEMENT_ARRAY_BUFFER = 34963, GL_COPY_WRITE_BUFFER = 36663, GL_STATIC_DRAW = 35044;

    private Order order(Mesh m, int who) {
        if (m.others == null) {
            m.others = new ArrayList<>(2);
            withOthers.add(m);
        }
        Order spare = null;
        for (Order o : m.others) {
            if (o.who == who) {
                o.used = frame;
                return o;
            }
            if (spare == null && o.used < frame - 20) spare = o;
        }
        if (spare == null) m.others.add(spare = new Order());
        else {
            spare.sortedFor.set(Float.NaN, 0, 0);
            spare.loaded = false;
        }
        spare.who = who;
        spare.used = frame;
        return spare;
    }

    /** {@link #keepSorted(Mesh, Vector3f, float)} for another one's own order */
    private void keepSorted(Mesh m, Order o, Vector3f camRest, float moveBy) {
        if (o.sorting != null) {
            if (!o.sorting.isDone()) return;
            SortJob j;
            try {
                j = o.sorting.join();
            } catch (CompletionException | CancellationException e) {
                HollowbellMod.LOG.debug("Glass sort failed", e);
                o.sorting = null;
                return;
            }
            o.sorting = null;
            if (j != null) {
                if (o.ibo == 0) o.ibo = GlStateManager._glGenBuffers();
                // (sent through a binding no vertex array keeps)
                GlStateManager._glBindBuffer(GL_COPY_WRITE_BUFFER, o.ibo);
                GlStateManager._glBufferData(GL_COPY_WRITE_BUFFER, j.result.byteBuffer(), GL_STATIC_DRAW);
                GlStateManager._glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
                j.result.close();
                j.mem.close();
                o.loaded = true;
                if (RenderStats.on) RenderStats.sortUploads++;
            }
            return;
        }
        if (!Float.isNaN(o.sortedFor.x) && o.sortedFor.distanceSquared(camRest) < moveBy * moveBy) return;
        o.sortedFor.set(camRest);
        if (RenderStats.on) RenderStats.sortsStarted++;
        Vector3f at = new Vector3f(camRest);
        MeshData.SortState st = m.sort;
        int bytes = m.quads * 6 * 4 + 256;
        o.sorting = CompletableFuture.supplyAsync(
                () -> {
                    ByteBufferBuilder mem = new ByteBufferBuilder(bytes);
                    ByteBufferBuilder.Result r = st.buildSortedIndexBuffer(mem, VertexSorting.byDistance(at));
                    return new SortJob(mem, r);
                },
                Util.backgroundExecutor());
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
                if (RenderStats.on) RenderStats.sortUploads++;
            }
            return;
        }
        if (!Float.isNaN(m.sortedFor.x) && m.sortedFor.distanceSquared(camRest) < moveBy * moveBy) return;
        m.sortedFor.set(camRest);
        if (RenderStats.on) RenderStats.sortsStarted++;
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

    /** a sort still running when the meshes go: its memory is let go once it ends */
    private static void letGoLater(Order o) {
        if (o.ibo != 0) GlStateManager._glDeleteBuffers(o.ibo);
        o.ibo = 0;
        if (o.sorting != null)
            o.sorting.thenAccept(j -> {
                if (j != null) {
                    j.result.close();
                    j.mem.close();
                }
            });
    }

    /** textures changed (resource pack switch): throw the meshes away, they rebuild on the next frame */
    public void invalidate() {
        RenderSystem.recordRenderCall(() -> {
            for (Mesh m : withOthers) if (m.others != null) for (Order o : m.others) if (!free(o)) letGoLater(o);
            withOthers.clear();
            if (uploading != null) for (ByteBufferBuilder m : uploading.memory) if (m != null) m.close();
            uploading = null;
            if (meshes != null)
                for (Mesh[][] a : meshes) for (Mesh[] b : a) for (Mesh m : b) if (m != null) m.vb.close();
            meshes = null;
            ready = false;
            building = null;
        });
    }
}
