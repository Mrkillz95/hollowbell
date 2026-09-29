package net.jj.hollowbell.lab;

import net.jj.hollowbell.world.BellPlan;
import net.jj.hollowbell.world.BellPlan.Mat;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Draws the Bell Hollows without the game: the real plan run over a made-up piece of land (soft hills, a flat
 * plain and a river), written to pictures/ground/. Development only, never in the jar.
 *   ./gradlew groundLab            (or javac + java on this file and BellPlan, it needs nothing else)
 */
public final class GroundLab {
    static final int SEA = 62;
    static final long SEED = Long.getLong("lab.seed", 0x5EEDB311L);
    static final int RADIUS = Integer.getInteger("lab.radius", 900);
    static File out = new File(System.getProperty("lab.out", "pictures/ground"));

    // ------------------------------------------------------------------ the made-up land

    static double vn(int salt, double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        double sx = fx * fx * (3 - 2 * fx), sz = fz * fz * (3 - 2 * fz);
        double a = BellPlan.h01(77, x0, z0, salt), b = BellPlan.h01(77, x0 + 1, z0, salt);
        double c = BellPlan.h01(77, x0, z0 + 1, salt), d = BellPlan.h01(77, x0 + 1, z0 + 1, salt);
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sz;
    }

    static double riverDist(int x, int z) {
        double cz = 330 + 160 * Math.sin(x / 230.0) + 60 * Math.sin(x / 71.0 + 1.3);
        return Math.abs(z - cz);
    }

    /** the land's height before he comes: the top block */
    static int land(int x, int z) {
        double hills = 0;
        double amp = 1, f = 1 / 180.0, tot = 0;
        for (int o = 0; o < 4; o++) { hills += amp * vn(o, x * f, z * f); tot += amp; amp *= 0.5; f *= 2.1; }
        hills /= tot;
        double y = 66 + 26 * (hills - 0.35);
        double plain = smooth(-120, 120, x - 250 + 80 * vn(9, z / 200.0, 0.5));   // the east side lies flat
        y = y + (69 - y) * plain * 0.85;
        double rd = riverDist(x, z);
        if (rd < 30) y = SEA - 3 + (y - (SEA - 3)) * smooth(6, 30, rd);
        return (int) Math.round(y);
    }

    static boolean wet(int x, int z) { return land(x, z) < SEA; }

    static int surface(int x, int z) { return Math.max(land(x, z), wet(x, z) ? SEA : Integer.MIN_VALUE); }

