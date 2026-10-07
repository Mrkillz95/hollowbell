package net.jj.giants.client;

import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Giants Guide, open. Drawn like the giants' own books: a title, a close button and tabs along the top. On the
 * left, every giant that is installed, with a picture; on the right, the picked giant's pages. The last entry on the
 * left, "Giants together", has who fights whom and the /giants commands.
 * <p>
 * The pages are lines in the language file (guide.jj_giants.KEY.PAGE.1, .2, ...), with a little markup:
 * "# heading", "- point", "| left | right" (a table row), "@item id,id | title | text" (item pictures, drawn from the
 * real items), "@recipe id" (a crafting grid from the real recipe), "@picture" (his picture, name and role),
 * "@live" (what the server says right now), "@meettable" (who fights whom). The moves page is made from his moves.
 */
public class GuideScreen extends Screen {
    private static final int GOLD = 0xFFE0B050, TITLE = 0xFFE9C9BC, TEXT = 0xFFD8D8D8, KEY = 0xFFFFE080, GREY = 0xFF9A9A9A,
            PANEL = 0xE8141210, EDGE = 0xFF6B5A44, LINE = 0x30FFFFFF;
    private static final String[] TOGETHER_PAGES = {"meetings", "giants"};

    /** what was open last time (kept while the game runs) */
    private static String pickKey = "";
    private static int tab;
    private static double keptScroll;

    private List<Giant> giants = List.of();
    private int pick;
    private final List<Block> blocks = new ArrayList<>();
    private int contentH;
    private double scroll;
    private boolean dragging;
    private @Nullable ItemStack hovered;
    private @Nullable Live.News shownNews;

    // where things go (set in init)
    private int px0, py0, pw, ph, lw, rowH, rx, rw, cy, ch, cw;

    public GuideScreen() { super(Component.translatable("guide.jj_giants.title")); }

    /** open on a giant ("together" for the last entry) and a tab, scrolled to the top (the picture-taking uses this) */
    public static void showPage(String key, int t, double sc) { pickKey = key; tab = t; keptScroll = sc; }

    private boolean together() { return pick >= giants.size(); }
    private @Nullable Giant giant() { return together() ? null : giants.get(pick); }
    private String[] pages() { return together() ? TOGETHER_PAGES : Giants.pages(giant()); }
    private int entries() { return giants.isEmpty() ? 0 : giants.size() + 1; }

    @Override
    protected void init() {
        giants = Giants.installed();
        pick = pickKey.equals("together") ? giants.size() : 0;
        for (int i = 0; i < giants.size(); i++) if (giants.get(i).key().equals(pickKey)) pick = i;
        if (giants.isEmpty()) pick = 0;
        else pickKey = together() ? "together" : giants.get(pick).key();
        tab = Mth.clamp(tab, 0, pages().length - 1);

        pw = Math.min(width - 8, 480);
        ph = Math.min(height - 8, 300);
        px0 = (width - pw) / 2;
        py0 = (height - ph) / 2;
        lw = Mth.clamp((int) (pw / 4.6f), 84, 116);
        rowH = entries() * 24 <= ph - 24 ? 24 : 20;
        rx = px0 + 6 + lw + 6;
        rw = px0 + pw - 6 - rx;
        cy = py0 + 18 + 18 + 6;
        ch = py0 + ph - 6 - cy;
        cw = rw - 8;

        if (!giants.isEmpty()) {
            // the tabs: each as wide as its name needs, the space left shared out
            String[] pg = pages();
            Component[] names = new Component[pg.length];
            int need = 0;
            for (int i = 0; i < pg.length; i++) { names[i] = Component.translatable("guide.jj_giants.tab." + pg[i]); need += font.width(names[i]) + 8; }
            int gap = 2, spare = Math.max(0, rw - need - gap * (pg.length - 1)), x = rx;
            for (int i = 0; i < pg.length; i++) {
                final int which = i;
                int w = font.width(names[i]) + 8 + spare / pg.length + (i < spare % pg.length ? 1 : 0);
                Component label = i == tab ? names[i].copy().withStyle(ChatFormatting.YELLOW) : names[i];
                addRenderableWidget(Button.builder(label, b -> { tab = which; keptScroll = 0; rebuild(); })
                        .bounds(x, py0 + 18, w, 18).build());
                x += w + gap;
            }
        }
        Button close = Button.builder(Component.literal("✕"), b -> onClose()).bounds(px0 + pw - 19, py0 + 2, 16, 14).build();
        close.setTooltip(Tooltip.create(Component.translatable("guide.jj_giants.close")));
        addRenderableWidget(close);

        layout();
        scroll = Mth.clamp(keptScroll, 0, Math.max(0, contentH - ch));
    }

