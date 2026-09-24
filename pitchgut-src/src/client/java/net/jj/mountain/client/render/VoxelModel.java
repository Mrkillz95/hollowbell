package net.jj.mountain.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.jj.mountain.MountainMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

/**
 * A voxel build baked into GPU meshes, one per bone, from the game's own block models so every block looks exactly
 * like it does in the world (and follows the resource pack). Built on a worker thread the first time it is needed,
 * uploaded once, then drawn with each bone's matrix. Glowing blocks go in a second mesh per bone that ignores darkness.
 */
public final class VoxelModel {
    public static final VoxelModel MOUNTAIN = new VoxelModel("/mountain_breathes/mountain_model.bin", "MTNB");
    public static final VoxelModel HEART = new VoxelModel("/mountain_breathes/heart_model.bin", "MTNB");
    public static final VoxelModel TENTACLE = new VoxelModel("/mountain_breathes/gut_tentacle_model.bin", "MTNB");
    public static final VoxelModel LEECH = new VoxelModel("/mountain_breathes/gut_leech_model.bin", "MTNB");
    public static final VoxelModel WATCHER = new VoxelModel("/mountain_breathes/watcher_model.bin", "MTNB");
    public static final VoxelModel[] ALL = {MOUNTAIN, HEART, TENTACLE, LEECH, WATCHER};
    private static final Direction[] DIRS = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    public static final class Emitter {
        public final int kind, bone; public final float x, y, z;
        Emitter(int kind, int bone, float x, float y, float z) { this.kind = kind; this.bone = bone; this.x = x; this.y = y; this.z = z; }
    }

    private final String path, magic;
    private String[] palette;
    private String[] boneNames;
    private short[][] vx, vy, vz, vp; private byte[][] vf;
    public final List<Emitter> emitters = new ArrayList<>();
    private boolean loaded;

    private VertexBuffer[] solid, glow;
    /** the same model with every 2x2x2 lump of blocks merged into one: drawn when he is far off */
    private VertexBuffer[] farSolid, farGlow;
    public long farQuadCount;
    private CompletableFuture<Built> building;
    private boolean ready;
    public long quadCount;

    private record Built(MeshData[] solid, MeshData[] glow, MeshData[] farSolid, MeshData[] farGlow, ByteBufferBuilder[] memory, int[] sq, int[] gq, int[] fq) {}

    private VoxelModel(String path, String magic) { this.path = path; this.magic = magic; }

    private synchronized void loadRaw() {
        if (loaded) return;
        try (InputStream in = VoxelModel.class.getResourceAsStream(path);
             DataInputStream d = new DataInputStream(new GZIPInputStream(in, 1 << 16))) {
            byte[] mg = new byte[4]; d.readFully(mg);
            if (!new String(mg).equals(magic)) throw new IllegalStateException("bad model file " + path);
            d.readInt();
            int np = d.readInt();
            palette = new String[np];
            for (int i = 0; i < np; i++) palette[i] = d.readUTF();
            int nb = d.readInt();
            boneNames = new String[nb];
            vx = new short[nb][]; vy = new short[nb][]; vz = new short[nb][]; vp = new short[nb][]; vf = new byte[nb][];
            for (int b = 0; b < nb; b++) {
                boneNames[b] = d.readUTF();
                int n = d.readInt();
                vx[b] = new short[n]; vy[b] = new short[n]; vz[b] = new short[n]; vp[b] = new short[n]; vf[b] = new byte[n];
                for (int i = 0; i < n; i++) { vx[b][i] = d.readShort(); vy[b][i] = d.readShort(); vz[b][i] = d.readShort(); vp[b][i] = d.readShort(); vf[b][i] = d.readByte(); }
            }
            int ne = d.readInt();
            for (int i = 0; i < ne; i++) {
                int kind = d.readByte(); int bone = d.readShort();
                emitters.add(new Emitter(kind, bone, d.readFloat(), d.readFloat(), d.readFloat()));
            }
            loaded = true;
        } catch (Exception e) {
            throw new RuntimeException("The Mountain That Breathes: could not read " + path, e);
        }
    }

    public List<Emitter> emitters() { loadRaw(); return emitters; }
    public int boneCount() { loadRaw(); return boneNames.length; }
    public String boneName(int b) { loadRaw(); return boneNames[b]; }