    static double smooth(double e0, double e1, double v) {
        double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ running the plan

    static BellPlan plan() {
        return new BellPlan(SEED, 0, 0, RADIUS, SEA, -64, 320, GroundLab::surface);
    }

    /** a solved chunk plus the land it was solved on */
    static final class Chunk {
        final int cx, cz; final BellPlan.Out o = new BellPlan.Out(); final int[] y0 = new int[256]; final boolean[] wet = new boolean[256];
        Chunk(int cx, int cz) { this.cx = cx; this.cz = cz; }
    }

    static Chunk solve(BellPlan p, int cx, int cz) {
        Chunk c = new Chunk(cx, cz);
        boolean[] ok = new boolean[256];
        int[] lowest = new int[256];
        for (int i = 0; i < 256; i++) {
            int x = (cx << 4) + (i & 15), z = (cz << 4) + (i >> 4);
            c.wet[i] = wet(x, z);
            c.y0[i] = surface(x, z);
            ok[i] = true;
            lowest[i] = c.y0[i] - 10;
        }
        p.chunk(cx, cz, c.y0, ok, c.wet, lowest, c.o);
        return c;
    }

    /** every block of the finished land in a box, like the world would hold it */
    static final class Box {
        final int x0, z0, w, d, yb, h; final Mat[] m; final int[] orig;   // orig: 1 grass, 2 dirt, 3 stone, 4 sand, 5 water
        Box(int x0, int z0, int w, int d, int yb, int h) {
            this.x0 = x0; this.z0 = z0; this.w = w; this.d = d; this.yb = yb; this.h = h;
            m = new Mat[w * d * h]; orig = new int[w * d * h];
        }
        int idx(int x, int y, int z) { return ((y - yb) * d + (z - z0)) * w + (x - x0); }
        boolean in(int x, int y, int z) { return x >= x0 && x < x0 + w && z >= z0 && z < z0 + d && y >= yb && y < yb + h; }
    }

    static Box build(BellPlan p, int x0, int z0, int w, int d) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        List<Chunk> cs = new ArrayList<>();
        for (int cx = x0 >> 4; cx <= (x0 + w - 1) >> 4; cx++)
            for (int cz = z0 >> 4; cz <= (z0 + d - 1) >> 4; cz++) cs.add(solve(p, cx, cz));
        for (Chunk c : cs) for (int i = 0; i < 256; i++) {
            lo = Math.min(lo, Math.min(c.o.paint[i] ? c.o.top[i] : c.y0[i], c.y0[i]));
            int t = c.o.top[i];
            for (int k = 0; k < c.o.an[i]; k++) t = Math.max(t, c.o.ay[i][k]);
            hi = Math.max(hi, Math.max(t, c.y0[i]));
        }
        int yb = lo - 5;
        Box b = new Box(x0, z0, w, d, yb, hi - yb + 2);
        for (Chunk c : cs) for (int i = 0; i < 256; i++) {
            int x = (c.cx << 4) + (i & 15), z = (c.cz << 4) + (i >> 4);
            if (x < x0 || x >= x0 + w || z < z0 || z >= z0 + d) continue;
            int land = land(x, z);
            boolean painted = c.o.paint[i];
            int top = painted && !c.wet[i] ? c.o.top[i] : land;
            for (int y = yb; y <= top; y++) {
                int k = top - y;
                Mat m = null;
                if (painted && !c.wet[i] && k < c.o.layers[i]) m = c.o.layer[i][k];
                else if (painted && !c.wet[i]) m = p.strata(x, y, z);
                else if (painted && c.wet[i] && y > land - 3) m = p.strata(x, y, z);
                int j = b.idx(x, y, z);
                if (m != null && m != Mat.KEEP) b.m[j] = m;
                else b.orig[j] = c.wet[i] ? 4 : y == land && y == top ? 1 : y > land - 4 ? 2 : 3;
                if (m == Mat.KEEP && y == top && top > land) b.orig[j] = 1;
            }
            if (c.wet[i]) for (int y = land + 1; y <= SEA; y++) b.orig[b.idx(x, y, z)] = 5;
            if (painted) for (int k = 0; k < c.o.an[i]; k++) {
                int y = c.o.ay[i][k];
                if (b.in(x, y, z)) b.m[b.idx(x, y, z)] = c.o.am[i][k];
            }
        }
        return b;
    }

    static int origRgb(int o) {
        switch (o) {
            case 1: return 0x7DB765;
            case 2: return 0x86603F;
            case 3: return 0x7C7C7C;
            case 4: return 0xD8CC96;
            case 5: return 0x3C6FD8;
            default: return 0;
        }
    }

    // ------------------------------------------------------------------ the top-down map