    private void rebuild() {
        keptScroll = scroll;
        clearWidgets();
        init();
    }

    // ------------------------------------------------------------------ the page's blocks

    private interface Block {
        int height();
        void draw(GuiGraphics g, int x, int y, int mx, int my);
    }

    private void layout() {
        blocks.clear();
        if (giants.isEmpty()) {
            para(Component.translatable("guide.jj_giants.none"), TEXT, 0);
        } else if (together()) {
            lines("guide.jj_giants.together." + TOGETHER_PAGES[tab] + ".");
        } else {
            Giant g = giant();
            String page = Giants.pages(g)[tab];
            if (page.equals("moves")) movesPage(g);
            else lines("guide.jj_giants." + g.key() + "." + page + ".");
            if (page.equals("where")) { Live.ask(g.key()); shownNews = Live.news(g.key()); }
        }
        contentH = 0;
        for (Block b : blocks) contentH += b.height();
        contentH += 4;
    }

    private static boolean has(String key) { return Language.getInstance().has(key); }
    private static String raw(String key) { return Language.getInstance().getOrDefault(key); }

    /** the numbered lines prefix+1, prefix+2, ... until one is missing */
    private void lines(String prefix) {
        List<String> all = new ArrayList<>();
        for (int n = 1; n <= Giants.MAX_LINES && has(prefix + n); n++) all.add(raw(prefix + n));
        int leftW = leftWidth(all);
        for (String s : all) line(s, leftW);
    }

    /** how wide the left column of this page's tables is: as wide as its longest left cell, up to 45% */
    private int leftWidth(List<String> all) {
        int w = 40;
        for (String s : all) if (s.startsWith("| ")) w = Math.max(w, font.width(cells(s)[0]) + 6);
        return Math.min(w, (int) (cw * 0.45f));
    }

    private static String[] cells(String s) {
        String body = s.substring(2);
        int bar = body.indexOf(" | ");
        return bar < 0 ? new String[]{body.trim(), ""} : new String[]{body.substring(0, bar).trim(), body.substring(bar + 3).trim()};
    }

    private void line(String s, int leftW) {
        if (s.startsWith("# ")) heading(Component.literal(s.substring(2)), GOLD);
        else if (s.startsWith("- ")) bullet(Component.literal(s.substring(2)));
        else if (s.startsWith("| ")) { String[] c = cells(s); row(Component.literal(c[0]), Component.literal(c[1]), leftW, KEY); }
        else if (s.startsWith("@item ")) items(s.substring(6));
        else if (s.startsWith("@recipe ")) recipe(s.substring(8).trim());
        else if (s.equals("@picture")) picture(giant());
        else if (s.equals("@live")) live(giant());
        else if (s.equals("@meettable")) meetTable();
        else para(Component.literal(s), TEXT, 0);
    }

    private void gap(int h) {
        blocks.add(new Block() {
            public int height() { return h; }
            public void draw(GuiGraphics g, int x, int y, int mx, int my) {}
        });
    }

    private void para(Component c, int colour, int indent) {
        List<FormattedCharSequence> ls = font.split(c, cw - indent);
        blocks.add(new Block() {
            public int height() { return ls.size() * 10 + 3; }
            public void draw(GuiGraphics g, int x, int y, int mx, int my) {
                for (int i = 0; i < ls.size(); i++) g.drawString(font, ls.get(i), x + indent, y + i * 10, colour, false);
            }
        });
    }

    private void heading(Component c, int colour) {
        boolean first = blocks.isEmpty();
        List<FormattedCharSequence> ls = font.split(c, cw);
        blocks.add(new Block() {
            public int height() { return (first ? 0 : 5) + ls.size() * 10 + 4; }
            public void draw(GuiGraphics g, int x, int y, int mx, int my) {
                int y0 = y + (first ? 0 : 5);
                for (int i = 0; i < ls.size(); i++) g.drawString(font, ls.get(i), x, y0 + i * 10, colour, false);
                g.fill(x, y0 + ls.size() * 10, x + cw, y0 + ls.size() * 10 + 1, (colour & 0x00FFFFFF) | 0x50000000);
            }
        });
    }