    /** Called from the render thread each frame it is drawn. Returns true once the meshes are on the GPU. */
    public boolean ensureReady() {
        if (ready) return true;
        if (building == null) {
            loadRaw();
            building = CompletableFuture.supplyAsync(this::buildMeshes, Util.backgroundExecutor());
            return false;
        }
        if (!building.isDone()) return false;
        Built b;
        try { b = building.join(); }
        catch (Exception e) { MountainMod.LOG.error("Mesh build failed for {}", path, e); building = null; return false; }
        int nb = b.solid.length;
        solid = new VertexBuffer[nb]; glow = new VertexBuffer[nb];
        farSolid = new VertexBuffer[nb]; farGlow = new VertexBuffer[nb];
        for (int i = 0; i < nb; i++) {
            if (b.solid[i] != null) { solid[i] = new VertexBuffer(VertexBuffer.Usage.STATIC); solid[i].bind(); solid[i].upload(b.solid[i]); }
            if (b.glow[i] != null) { glow[i] = new VertexBuffer(VertexBuffer.Usage.STATIC); glow[i].bind(); glow[i].upload(b.glow[i]); }
            if (b.farSolid[i] != null) { farSolid[i] = new VertexBuffer(VertexBuffer.Usage.STATIC); farSolid[i].bind(); farSolid[i].upload(b.farSolid[i]); }
            if (b.farGlow[i] != null) { farGlow[i] = new VertexBuffer(VertexBuffer.Usage.STATIC); farGlow[i].bind(); farGlow[i].upload(b.farGlow[i]); }
        }
        VertexBuffer.unbind();
        for (ByteBufferBuilder m : b.memory) if (m != null) m.close();
        long q = 0; for (int i = 0; i < nb; i++) q += b.sq[i] + b.gq[i];
        long fq = 0; for (int i = 0; i < nb; i++) fq += b.fq[i];
        quadCount = q; farQuadCount = fq;
        ready = true;
        MountainMod.LOG.info("{} on the GPU: {} faces in {} bones ({} faces far away)", path, q, nb, fq);
        return true;
    }

    public boolean isReady() { return ready; }

