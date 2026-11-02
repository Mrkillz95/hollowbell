package net.jj.giants.client.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminBookItem;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminInsight;
import net.jj.giants.admin.AdminSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Giants Admin Book, open (ADMIN_BOOK.md, Part 2A). Drawn like the Guide and the Performance screen: a title and
 * close button on top; down the left, Home, All giants and one page per giant; on the right a search box, Simple or
 * Advanced, and where changes are saved (this world, or all worlds: the server default), then the page. Home has the
 * preset, how dangerous the giants are now, settings that clash and the giants; each giant's page is made from what
 * his mod reports, by category, one line a setting (each move on one line). Every change is a request the server
 * checks and does exactly as /giants admin set does; the pages come back fresh whenever anyone changes anything.
 */
public class AdminBookScreen extends Screen {
    static final int GOLD = 0xFFE0B050,
            TITLE = 0xFFE9C9BC,
            TEXT = 0xFFD8D8D8,
            KEY = 0xFFFFE080,
            GREY = 0xFF9A9A9A,
            DIM = 0xFF6E6E6E,
            ORANGE = 0xFFE0A060,
            BLUE = 0xFF88B8E8,
            WARN = 0xFFFF7060,
            GOOD = 0xFF80E080,
            PANEL = 0xE8141210,
            EDGE = 0xFF6B5A44,
            LINE = 0x30FFFFFF;
    private static final int SET_H = 26, SIZE_H = 40, MOVE_H = 20, HEAD_H = 20;
    /** most lines a search shows */
    private static final int MOST_FOUND = 80;
    public static final String HOME = "home";
    /** the size settings that get a picture beside a player */
    private static final List<String> SIZES = List.of("worldScale", "spawnEggScale", "smallEggScale", "all.size");

    /** kept while the game runs: the page, where it was scrolled, what was searched */
    private static String keptPage = HOME;
    private static double keptScroll;
    private static String keptQuery = "";
    /** (the picture-taking) open on this walkthrough page, or the confirm for this setting */
    private static int startIntro = -1;
    private static @Nullable String[] startConfirm;

    private String pageKey = HOME;
    private boolean advanced, allWorlds;
    private int seenVersion = -1;
    private long seenSaid;
    private int ticks;
    private boolean mouseDown;

    // the list
    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private int contentH;
    private boolean dragging;
    /** values sent and not yet confirmed by the server's pages: page + "/" + id */
    private final Map<String, Pend> pending = new HashMap<>();

    private record Pend(JsonElement v, long at) {}

    // the walkthrough or a question: drawn over everything, only its buttons answer
    private @Nullable Modal modal;

    private EditBox search;
    private Button modeButton, scopeButton, headButton;
    private Component status = Component.empty();
    private int statusColour = GREY;
    private long statusAt;

    // where things go (set in init)
    private int px0, py0, pw, ph, navX, navW, navY, navRowH, lx, lw, toolY, headY, ly0, ly1;

    public AdminBookScreen() {
        super(Component.translatable("admin.jj_giants.book.title"));
    }

    /** open next time on this page, scrolled this far, with this search (the picture-taking uses this) */
    public static void showAt(String page, double scroll, String query) {
        keptPage = page == null || page.isEmpty() ? HOME : page;
        keptScroll = scroll;
        keptQuery = query == null ? "" : query;
        wantScroll = scroll;
    }

    /** where to scroll once the pages are there (they may come a moment after the book opens) */
    private static double wantScroll = -1;
    private static boolean startAllWorlds;

    /** (the picture-taking) saving to all worlds next time */
    public static void scopeAt(boolean all) {
        startAllWorlds = all;
    }

    /** (the picture-taking) the walkthrough at this page next time (-1: only if not seen yet) */
    public static void introAt(int page) {
        startIntro = page;
    }

    /** (the picture-taking) the "are you sure" for changing this setting next time */
    public static void confirmAt(String giant, String id) {
        startConfirm = new String[] {giant, id};
    }

