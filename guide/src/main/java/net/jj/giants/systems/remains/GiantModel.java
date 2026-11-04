package net.jj.giants.systems.remains;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * (remains) A giant's own model, as his mod draws him: the blocks of his build by bone (model space is blocks at size
 * 1, y 0 the ground under him) and his rig (each bone's parent and pivot). Read from his mod's own files, so his
 * remains are his real shape and colours. Plain Java (no game classes), so it can be tried outside the game.
 */
public final class GiantModel {
    public final String kind;
    /** each palette entry as a full block state ("minecraft:bone_block[axis=y]") */
    public final String[] palette;
    public final String[] names;
    public final short[][] x, y, z, pal;
    /** each bone's parent (-1 none) and pivot (model space at rest) */
    public final int[] parent;
    public final float[][] pivot;
    /** the whole rig file (each giant's own extras: legs, heads, segments...) */
    public final JsonObject rig;
    private final Map<String, Integer> index = new HashMap<>();

    private GiantModel(String kind, String[] palette, String[] names, short[][] x, short[][] y, short[][] z,
            short[][] pal, JsonObject rig) {
        this.kind = kind;
        this.palette = palette;
        this.names = names;
        this.x = x;
        this.y = y;
        this.z = z;
        this.pal = pal;
        this.rig = rig;
        for (int b = 0; b < names.length; b++) index.putIfAbsent(names[b], b);
        parent = new int[names.length];
        pivot = new float[names.length][];
        java.util.Arrays.fill(parent, -1);
        JsonElement bs = rig == null ? null : rig.get("bones");
        if (bs != null && bs.isJsonArray()) {
            for (JsonElement e : bs.getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                Integer b = index.get(o.get("name").getAsString());
                if (b == null) continue;
                JsonElement p = o.get("parent");
                if (p != null && !p.isJsonNull()) {
                    Integer pb = index.get(p.getAsString());
                    parent[b] = pb == null ? -1 : pb;
                }
                JsonArray pv = o.getAsJsonArray("pivot");
                if (pv != null) pivot[b] = new float[] {pv.get(0).getAsFloat(), pv.get(1).getAsFloat(),
                        pv.get(2).getAsFloat()};
            }
        }
        for (int b = 0; b < names.length; b++) if (pivot[b] == null) pivot[b] = centre(b);
    }

    public int bone(String name) {
        Integer b = index.get(name);
        return b == null ? -1 : b;
    }

    public int count(int b) {
        return x[b].length;
    }

    public int total() {
        int n = 0;
        for (short[] a : x) n += a.length;
        return n;
    }

    /** a bone's middle (the middle of its blocks), or 0 0 0 when it has none */
    public float[] centre(int b) {
        int n = x[b].length;
        if (n == 0) return new float[3];
        double sx = 0, sy = 0, sz = 0;
        for (int i = 0; i < n; i++) {
            sx += x[b][i];
            sy += y[b][i];
            sz += z[b][i];
        }
        return new float[] {(float) (sx / n + 0.5), (float) (sy / n + 0.5), (float) (sz / n + 0.5)};
    }

    /** a bone's box: min x y z, max x y z (block corners), or null */
    public float[] box(int b) {
        int n = x[b].length;
        if (n == 0) return null;
        float[] q = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE,
            -Float.MAX_VALUE};
        for (int i = 0; i < n; i++) {
            q[0] = Math.min(q[0], x[b][i]);
            q[1] = Math.min(q[1], y[b][i]);
            q[2] = Math.min(q[2], z[b][i]);
            q[3] = Math.max(q[3], x[b][i] + 1);
            q[4] = Math.max(q[4], y[b][i] + 1);
            q[5] = Math.max(q[5], z[b][i] + 1);
        }
        return q;
    }

    /**
     * Reads a model file and its rig. The files differ a little by giant (what follows each bone's blocks), told by
     * the file's first four letters.
     */
    public static GiantModel read(String kind, InputStream model, InputStream rigIn) throws IOException {
        JsonObject rig = null;
        if (rigIn != null) {
            try (InputStreamReader r = new InputStreamReader(rigIn, StandardCharsets.UTF_8)) {
                rig = JsonParser.parseReader(r).getAsJsonObject();
            }
        }
        try (DataInputStream d = new DataInputStream(new GZIPInputStream(model, 1 << 16))) {
            byte[] mg = new byte[4];
            d.readFully(mg);
            String magic = new String(mg, StandardCharsets.US_ASCII);
            int version = d.readInt();
            int np = d.readInt();
            String[] palette = new String[np];
            for (int i = 0; i < np; i++) {
                String s = d.readUTF();
                palette[i] = s.contains(":") ? s : "minecraft:" + s;
            }
            boolean alpha = magic.equals("WREK") || magic.equals("LWIL") || magic.equals("SWRM")
                    || magic.equals("HBEL");
            boolean along = alpha && !magic.equals("HBEL") && version >= 2;
            int nb = d.readInt();
            String[] names = new String[nb];
            short[][] x = new short[nb][], y = new short[nb][], z = new short[nb][], pal = new short[nb][];
            for (int b = 0; b < nb; b++) {
                names[b] = d.readUTF();
                int n = d.readInt();
                x[b] = new short[n];
                y[b] = new short[n];
                z[b] = new short[n];
                pal[b] = new short[n];
                for (int i = 0; i < n; i++) {
                    x[b][i] = d.readShort();
                    y[b][i] = d.readShort();
                    z[b][i] = d.readShort();
                    pal[b][i] = d.readShort();
                    d.readByte();
                }
                if (alpha && d.readByte() != 0) d.skipNBytes(n);
                if (along && d.readByte() != 0) d.skipNBytes(n);
            }
            return new GiantModel(kind, palette, names, x, y, z, pal, rig);
        }
    }
}
