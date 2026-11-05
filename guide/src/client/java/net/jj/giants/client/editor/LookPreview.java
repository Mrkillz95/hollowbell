package net.jj.giants.client.editor;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.LinkedHashMap;
import java.util.Map;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.editor.LookSets;
import net.jj.giants.systems.remains.GiantModel;
import net.jj.giants.systems.remains.GiantModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 12) The Editor page's preview: a giant seen from the side, every block of his own model in its colour, in his
 * own look or in a set. Made once for each giant and look (a few milliseconds) and kept while it is shown; the last
 * dozen are kept, older ones let go.
 */
public final class LookPreview {
    private LookPreview() {}

    public record Pic(ResourceLocation tex, int w, int h) {}

    private static final int KEEP = 12;
    private static final Map<String, Pic> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static int made;

    /** his picture in this look (map: palette entry -> block; empty: his own); null when his model can't be read */
    public static @Nullable Pic of(String kind, Map<String, String> map) {
        String key = kind + "|" + LookSets.key("p", map);
        Pic p = CACHE.get(key);
        if (p != null) return p;
        GiantModel m = GiantModels.get(kind);
        if (m == null) return null;
        NativeImage img = render(m, map);
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "editor_preview/" + made++);
        Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
        p = new Pic(rl, img.getWidth(), img.getHeight());
        CACHE.put(key, p);
        if (CACHE.size() > KEEP) {
            var it = CACHE.entrySet().iterator();
            Pic old = it.next().getValue();
            it.remove();
            Minecraft.getInstance().getTextureManager().release(old.tex());
        }
        return p;
    }

    /** from his right side: the nearest block in each column, a little darker under an overhang and further back */
    private static NativeImage render(GiantModel m, Map<String, String> map) {
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE,
                minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        for (int b = 0; b < m.x.length; b++)
            for (int i = 0; i < m.x[b].length; i++) {
                minY = Math.min(minY, m.y[b][i]);
                maxY = Math.max(maxY, m.y[b][i]);
                minZ = Math.min(minZ, m.z[b][i]);
                maxZ = Math.max(maxZ, m.z[b][i]);
                minX = Math.min(minX, m.x[b][i]);
                maxX = Math.max(maxX, m.x[b][i]);
            }
        int w = Math.max(1, maxZ - minZ + 1), h = Math.max(1, maxY - minY + 1);
        // (one picture pixel for up to 2x2 blocks on the biggest: at most 256 wide)
        int step = Math.max(1, (Math.max(w, h) + 255) / 256);
        int iw = (w + step - 1) / step, ih = (h + step - 1) / step;
        int[] near = new int[iw * ih], pal = new int[iw * ih];
        java.util.Arrays.fill(near, Integer.MIN_VALUE);
        for (int b = 0; b < m.x.length; b++)
            for (int i = 0; i < m.x[b].length; i++) {
                int u = (m.z[b][i] - minZ) / step, v = (maxY - m.y[b][i]) / step, k = v * iw + u;
                if (m.x[b][i] > near[k]) {
                    near[k] = m.x[b][i];
                    pal[k] = m.pal[b][i];
                }
            }
        int[] rgb = new int[m.palette.length];
        for (int i = 0; i < rgb.length; i++) rgb[i] = LookSets.rgb(map.getOrDefault(m.palette[i], m.palette[i]));
        NativeImage img = new NativeImage(iw, ih, true);
        float span = Math.max(1, maxX - minX);
        for (int v = 0; v < ih; v++)
            for (int u = 0; u < iw; u++) {
                int k = v * iw + u;
                if (near[k] == Integer.MIN_VALUE) {
                    img.setPixelRGBA(u, v, 0);
                    continue;
                }
                float shade = 0.72f + 0.28f * (near[k] - minX) / span;
                if (v > 0 && near[k - iw] != Integer.MIN_VALUE && near[k - iw] > near[k] + 1) shade *= 0.82f;
                int c = rgb[pal[k]];
                int r = Math.min(255, (int) (((c >> 16) & 255) * shade)), g = Math.min(255,
                        (int) (((c >> 8) & 255) * shade)), bl = Math.min(255, (int) ((c & 255) * shade));
                // (NativeImage keeps ABGR)
                img.setPixelRGBA(u, v, 0xFF000000 | bl << 16 | g << 8 | r);
            }
        return img;
    }
}