    private void bullet(Component c) {
        List<FormattedCharSequence> ls = font.split(c, cw - 9);
        blocks.add(new Block() {
            public int height() { return ls.size() * 10 + 2; }
            public void draw(GuiGraphics g, int x, int y, int mx, int my) {
                g.drawString(font, "•", x + 1, y, GREY, false);
                for (int i = 0; i < ls.size(); i++) g.drawString(font, ls.get(i), x + 9, y + i * 10, TEXT, false);
            }
        });
    }

    private void row(Component left, Component right, int leftW, int leftColour) {
        List<FormattedCharSequence> a = font.split(left, leftW - 4), b = font.split(right, cw - leftW - 2);
        int n = Math.max(1, Math.max(a.size(), b.size()));
        blocks.add(new Block() {
            public int height() { return n * 10 + 4; }
            public void draw(GuiGraphics g, int x, int y, int mx, int my) {
                for (int i = 0; i < a.size(); i++) g.drawString(font, a.get(i), x, y + 1 + i * 10, leftColour, false);
                for (int i = 0; i < b.size(); i++) g.drawString(font, b.get(i), x + leftW, y + 1 + i * 10, TEXT, false);
                g.fill(x, y + n * 10 + 2, x + cw, y + n * 10 + 3, LINE);
            }
        });
    }

    private static List<ItemStack> stacks(String ids) {
        List<ItemStack> out = new ArrayList<>();
        for (String id : ids.split(",")) {
            ResourceLocation rl = ResourceLocation.tryParse(id.trim());
            if (rl == null) continue;
            BuiltInRegistries.ITEM.getOptional(rl).ifPresent(it -> out.add(new ItemStack(it)));
        }
        return out;
    }

    /** "@item a,b,c | title | text": the items' pictures, a title (or the first item's own name), and what it's for */
    private void items(String spec) {
        String[] p = spec.split(" \\| ", 3);
        List<ItemStack> st = stacks(p[0]);
        String t = p.length > 1 ? p[1].trim() : "";
        Component title = !t.isEmpty() ? Component.literal(t) : !st.isEmpty() ? st.get(0).getHoverName() : Component.empty();
        List<FormattedCharSequence> text = p.length > 2 && !p[2].isBlank() ? font.split(Component.literal(p[2].trim()), cw - 4) : List.of();
        int iconsW = st.size() * 18;
        List<FormattedCharSequence> head = font.split(title, Math.max(40, cw - iconsW - 4));
        blocks.add(new Block() {
            public int height() { return Math.max(st.isEmpty() ? 10 : 18, head.size() * 10) + text.size() * 10 + 6; }
            public void draw(GuiGraphics g, int x, int y, int mx, int my) {
                for (int i = 0; i < st.size(); i++) {
                    int ix = x + i * 18, iy = y + 1;
                    g.fill(ix, iy, ix + 17, iy + 17, 0x50000000);
                    g.renderItem(st.get(i), ix, iy);
                    if (mx >= ix && mx < ix + 17 && my >= iy && my < iy + 17 && inContent(mx, my)) hovered = st.get(i);
                }
                int hy = y + (st.isEmpty() ? 0 : Math.max(0, (18 - head.size() * 10) / 2 + 1));
                for (int i = 0; i < head.size(); i++) g.drawString(font, head.get(i), x + iconsW + (st.isEmpty() ? 0 : 4), hy + i * 10, KEY, false);
                int ty = y + Math.max(st.isEmpty() ? 10 : 18, head.size() * 10) + 2;
                for (int i = 0; i < text.size(); i++) g.drawString(font, text.get(i), x + 4, ty + i * 10, TEXT, false);
            }
        });
    }

