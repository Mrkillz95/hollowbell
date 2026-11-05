package net.jj.giants.client.dev;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * (lagfix, development only) The layout check: the open screen drawn once into a recording, and every two of its
 * visible widgets that overlap, and every line of text that runs into a widget it isn't the label of, written down.
 */
public final class LayoutCheck {
    private LayoutCheck() {}

    /** a rectangle on the screen (GUI pixels) */
    record Box(int x0, int y0, int x1, int y1, String what) {
        boolean meets(Box o) {
            return x0 < o.x1 && o.x0 < x1 && y0 < o.y1 && o.y0 < y1;
        }

        boolean inside(Box o, int slack) {
            return x0 >= o.x0 - slack && y0 >= o.y0 - slack && x1 <= o.x1 + slack && y1 <= o.y1 + slack;
        }

        @Nullable Box clip(@Nullable Box c) {
            if (c == null) return this;
            Box b = new Box(Math.max(x0, c.x0), Math.max(y0, c.y0), Math.min(x1, c.x1), Math.min(y1, c.y1), what);
            return b.x0 < b.x1 && b.y0 < b.y1 ? b : null;
        }

        @Override
        public String toString() {
            return what + " [" + x0 + "," + y0 + " " + (x1 - x0) + "x" + (y1 - y0) + "]";
        }
    }

    /** a GuiGraphics that writes down where each line of text went (after the pose and the scissor) */
    static final class Recorder extends GuiGraphics {
        final List<Box> text = new ArrayList<>();
        final Deque<Box> scissors = new ArrayDeque<>();

        Recorder(Minecraft mc) {
            super(mc, mc.renderBuffers().bufferSource());
        }

        private void note(Font font, int w, int x, int y, String what) {
            if (w <= 0) return;
            var m = pose().last().pose();
            Vector3f a = m.transformPosition(new Vector3f(x, y, 0)), b = m.transformPosition(new Vector3f(x + w,
                    y + font.lineHeight - 1, 0));
            Box t = new Box(Math.round(Math.min(a.x, b.x)), Math.round(Math.min(a.y, b.y)),
                    Math.round(Math.max(a.x, b.x)), Math.round(Math.max(a.y, b.y)), "text \"" + what + "\"");
            t = t.clip(scissors.peek());
            if (t != null) text.add(t);
        }

        @Override
        public int drawString(Font font, @Nullable String s, int x, int y, int colour, boolean shadow) {
            if (s != null) note(font, font.width(s), x, y, cut(s));
            return super.drawString(font, s, x, y, colour, shadow);
        }

        @Override
        public int drawString(Font font, FormattedCharSequence s, int x, int y, int colour, boolean shadow) {
            StringBuilder sb = new StringBuilder();
            s.accept((i, st, c) -> {
                sb.appendCodePoint(c);
                return true;
            });
            note(font, font.width(s), x, y, cut(sb.toString()));
            return super.drawString(font, s, x, y, colour, shadow);
        }

        @Override
        public void enableScissor(int x0, int y0, int x1, int y1) {
            Box b = new Box(x0, y0, x1, y1, "scissor");
            Box top = scissors.peek();
            Box c = b.clip(top);
            scissors.push(c == null ? new Box(0, 0, 0, 0, "scissor") : c);
            super.enableScissor(x0, y0, x1, y1);
        }

        @Override
        public void disableScissor() {
            if (!scissors.isEmpty()) scissors.pop();
            super.disableScissor();
        }

        static String cut(String s) {
            return s.length() > 40 ? s.substring(0, 40) + "…" : s;
        }
    }

    /** the open screen's overlaps, each one line ("" none) */
    public static List<String> check(Minecraft mc, String where) {
        List<String> out = new ArrayList<>();
        Screen sc = mc.screen;
        if (sc == null) return out;
        List<Box> ws = new ArrayList<>();
        for (var c : sc.children())
            if (c instanceof AbstractWidget w && w.visible && w.getWidth() > 0 && w.getHeight() > 0)
                ws.add(new Box(w.getX(), w.getY(), w.getX() + w.getWidth(), w.getY() + w.getHeight(),
                        w.getClass().getSimpleName() + " \"" + Recorder.cut(w.getMessage().getString()) + "\""));
        for (int i = 0; i < ws.size(); i++)
            for (int j = i + 1; j < ws.size(); j++)
                if (ws.get(i).meets(ws.get(j))) out.add(where + ": " + ws.get(i) + " overlaps " + ws.get(j));
        Recorder r = new Recorder(mc);
        try {
            sc.render(r, -1000, -1000, 0);
        } catch (RuntimeException e) {
            out.add(where + ": couldn't draw it to look at its words: " + e);
        }
        r.flush();
        for (Box t : r.text) {
            for (Box w : ws) {
                Box inner = new Box(w.x0 + 1, w.y0 + 1, w.x1 - 1, w.y1 - 1, w.what);
                if (t.meets(inner) && !t.inside(w, 1)) {
                    out.add(where + ": " + t + " runs into " + w);
                    break;
                }
            }
        }
        return out;
    }
}
