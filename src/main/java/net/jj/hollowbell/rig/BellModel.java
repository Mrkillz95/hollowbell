package net.jj.hollowbell.rig;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.jj.hollowbell.HollowbellMod;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * The voxels of the build, one list per bone, read from hollowbell_model.bin (made by tools/convert.js).
 * The client bakes them into meshes; both sides use them to tell exactly which block a swing or an arrow hit.
 * Only the blocks that show a face are in the file: the ones buried inside never need drawing or hitting.
 */
public final class BellModel {
    private static BellModel instance;

    public static synchronized BellModel get() {
        if (instance == null) instance = load("/hollowbell/hollowbell_model.bin");
        return instance;
    }

    public final String[] palette;
    public final String[] boneNames;
    public final short[][] x, y, z, pal;
    public final byte[][] faces;
    /** per voxel fade (0-255) for bones that fade out, else null (none do now) */
    public final byte[][] alpha;
    /** each bone's blocks at rest, for hit tests */
    private final LongOpenHashSet[] occupied;
    /** each bone's box at rest: minx miny minz maxx maxy maxz (block corners) */
    public final float[][] bounds;

    private BellModel(String[] palette, String[] boneNames, short[][] x, short[][] y, short[][] z, short[][] pal, byte[][] faces, byte[][] alpha) {
        this.palette = palette; this.boneNames = boneNames;
        this.x = x; this.y = y; this.z = z; this.pal = pal; this.faces = faces; this.alpha = alpha;
        int nb = boneNames.length;
        occupied = new LongOpenHashSet[nb];
        bounds = new float[nb][];
        for (int b = 0; b < nb; b++) {
            int n = x[b].length;
            LongOpenHashSet s = new LongOpenHashSet(Math.max(4, n));
            float[] bb = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int i = 0; i < n; i++) {
                // anything nearly faded out is too thin to hit
                if (alpha[b] != null && (alpha[b][i] & 0xff) < 90) continue;
                s.add(key(x[b][i], y[b][i], z[b][i]));
                bb[0] = Math.min(bb[0], x[b][i]); bb[1] = Math.min(bb[1], y[b][i]); bb[2] = Math.min(bb[2], z[b][i]);
                bb[3] = Math.max(bb[3], x[b][i] + 1); bb[4] = Math.max(bb[4], y[b][i] + 1); bb[5] = Math.max(bb[5], z[b][i] + 1);
            }
            occupied[b] = s;
            bounds[b] = s.isEmpty() ? null : bb;
        }
    }

    public static long key(int x, int y, int z) {
        return ((long) (x + 4096) << 26) | ((long) (y + 4096) << 13) | (long) (z + 4096);
    }

    public boolean has(int bone, int bx, int by, int bz) { return occupied[bone].contains(key(bx, by, bz)); }
    public int count(int bone) { return x[bone].length; }
    public int boneCount() { return boneNames.length; }

    private static BellModel load(String path) {
        try (InputStream in = BellModel.class.getResourceAsStream(path);
             DataInputStream d = new DataInputStream(new GZIPInputStream(in, 1 << 16))) {
            byte[] mg = new byte[4];
            d.readFully(mg);
            if (!new String(mg).equals("HBEL")) throw new IllegalStateException("bad model file " + path);
            d.readInt();
            int np = d.readInt();
            String[] palette = new String[np];
            for (int i = 0; i < np; i++) palette[i] = d.readUTF();
            int nb = d.readInt();
            String[] names = new String[nb];
            short[][] x = new short[nb][], y = new short[nb][], z = new short[nb][], pal = new short[nb][];
            byte[][] faces = new byte[nb][], alpha = new byte[nb][];
            for (int b = 0; b < nb; b++) {
                names[b] = d.readUTF();
                int n = d.readInt();
                x[b] = new short[n]; y[b] = new short[n]; z[b] = new short[n]; pal[b] = new short[n]; faces[b] = new byte[n];
                for (int i = 0; i < n; i++) {
                    x[b][i] = d.readShort(); y[b][i] = d.readShort(); z[b][i] = d.readShort();
                    pal[b][i] = d.readShort(); faces[b][i] = d.readByte();
                }
                if (d.readByte() != 0) { alpha[b] = new byte[n]; d.readFully(alpha[b]); }
            }
            return new BellModel(palette, names, x, y, z, pal, faces, alpha);
        } catch (Exception e) {
            throw new RuntimeException("Hollowbell: could not read " + path, e);
        }
    }

    /** loads it off the main thread at start, so the first one seen doesn't hitch */
    public static void preload() {
        Thread t = new Thread(() -> {
            try { get(); } catch (Exception e) { HollowbellMod.LOG.error("Hollowbell model failed to load", e); }
        }, "Hollowbell model");
        t.setDaemon(true);
        t.start();
    }
}
