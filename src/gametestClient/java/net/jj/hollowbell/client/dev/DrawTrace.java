package net.jj.hollowbell.client.dev;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.jj.hollowbell.GiantsPerf;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.client.dev.mixin.ShaderUniforms;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Development only (autotest "drawcheck name"): what the graphics card is told to draw for him, frames drawn Classic
 * and frames drawn Fast, compared. Each draw call while he is being drawn (his solid and glow pass and his glass pass)
 * is written down with the vertex array and index buffer bound, the count, the program, and every uniform of the
 * shader as last sent to the card. The same lists = the same picture, draw for draw.
 */
public final class DrawTrace {
    private DrawTrace() {}

    /** a frame is being written down */
    private static boolean frame;
    /** inside his renderer (his render or the glass pass) */
    public static int inHim;

    private static final Map<Uniform, String> sent = new IdentityHashMap<>();
    private static List<String> draws = new ArrayList<>();

    public static boolean recording() {
        return frame;
    }

    /** a uniform sent to the card (its values as sent) */
    public static void uploaded(Uniform u, int type, IntBuffer ints, FloatBuffer floats) {
        if (type <= 3) {
            int[] a = new int[ints.limit()];
            for (int i = 0; i < a.length; i++) a[i] = ints.get(i);
            sent.put(u, Arrays.toString(a));
        } else {
            float[] a = new float[floats.limit()];
            for (int i = 0; i < a.length; i++) a[i] = floats.get(i);
            sent.put(u, Arrays.toString(a));
        }
    }

    public static void drawn(int mode, int count, int type) {
        if (!frame || inHim <= 0) return;
        StringBuilder b = new StringBuilder();
        b.append("vao ").append(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
        b.append(" ibo ").append(GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING));
        b.append(" prog ").append(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        b.append(" mode ").append(mode).append(" count ").append(count).append(" type ").append(type);
        ShaderInstance sh = RenderSystem.getShader();
        if (sh != null) {
            b.append(" shader ").append(sh.getName());
            for (Uniform u : ((ShaderUniforms) sh).hollowbell$uniforms())
                b.append(" | ").append(u.getName()).append('=').append(sent.getOrDefault(u, "?"));
        }
        draws.add(b.toString());
    }

    // the check: frames to settle, then frames drawn Classic, Fast, Classic, Fast... each written down. A Classic
    // frame and the Fast frame right after it are compared when no game tick came between them (a tick moves the
    // far-off stand-ins on, frozen or not)

    /** game ticks on this client (counted by the autotest) */
    public static long ticks;

    private static int step = -1;
    private static String name;
    private static boolean wasFast;
    private static final List<List<String>> got = new ArrayList<>();
    private static final List<Long> gotTick = new ArrayList<>();

    private static final int FIRST = 8, FRAMES = 12;

    public static void start(String n) {
        name = n;
        wasFast = GiantsPerf.fastDrawing();
        got.clear();
        gotTick.clear();
        step = 0;
    }

    public static boolean busy() {
        return step >= 0;
    }

    private static void drawing(boolean fast) {
        GiantsPerf.receive("{\"perf\":1,\"side\":\"client\",\"values\":{\"detail.drawing\":" + (fast ? 1 : 0) + "}}");
    }

    /** a frame starts */
    public static void frameStart() {
        if (step < 0) return;
        frame = step >= FIRST;
        drawing(frame && (step - FIRST) % 2 == 1);
        if (frame) {
            sent.clear();
            draws = new ArrayList<>();
        }
    }

    /** a frame ends */
    public static void frameEnd() {
        if (step < 0) return;
        if (frame) {
            got.add(draws);
            gotTick.add(ticks);
        }
        frame = false;
        if (++step < FIRST + FRAMES) return;
        step = -1;
        drawing(wasFast);
        int pairs = 0, same = 0, culled = 0, classicSame = 0, classicPairs = 0, sameButOrder = 0;
        String bad = "none";
        for (int i = 0; i + 1 < got.size(); i += 2) {
            if (i + 2 < got.size() && gotTick.get(i).equals(gotTick.get(i + 2))) {
                classicPairs++;
                if (got.get(i).equals(got.get(i + 2))) classicSame++;
            }
            if (!gotTick.get(i).equals(gotTick.get(i + 1))) continue;
            pairs++;
            int left = leftOut(got.get(i), got.get(i + 1));
            if (left >= 0) {
                same++;
                culled = Math.max(culled, left);
            } else {
                if (leftOut(noIbo(got.get(i)), noIbo(got.get(i + 1))) >= 0) sameButOrder++;
                if (bad.equals("none")) bad = firstDiff(got.get(i), got.get(i + 1));
            }
        }
        HollowbellMod.LOG.info(
                "autotest drawcheck {}: classic {} draws, fast {} draws; Classic/Fast frame pairs in one tick {}, FAST"
                        + " DRAWS EXACTLY CLASSIC'S (minus {} wholly off the screen) in {} of them; Classic frames"
                        + " alike {} of {}; pairs alike but for which index buffer (glass order) a draw used: {};"
                        + " first difference: {}",
                name,
                got.get(0).size(),
                got.get(1).size(),
                pairs,
                culled,
                same,
                classicSame,
                classicPairs,
                sameButOrder,
                bad);
    }

    /**
     * how many of Classic's draws Fast left out, when every draw Fast made is Classic's, in the same order, the same in
     * every way; -1 when not
     */
    private static int leftOut(List<String> classic, List<String> fast) {
        int j = 0;
        for (String c : classic) if (j < fast.size() && c.equals(fast.get(j))) j++;
        return j == fast.size() ? classic.size() - fast.size() : -1;
    }

    private static List<String> noIbo(List<String> l) {
        List<String> out = new ArrayList<>(l.size());
        for (String d : l) out.add(d.replaceFirst(" ibo [0-9]+", ""));
        return out;
    }

    private static String firstDiff(List<String> x, List<String> y) {
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            String a = i < x.size() ? x.get(i) : "(no draw)", b = i < y.size() ? y.get(i) : "(no draw)";
            if (!a.equals(b)) return "draw " + i + ": [" + a + "] vs [" + b + "]";
        }
        return "none";
    }
}