    /**
     * (the picture-taking) a change as if made with the setting's control on the open book: the same path (asked
     * about first when it only reaches new land or a new world, then sent to the server)
     */
    public static boolean testChange(String giant, String id, String text) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof AdminBookScreen b)) return false;
        AdminBookClient.Page p = AdminBookClient.page(giant);
        AdminSetting s = p == null ? null : p.setting(id);
        if (s == null) return false;
        try {
            b.commit(s, s.parse(text));
            return true;
        } catch (AdminSetting.BadValue e) {
            return false;
        }
    }

    /** (the picture-taking) presses the open question's first button ("Change it", "Next") */
    public static boolean testPress() {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof AdminBookScreen b) || b.modal == null)
            return false;
        // (a question's yes is its first button; the walkthrough's Next its last)
        List<Button> bs = b.modal.buttons;
        bs.get(b.modal instanceof Intro ? bs.size() - 1 : 0).onPress();
        return true;
    }

    static MutableComponent tr(String key, Object... args) {
        return Component.translatable("admin.jj_giants.book." + key, args);
    }

    // the settings' words

    static Component name(AdminSetting s) {
        return s.key().isEmpty() ? Component.literal(s.name()) : Component.translatableWithFallback(s.key(), s.name());
    }

    static Component what(AdminSetting s) {
        if (s.what().isEmpty() && s.key().isEmpty()) return Component.empty();
        return s.key().isEmpty()
                ? Component.literal(s.what())
                : Component.translatableWithFallback(s.key() + ".what", s.what());
    }

    private static boolean shared(AdminSetting s) {
        return s.giant().equals(AdminCatalog.ALL);
    }

    private static boolean move(AdminSetting s) {
        return s.id().startsWith("move.");
    }

    /** a value as the book shows it: on/off, x2 for multipliers, 0.5-1.5 for a range */
    static String show(AdminSetting s, JsonElement v) {
        boolean times = shared(s) || s.id().endsWith(".cooldown") || s.id().endsWith(".wind");
        return (times && s.type() != AdminSetting.Type.BOOL ? "x" : "") + AdminSetting.text(v);
    }

    /** the value shown now: one sent and not answered yet, else the server's (the world's, or the server default) */
    private JsonElement shown(AdminSetting s) {
        Pend p = pending.get(s.giant() + "/" + s.id());
        if (p != null) return p.v();
        // (saving to all worlds, a world-only one still shows the world's value, greyed)
        return allWorlds && editable(s) ? s.def() : s.value();
    }

    /** changed: this world has its own (or, saving to all worlds, the server default isn't what the mod ships) */
    private boolean changed(AdminSetting s) {
        return allWorlds && editable(s) ? !AdminSetting.same(s.def(), s.jar()) : s.set();
    }

    /** whether it can be changed where changes go now (the all-giants and move ones only have world values) */
    private boolean editable(AdminSetting s) {
        return !allWorlds || !shared(s) && !move(s);
    }

    private String giantName(String key) {
        if (key.equals(AdminCatalog.ALL)) return tr("all").getString();
        AdminBookClient.Page p = AdminBookClient.page(key);
        if (p != null && !p.name().isEmpty()) return p.name();
        JsonObject h = AdminBookClient.home();
        if (h != null && h.get("giants") instanceof JsonArray a)
            for (JsonElement e : a)
                if (e.isJsonObject() && key.equals(AdminBookClient.AdminJsonBridge.str(e.getAsJsonObject(), "key")))
                    return AdminBookClient.AdminJsonBridge.str(e.getAsJsonObject(), "name");
        return key;
    }

    // the pages down the left

    /** Home, All giants, then each giant the server has */
    private List<String> navKeys() {
        List<String> out = new ArrayList<>(List.of(HOME, AdminCatalog.ALL));
        JsonObject h = AdminBookClient.home();
        if (h != null && h.get("giants") instanceof JsonArray a)
            for (JsonElement e : a)
                if (e.isJsonObject()) out.add(AdminBookClient.AdminJsonBridge.str(e.getAsJsonObject(), "key"));
        return out;
    }

    private @Nullable JsonObject homeGiant(String key) {
        JsonObject h = AdminBookClient.home();
        if (h != null && h.get("giants") instanceof JsonArray a)
            for (JsonElement e : a)
                if (e.isJsonObject() && key.equals(AdminBookClient.AdminJsonBridge.str(e.getAsJsonObject(), "key")))
                    return e.getAsJsonObject();
        return null;
    }

    /** how many settings the world set on a page (from Home's numbers) */
    private int setCount(String key) {
        JsonObject h = AdminBookClient.home();
        if (h == null) return 0;
        if (key.equals(AdminCatalog.ALL)) return AdminBookClient.AdminJsonBridge.num(h, "allSet", 0);
        JsonObject g = homeGiant(key);
        return g == null ? 0 : AdminBookClient.AdminJsonBridge.num(g, "set", 0);
    }

    // layout

    @Override
    protected void init() {
        advanced = AdminPrefs.advanced();
        if (startAllWorlds) {
            allWorlds = true;
            startAllWorlds = false;
        }
        pageKey = keptPage;
        seenVersion = AdminBookClient.version();
        seenSaid = AdminBookClient.saidAt();

        pw = Math.min(width - 8, 720);
        ph = Math.min(height - 8, 460);
        px0 = (width - pw) / 2;
        py0 = (height - ph) / 2;
        navX = px0 + 6;
        int names = 0;
        for (String k : navKeys())
            names = Math.max(names, font.width(k.equals(HOME) ? tr("home").getString() : giantName(k)));
        // (room for the picture, the name and a count)
        navW = Mth.clamp(Math.max(pw / 5, names + 34), 76, Math.max(76, Math.min(120, pw / 4)));
        navY = py0 + 20;
        int entries = Math.max(2, navKeys().size());
        navRowH = entries * 20 <= ph - 26 ? 20 : entries * 17 <= ph - 26 ? 17 : 14;
        lx = navX + navW + 8;
        lw = px0 + pw - 6 - 6 - lx;
        toolY = py0 + 20;
        headY = toolY + 20;
        ly0 = headY + 24;
        ly1 = py0 + ph - 14;

        // the tool bar: search, Simple/Advanced, where changes go
        Component scopeWords = scopeLabel();
        int scopeW = Math.max(font.width(tr("scope.world.button")), font.width(tr("scope.all.button"))) + 12;
        int modeW = Math.max(font.width(tr("mode.simple")), font.width(tr("mode.advanced"))) + 12;
        int searchW = Math.max(70, lw - scopeW - modeW - 6);
        search = new EditBox(font, lx, toolY, searchW, 16, tr("search"));
        search.setHint(tr("search").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(48);
        search.setValue(keptQuery);
        search.setResponder(q -> {
            if (q.equals(keptQuery)) return;
            keptQuery = q;
            scroll = 0;
            build();
        });
        addRenderableWidget(search);
        modeButton = addRenderableWidget(Button.builder(modeLabel(), b -> {
                    advanced = !advanced;
                    AdminPrefs.advanced(advanced);
                    b.setMessage(modeLabel());
                    b.setTooltip(Tooltip.create(tr(advanced ? "mode.advanced.tip" : "mode.simple.tip")));
                    scroll = 0;
                    build();
                })
                .bounds(lx + searchW + 3, toolY, modeW, 16)
                .build());
        modeButton.setTooltip(Tooltip.create(tr(advanced ? "mode.advanced.tip" : "mode.simple.tip")));
        scopeButton = addRenderableWidget(Button.builder(scopeWords, b -> {
                    allWorlds = !allWorlds;
                    pending.clear();
                    b.setMessage(scopeLabel());
                    b.setTooltip(Tooltip.create(tr(allWorlds ? "scope.all.tip" : "scope.world.tip")));
                    build();
                })
                .bounds(lx + lw - scopeW, toolY, scopeW, 16)
                .build());
        scopeButton.setTooltip(Tooltip.create(tr(allWorlds ? "scope.all.tip" : "scope.world.tip")));

        // the page's own button (reset this giant, or on Home every giant)
        headButton = addRenderableWidget(Button.builder(Component.empty(), b -> headAction())
                .bounds(0, headY + 2, 10, 16)
                .build());

        Button close = Button.builder(Component.literal("✕"), b -> onClose())
                .bounds(px0 + pw - 19, py0 + 2, 16, 14)
                .build();
        close.setTooltip(Tooltip.create(Component.translatable("guide.jj_giants.close")));
        addRenderableWidget(close);
        Button help = Button.builder(Component.literal("?"), b -> openIntro(0))
                .bounds(px0 + pw - 19 - 18, py0 + 2, 16, 14)
                .build();
        help.setTooltip(Tooltip.create(tr("help.tip")));
        addRenderableWidget(help);

        build();
        scroll = Mth.clamp(keptScroll, 0, Math.max(0, contentH - (ly1 - ly0)));
        place();

        if (modal instanceof Intro in) in.setup();
        else if (modal != null) modal.layout(modal.buttonsW());
        if (startIntro >= 0) {
            openIntro(startIntro);
            startIntro = -1;
        } else if (!AdminPrefs.seenIntro() && modal == null) openIntro(0);
        if (startConfirm != null) {
            AdminBookClient.Page p = AdminBookClient.page(startConfirm[0]);
            AdminSetting s = p == null ? null : p.setting(startConfirm[1]);
            if (s != null) askCreation(s, s.value(), () -> {});
            startConfirm = null;
        }
    }

    private Component modeLabel() {
        return tr(advanced ? "mode.advanced" : "mode.simple");
    }

    private Component scopeLabel() {
        return allWorlds
                ? tr("scope.all.button").withStyle(ChatFormatting.GOLD)
                : tr("scope.world.button");
    }

    private void headAction() {
        if (pageKey.equals(HOME)) {
            confirm(tr("reset_all.title"), tr("reset_all.body"), tr("reset_all.yes"),
                    () -> AdminBookClient.reset("everything", null, false));
            return;
        }
        String key = pageKey;
        int n = setCount(key);
        confirm(tr("reset_page.title", giantName(key)),
                key.equals(AdminCatalog.ALL) ? tr("reset_page.all") : tr("reset_page.body", giantName(key), n),
                tr("reset_page.yes"), () -> AdminBookClient.reset(key, null, false));
    }

    // the rows

    /** a widget in a row: so far in from the row's right edge (or left), so far down */
    private record Placed(AbstractWidget w, int dx, int dy, boolean right) {}

    private abstract class Row {
        int y, h;
        final List<Placed> ws = new ArrayList<>();
        /** its ↺, if it has one */
        @Nullable Button reset;

        abstract void draw(GuiGraphics g, int x, int y, int w, int mx, int my);

        <T extends AbstractWidget> T add(T w, int dx, int dy, boolean right) {
            ws.add(new Placed(addRenderableWidget(w), dx, dy, right));
            return w;
        }
    }

    /** a category's (or a giant's, in a search) heading */
    private final class Head extends Row {
        final Component title;
        final @Nullable String giant;
        final List<FormattedCharSequence> note;

        Head(Component title, @Nullable Component note, @Nullable String giant) {
            this.title = title;
            this.giant = giant;
            this.note = note == null ? List.of() : font.split(note, lw);
            h = HEAD_H + this.note.size() * 10;
        }

        @Override
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
            int tx = x;
            if (giant != null) {
                icon(g, giant, x, y + 4, 12);
                tx += 16;
            }
            g.drawString(font, title, tx, y + 7, GOLD, false);
            g.fill(x, y + HEAD_H - 3, x + w, y + HEAD_H - 2, LINE);
            for (int i = 0; i < note.size(); i++) g.drawString(font, note.get(i), x, y + HEAD_H + i * 10, GREY, false);
        }
    }

    /** a few lines of words */
    private final class Note extends Row {
        final List<FormattedCharSequence> lines;
        final int colour;

        Note(Component text, int colour) {
            this.lines = font.split(text, lw);
            this.colour = colour;
            h = lines.size() * 10 + 6;
        }

        @Override
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x, y + 3 + i * 10, colour, false);
        }
    }

    /** anything else: Home's parts */
    private final class Block extends Row {
        final Drawer d;

        Block(int h, Drawer d) {
            this.h = h;
            this.d = d;
        }

        @Override
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
            d.draw(g, x, y, w, mx, my);
        }
    }

    private interface Drawer {
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my);
    }

    private int controlW() {
        return Mth.clamp(lw * 3 / 10, 72, 132);
    }

    /** one setting: its name and notes on the left; its control and ↺ on the right */
    private final class SetRow extends Row {
        final AdminSetting s;
        final boolean size;

        SetRow(AdminSetting s) {
            this.s = s;
            this.size = SIZES.contains(s.id());
            h = size ? SIZE_H : SET_H;
            int cw = controlW(), dy = (h - 16) / 2;
            List<AbstractWidget> c = controls(s, cw);
            if (c.size() == 2) {
                // a range: the low end on the left, the high end on the right
                add(c.get(0), 18 + c.get(1).getWidth() + 2, dy, true);
                add(c.get(1), 18, dy, true);
            } else for (AbstractWidget w : c) add(w, 18, dy, true);
            reset = add(resetButton(List.of(s)), 0, dy, true);
        }

        int textW() {
            return lw - controlW() - 22;
        }

        @Override
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
            boolean ch = changed(s), can = editable(s), overPicture = false;
            if (ch) g.fill(x - 4, y + 2, x - 2, y + h - 2, GOLD);
            int tw = textW();
            // line 1: the name, "?", and when it takes effect
            Component marker = marker(s);
            String full = name(s).getString();
            if (!marker.getString().isEmpty() && font.width(full) + 12 + font.width(marker) + 4 > tw)
                marker = shortMarker(s);
            int markerW = marker.getString().isEmpty() ? 0 : font.width(marker) + 4;
            String nm = cut(full, Math.max(30, tw - 12 - markerW));
            int nx = g.drawString(font, nm, x, y + 3, !can ? GREY : ch ? GOLD : TEXT, false);
            int qx = nx + 3;
            boolean overQ = mx >= qx - 1 && mx < qx + 8 && my >= y + 2 && my < y + 12;
            g.fill(qx - 1, y + 2, qx + 7, y + 12, overQ ? 0x80FFFFFF : 0x40FFFFFF);
            g.drawString(font, "?", qx + 1, y + 3, overQ ? 0xFF202020 : BLUE, false);
            if (markerW > 0) g.drawString(font, marker, qx + 12, y + 3, markerColour(s), false);
            // line 2: the server default (or what the world keeps), then what it does
            MutableComponent l2 = line2(s, false);
            // (a size's lines 2 and 3 leave room for its picture)
            int lineW = size ? tw - previewW() - 6 : tw;
            if (font.width(l2) > lineW) l2 = line2(s, true);
            String l2s = cut(l2.getString(), lineW);
            int ux = l2s.equals(l2.getString()) ? g.drawString(font, l2, x, y + 14, GREY, false)
                    : g.drawString(font, l2s, x, y + 14, GREY, false);
            String rest = what(s).getString();
            if (!rest.isEmpty() && ux + 12 < x + lineW)
                g.drawString(font, cut("· " + rest, x + lineW - ux - 4), ux + 4, y + 14, DIM, false);
            if (size) {
                double v = number(shown(s));
                String key = s.giant();
                double tall = (shared(s) ? 200 : body(key)[0]) * v;
                g.drawString(font, cut(tr(shared(s) ? "size.all" : "size.tall", AdminSetting.plain(Math.round(tall)))
                        .getString(), lineW), x, y + 25, GREY, false);
                int bx = x + tw - previewW();
                preview(g, bx, y + 13, previewW(), h - 15, shared(s) ? "" : key, v);
                if (mx >= bx && mx < bx + previewW() && my >= Math.max(y + 13, ly0) && my < Math.min(y + h - 2, ly1)) {
                    setTooltipForNextRenderPass(font.split(tr(shared(s) ? "size.tip.all" : "size.tip",
                            AdminSetting.plain(Math.round(tall))), 220));
                    overPicture = true;
                }
            }
            if (ch || pending.containsKey(s.giant() + "/" + s.id()))
                for (Placed p : ws)
                    if (p.w() != reset && p.w().visible)
                        g.renderOutline(p.w().getX() - 1, p.w().getY() - 1, p.w().getWidth() + 2, 18,
                                pending.containsKey(s.giant() + "/" + s.id()) ? GREY : GOLD);
            if (!overPicture && mx >= x - 4 && mx < x + tw && my >= Math.max(y, ly0) && my < Math.min(y + h, ly1))
                setTooltipForNextRenderPass(tip(s));
        }
    }

    private int previewW() {
        return Mth.clamp((lw - controlW() - 22) / 4, 40, 64);
    }

    /** what line 2 says: where the value comes from */
    private MutableComponent line2(AdminSetting s, boolean narrow) {
        String n = narrow ? ".short" : "";
        if (allWorlds) {
            if (shared(s) || move(s)) return tr("line.world_only" + n);
            MutableComponent c = tr("line.ships" + n, show(s, s.jar()));
            if (s.set()) c.append(tr("line.keeps" + n, show(s, s.value())).withStyle(st -> st.withColor(ORANGE)));
            return c;
        }
        if (s.set())
            return tr("line.this_world" + n).withStyle(st -> st.withColor(GOLD))
                    .append(tr("line.default_after" + n, show(s, s.def())).withStyle(st -> st.withColor(GREY)));
        return tr("line.default", show(s, s.def()));
    }

    /** the marker for when a change takes effect */
    static Component marker(AdminSetting s) {
        return switch (s.when()) {
            case AdminSetting.NEW_SPAWNS -> tr("when.newSpawns");
            case AdminSetting.NEW_LAND -> tr("when.newLand");
            case AdminSetting.WORLD_CREATION -> tr("when.worldCreation");
            case AdminSetting.RESTART -> tr("when.restart");
            default -> Component.empty();
        };
    }

    /** the same in a word or two, for narrow pages */
    static Component shortMarker(AdminSetting s) {
        return switch (s.when()) {
            case AdminSetting.NEW_SPAWNS -> tr("when.short.newSpawns");
            case AdminSetting.NEW_LAND -> tr("when.short.newLand");
            case AdminSetting.WORLD_CREATION -> tr("when.short.worldCreation");
            case AdminSetting.RESTART -> tr("when.short.restart");
            default -> Component.empty();
        };
    }

    static int markerColour(AdminSetting s) {
        return switch (s.when()) {
            case AdminSetting.NEW_SPAWNS -> BLUE;
            case AdminSetting.NEW_LAND, AdminSetting.WORLD_CREATION -> ORANGE;
            default -> GREY;
        };
    }

    /** everything about a setting, on hover */
    private List<FormattedCharSequence> tip(AdminSetting s) {
        List<FormattedCharSequence> t = new ArrayList<>();
        t.add(name(s).copy().withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        if (!what(s).getString().isEmpty()) t.addAll(font.split(what(s), 260));
        t.addAll(font.split(tr("tip.takes", s.type() == AdminSetting.Type.BOOL ? tr("tip.onoff").getString()
                : (shared(s) || move(s) && s.type() != AdminSetting.Type.BOOL ? "x" : "") + s.rangeText())
                .withStyle(ChatFormatting.GRAY), 260));
        t.addAll(font.split(tr("tip.values", show(s, s.value()), show(s, s.def()), show(s, s.jar()))
                .withStyle(ChatFormatting.GRAY), 260));
        t.addAll(font.split(tr(s.set() ? "tip.set" : "tip.unset").withStyle(s.set() ? ChatFormatting.GOLD
                : ChatFormatting.GRAY), 260));
        if (!s.whenNote().isEmpty())
            t.addAll(font.split(Component.literal(s.whenNote()).withStyle(s.creationOnly() ? ChatFormatting.GOLD
                    : ChatFormatting.AQUA), 260));
        if (!editable(s)) t.addAll(font.split(tr("tip.world_only").withStyle(ChatFormatting.RED), 260));
        t.add(Component.literal(s.id()).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        return t;
    }

    /** one move: its name, then on/off, its wait and its wind, side by side */
    private final class MoveRow extends Row {
        final String name;
        final List<AdminSetting> parts;

        MoveRow(String name, List<AdminSetting> parts) {
            this.name = name;
            this.parts = parts;
            h = MOVE_H;
            int total = moveW(), onW = 30;
            int sliders = (int) parts.stream().filter(p -> p.type() != AdminSetting.Type.BOOL).count();
            int sw = sliders == 0 ? 0 : (total - onW - 2 - 2 * (sliders - 1)) / sliders;
            // from the right: ↺, then the parts right to left
            reset = add(resetButton(parts), 0, 2, true);
            int dx = 18;
            for (int i = parts.size() - 1; i >= 0; i--) {
                AdminSetting s = parts.get(i);
                int w = s.type() == AdminSetting.Type.BOOL ? onW : sw;
                List<AbstractWidget> c = controls(s, w);
                if (c.size() != 1) continue;
                add(c.get(0), dx, 2, true);
                dx += w + 2;
            }
        }

        @Override
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
            boolean any = false;
            for (AdminSetting s : parts) any |= changed(s);
            if (any) g.fill(x - 4, y + 2, x - 2, y + h - 2, GOLD);
            int nameW = lw - moveW() - 22;
            g.drawString(font, cut(name, nameW), x, y + 6, any ? GOLD : TEXT, false);
            for (Placed p : ws) {
                AdminSetting s = partOf(p.w());
                if (s == null || !p.w().visible) continue;
                boolean pend = pending.containsKey(s.giant() + "/" + s.id());
                if (changed(s) || pend)
                    g.renderOutline(p.w().getX() - 1, p.w().getY() - 1, p.w().getWidth() + 2, 18, pend ? GREY : GOLD);
                if (p.w().isMouseOver(mx, my) && my >= ly0 && my < ly1) setTooltipForNextRenderPass(tip(s));
            }
            if (mx >= x && mx < x + nameW && my >= Math.max(y, ly0) && my < Math.min(y + h, ly1)) {
                List<FormattedCharSequence> t = new ArrayList<>();
                t.add(Component.literal(name).withStyle(ChatFormatting.YELLOW).getVisualOrderText());
                for (AdminSetting s : parts)
                    t.addAll(font.split(Component.literal(name(s).getString() + ": " + show(s, s.value()) + " ("
                                    + tr("tip.default_short", show(s, s.def())).getString() + ")")
                            .withStyle(changed(s) ? ChatFormatting.GOLD : ChatFormatting.GRAY), 260));
                setTooltipForNextRenderPass(t);
            }
        }

        @Nullable AdminSetting partOf(AbstractWidget w) {
            for (int i = 0; i < ws.size(); i++)
                if (ws.get(i).w() == w) {
                    // (the first is ↺; the rest are the parts, right to left)
                    int k = parts.size() - i;
                    return i == 0 || k < 0 || k >= parts.size() ? null : parts.get(k);
                }
            return null;
        }
    }

    private int moveW() {
        return Mth.clamp(lw - 110, 130, 220);
    }

    /** a move's name from its rows' names ("Claw snip: on" → "Claw snip") */
    private static String moveName(AdminSetting s) {
        String n = name(s).getString();
        int c = n.lastIndexOf(": ");
        return c > 0 ? n.substring(0, c) : n;
    }

    // the controls

    private List<AbstractWidget> controls(AdminSetting s, int cw) {
        List<AbstractWidget> out = new ArrayList<>();
        boolean can = editable(s);
        switch (s.type()) {
            case BOOL -> {
                Button b = Button.builder(onOff(shown(s)), x -> {
                            boolean now = !shown(s).getAsBoolean();
                            commit(s, new JsonPrimitive(now));
                        })
                        .bounds(0, 0, cw, 16)
                        .build();
                out.add(b);
            }
            case CHOICE -> {
                Button b = Button.builder(Component.literal(AdminSetting.text(shown(s))), x -> {
                            int i = s.options().indexOf(shown(s).getAsString());
                            commit(s, new JsonPrimitive(s.options().get((i + 1) % s.options().size())));
                        })
                        .bounds(0, 0, cw, 16)
                        .build();
                out.add(b);
            }
            case RANGE -> {
                JsonElement v = shown(s);
                double lo = v.getAsJsonArray().get(0).getAsDouble(), hi = v.getAsJsonArray().get(1).getAsDouble();
                int half = (cw - 2) / 2;
                Slider[] two = new Slider[2];
                two[0] = new Slider(half, s, lo, "", d -> {
                    double h2 = two[1].current();
                    commit(s, range(Math.min(d, h2), Math.max(d, h2)));
                });
                two[1] = new Slider(half, s, hi, "", d -> {
                    double l2 = two[0].current();
                    commit(s, range(Math.min(l2, d), Math.max(l2, d)));
                });
                out.add(two[0]);
                out.add(two[1]);
            }
            default -> {
                if (boxed(s)) out.add(new NumBox(cw, s));
                else {
                    String prefix = move(s) ? tr(s.id().endsWith(".wind") ? "move.wind" : "move.wait").getString() + " "
                            : "";
                    out.add(new Slider(cw, s, number(shown(s)), prefix, d -> commit(s, numberValue(s, d))));
                }
            }
        }
        for (AbstractWidget w : out) {
            w.active = can;
            if (!can) w.setTooltip(Tooltip.create(tr("tip.world_only")));
        }
        return out;
    }

    private static JsonElement range(double lo, double hi) {
        JsonArray a = new JsonArray();
        a.add(lo);
        a.add(hi);
        return a;
    }

    private static JsonElement numberValue(AdminSetting s, double d) {
        return s.type() == AdminSetting.Type.INT ? new JsonPrimitive(Math.round(d)) : new JsonPrimitive(d);
    }

    static double number(JsonElement v) {
        try {
            return v.getAsDouble();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static Component onOff(JsonElement v) {
        boolean on = v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean() && v.getAsBoolean();
        return tr(on ? "on" : "off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    /** a number with too many steps for a slider (health up to a million, blocks up to 100,000) gets a box */
    private static boolean boxed(AdminSetting s) {
        double step = step(s);
        return (s.max() - s.min()) / step > 1000;
    }

    static double step(AdminSetting s) {
        if (s.step() > 0) return s.step();
        if (s.type() == AdminSetting.Type.INT) return 1;
        double raw = (s.max() - s.min()) / 200;
        double p = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 1e-6))));
        return raw / p < 2 ? p : raw / p < 5 ? 2 * p : 5 * p;
    }

    private Button resetButton(List<AdminSetting> ss) {
        Button b = new ResetButton(() -> reset(ss));
        boolean any = false, can = true;
        for (AdminSetting s : ss) {
            any |= changed(s);
            can &= editable(s);
        }
        b.active = any && can;
        AdminSetting s = ss.get(0);
        b.setTooltip(Tooltip.create(!can ? tr("tip.world_only")
                : allWorlds ? tr("reset.all.tip", show(s, s.jar()))
                : ss.size() > 1 ? tr("reset.move.tip") : tr("reset.world.tip", show(s, s.def()))));
        return b;
    }

    /** a button with a small "back" arrow drawn on it */
    private static final class ResetButton extends Button {
        private static final String[] ARROW = {
            "..###.#", ".#...##", "#...###", "#......", "#.....#", ".#...#.", "..###.."
        };

        ResetButton(Runnable r) {
            super(0, 0, 16, 16, Component.empty(), b -> r.run(), DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            super.renderWidget(g, mx, my, partial);
            int c = active ? (isHoveredOrFocused() ? 0xFFFFFFA0 : 0xFFE0E0E0) : 0xFF606060;
            int x0 = getX() + 5, y0 = getY() + 5;
            for (int r = 0; r < ARROW.length; r++)
                for (int k = 0; k < ARROW[r].length(); k++)
                    if (ARROW[r].charAt(k) == '#') g.fill(x0 + k, y0 + r, x0 + k + 1, y0 + r + 1, c);
        }
    }

    private void reset(List<AdminSetting> ss) {
        List<AdminSetting> todo = ss.stream().filter(this::changed).toList();
        if (todo.isEmpty()) return;
        Runnable go = () -> {
            for (AdminSetting s : todo) {
                AdminBookClient.reset(s.giant(), s.id(), allWorlds);
                pending.put(s.giant() + "/" + s.id(), new Pend(allWorlds ? s.jar() : s.def(), Util.getMillis()));
            }
            say(tr("sent"), GREY);
            refreshControls();
        };
        AdminSetting first = todo.get(0);
        if (first.creationOnly()) askCreation(first, allWorlds ? first.jar() : first.def(), go);
        else go.run();
    }

    /** a change from a control: asked about first if it only reaches new land or a new world, then sent */
    private void commit(AdminSetting s, JsonElement v) {
        if (AdminSetting.same(v, shown(s))) return;
        Runnable go = () -> {
            if (!AdminBookClient.set(s.giant(), s.id(), AdminSetting.text(v), allWorlds)) {
                say(tr("no_server"), WARN);
                refreshControls();
                return;
            }
            pending.put(s.giant() + "/" + s.id(), new Pend(v, Util.getMillis()));
            say(tr("sent"), GREY);
            refreshControls();
        };
        if (s.creationOnly()) askCreation(s, v, go);
        else go.run();
    }

    /** "Only affects new land": are you sure? (cancel puts the control back) */
    private void askCreation(AdminSetting s, JsonElement v, Runnable go) {
        boolean land = s.when().equals(AdminSetting.NEW_LAND);
        confirm(tr(land ? "creation.land.title" : "creation.world.title"),
                tr(land ? "creation.land.body" : "creation.world.body", name(s), show(s, v),
                        allWorlds ? tr("creation.all_worlds") : Component.empty()),
                tr("creation.yes"), go);
    }

    /** a slider that snaps to its setting's steps; it sends its value when let go (or a moment after the keys) */
    private final class Slider extends AbstractSliderButton {
        private final AdminSetting s;
        private final String prefix;
        private final Consumer<Double> onCommit;
        int keyedAt = -1;

        Slider(int w, AdminSetting s, double v, String prefix, Consumer<Double> onCommit) {
            super(0, 0, w, 16, Component.empty(), 0);
            this.s = s;
            this.prefix = prefix;
            this.onCommit = onCommit;
            show(v);
        }

        private double frac(double v) {
            return s.max() > s.min() ? (Mth.clamp(v, s.min(), s.max()) - s.min()) / (s.max() - s.min()) : 0;
        }

        double current() {
            double st = step(s);
            double v = s.min() + value * (s.max() - s.min());
            v = s.min() + Math.round((v - s.min()) / st) * st;
            return Mth.clamp(Math.round(v * 10000) / 10000.0, s.min(), s.max());
        }

        void show(double v) {
            value = frac(v);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            boolean times = shared(s) || move(s);
            setMessage(Component.literal(prefix + (times ? "x" : "") + AdminSetting.plain(current())));
        }

        @Override
        protected void applyValue() {}

        @Override
        public void onRelease(double mx, double my) {
            super.onRelease(mx, my);
            onCommit.accept(current());
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            boolean r = super.keyPressed(key, scan, mods);
            if (r && (key == 263 || key == 262)) keyedAt = ticks;
            return r;
        }

        void tick() {
            if (keyedAt >= 0 && ticks - keyedAt >= 12) {
                keyedAt = -1;
                onCommit.accept(current());
            }
        }
    }

    /** a box to type a number in (Enter, or clicking away, sends it) */
    private final class NumBox extends EditBox {
        final AdminSetting s;

        NumBox(int w, AdminSetting s) {
            super(AdminBookScreen.this.font, 0, 0, w, 16, name(s));
            this.s = s;
            setMaxLength(16);
            setValue(AdminSetting.text(shown(s)));
            setResponder(t -> setTextColor(ok(t) ? 0xFFE0E0E0 : 0xFFFF6060));
        }

        boolean ok(String t) {
            try {
                s.parse(t);
                return true;
            } catch (AdminSetting.BadValue e) {
                return false;
            }
        }

        void send() {
            String t = getValue().trim();
            if (t.equals(AdminSetting.text(shown(s)))) return;
            try {
                commit(s, s.parse(t));
            } catch (AdminSetting.BadValue e) {
                say(Component.literal(e.getMessage()), WARN);
                setValue(AdminSetting.text(shown(s)));
            }
        }

        @Override
        public void setFocused(boolean f) {
            boolean was = isFocused();
            super.setFocused(f);
            if (was && !f) send();
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            if (key == 257 || key == 335) {
                send();
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
    }

    /** every control shows its setting's value again (after a change, a cancel, or new pages) */
    private void refreshControls() {
        for (Row r : rows) {
            List<AdminSetting> ss = r instanceof SetRow sr ? List.of(sr.s) : r instanceof MoveRow mr ? mr.parts : null;
            if (ss == null) continue;
            for (Placed p : r.ws) {
                AdminSetting s = p.w() == r.reset ? null : r instanceof MoveRow mr ? mr.partOf(p.w()) : ((SetRow) r).s;
                if (s == null) {
                    // the ↺
                    boolean any = false;
                    for (AdminSetting x : ss) any |= changed(x);
                    boolean can = ss.stream().allMatch(this::editable);
                    p.w().active = any && can;
                    continue;
                }
                AbstractWidget w = p.w();
                JsonElement v = shown(s);
                if (w instanceof Slider sl) {
                    if (s.type() == AdminSetting.Type.RANGE) {
                        int i = r.ws.indexOf(p);
                        sl.show(v.getAsJsonArray().get(i == 0 ? 0 : 1).getAsDouble());
                    } else sl.show(number(v));
                } else if (w instanceof NumBox nb) {
                    if (!nb.isFocused()) nb.setValue(AdminSetting.text(v));
                } else if (s.type() == AdminSetting.Type.BOOL) w.setMessage(onOff(v));
                else w.setMessage(Component.literal(AdminSetting.text(v)));
            }
        }
    }

    // building a page

    private void build() {
        for (Row r : rows) for (Placed p : r.ws) removeWidget(p.w());
        rows.clear();
        tiles.clear();
        String q = keptQuery.strip().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) searchRows(q);
        else if (pageKey.equals(HOME)) homeRows();
        else settingRows(pageKey);
        int y = 0;
        for (Row r : rows) {
            r.y = y;
            y += r.h;
        }
        contentH = y + 4;
        updateHead();
        place();
    }

    private void updateHead() {
        boolean searching = !keptQuery.isBlank();
        headButton.visible = false;
        if (searching) return;
        Component label;
        if (pageKey.equals(HOME)) {
            label = tr("reset_all");
            headButton.active = AdminBookClient.home() != null && anySet();
        } else {
            label = tr("reset_page");
            headButton.active = setCount(pageKey) > 0;
        }
        if (allWorlds) return;
        int w = font.width(label) + 12;
        headButton.setMessage(label);
        headButton.setWidth(w);
        headButton.setX(lx + lw - w);
        headButton.visible = AdminBookClient.home() != null
                && (pageKey.equals(HOME) || AdminBookClient.page(pageKey) != null);
        headButton.setTooltip(Tooltip.create(tr(pageKey.equals(HOME) ? "reset_all.tip" : "reset_page.tip")));
    }

    private boolean anySet() {
        for (String k : navKeys()) if (!k.equals(HOME) && setCount(k) > 0) return true;
        return false;
    }

    /** a giant's page (or the all-giants one): by category, the moves folded one to a line */
    private void settingRows(String key) {
        AdminBookClient.Page p = AdminBookClient.page(key);
        if (p == null) {
            JsonObject g = homeGiant(key);
            boolean asking = AdminBookClient.home() == null || g != null && g.get("speaks") != null
                    && g.get("speaks").getAsBoolean();
            rows.add(new Note(asking ? tr("asking") : tr("no_settings", giantName(key)), GREY));
            return;
        }
        int hidden = 0;
        for (String cat : AdminSetting.CATEGORIES) {
            List<AdminSetting> plain = new ArrayList<>();
            Map<String, List<AdminSetting>> groups = new LinkedHashMap<>();
            for (AdminSetting s : p.settings()) {
                if (!s.cat().equals(cat)) continue;
                boolean show = advanced || AdminInsight.simple(s.id());
                if (!show) {
                    hidden++;
                    continue;
                }
                if (s.group().isEmpty()) plain.add(s);
                else groups.computeIfAbsent(s.group(), k -> new ArrayList<>()).add(s);
            }
            if (plain.isEmpty() && groups.isEmpty()) continue;
            Component note = null;
            if (cat.equals("moves") && !groups.isEmpty()) {
                boolean wind = groups.values().stream().anyMatch(l -> l.stream().anyMatch(s -> s.id().endsWith(".wind")));
                note = tr(wind ? "moves.note" : "moves.note_nowind");
            }
            if (key.equals(AdminCatalog.ALL) && rows.isEmpty()) note = tr("all.note");
            rows.add(new Head(tr("cat." + cat), note, null));
            for (AdminSetting s : plain) rows.add(new SetRow(s));
            for (List<AdminSetting> g : groups.values()) rows.add(new MoveRow(moveName(g.get(0)), g));
        }
        if (hidden > 0) {
            rows.add(new Note(tr("simple.more", hidden), GREY));
            Block b = new Block(22, (g, x, y, w, mx, my) -> {});
            b.add(Button.builder(tr("simple.show_all"), x -> {
                        advanced = true;
                        AdminPrefs.advanced(true);
                        modeButton.setMessage(modeLabel());
                        build();
                    })
                    .bounds(0, 0, font.width(tr("simple.show_all")) + 16, 16)
                    .build(), 0, 2, false);
            rows.add(b);
        }
        if (rows.isEmpty()) rows.add(new Note(tr("empty"), GREY));
    }

    /** every setting on every page whose name, id or note has the words */
    private void searchRows(String q) {
        int found = 0, more = 0;
        List<String> keys = new ArrayList<>(navKeys());
        keys.remove(HOME);
        for (String key : keys) {
            AdminBookClient.Page p = AdminBookClient.page(key);
            if (p == null) continue;
            List<Row> mine = new ArrayList<>();
            Map<String, Boolean> groupDone = new HashMap<>();
            for (AdminSetting s : p.settings()) {
                String hay = (name(s).getString() + " " + s.name() + " " + s.id() + " " + what(s).getString() + " "
                        + s.what()).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
                if (found >= MOST_FOUND) {
                    more++;
                    continue;
                }
                if (!s.group().isEmpty()) {
                    if (groupDone.put(s.group(), true) != null) continue;
                    List<AdminSetting> parts = p.settings().stream().filter(x -> x.group().equals(s.group())).toList();
                    mine.add(new MoveRow(moveName(s), parts));
                } else mine.add(new SetRow(s));
                found++;
            }
            if (!mine.isEmpty()) {
                rows.add(new Head(Component.literal(giantName(key)), null, key));
                rows.addAll(mine);
            }
        }
        foundTotal = found + more;
        if (found == 0) rows.add(new Note(tr("search.none", keptQuery.strip()), GREY));
        else if (more > 0) rows.add(new Note(tr("search.more", more), GREY));
    }

    /** how many settings the search found (more than it shows, past MOST_FOUND) */
    private int foundTotal;

    /** Home: the preset, the danger rating, clashes, the giants, a few quick ways in */
    private void homeRows() {
        JsonObject h = AdminBookClient.home();
        if (h == null) {
            rows.add(new Note(tr("asking"), GREY));
            return;
        }
        // the preset and how dangerous it all is
        String preset = AdminBookClient.AdminJsonBridge.str(h, "preset");
        JsonObject d = h.get("danger") instanceof JsonObject o ? o : new JsonObject();
        int level = Mth.clamp(AdminBookClient.AdminJsonBridge.num(d, "level", 3), 1, 5);
        String label = AdminBookClient.AdminJsonBridge.str(d, "label");
        List<FormattedCharSequence> reason =
                font.split(Component.literal(AdminBookClient.AdminJsonBridge.str(d, "reason")), lw - 8);
        rows.add(new Block(16, (g, x, y, w, mx, my) -> {
            int nx = g.drawString(font, tr("home.preset"), x, y + 3, GREY, false);
            g.drawString(font, Component.literal(preset), nx + 4, y + 3, KEY, false);
            if (mx >= x && mx < x + w && my >= y && my < y + 14)
                setTooltipForNextRenderPass(font.split(tr("home.preset.tip"), 260));
        }));
        rows.add(new Block(18 + reason.size() * 10 + 4, (g, x, y, w, mx, my) -> {
            int nx = g.drawString(font, tr("home.danger"), x, y + 4, GREY, false) + 6;
            int[] colours = {0xFF60C060, 0xFFA0C850, 0xFFE0C050, 0xFFE88040, 0xFFE04040};
            for (int i = 0; i < 5; i++) {
                int bx = nx + i * 13;
                g.fill(bx, y + 3, bx + 11, y + 11, i < level ? colours[level - 1] : 0x30FFFFFF);
            }
            g.drawString(font, level + " · " + label, nx + 5 * 13 + 4, y + 4, colours[level - 1], false);
            for (int i = 0; i < reason.size(); i++)
                g.drawString(font, reason.get(i), x + 8, y + 16 + i * 10, TEXT, false);
            if (mx >= x && mx < x + w && my >= y && my < y + 14)
                setTooltipForNextRenderPass(font.split(tr("home.danger.tip"), 280));
        }));
        // what clashes
        rows.add(new Head(tr("home.clashes"), null, null));
        JsonArray warn = h.get("warnings") instanceof JsonArray a ? a : new JsonArray();
        if (warn.isEmpty()) rows.add(new Note(tr("home.no_clashes"), GOOD));
        for (JsonElement e : warn) {
            if (!e.isJsonObject()) continue;
            String text = AdminBookClient.AdminJsonBridge.str(e.getAsJsonObject(), "text");
            List<FormattedCharSequence> ls = font.split(Component.literal(text), lw - 12);
            rows.add(new Block(ls.size() * 10 + 4, (g, x, y, w, mx, my) -> {
                g.drawString(font, "⚠", x, y + 1, KEY, false);
                for (int i = 0; i < ls.size(); i++) g.drawString(font, ls.get(i), x + 11, y + 1 + i * 10, TEXT, false);
            }));
        }
        // the giants: a tile each, the all-giants one first
        rows.add(new Head(tr("home.pages"), null, null));
        List<String> keys = new ArrayList<>(navKeys());
        keys.remove(HOME);
        int cols = Math.max(1, Math.min(4, (lw + 4) / 124)), tileW = (lw - (cols - 1) * 4) / cols, tileH = 30;
        for (int i = 0; i < keys.size(); i += cols) {
            List<String> line = keys.subList(i, Math.min(keys.size(), i + cols));
            rows.add(new Block(tileH + 4, (g, x, y, w, mx, my) -> {
                for (int c = 0; c < line.size(); c++) tile(g, line.get(c), x + c * (tileW + 4), y, tileW, tileH, mx, my);
            }));
            tiles.add(new TileLine(rows.get(rows.size() - 1), line, tileW, tileH));
        }
        // quick ways in
        rows.add(new Head(tr("home.quick"), null, null));
        Block quick = new Block(22, (g, x, y, w, mx, my) -> {});
        int qx = 0, qy = 2;
        for (Object[] q : new Object[][] {
            {"home.quick.search", (Runnable) () -> setFocused(search)},
            {"home.quick.intro", (Runnable) () -> openIntro(0)},
            {"home.quick.all", (Runnable) () -> go(AdminCatalog.ALL)},
            {"home.quick.mode", (Runnable) () -> modeButton.onPress()}
        }) {
            Component c = q[0].equals("home.quick.mode") ? tr(advanced ? "home.quick.simple" : "home.quick.advanced")
                    : tr((String) q[0]);
            int bw = font.width(c) + 12;
            if (qx > 0 && qx + bw > lw) {
                // (on to the next row)
                qx = 0;
                qy += 20;
                quick.h += 20;
            }
            Runnable r = (Runnable) q[1];
            quick.add(Button.builder(c, x -> r.run()).bounds(0, 0, bw, 16).build(), qx, qy, false);
            qx += bw + 4;
        }
        rows.add(quick);
        // the last change, and what comes next
        if (h.get("last") instanceof JsonObject last) {
            String when = new java.text.SimpleDateFormat("MMM d HH:mm", Locale.ROOT)
                    .format(new java.util.Date(last.get("time").getAsLong()));
            rows.add(new Note(tr("home.last", AdminBookClient.AdminJsonBridge.str(last, "who"),
                    AdminBookClient.AdminJsonBridge.str(last, "text"), when), GREY));
        } else rows.add(new Note(tr("home.no_changes"), GREY));
        rows.add(new Note(tr("home.soon"), DIM));
    }

    /** where Home's giant tiles are (for clicks) */
    private record TileLine(Row row, List<String> keys, int tileW, int tileH) {}

    private final List<TileLine> tiles = new ArrayList<>();

    private void tile(GuiGraphics g, String key, int x, int y, int w, int h, int mx, int my) {
        boolean over = mx >= x && mx < x + w && my >= y && my < y + h && my >= ly0 && my < ly1;
        g.fill(x, y, x + w, y + h, over ? 0x40FFFFFF : 0x20FFFFFF);
        g.renderOutline(x, y, w, h, over ? TITLE : 0x60FFFFFF);
        icon(g, key, x + 3, y + 3, h - 6);
        int tx = x + h, tw = w - h - 3;
        g.drawString(font, cut(giantName(key), tw), tx, y + 5, TEXT, false);
        int n = setCount(key);
        JsonObject gj = homeGiant(key);
        boolean speaks = key.equals(AdminCatalog.ALL) || gj == null || gj.get("speaks") == null
                || gj.get("speaks").getAsBoolean();
        Component sub = !speaks ? tr("tile.none") : n > 0 ? tr("tile.set", n) : tr("tile.default");
        g.drawString(font, cut(sub.getString(), tw), tx, y + 16, !speaks ? DIM : n > 0 ? GOLD : GREY, false);
    }

    /** a giant's picture (the book, or the Guide for "all giants"), size x size */
    private void icon(GuiGraphics g, String key, int x, int y, int size) {
        if (key.equals(HOME) || key.equals(AdminCatalog.ALL)) {
            ItemStack st = new ItemStack(key.equals(HOME) ? AdminBookItem.BOOK : GiantsGuideMod.GUIDE);
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(size / 16f, size / 16f, 1);
            g.renderItem(st, 0, 0);
            g.pose().popPose();
            return;
        }
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, EDGE);
        g.blit(ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "textures/gui/giant/" + key + ".png"), x, y,
                size, size, 0, 0, 64, 64, 64, 64);
    }

    /** the list's widgets follow the scroll; any not wholly in view are hidden */
    private void place() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - (ly1 - ly0)));
        keptScroll = scroll;
        for (Row r : rows) {
            int top = ly0 + r.y - (int) scroll;
            for (Placed p : r.ws) {
                AbstractWidget w = p.w();
                int x = p.right() ? lx + lw - p.dx() - w.getWidth() : lx + p.dx();
                int y = top + p.dy();
                w.setX(x);
                w.setY(y);
                w.visible = y >= ly0 && y + w.getHeight() <= ly1;
            }
        }
    }

    private void go(String key) {
        if (key.equals(pageKey) && keptQuery.isBlank()) return;
        pageKey = key;
        keptPage = key;
        keptQuery = "";
        search.setValue("");
        scroll = 0;
        build();
    }

    // the server

    @Override
    public void tick() {
        ticks++;
        for (Row r : rows) for (Placed p : r.ws) if (p.w() instanceof Slider s) s.tick();
        long now = Util.getMillis();
        // the server's word on a change
        if (AdminBookClient.saidAt() != seenSaid) {
            seenSaid = AdminBookClient.saidAt();
            List<String> said = AdminBookClient.said();
            if (!said.isEmpty()) say(Component.literal(String.join(" ", said)), AdminBookClient.saidOk() ? GOOD : WARN);
            if (!AdminBookClient.saidOk()) {
                pending.clear();
                refreshControls();
            }
        }
        // fresh pages: values sent and now in them are done; the page is laid out again unless something is held
        if (AdminBookClient.version() != seenVersion && !mouseDown && !typing()) {
            seenVersion = AdminBookClient.version();
            pending.entrySet().removeIf(e -> {
                if (now - e.getValue().at() > 4000) return true;
                String[] k = e.getKey().split("/", 2);
                AdminBookClient.Page p = AdminBookClient.page(k[0]);
                AdminSetting s = p == null ? null : p.setting(k[1]);
                return s == null || AdminSetting.same(allWorlds ? s.def() : s.value(), e.getValue().v());
            });
            rebuild();
        }
    }

    private boolean typing() {
        for (Row r : rows) for (Placed p : r.ws) if (p.w() instanceof NumBox b && b.isFocused()) return true;
        return false;
    }

    private void rebuild() {
        double sc = scroll;
        boolean searching = getFocused() == search;
        int entries = Math.max(2, navKeys().size());
        int want = entries * 20 <= ph - 26 ? 20 : entries * 17 <= ph - 26 ? 17 : 14;
        if (want != navRowH) navRowH = want;
        build();
        scroll = sc;
        if (wantScroll >= 0 && AdminBookClient.home() != null
                && (pageKey.equals(HOME) || AdminBookClient.page(pageKey) != null || !keptQuery.isBlank())) {
            scroll = wantScroll;
            wantScroll = -1;
        }
        place();
        if (searching) setFocused(search);
    }

    private void say(Component c, int colour) {
        status = c;
        statusColour = colour;
        statusAt = Util.getMillis();
    }

    @Override
    public void removed() {
        // (a number typed and not sent yet goes when the book closes)
        for (Row r : rows) for (Placed p : r.ws) if (p.w() instanceof NumBox nb && nb.isFocused()) nb.send();
        AdminBookClient.closed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        // (under a question nothing else is hovered)
        boolean held = modal != null;
        super.render(g, held ? -1000 : mx, held ? -1000 : my, partial);
        if (modal != null) modal.render(g, mx, my, partial);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        super.renderBackground(g, mx, my, partial);
        g.fill(px0, py0, px0 + pw, py0 + ph, PANEL);
        g.renderOutline(px0, py0, pw, ph, EDGE);
        g.drawCenteredString(font, title, px0 + pw / 2, py0 + 5, TITLE);
        g.fill(lx - 5, py0 + 18, lx - 4, py0 + ph - 6, EDGE);
        drawNav(g, mx, my);
        drawHead(g, mx, my);
        g.fill(lx, ly0 - 3, lx + lw, ly0 - 2, EDGE);
        drawList(g, mx, my);
        if (!status.getString().isEmpty() && Util.getMillis() - statusAt < 8000) {
            String line = cut(status.getString(), lw);
            g.drawString(font, line, lx, py0 + ph - 11, statusColour, false);
            // (all of it on hover, when it didn't fit)
            if (!line.equals(status.getString()) && modal == null && mx >= lx && mx < lx + lw && my >= py0 + ph - 12
                    && my < py0 + ph - 2)
                setTooltipForNextRenderPass(font.split(status, 300));
        }
        else if (AdminBookClient.home() == null)
            g.drawString(font, tr("asking"), lx, py0 + ph - 11, GREY, false);
    }

    private void drawNav(GuiGraphics g, int mx, int my) {
        List<String> keys = navKeys();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            int y = navY + i * navRowH;
            if (y + navRowH > py0 + ph - 4) break;
            boolean sel = key.equals(pageKey) && keptQuery.isBlank();
            boolean over = modal == null && mx >= navX && mx < navX + navW && my >= y && my < y + navRowH - 2;
            if (sel) {
                g.fill(navX, y, navX + navW, y + navRowH - 2, 0xFF3A3020);
                g.renderOutline(navX, y, navW, navRowH - 2, TITLE);
            } else if (over) g.fill(navX, y, navX + navW, y + navRowH - 2, 0x30FFFFFF);
            int pic = navRowH - 6;
            icon(g, key, navX + 2, y + 2, pic);
            Component name = key.equals(HOME) ? tr("home") : Component.literal(giantName(key));
            JsonObject gj = homeGiant(key);
            boolean speaks = gj == null || gj.get("speaks") == null || gj.get("speaks").getAsBoolean();
            int n = key.equals(HOME) ? 0 : setCount(key);
            String count = n > 0 ? String.valueOf(n) : "";
            int cw = count.isEmpty() ? 0 : font.width(count) + 4;
            int tx = navX + pic + 6, tw = navW - pic - 9 - cw;
            g.drawString(font, cut(name.getString(), tw), tx, y + (navRowH - 2 - 8) / 2, !speaks ? DIM : sel ? KEY : TEXT,
                    false);
            if (n > 0) g.drawString(font, count, navX + navW - 3 - font.width(count), y + (navRowH - 2 - 8) / 2, GOLD, false);
            if (over && n > 0)
                setTooltipForNextRenderPass(font.split(tr("nav.set", giantName(key), n), 220));
        }
    }

    private void drawHead(GuiGraphics g, int mx, int my) {
        Component title, sub;
        String q = keptQuery.strip();
        if (!q.isEmpty()) {
            title = tr("search.title", q);
            sub = foundTotal > MOST_FOUND ? tr("search.sub.more", foundTotal, MOST_FOUND) : tr("search.sub", foundTotal);
        } else if (pageKey.equals(HOME)) {
            title = tr("home");
            sub = tr("home.sub");
        } else {
            title = Component.literal(giantName(pageKey));
            AdminBookClient.Page p = AdminBookClient.page(pageKey);
            int n = p == null ? 0 : p.settings().size(), set = setCount(pageKey);
            sub = pageKey.equals(AdminCatalog.ALL) ? tr("all.sub")
                    : p == null ? Component.empty() : set > 0 ? tr("page.sub.set", n, set) : tr("page.sub", n);
        }
        int right = headButton.visible ? headButton.getX() - 6 : lx + lw;
        int tx = lx;
        if (q.isEmpty() && !pageKey.equals(HOME) && !pageKey.equals(AdminCatalog.ALL)) {
            icon(g, pageKey, lx, headY + 2, 18);
            tx += 23;
        }
        g.drawString(font, cut(title.getString(), right - tx), tx, headY + 2, GOLD, false);
        Component line = allWorlds ? tr("scope.all.banner") : sub;
        g.drawString(font, cut(line.getString(), right - tx), tx, headY + 12, allWorlds ? ORANGE : GREY, false);
        if (allWorlds && mx >= tx && mx < right && my >= headY + 10 && my < headY + 21)
            setTooltipForNextRenderPass(font.split(tr("scope.all.tip"), 260));
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        g.enableScissor(lx - 5, ly0, lx + lw + 6, ly1);
        for (Row r : rows) {
            int y = ly0 + r.y - (int) scroll;
            if (y + r.h < ly0 || y > ly1) continue;
            r.draw(g, lx, y, lw, mx, my);
        }
        g.disableScissor();
        if (contentH > ly1 - ly0) {
            int track = ly1 - ly0, bar = Math.max(16, track * track / contentH);
            int by = ly0 + (int) ((track - bar) * scroll / Math.max(1, contentH - track));
            g.fill(lx + lw + 3, ly0, lx + lw + 5, ly1, LINE);
            g.fill(lx + lw + 3, by, lx + lw + 5, by + bar, EDGE);
        }
    }

    String cut(String s, int w) {
        if (w <= 0) return "";
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width("…"))) + "…";
    }

    // the size picture: the giant at that size beside a player

    /** his height and length at full size, in blocks, and his shape: q four legs, w a worm, d a dome, t a tree */
    static double[] body(String key) {
        return switch (key) {
            case "pitchgut" -> new double[] {185, 265, 'q'};
            case "furrowmaw" -> new double[] {45, 600, 'w'};
            case "cerberus" -> new double[] {200, 280, 'q'};
            case "hollowbell" -> new double[] {200, 200, 'd'};
            case "lanternwillow" -> new double[] {200, 200, 't'};
            case "wreckback" -> new double[] {208, 323, 'q'};
            case "swarmforge" -> new double[] {152, 225, 'q'};
            case "gloamwick" -> new double[] {200, 277, 'q'};
            default -> new double[] {200, 260, 'q'};
        };
    }

    /**
     * A dark box with the ground, a player (1.8 blocks) and the giant at this size, to the same scale. The giant
     * fills the box; the player beside him is as big as he really is next to him (a speck beside a full-size giant,
     * plain to see once the giant is small), never less than a dot.
     */
    void preview(GuiGraphics g, int x, int y, int w, int h, String key, double size) {
        g.fill(x, y, x + w, y + h, 0x50000000);
        int ground = y + h - 2;
        g.fill(x + 1, ground, x + w - 1, ground + 1, 0x60FFFFFF);
        double[] b = body(key);
        double gh = Math.max(0.05, b[0] * size), gl = Math.max(0.05, b[1] * size);
        double k = Math.min((h - 4) / Math.max(gh, 1.8), (w - 8) / (gl + 1.2));
        int pH = Math.max(1, (int) Math.round(1.8 * k)), pW = Math.max(1, (int) Math.round(0.6 * k));
        int px = x + 3;
        g.fill(px, ground - pH, px + pW, ground, 0xFFF0F0F0);
        if (pH >= 6) g.fill(px, ground - pH, px + pW, ground - pH + Math.max(1, pH / 4), 0xFFFFC890);
        if (pH < 5) {
            // (a speck: a small mark over him so the eye finds him)
            int mx0 = px + pW / 2, my0 = ground - pH - 3;
            g.fill(mx0 - 1, my0 - 2, mx0 + 2, my0 - 1, 0xFFF0F0F0);
            g.fill(mx0, my0 - 1, mx0 + 1, my0, 0xFFF0F0F0);
        }
        int gx = px + Math.max(pW, 3) + 2;
        int gw = Mth.clamp((int) Math.round(gl * k), 2, x + w - 2 - gx);
        int gt = Math.max(2, (int) Math.round(gh * k));
        int col = 0xFFB09070;
        switch ((char) b[2]) {
            case 'w' -> ellipse(g, gx, ground - gt, gw, gt * 2, ground, col);
            case 'd' -> {
                ellipse(g, gx, ground - gt, gw, gt * 3 / 2, ground - gt * 3 / 10, col);
                int lw = Math.max(1, gw / 12);
                for (int i = 1; i <= 3; i++) g.fill(gx + gw * i / 4 - lw / 2, ground - gt * 3 / 10 - 1,
                        gx + gw * i / 4 - lw / 2 + lw, ground, col);
            }
            case 't' -> {
                int tw = Math.max(1, gw / 7);
                g.fill(gx + gw / 2 - tw / 2, ground - gt / 2, gx + gw / 2 - tw / 2 + tw, ground, 0xFF806048);
                ellipse(g, gx, ground - gt, gw, gt * 7 / 10, ground, col);
            }
            default -> {
                // four legs, a body, a neck and a head at the front (on the right)
                int legH = gt * 38 / 100, lw = Math.max(1, gw / 10);
                for (double f : new double[] {0.06, 0.24, 0.52, 0.68})
                    g.fill(gx + (int) (gw * f), ground - legH - 1, gx + (int) (gw * f) + lw, ground, col);
                ellipse(g, gx, ground - gt * 85 / 100, gw * 80 / 100, gt * 55 / 100, ground, col);
                g.fill(gx + gw * 70 / 100, ground - gt * 90 / 100, gx + gw * 82 / 100, ground - gt * 55 / 100, col);
                ellipse(g, gx + gw * 72 / 100, ground - gt, Math.max(1, gw * 28 / 100), Math.max(1, gt * 25 / 100),
                        ground, col);
            }
        }
    }

    /** the part of an ellipse (in the box x, y, w, h) above the line yCut */
    private static void ellipse(GuiGraphics g, int x, int y, int w, int h, int yCut, int colour) {
        double cx = x + w / 2.0, cy = y + h / 2.0, rx = w / 2.0, ry = Math.max(0.5, h / 2.0);
        for (int yy = y; yy < Math.min(y + h, yCut); yy++) {
            double t = (yy + 0.5 - cy) / ry;
            if (Math.abs(t) > 1) continue;
            double half = rx * Math.sqrt(1 - t * t);
            int a = (int) Math.round(cx - half), z = (int) Math.round(cx + half);
            if (z <= a) z = a + 1;
            g.fill(a, yy, z, yy + 1, colour);
        }
    }

    // the mouse and keys

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (modal != null) return modal.mouseClicked(mx, my, button);
        mouseDown = true;
        // the pages down the left
        if (button == 0 && mx >= navX && mx < navX + navW && my >= navY) {
            List<String> keys = navKeys();
            int i = (int) ((my - navY) / navRowH);
            if (i < keys.size() && my - navY - i * navRowH < navRowH - 2) {
                go(keys.get(i));
                return true;
            }
        }
        // Home's tiles
        if (button == 0 && keptQuery.isBlank() && pageKey.equals(HOME) && my >= ly0 && my < ly1)
            for (TileLine t : tiles) {
                int top = ly0 + t.row().y - (int) scroll;
                if (my < top || my >= top + t.tileH()) continue;
                for (int c = 0; c < t.keys().size(); c++) {
                    int x = lx + c * (t.tileW() + 4);
                    if (mx >= x && mx < x + t.tileW()) {
                        go(t.keys().get(c));
                        return true;
                    }
                }
            }
        if (contentH > ly1 - ly0 && mx >= lx + lw + 2 && mx < lx + lw + 7 && my >= ly0 && my < ly1) {
            dragging = true;
            dragTo(my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (modal != null) return true;
        if (dragging) {
            dragTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        mouseDown = false;
        dragging = false;
        if (modal != null) return true;
        return super.mouseReleased(mx, my, button);
    }

    private void dragTo(double my) {
        double f = Mth.clamp((my - ly0) / Math.max(1, ly1 - ly0), 0, 1);
        scroll = f * Math.max(0, contentH - (ly1 - ly0));
        place();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (modal != null) return true;
        if (mx >= lx - 5 && mx < lx + lw + 8 && my >= ly0 && my < ly1) {
            scroll -= sy * 24;
            place();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (modal != null) {
            if (key == 256) {
                modal.cancel();
                return true;
            }
            return modal.keyPressed(key);
        }
        if (getFocused() == search && key == 256 && !search.getValue().isEmpty()) {
            search.setValue("");
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // the walkthrough and questions

    private void openIntro(int page) {
        modal = new Intro(Mth.clamp(page, 0, Intro.PAGES - 1));
        place();
    }

    private void confirm(Component title, Component body, Component yes, Runnable go) {
        modal = new Ask(title, body, yes, go);
        place();
    }

    private void closeModal() {
        modal = null;
        refreshControls();
        place();
    }

    /** something drawn over the book, with its own buttons (nothing else answers while it is up) */
    private abstract class Modal {
        final List<Button> buttons = new ArrayList<>();
        int mx0, my0, mw, mh;

        abstract Component title();

        abstract List<FormattedCharSequence> body();

        abstract void cancel();

        void layout(int buttonsW) {
            mw = Math.min(pw - 40, 300);
            List<FormattedCharSequence> b = body();
            mh = 30 + b.size() * 10 + 28;
            mx0 = (width - mw) / 2;
            my0 = (height - mh) / 2;
            int x = mx0 + mw - 8 - buttonsW, y = my0 + mh - 24;
            for (Button bt : buttons) {
                bt.setX(x);
                bt.setY(y);
                x += bt.getWidth() + 4;
            }
        }

        Button button(Component c, Runnable r) {
            Button b = Button.builder(c, x -> r.run()).bounds(0, 0, font.width(c) + 16, 16).build();
            buttons.add(b);
            return b;
        }

        int buttonsW() {
            int w = -4;
            for (Button b : buttons) w += b.getWidth() + 4;
            return w;
        }

        void render(GuiGraphics g, int mx, int my, float partial) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(0, 0, width, height, 0x90000000);
            g.fill(mx0, my0, mx0 + mw, my0 + mh, 0xF0181410);
            g.renderOutline(mx0, my0, mw, mh, TITLE);
            g.drawString(font, title(), mx0 + 8, my0 + 8, GOLD, false);
            g.fill(mx0 + 8, my0 + 19, mx0 + mw - 8, my0 + 20, EDGE);
            List<FormattedCharSequence> b = body();
            for (int i = 0; i < b.size(); i++) g.drawString(font, b.get(i), mx0 + 8, my0 + 26 + i * 10, TEXT, false);
            extra(g);
            for (Button bt : buttons) bt.render(g, mx, my, partial);
            g.pose().popPose();
        }

        void extra(GuiGraphics g) {}

        boolean mouseClicked(double mx, double my, int button) {
            for (Button b : List.copyOf(buttons)) if (b.mouseClicked(mx, my, button)) return true;
            return true;
        }

        boolean keyPressed(int key) {
            return true;
        }
    }

    /** "are you sure?" */
    private final class Ask extends Modal {
        final Component title, body;

        Ask(Component title, Component body, Component yes, Runnable go) {
            this.title = title;
            this.body = body;
            button(yes, () -> {
                closeModal();
                go.run();
            });
            button(tr("cancel"), this::cancel);
            layout(buttonsW());
        }

        @Override
        Component title() {
            return title;
        }

        @Override
        List<FormattedCharSequence> body() {
            return font.split(body, Math.min(pw - 40, 300) - 16);
        }

        @Override
        void cancel() {
            closeModal();
        }

        @Override
        boolean keyPressed(int key) {
            if (key == 257 || key == 335) {
                buttons.get(0).onPress();
                return true;
            }
            return true;
        }
    }

    /** the new admin's walkthrough: a few short pages, skippable, once per player (and from the ? button) */
    private final class Intro extends Modal {
        static final int PAGES = 4;
        int page;

        Intro(int page) {
            this.page = page;
            setup();
        }

        void setup() {
            buttons.clear();
            button(tr("intro.skip"), this::cancel);
            if (page > 0) button(tr("intro.back"), () -> {
                page--;
                setup();
            });
            button(tr(page == PAGES - 1 ? "intro.done" : "intro.next"), () -> {
                if (page == PAGES - 1) cancel();
                else {
                    page++;
                    setup();
                }
            });
            layout(buttonsW());
            // (Skip sits on the left)
            buttons.get(0).setX(mx0 + 8);
        }

        @Override
        Component title() {
            return tr("intro." + page + ".title");
        }

        @Override
        List<FormattedCharSequence> body() {
            return font.split(tr("intro." + page + ".body"), Math.min(pw - 40, 300) - 16);
        }

        @Override
        void extra(GuiGraphics g) {
            String n = (page + 1) + " / " + PAGES;
            g.drawString(font, n, mx0 + mw - 8 - font.width(n), my0 + 8, GREY, false);
        }

        @Override
        void cancel() {
            AdminPrefs.seenIntro(true);
            closeModal();
        }

        @Override
        boolean keyPressed(int key) {
            if (key == 262 || key == 257 || key == 335) buttons.get(buttons.size() - 1).onPress();
            else if (key == 263 && page > 0) {
                page--;
                setup();
            }
            return true;
        }
    }
}
