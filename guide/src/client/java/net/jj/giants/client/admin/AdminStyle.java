package net.jj.giants.client.admin;

import java.util.List;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * (Part 2C, JJ Nov 2) How the admin book looks: a leather cover with gold trim and corner pieces round parchment
 * pages (its own pictures, made by tools/admin_textures.py in the giants' book-cover style), ink-coloured words, book
 * tabs, and a small picture for each section. Shared by the book, the Create World picker and their questions.
 */
public final class AdminStyle {
    private AdminStyle() {}

    // ink on parchment
    public static final int INK = 0xFF3A2814,
            INK_SOFT = 0xFF6A5236,
            INK_DIM = 0xFF96825F,
            RUBRIC = 0xFF8A2A14,
            AMBER = 0xFFA06208,
            BLUE = 0xFF2A5E9A,
            ORANGE = 0xFFB05A10,
            WARN = 0xFFB82818,
            GOOD = 0xFF2E7A2E,
            GILT = 0xFFF0D070,
            RULE = 0xFFB89C70,
            SHADE = 0x22000000,
            TINT = 0x1A8A5A10;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "textures/gui/admin/" + name + ".png");
    }

    public static final ResourceLocation FRAME = tex("frame"), PARCHMENT = tex("parchment"), INDEX = tex("index"),
            TAB = tex("tab"), ICONS = tex("icons"), FLOURISH = tex("flourish");
    /** the frame's edge in pixels (its picture is 40x40) */
    public static final int EDGE = 10;

    /** (Part 7) the Fun and World tools pages' pictures, in the order tools/fun_textures.py draws them */
    public static final List<String> P7_ICONS = List.of("fun", "world", "watch", "fight", "tag", "brush", "build",
            "mix");
    static final ResourceLocation P7 = tex("icons_p7");

    /** the section pictures, in the order tools/admin_textures.py draws them */
    public static final List<String> ICON_NAMES = List.of("stats", "moves", "spawning", "behaviour", "healing",
            "taming", "loot", "players", "effects", "worldgen", "misc", "home", "live", "presets", "changes", "all",
            "general", "special", "search");

    /** a picture repeated over a box (64x64 pictures) */
    public static void tile(GuiGraphics g, ResourceLocation t, int x, int y, int w, int h, int tw, int th) {
        for (int yy = y; yy < y + h; yy += th)
            for (int xx = x; xx < x + w; xx += tw) {
                int cw = Math.min(tw, x + w - xx), ch = Math.min(th, y + h - yy);
                g.blit(t, xx, yy, 0, 0, cw, ch, tw, th);
            }
    }

    /** the leather cover round a box: corners as drawn, edges and middle repeated (never stretched) */
    public static void frame(GuiGraphics g, int x, int y, int w, int h) {
        int e = EDGE, s = 40, m = s - 2 * e;
        // corners
        g.blit(FRAME, x, y, 0, 0, e, e, s, s);
        g.blit(FRAME, x + w - e, y, s - e, 0, e, e, s, s);
        g.blit(FRAME, x, y + h - e, 0, s - e, e, e, s, s);
        g.blit(FRAME, x + w - e, y + h - e, s - e, s - e, e, e, s, s);
        // edges
        for (int xx = x + e; xx < x + w - e; xx += m) {
            int cw = Math.min(m, x + w - e - xx);
            g.blit(FRAME, xx, y, e, 0, cw, e, s, s);
            g.blit(FRAME, xx, y + h - e, e, s - e, cw, e, s, s);
        }
        for (int yy = y + e; yy < y + h - e; yy += m) {
            int ch = Math.min(m, y + h - e - yy);
            g.blit(FRAME, x, yy, 0, e, e, ch, s, s);
            g.blit(FRAME, x + w - e, yy, s - e, e, e, ch, s, s);
        }
    }

    /** a page: parchment, darker toward its edges */
    public static void page(GuiGraphics g, int x, int y, int w, int h) {
        tile(g, PARCHMENT, x, y, w, h, 64, 64);
        shadeEdges(g, x, y, w, h, 6);
    }

    /** the index down the left: older, darker paper with a shadow at the spine */
    public static void index(GuiGraphics g, int x, int y, int w, int h) {
        tile(g, INDEX, x, y, w, h, 64, 64);
        shadeEdges(g, x, y, w, h, 4);
        for (int i = 0; i < 6; i++) g.fill(x + w - 6 + i, y, x + w - 5 + i, y + h, (0x0C + i * 6) << 24);
    }

    /** soft dark toward a box's edges */
    public static void shadeEdges(GuiGraphics g, int x, int y, int w, int h, int n) {
        for (int i = 0; i < n; i++) {
            int a = (n - i) * 7 << 24;
            g.fill(x + i, y + i, x + w - i, y + i + 1, a);
            g.fill(x + i, y + h - i - 1, x + w - i, y + h - i, a);
            g.fill(x + i, y + i + 1, x + i + 1, y + h - i - 1, a);
            g.fill(x + w - i - 1, y + i + 1, x + w - i, y + h - i - 1, a);
        }
    }

    /** a section's (or page's) picture, 16x16 at size */
    public static void icon(GuiGraphics g, String name, int x, int y, int size) {
        int i = ICON_NAMES.indexOf(name);
        // (Part 7) the Fun and World tools pages' own pictures
        if (i < 0 && P7_ICONS.contains(name)) {
            g.blit(P7, x, y, size, size, P7_ICONS.indexOf(name) * 16, 0, 16, 16, P7_ICONS.size() * 16, 16);
            return;
        }
        if (i < 0) i = ICON_NAMES.indexOf("misc");
        g.blit(ICONS, x, y, size, size, i * 16, 0, 16, 16, ICON_NAMES.size() * 16, 16);
    }

    /** a book tab (picked: lighter, with a red top and no line under it) */
    public static void tab(GuiGraphics g, int x, int y, int w, int h, boolean picked, boolean over) {
        int v = picked ? 16 : 0, e = 4;
        // left, middle (repeated), right; the picture's 16 rows squeezed or cut to h
        int hh = Math.min(16, h);
        g.blit(TAB, x, y, 0, v, e, hh, 32, 32);
        for (int xx = x + e; xx < x + w - e; xx += 24) {
            int cw = Math.min(24, x + w - e - xx);
            g.blit(TAB, xx, y, e, v, cw, hh, 32, 32);
        }
        g.blit(TAB, x + w - e, y, 32 - e, v, e, hh, 32, 32);
        if (h > 16) g.fill(x + 1, y + 16, x + w - 1, y + h, picked ? 0xFFF4E6C2 : 0xFFD2BC8A);
        if (over && !picked) g.fill(x + 1, y + 2, x + w - 1, y + h - 1, 0x22FFFFFF);
    }

    /** the rubric line with a diamond, across w */
    public static void flourish(GuiGraphics g, int x, int y, int w) {
        if (w < 64) {
            g.fill(x, y + 3, x + w, y + 4, RUBRIC);
            return;
        }
        int mid = x + w / 2;
        g.fill(x + 4, y + 3, mid - 4, y + 4, 0xA08A3A1A);
        g.fill(mid + 4, y + 3, x + w - 4, y + 4, 0xA08A3A1A);
        g.blit(FLOURISH, mid - 32, y, 0, 0, 64, 7, 64, 7);
    }

    /** a dotted line under a row */
    public static void dots(GuiGraphics g, int x, int y, int w) {
        for (int xx = x; xx < x + w; xx += 3) g.fill(xx, y, xx + 1, y + 1, 0x40503010);
    }

    /** a little card: lighter paper with a thin brown edge */
    public static void card(GuiGraphics g, int x, int y, int w, int h, boolean over) {
        g.fill(x, y, x + w, y + h, over ? 0x40FFF8E0 : 0x28FFF8E0);
        g.renderOutline(x, y, w, h, over ? 0xFF8A2A14 : 0x80705030);
    }
}