    /** "@recipe id": the crafting grid from the game's own recipe, so it is always right */
    private void recipe(String id) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || mc.level == null) return;
        RecipeHolder<?> holder = mc.level.getRecipeManager().byKey(rl).orElse(null);
        if (holder == null) return;
        Recipe<?> r = holder.value();
        List<Ingredient> ing = r.getIngredients();
        int w = r instanceof ShapedRecipe s ? s.getWidth() : 3;
        ItemStack result = r.getResultItem(mc.level.registryAccess());
        blocks.add(new Block() {
            public int height() { return 3 * 18 + 6; }
            public void draw(GuiGraphics g, int x, int y, int mx, int my) {
                int x0 = x + 4;
                for (int sy = 0; sy < 3; sy++)
                    for (int sx = 0; sx < 3; sx++) slot(g, x0 + sx * 18, y + sy * 18);
                for (int i = 0; i < ing.size() && i < 9; i++) {
                    ItemStack[] opts = ing.get(i).getItems();
                    if (opts.length == 0) continue;
                    ItemStack st = opts[(int) (Util.getMillis() / 1000 % opts.length)];
                    int ix = x0 + (i % w) * 18 + 1, iy = y + (i / w) * 18 + 1;
                    g.renderItem(st, ix, iy);
                    if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16 && inContent(mx, my)) hovered = st;
                }
                int ax = x0 + 3 * 18 + 6;
                g.drawString(font, "→", ax, y + 23, GREY, false);
                int rxx = ax + 14;
                slot(g, rxx, y + 18);
                g.renderItem(result, rxx + 1, y + 19);
                if (mx >= rxx && mx < rxx + 18 && my >= y + 18 && my < y + 36 && inContent(mx, my)) hovered = result;
                List<FormattedCharSequence> nm = font.split(result.getHoverName(), Math.max(30, cw - (rxx + 24 - x)));
                for (int i = 0; i < nm.size() && i < 3; i++) g.drawString(font, nm.get(i), rxx + 24, y + 23 - (nm.size() - 1) * 5 + i * 10, KEY, false);
            }
        });
    }

    private static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    /** "@picture": his picture, name, role and size */
    private void picture(@Nullable Giant gi) {
        if (gi == null) return;
        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "textures/gui/giant/" + gi.key() + ".png");
        List<FormattedCharSequence> size = font.split(Component.translatable(gi.sizeKey()), cw - 60);
        blocks.add(new Block() {
            public int height() { return Math.max(54, 26 + size.size() * 10) + 4; }
            public void draw(GuiGraphics g, int x, int y, int mx, int my) {
                g.fill(x, y, x + 52, y + 52, EDGE);
                g.blit(tex, x + 2, y + 2, 48, 48, 0, 0, 64, 64, 64, 64);
                g.drawString(font, Component.translatable(gi.nameKey()), x + 58, y + 2, TITLE, false);
                g.drawString(font, Component.translatable(gi.roleKey()), x + 58, y + 13, GOLD, false);
                for (int i = 0; i < size.size(); i++) g.drawString(font, size.get(i), x + 58, y + 26 + i * 10, GREY, false);
            }
        });
    }

    /** "@live": what the server says about him right now (his own mod's words, the same as /giants where) */
    private void live(@Nullable Giant gi) {
        if (gi == null) return;
        heading(Component.translatable("guide.jj_giants.live.title"), GOLD);
        Live.News n = Live.news(gi.key());
        if (n == null || n.state() == Live.State.ASKING && n.lines().isEmpty()) para(Component.translatable("guide.jj_giants.live.asking"), GREY, 0);
        else if (n.state() == Live.State.NO_SERVER) para(Component.translatable("guide.jj_giants.live.no_server"), GREY, 0);
        else if (n.lines().isEmpty()) para(Component.translatable("guide.jj_giants.live.nothing"), GREY, 0);
        else for (String s : n.lines()) bullet(Component.literal(s));
    }

    /** "@meettable": who fights whom when they meet, for the giants installed */
    private void meetTable() {
        int n = giants.size();
        if (n < 2) { para(Component.translatable("guide.jj_giants.together.one"), GREY, 0); return; }
        int labelW = 0;
        for (Giant g : giants) labelW = Math.max(labelW, font.width(Component.translatable("guide.jj_giants." + g.key() + ".short")) + 6);
        int colW = Math.max(24, (cw - labelW) / n);
        Component fight = Component.translatable("guide.jj_giants.together.fight"), away = Component.translatable("guide.jj_giants.together.away");
        if (font.width(away) > colW - 2) away = Component.translatable("guide.jj_giants.together.away_short");
        final Component fightC = fight, awayC = away;
        final int lab = labelW;
        // the column heads are the names when they fit, or else the giants' pictures
        boolean names = true;
        for (Giant g : giants) names &= font.width(Component.translatable("guide.jj_giants." + g.key() + ".short")) <= colW - 4;
        final boolean byName = names;
        final int headH = byName ? 13 : 18;
        blocks.add(new Block() {
            public int height() { return headH + n * 13 + 4; }
            public void draw(GuiGraphics g, int x, int y, int mx, int my) {
                for (int c = 0; c < n; c++) {
                    Giant gc = giants.get(c);
                    int hx = x + lab + c * colW + colW / 2;
                    if (byName) {
                        Component h = Component.translatable("guide.jj_giants." + gc.key() + ".short");
                        g.drawString(font, h, hx - font.width(h) / 2, y + 2, KEY, false);
                    } else g.blit(ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "textures/gui/giant/" + gc.key() + ".png"),
                            hx - 8, y, 16, 16, 0, 0, 64, 64, 64, 64);
                }
                for (int r = 0; r < n; r++) {
                    int ry = y + headH + r * 13;
                    g.fill(x, ry - 1, x + lab + n * colW, ry, LINE);
                    g.drawString(font, Component.translatable("guide.jj_giants." + giants.get(r).key() + ".short"), x, ry + 2, KEY, false);
                    for (int c = 0; c < n; c++) {
                        int cx = x + lab + c * colW;
                        if (r == c) { g.drawString(font, "–", cx + colW / 2 - 2, ry + 2, GREY, false); continue; }
                        boolean f = Giants.fight(giants.get(r), giants.get(c));
                        g.fill(cx + 1, ry, cx + colW - 1, ry + 12, f ? 0x60A02020 : 0x602060A0);
                        Component t = f ? fightC : awayC;
                        g.drawString(font, t, cx + colW / 2 - font.width(t) / 2, ry + 2, f ? 0xFFFF8080 : 0xFF80C0FF, false);
                    }
                }
            }
        });
    }

    /** made from his list of moves: a part for light, medium and heavy, each move's name and what it does */
    private void movesPage(Giant g) {
        List<String> intro = new ArrayList<>();
        for (int n = 1; n <= Giants.MAX_LINES && has(g.lineKey("moves", n)); n++) intro.add(raw(g.lineKey("moves", n)));
        int leftW = 40;
        for (Giant.Move m : g.moves()) leftW = Math.max(leftW, font.width(moveName(g, m)) + 6);
        leftW = Math.min(leftW, (int) (cw * 0.4f));
        for (String s : intro) line(s, leftW);
        int[] colours = {0xFFFFFFFF, 0xFFFFAA00, 0xFFFF5555, 0xFF55FFFF};
        for (int t = 0; t < Giants.TIER_NAMES.length; t++) {
            final int tier = t;
            if (g.moves().stream().noneMatch(m -> m.tier() == tier)) continue;
            heading(Component.translatable("guide.jj_giants.tier." + Giants.TIER_NAMES[t]), colours[t]);
            para(Component.translatable("guide.jj_giants.tier_note." + Giants.TIER_NAMES[t]), GREY, 0);
            for (Giant.Move m : g.moves()) if (m.tier() == t) row(moveName(g, m), moveWhat(g, m), leftW, colours[t]);
        }
    }

    /** his mod's own name for the move when it has one, else the guide's copy */
    static Component moveName(Giant g, Giant.Move m) {
        return Component.translatable(has(m.nameKey()) ? m.nameKey() : g.moveNameCopy(m));
    }

    static Component moveWhat(Giant g, Giant.Move m) {
        return Component.translatable(has(m.whatKey()) ? m.whatKey() : g.moveWhatCopy(m));
    }

    // ------------------------------------------------------------------ drawing

    private boolean inContent(int mx, int my) { return mx >= rx && mx < rx + rw && my >= cy && my < cy + ch; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        super.renderBackground(g, mx, my, partial);
        g.fill(px0, py0, px0 + pw, py0 + ph, PANEL);
        g.renderOutline(px0, py0, pw, ph, EDGE);
        g.drawCenteredString(font, title, px0 + pw / 2, py0 + 5, TITLE);
        g.fill(rx - 6, py0 + 18, rx - 5, py0 + ph - 6, EDGE);
        drawList(g, mx, my);
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        int x = px0 + 6, y0 = py0 + 18;
        for (int i = 0; i < entries(); i++) {
            int y = y0 + i * rowH;
            boolean sel = i == pick, over = mx >= x && mx < x + lw && my >= y && my < y + rowH - 2;
            if (sel) { g.fill(x, y, x + lw, y + rowH - 2, 0xFF3A3020); g.renderOutline(x, y, lw, rowH - 2, TITLE); }
            else if (over) g.fill(x, y, x + lw, y + rowH - 2, 0x30FFFFFF);
            int pic = rowH - 6;
            Component name;
            if (i < giants.size()) {
                Giant gi = giants.get(i);
                g.blit(ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "textures/gui/giant/" + gi.key() + ".png"), x + 2, y + 2, pic, pic, 0, 0, 64, 64, 64, 64);
                name = Component.translatable(gi.nameKey());
            } else {
                g.renderItem(new ItemStack(GiantsGuideMod.GUIDE), x + 2 + (pic - 16) / 2, y + 2 + (pic - 16) / 2);
                name = Component.translatable("guide.jj_giants.together");
            }
            List<FormattedCharSequence> ls = font.split(name, lw - pic - 8);
            int ty = y + (rowH - 2 - Math.min(2, ls.size()) * 9) / 2;
            for (int k = 0; k < ls.size() && k < 2; k++) g.drawString(font, ls.get(k), x + pic + 6, ty + k * 9, sel ? KEY : TEXT, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        // the live news came in (or changed): lay the page out again where it is
        if (!together() && !giants.isEmpty() && Giants.pages(giant())[tab].equals("where")) {
            Live.News n = Live.news(giant().key());
            if (n != shownNews) {
                boolean atEnd = contentH > ch && scroll >= contentH - ch - 1;
                keptScroll = scroll;
                layout();
                scroll = Mth.clamp(atEnd ? contentH : keptScroll, 0, Math.max(0, contentH - ch));
            }
        }
        super.render(g, mx, my, partial);
        hovered = null;
        g.enableScissor(rx, cy, rx + rw, cy + ch);
        int y = cy - (int) scroll;
        for (Block b : blocks) {
            int h = b.height();
            if (y + h >= cy && y <= cy + ch) b.draw(g, rx, y, mx, my);
            y += h;
        }
        g.disableScissor();
        if (contentH > ch) {
            int bx = rx + rw - 3;
            g.fill(bx, cy, bx + 3, cy + ch, 0x40000000);
            int th = Math.max(12, ch * ch / contentH);
            int ty = cy + (int) ((ch - th) * (scroll / (contentH - ch)));
            g.fill(bx, ty, bx + 3, ty + th, 0xFFA09080);
        }
        if (hovered != null && !hovered.isEmpty()) g.renderTooltip(font, hovered, mx, my);
    }

    // ------------------------------------------------------------------ mouse

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = px0 + 6, y0 = py0 + 18;
        if (button == 0 && mx >= x && mx < x + lw && my >= y0) {
            int i = (int) ((my - y0) / rowH);
            if (i < entries() && my - y0 - i * rowH < rowH - 2) {
                if (i != pick) {
                    pick = i;
                    pickKey = together() ? "together" : giants.get(i).key();
                    tab = Mth.clamp(tab, 0, pages().length - 1);
                    scroll = 0;
                    rebuild();
                }
                return true;
            }
        }
        if (button == 0 && contentH > ch && mx >= rx + rw - 5 && mx < rx + rw && my >= cy && my < cy + ch) {
            dragging = true;
            dragTo(my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) { dragTo(my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    private void dragTo(double my) {
        double f = Mth.clamp((my - cy) / Math.max(1, ch), 0, 1);
        scroll = f * Math.max(0, contentH - ch);
        keptScroll = scroll;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Mth.clamp(scroll - sy * 20, 0, Math.max(0, contentH - ch));
        keptScroll = scroll;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 264 || key == 265 || key == 266 || key == 267) { // down, up, page down, page up
            double step = key == 264 ? 20 : key == 265 ? -20 : key == 267 ? ch - 20 : -(ch - 20);
            scroll = Mth.clamp(scroll + step, 0, Math.max(0, contentH - ch));
            keptScroll = scroll;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}