    static void map() throws Exception {
        BellPlan p = plan();
        int half = RADIUS + 40, scale = 2;
        int size = half * 2 / scale;
        int[] topY = new int[size * size], rgb = new int[size * size], zone = new int[size * size];
        boolean[] paint = new boolean[size * size];
        for (int cx = -half >> 4; cx <= (half - 1) >> 4; cx++)
            for (int cz = -half >> 4; cz <= (half - 1) >> 4; cz++) {
                Chunk c = solve(p, cx, cz);
                for (int i = 0; i < 256; i++) {
                    int x = (cx << 4) + (i & 15), z = (cz << 4) + (i >> 4);
                    if ((x & 1) != 0 || (z & 1) != 0) continue;
                    int px = (x + half) / scale, pz = (z + half) / scale;
                    if (px < 0 || pz < 0 || px >= size || pz >= size) continue;
                    int j = pz * size + px;
                    int top = c.y0[i], col;
                    boolean water = c.wet[i];
                    if (c.o.paint[i] && !c.wet[i]) {
                        top = c.o.top[i];
                        Mat m = c.o.layer[i][0];
                        col = m == null || m == Mat.KEEP ? origRgb(1) : m.rgb;
                        int best = top;
                        for (int k = 0; k < c.o.an[i]; k++) if (c.o.ay[i][k] > best) {
                            best = c.o.ay[i][k]; Mat a = c.o.am[i][k];
                            col = a == Mat.CHAIN ? col : a.rgb;
                            water = a == Mat.WATER;
                        }
                        top = best;
                    } else col = water ? origRgb(5) : origRgb(1);
                    if (!c.o.paint[i] && !water) col = mixc(col, 0x303030, 0.25);
                    topY[j] = top; rgb[j] = col; zone[j] = c.o.zone[i]; paint[j] = c.o.paint[i];
                }
            }
        BufferedImage img = new BufferedImage(size, size + 70, BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < size; pz++) for (int px = 0; px < size; px++) {
            int j = pz * size + px;
            int l = px > 0 ? topY[j - 1] : topY[j], u = pz > 0 ? topY[j - size] : topY[j];
            double shade = 1 + 0.06 * ((topY[j] - l) + (topY[j] - u));
            shade = Math.max(0.6, Math.min(1.35, shade));
            int c = scalec(rgb[j], shade);
            // the zone lines, thin and dark
            if (px + 1 < size && pz + 1 < size && (zone[j] != zone[j + 1] || zone[j] != zone[j + size]) && zone[j] != BellPlan.FRINGE - 1
                    && Math.min(zone[j], Math.min(zone[j + 1], zone[j + size])) >= BellPlan.FRINGE)
                c = mixc(c, 0x1A2A22, 0.55);
            img.setRGB(px, pz, c);
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x20262A)); g.fillRect(0, size, size, 70);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(Color.WHITE);
        g.drawString("The Bell Hollows, radius " + RADIUS + ", 1 pixel = 2 blocks. Thin dark lines: core / middle / fringe. Seed " + Long.toHexString(SEED), 10, size + 20);
        g.drawString("Made-up land: hills to the west, a flat plain to the east, a river across the south.", 10, size + 40);
        g.drawString("Den (the great glass bell) at the centre. Spires, shards, ribs, reefs, pools and lights all over.", 10, size + 60);
        g.dispose();
        ImageIO.write(img, "png", new File(out, "map.png"));
        // a closer look at the middle, 1 pixel = 1 block
        crop(p, 0, 0, 360, "map_centre.png");
    }

    static void crop(BellPlan p, int mx, int mz, int half, String name) throws Exception {
        int size = half * 2;
        Box b = build(p, mx - half, mz - half, size, size);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int[] tops = new int[size * size];
        int[] cols = new int[size * size];
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            int wx = b.x0 + x, wz = b.z0 + z;
            for (int y = b.yb + b.h - 1; y >= b.yb; y--) {
                int j = b.idx(wx, y, wz);
                Mat m = b.m[j];
                int o = b.orig[j];
                if (m == null && o == 0) continue;
                if (m == Mat.CHAIN) continue;
                int c = m != null ? m.rgb : origRgb(o);
                if (m != null && m.kind == 2) {                  // glass: see the inside a little
                    int under = 0;
                    for (int yy = y - 1; yy >= b.yb; yy--) {
                        int jj = b.idx(wx, yy, wz);
                        if (b.m[jj] != null && b.m[jj] != Mat.CHAIN) { under = b.m[jj].rgb; break; }
                        if (b.orig[jj] != 0) { under = origRgb(b.orig[jj]); break; }
                    }
                    c = mixc(c, under, 0.35);
                }
                tops[z * size + x] = y; cols[z * size + x] = c;
                break;
            }
        }
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            int j = z * size + x;
            int l = x > 0 ? tops[j - 1] : tops[j], u = z > 0 ? tops[j - size] : tops[j];
            double shade = Math.max(0.6, Math.min(1.35, 1 + 0.07 * ((tops[j] - l) + (tops[j] - u))));
            img.setRGB(x, z, scalec(cols[j], shade));
        }
        ImageIO.write(img, "png", new File(out, name));
    }

    // ------------------------------------------------------------------ the iso pictures

    /** the same box turned a quarter at a time, so the side worth seeing faces the viewer */
    static Box turn(Box a, int rot) {
        if (rot == 0) return a;
        Box b = new Box(a.x0, a.z0, a.w, a.d, a.yb, a.h);
        int w = a.w;
        for (int y = a.yb; y < a.yb + a.h; y++) for (int lz = 0; lz < w; lz++) for (int lx = 0; lx < w; lx++) {
            int wx, wz;
            switch (rot) {
                case 1: wx = w - 1 - lz; wz = lx; break;
                case 2: wx = w - 1 - lx; wz = w - 1 - lz; break;
                default: wx = lz; wz = w - 1 - lx; break;
            }
            int from = a.idx(a.x0 + wx, y, a.z0 + wz), to = b.idx(a.x0 + lx, y, a.z0 + lz);
            b.m[to] = a.m[from]; b.orig[to] = a.orig[from];
        }
        return b;
    }

    /** the quarter turn that puts this direction (world x/z) toward the viewer */
    static int facing(double dx, double dz) {
        double[][] s = {{dx, dz}, {dz, -dx}, {-dx, -dz}, {-dz, dx}};
        int best = 0;
        for (int r = 1; r < 4; r++) if (s[r][0] + s[r][1] > s[best][0] + s[best][1]) best = r;
        return best;
    }

    static void iso(BellPlan p, int mx, int mz, int w, int tile, String name, String title) throws Exception {
        iso(p, mx, mz, w, tile, name, title, 0);
    }

    static void iso(BellPlan p, int mx, int mz, int w, int tile, String name, String title, int rot) throws Exception {
        Box b = turn(build(p, mx - w / 2, mz - w / 2, w, w), rot);
        int T = tile;                               // half the width of a block's top on screen
        int widthPx = (w + w) * T + 40;
        int heightPx = (w + w) * T / 2 + b.h * T + 80;
        BufferedImage img = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xDCE9E4)); g.fillRect(0, 0, widthPx, heightPx);
        int ox = w * T + 20, oy = b.h * T + 50;
        int minY = b.yb, maxY = b.yb + b.h - 1;
        for (int s = 0; s <= 2 * (w - 1); s++)
            for (int y = minY; y <= maxY; y++)
                for (int lx = Math.max(0, s - (w - 1)); lx <= Math.min(w - 1, s); lx++) {
                    int lz = s - lx;
                    int x = b.x0 + lx, z = b.z0 + lz;
                    int j = b.idx(x, y, z);
                    Mat m = b.m[j];
                    int o = b.orig[j];
                    if (m == null && o == 0) continue;
                    int sx = ox + (lx - lz) * T, sy = oy + (lx + lz) * T / 2 - (y - minY) * T;
                    drawBlock(g, b, x, y, z, m, o, sx, sy, T, minY, maxY);
                }
        g.setColor(new Color(0x20262A));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.drawString(title, 16, 26);
        g.dispose();
        ImageIO.write(img, "png", new File(out, name));
    }

    static boolean opaque(Box b, int x, int y, int z) {
        if (!b.in(x, y, z)) return false;
        int j = b.idx(x, y, z);
        Mat m = b.m[j];
        if (m != null) return m.kind == 1;
        return b.orig[j] != 0 && b.orig[j] != 5;
    }

    static boolean same(Box b, int x, int y, int z, Mat m) {
        return b.in(x, y, z) && b.m[b.idx(x, y, z)] == m;
    }

    static void drawBlock(Graphics2D g, Box b, int x, int y, int z, Mat m, int o, int sx, int sy, int T, int minY, int maxY) {
        int rgb = m != null ? m.rgb : origRgb(o);
        double lift = 0.86 + 0.28 * (y - minY) / Math.max(1.0, maxY - minY);
        boolean glass = m != null && m.kind == 2, water = (m != null && m.kind == 4) || o == 5;
        if (m != null && m.kind == 3) { drawSmall(g, m, sx, sy, T); return; }
        float alpha = glass ? 0.55f : water ? 0.78f : 1f;
        boolean topOpen = !opaque(b, x, y + 1, z) && !(glass && same(b, x, y + 1, z, m)) && !(water && (same(b, x, y + 1, z, Mat.WATER) || (b.in(x, y + 1, z) && b.orig[b.idx(x, y + 1, z)] == 5)));
        boolean leftOpen = !opaque(b, x, y, z + 1) && !(glass && same(b, x, y, z + 1, m)) && !water;
        boolean rightOpen = !opaque(b, x + 1, y, z) && !(glass && same(b, x + 1, y, z, m)) && !water;
        if (glass) { leftOpen |= b.in(x, y, z + 1) && !same(b, x, y, z + 1, m); rightOpen |= b.in(x + 1, y, z) && !same(b, x + 1, y, z, m); }
        if (topOpen) poly(g, scalec(rgb, lift * (m != null && m.glows() ? 1.12 : 1.0)), alpha, sx, sy - T / 2, sx + T, sy, sx, sy + T / 2, sx - T, sy);
        if (leftOpen) poly(g, scalec(rgb, lift * 0.74), alpha, sx - T, sy, sx, sy + T / 2, sx, sy + T / 2 + T, sx - T, sy + T);
        if (rightOpen) poly(g, scalec(rgb, lift * 0.6), alpha, sx, sy + T / 2, sx + T, sy, sx + T, sy + T, sx, sy + T / 2 + T);
        if (T >= 4 && !glass && !water && topOpen) {
            g.setColor(new Color(scalec(rgb, lift * 0.88)));
            g.setStroke(new BasicStroke(0.6f));
            Path2D e = new Path2D.Double();
            e.moveTo(sx - T, sy); e.lineTo(sx, sy - T / 2.0); e.lineTo(sx + T, sy);
            g.draw(e);
        }
    }

    static void drawSmall(Graphics2D g, Mat m, int sx, int sy, int T) {
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        int cy = sy + T / 2;
        switch (m) {
            case END_ROD: g.setColor(new Color(m.rgb)); g.fillRect(sx - Math.max(1, T / 5), cy - T, Math.max(2, T / 3), T + T / 2);
                g.setColor(new Color(255, 255, 240, 90)); g.fillOval(sx - T, cy - T - T / 2, 2 * T, 2 * T); break;
            case CHAIN: g.setColor(new Color(m.rgb)); g.fillRect(sx - 1, cy - T, 2, T + T / 2); break;
            case MOSS_CARPET: poly(g, m.rgb, 1f, sx, sy + T - T / 2, sx + T, sy + T, sx, sy + T + T / 2, sx - T, sy + T); break;
            default: g.setColor(new Color(m.rgb)); g.fillOval(sx - T / 2, cy - T / 3, T, T); break;
        }
    }

    static void poly(Graphics2D g, int rgb, float alpha, int... p) {
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        g.setColor(new Color(rgb));
        int n = p.length / 2;
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) { xs[i] = p[2 * i]; ys[i] = p[2 * i + 1]; }
        g.fillPolygon(xs, ys, n);
        g.setComposite(java.awt.AlphaComposite.SrcOver);
    }

    static int scalec(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f), gg = (int) Math.min(255, ((rgb >> 8) & 255) * f), b = (int) Math.min(255, (rgb & 255) * f);
        return (r << 16) | (gg << 8) | b;
    }

    static int mixc(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int[] find(BellPlan p, String kind) {
        for (int r = 180; r < 600; r += 16)
            for (int a = 0; a < 360; a += 7) {
                int cx = (int) (Math.cos(Math.toRadians(a)) * r) >> 4, cz = (int) (Math.sin(Math.toRadians(a)) * r) >> 4;
                Chunk c = solve(p, cx, cz);
                boolean anyWet = false;
                for (boolean w : c.wet) anyWet |= w;
                if (anyWet) continue;
                for (int i = 0; i < 256; i++) {
                    int n = 0, tall = 0;
                    for (int k = 0; k < c.o.an[i]; k++) {
                        Mat m = c.o.am[i][k];
                        int hgt = c.o.ay[i][k] - c.o.top[i];
                        boolean hit = switch (kind) {
                            case "arch" -> m == Mat.BONE && hgt > 4;
                            case "shard" -> (m == Mat.LIME_GLASS || m == Mat.BELL_SHARD) && hgt > 6;
                            case "pool" -> m == Mat.WATER;
                            case "spire" -> m == Mat.DIORITE && hgt > 10;
                            default -> m == Mat.TENDRIL_GLASS && hgt == 2;
                        };
                        if (hit) n++;
                    }
                    if (n > 0) return new int[]{(cx << 4) + (i & 15), (cz << 4) + (i >> 4)};
                }
            }
        return null;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        out.mkdirs();
        String what = args.length > 0 ? String.join(" ", args) : "all";
        BellPlan p = plan();
        long t0 = System.currentTimeMillis();
        if (what.equals("all") || what.equals("map")) map();
        if (what.equals("all") || what.equals("den")) iso(p, 0, 0, 140, 5, "den_iso.png", "His den: the great glass bell, half sunk, broken open on one side (140 x 140 blocks)",
                facing(Math.cos(p.breakAngle()), Math.sin(p.breakAngle())));
        if (what.equals("all") || what.equals("sample")) {
            Random r = new Random(SEED);
            int n = 0;
            while (n < 3) {
                double a = r.nextDouble() * Math.PI * 2, d = RADIUS * (0.25 + 0.4 * r.nextDouble());
                int x = (int) (Math.cos(a) * d), z = (int) (Math.sin(a) * d);
                double dn = p.dn(x, z);
                if (dn < 0.3 || dn > 0.72) continue;
                n++;
                iso(p, x, z, 96, 6, "sample_iso_" + n + ".png", "Middle of the Hollows near " + x + ", " + z + " (96 x 96 blocks)");
            }
        }
        if (what.equals("all") || what.startsWith("dome"))
            iso(p, 0, 0, 64, 10, "den_close.png", "The bell up close: broken open on one side, lights hanging inside, a still pool under it",
                    facing(Math.cos(p.breakAngle()), Math.sin(p.breakAngle())));
        if (what.equals("all") || what.startsWith("closeup")) {
            int[] at = find(p, "pool");
            if (at != null) iso(p, at[0], at[1], 56, 9, "closeup_hollows.png", "Close up: hollows with a still pool, a spore garden, spires and reefs (56 x 56 blocks)");
        }
        if (what.startsWith("find")) {
            // a close look at one of each kind: find it by what it leaves above the ground
            String[] kinds = {"arch", "shard", "pool", "spire", "reef"};
            for (String k : kinds) {
                int[] at = find(p, k);
                if (at == null) { System.out.println("no " + k); continue; }
                iso(p, at[0], at[1], 56, 9, "find_" + k + ".png", k + " near " + at[0] + ", " + at[1]);
                iso(p, at[0], at[1], 40, 12, "find_" + k + "_turned.png", k + " near " + at[0] + ", " + at[1], 1);
            }
        }
        if (what.startsWith("crop")) {
            String[] a = what.split(" ");
            crop(p, Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]), a[4]);
        }
        System.out.println("pictures in " + out.getAbsolutePath() + " (" + (System.currentTimeMillis() - t0) + " ms)");
    }
}