    private Built buildMeshes() {
        long t0 = System.currentTimeMillis();
        var mc = Minecraft.getInstance();
        var lookup = BuiltInRegistries.BLOCK.asLookup();
        BlockState[] states = new BlockState[palette.length];
        BakedModel[] models = new BakedModel[palette.length];
        boolean[] glows = new boolean[palette.length];
        for (int i = 0; i < palette.length; i++) {
            BlockState st;
            try { st = BlockStateParser.parseForBlock(lookup, palette[i], false).blockState(); }
            catch (Exception e) { MountainMod.LOG.warn("Unknown block {} in {}, using stone", palette[i], path); st = Blocks.STONE.defaultBlockState(); }
            states[i] = st;
            models[i] = mc.getBlockRenderer().getBlockModel(st);
            glows[i] = st.getLightEmission() > 0;
        }
        int nb = vx.length;
        MeshData[] sm = new MeshData[nb], gm = new MeshData[nb];
        MeshData[] fs = new MeshData[nb], fg = new MeshData[nb];
        ByteBufferBuilder[] mem = new ByteBufferBuilder[nb * 4];
        int[] sq = new int[nb], gq = new int[nb], fq = new int[nb];
        RandomSource rnd = RandomSource.create(42);
        PoseStack ps = new PoseStack();
        PoseStack.Pose pose = ps.last();
        int lightSolid = LightTexture.pack(0, 15), lightGlow = LightTexture.FULL_BRIGHT;
        for (int b = 0; b < nb; b++) {
            int nS = 0, nG = 0;
            for (int i = 0; i < vx[b].length; i++) {
                int faces = Integer.bitCount(vf[b][i] & 0x3f);
                if (glows[vp[b][i]]) nG += faces; else nS += faces;
            }
            ByteBufferBuilder memS = nS > 0 ? new ByteBufferBuilder(Math.max(256, nS * 4 * 36 + 1024)) : null;
            ByteBufferBuilder memG = nG > 0 ? new ByteBufferBuilder(Math.max(256, nG * 4 * 36 + 1024)) : null;
            BufferBuilder bs = memS != null ? new BufferBuilder(memS, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY) : null;
            BufferBuilder bg = memG != null ? new BufferBuilder(memG, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY) : null;
            for (int i = 0; i < vx[b].length; i++) {
                int p = vp[b][i], f = vf[b][i] & 0x3f;
                boolean g = glows[p];
                BufferBuilder out = g ? bg : bs;
                pose.pose().translation(vx[b][i], vy[b][i], vz[b][i]);
                for (int k = 0; k < 6; k++) {
                    if ((f & (1 << k)) == 0) continue;
                    List<BakedQuad> quads = models[p].getQuads(states[p], DIRS[k], rnd);
                    for (BakedQuad q : quads) {
                        out.putBulkData(pose, q, 1f, 1f, 1f, 1f, g ? lightGlow : lightSolid, OverlayTexture.NO_OVERLAY);
                        if (g) gq[b]++; else sq[b]++;
                    }
                }
            }
            sm[b] = bs != null ? bs.build() : null;
            gm[b] = bg != null ? bg.build() : null;
            mem[b * 4] = memS; mem[b * 4 + 1] = memG;

            // ---- the far-away version: every 2x2x2 lump of his blocks becomes one big block
            int n = vx[b].length;
            if (n == 0) continue;
            it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap pal = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap(n / 4 + 8);
            it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap best = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap(n / 4 + 8);
            for (int i = 0; i < n; i++) {
                long key = ckey(vx[b][i] >> 1, vy[b][i] >> 1, vz[b][i] >> 1);
                int score = Integer.bitCount(vf[b][i] & 0x3f) + 1;       // the block showing most faces wins the lump
                if (score > best.get(key)) { best.put(key, score); pal.put(key, (int) vp[b][i]); }
            }
            int nfS = 0, nfG = 0;
            long[] keys = pal.keySet().toLongArray();
            byte[] cf = new byte[keys.length];
            for (int i = 0; i < keys.length; i++) {
                long k = keys[i];
                int cx = (int) ((k >>> 24 & 0xfff) - 2048), cy = (int) ((k >>> 12 & 0xfff) - 2048), cz = (int) ((k & 0xfff) - 2048);
                int f = 0;
                for (int d = 0; d < 6; d++) {
                    int[] o = COFF[d];
                    if (!pal.containsKey(ckey(cx + o[0], cy + o[1], cz + o[2]))) f |= 1 << d;
                }
                cf[i] = (byte) f;
                int cnt = Integer.bitCount(f);
                if (glows[pal.get(k)]) nfG += cnt; else nfS += cnt;
            }
            ByteBufferBuilder memFS = nfS > 0 ? new ByteBufferBuilder(Math.max(256, nfS * 4 * 36 + 1024)) : null;
            ByteBufferBuilder memFG = nfG > 0 ? new ByteBufferBuilder(Math.max(256, nfG * 4 * 36 + 1024)) : null;
            BufferBuilder bfs = memFS != null ? new BufferBuilder(memFS, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY) : null;
            BufferBuilder bfg = memFG != null ? new BufferBuilder(memFG, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY) : null;
            for (int i = 0; i < keys.length; i++) {
                int f = cf[i] & 0x3f;
                if (f == 0) continue;
                long k = keys[i];
                int cx = (int) ((k >>> 24 & 0xfff) - 2048), cy = (int) ((k >>> 12 & 0xfff) - 2048), cz = (int) ((k & 0xfff) - 2048);
                int pi = pal.get(k);
                boolean g = glows[pi];
                BufferBuilder out = g ? bfg : bfs;
                pose.pose().translation(cx * 2, cy * 2, cz * 2).scale(2f);
                for (int d = 0; d < 6; d++) {
                    if ((f & (1 << d)) == 0) continue;
                    for (BakedQuad q : models[pi].getQuads(states[pi], DIRS[d], rnd)) {
                        out.putBulkData(pose, q, 1f, 1f, 1f, 1f, g ? lightGlow : lightSolid, OverlayTexture.NO_OVERLAY);
                        fq[b]++;
                    }
                }
            }
            pose.pose().identity();
            fs[b] = bfs != null ? bfs.build() : null;
            fg[b] = bfg != null ? bfg.build() : null;
            mem[b * 4 + 2] = memFS; mem[b * 4 + 3] = memFG;
        }
        MountainMod.LOG.info("{} meshes built in {} ms", path, System.currentTimeMillis() - t0);
        return new Built(sm, gm, fs, fg, mem, sq, gq, fq);
    }

    private static final int[][] COFF = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    private static long ckey(int x, int y, int z) { return ((long) (x + 2048) << 24) | ((long) (y + 2048) << 12) | (z + 2048); }

    public VertexBuffer solid(int bone) { return solid[bone]; }
    public VertexBuffer glow(int bone) { return glow[bone]; }
    /** the merged-block version for when he is far away (null if there is nothing to draw) */
    public VertexBuffer solid(int bone, boolean far) { return far && farSolid != null ? farSolid[bone] : solid[bone]; }
    public VertexBuffer glow(int bone, boolean far) { return far && farGlow != null ? farGlow[bone] : glow[bone]; }

    /** Textures changed (resource pack switch): throw the meshes away, they rebuild on the next frame. */
    public void invalidate() {
        RenderSystem.recordRenderCall(() -> {
            if (solid != null) for (VertexBuffer v : solid) if (v != null) v.close();
            if (glow != null) for (VertexBuffer v : glow) if (v != null) v.close();
            if (farSolid != null) for (VertexBuffer v : farSolid) if (v != null) v.close();
            if (farGlow != null) for (VertexBuffer v : farGlow) if (v != null) v.close();
            solid = glow = farSolid = farGlow = null; ready = false; building = null;
        });
    }
}
