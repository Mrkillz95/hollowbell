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
    // (Part 2C) ink on the admin book's parchment (AdminStyle); the names are the old ones so the pages read the same
    static final int GOLD = AdminStyle.AMBER,
            TITLE = AdminStyle.RUBRIC,
            TEXT = AdminStyle.INK,
            KEY = 0xFF5A2A08,
            GREY = AdminStyle.INK_SOFT,
            DIM = AdminStyle.INK_DIM,
            ORANGE = AdminStyle.ORANGE,
            BLUE = AdminStyle.BLUE,
            WARN = AdminStyle.WARN,
            GOOD = AdminStyle.GOOD,
            PANEL = 0xF0E8D8B0,
            EDGE = 0xFF8A6A40,
            LINE = 0x30503010,
            HEAD = AdminStyle.RUBRIC;
    private static final int SET_H = 34, SIZE_H = 48, MOVE_H = 22, HEAD_H = 22;
    /** most lines a search shows */
    private static final int MOST_FOUND = 80;
    public static final String HOME = "home";
    /** Part 2B's pages (moderators only ever see LIVE) */
    public static final String LIVE = "live", PRESETS = "presets", CHANGES = "changes";
    /** (Part 5) the Players page */
    public static final String PLAYERS = "players";

    private static boolean ownPage(String key) {
        return key.equals(HOME) || key.equals(LIVE) || key.equals(PRESETS) || key.equals(CHANGES)
                || key.equals(PLAYERS) || key.equals(TOOLS)
                || key.equals(FUN) || key.equals(WORLD) || key.equals(SYSTEMS);
    }
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
    /**
     * (Part 2C) on a giant's page, which half: "general" (the settings every giant shares, his values) or "special"
     * (only his own); and on a giant's page or All giants, the section shown ("all": every section, each one folding).
     * Kept per page while the game runs.
     */
    private String part = GENERAL, section = "";
    public static final String GENERAL = "general", SPECIAL = "special", ALL_SECTIONS = "all";
    private static final Map<String, String[]> keptTabs = new HashMap<>();
    /** the groups folded shut (page/part/group), kept while the game runs */
    private static final java.util.Set<String> folded = new java.util.HashSet<>();
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
    /** (Part 2C) inside the cover; where the page starts; the breadcrumb's line; the section tabs' line and rows */
    private int inX0, inY0, inX1, inY1, pageX0, crumbY, tabY, tabRows;
    /** the contents show only their pictures */
    private boolean slim;

    public AdminBookScreen() {
        super(Component.translatable("admin.jj_giants.book.title"));
        parent = null;
    }

    /**
     * (Part 3) the book over the Create World screen's draft (AdminBookClient's local mode): the same pages, search and
     * markers, no Live control, Changes, locks or "all worlds", no "only new land" question; closing goes back to the
     * parent screen
     */
    public AdminBookScreen(net.minecraft.client.gui.screens.Screen parent) {
        super(Component.translatable("admin.jj_giants.book.creation_title"));
        this.parent = parent;
    }

    private final net.minecraft.client.gui.screens.@Nullable Screen parent;

    /** showing a new world's draft */
    private boolean creating() {
        return parent != null && AdminBookClient.creating();
    }

    @Override
    public void onClose() {
        if (parent != null) {
            minecraft.setScreen(parent);
        } else super.onClose();
    }

    /** open next time on this page, scrolled this far, with this search (the picture-taking uses this) */
    public static void showAt(String page, double scroll, String query) {
        // (Part 2C) "wreckback/special/moves": a page, its half and its section
        if (page != null && page.contains("/")) {
            String[] w = page.split("/");
            page = w[0];
            keptTabs.put(page, new String[] {w.length > 1 ? w[1] : GENERAL, w.length > 2 ? w[2] : ""});
        }
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

    /** (the picture-taking) a request as the book sends it */
    public static boolean testRequest(String json) {
        try {
            return AdminBookClient.request(com.google.gson.JsonParser.parseString(json).getAsJsonObject());
        } catch (RuntimeException e) {
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

    /** (the picture-taking) presses the open question's button whose words start so ("See all") */
    public static boolean testPressWord(String start) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof AdminBookScreen b) || b.modal == null)
            return false;
        for (Button x : List.copyOf(b.modal.buttons))
            if (x.getMessage().getString().startsWith(start)) {
                x.onPress();
                return true;
            }
        return false;
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

    /** an all-giants multiplier, shown with an "x" (not the player settings, which are switches and choices) */
    private static boolean mult(AdminSetting s) {
        return shared(s) && !AdminCatalog.plain(s);
    }

    private static boolean move(AdminSetting s) {
        return s.id().startsWith("move.");
    }

    /** a value as the book shows it: on/off, x2 for multipliers, 0.5-1.5 for a range */
    static String show(AdminSetting s, JsonElement v) {
        boolean times = mult(s) || s.id().endsWith(".cooldown") || s.id().endsWith(".wind");
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
        // (Part 8) the systems' settings have a server default of their own
        return !allWorlds || (shared(s) ? AdminCatalog.systems(s) : !move(s));
    }

    private String giantName(String key) {
        if (key.equals(AdminCatalog.ALL)) return tr("all").getString();
        if (key.equals(TOOLS)) return tt("name", "Server tools").getString();
        if (ownPage(key)) return tr(key).getString();
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
        // (a moderator has Live control only; before the server says who you are, only Home, asking)
        if (AdminBookClient.role().equals("moderator")) return List.of(LIVE);
        if (!AdminBookClient.admin()) return List.of(HOME);
        List<String> out = new ArrayList<>(creating() ? List.of(HOME, PRESETS, AdminCatalog.ALL, PLAYERS, SYSTEMS)
                : List.of(HOME, LIVE, PRESETS, CHANGES, AdminCatalog.ALL, PLAYERS, TOOLS, FUN, WORLD, SYSTEMS));
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
        if (key.equals(PLAYERS)) return playersSet();
        if (key.equals(TOOLS)) return toolsSet();
        if (key.equals(SYSTEMS)) return systemsSet();
        if (key.equals(AdminCatalog.ALL))
            return AdminBookClient.AdminJsonBridge.num(h, "allSet", 0) - playersSet() - toolsSet() - systemsSet();
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
        loadTabs();
        if (creating()) {
            allWorlds = false;
            if (pageKey.equals(LIVE) || pageKey.equals(CHANGES) || pageKey.equals(TOOLS) || pageKey.equals(FUN)
                    || pageKey.equals(WORLD))
                pageKey = HOME;
        }
        if (livePage(pageKey)) AdminBookClient.watchLive(true);
        if (pageKey.equals(TOOLS)) AdminBookClient.watchTools(true);
        if (pageKey.equals(SYSTEMS)) AdminBookClient.watchSystems(true);
        seenVersion = AdminBookClient.version();
        seenLive = AdminBookClient.liveVersion();
        seenPreview = AdminBookClient.previewVersion();
        seenSaid = AdminBookClient.saidAt();

        // (Part 2C) the book: a leather cover, the contents down the left on older paper, the page on the right
        pw = Math.min(width - 8, 760);
        ph = Math.min(height - 8, 480);
        px0 = (width - pw) / 2;
        py0 = (height - ph) / 2;
        int e = AdminStyle.EDGE;
        inX0 = px0 + e;
        inY0 = py0 + e;
        inX1 = px0 + pw - e;
        inY1 = py0 + ph - e;
        navX = inX0 + 5;
        int names = 0;
        for (String k : navKeys())
            names = Math.max(names, font.width(k.equals(HOME) ? tr("home").getString() : giantName(k)));
        // (room for the picture, the name and a count)
        navW = Mth.clamp(Math.max(pw / 5, names + 36), 80, Math.max(80, Math.min(128, pw / 4)));
        // (a narrow book, GUI scale 3 on a small window: the contents show only their pictures, names on hover)
        slim = pw < 560;
        if (slim) navW = 24;
        navY = inY0 + 18;
        int entries = Math.max(2, navKeys().size()) + 1;
        int room = inY1 - navY - 4;
        navRowH = entries * 19 <= room ? 19 : entries * 16 <= room ? 16 : 13;
        pageX0 = navX + navW + 8;
        lx = pageX0 + 10;
        lw = inX1 - 10 - 6 - lx;
        crumbY = inY0 + 6;
        toolY = crumbY + 15;
        headY = toolY + 21;
        tabY = headY + 25;
        ly0 = tabY + 10;
        ly1 = inY1 - 15;

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
        if (creating()) {
            // (no server yet: everything goes into the new world; the search takes the room)
            scopeButton.visible = false;
            search.setWidth(Math.max(70, lw - modeW - 3));
            modeButton.setX(lx + lw - modeW);
        }

        // the page's own button (reset this giant, or on Home every giant)
        headButton = addRenderableWidget(Button.builder(Component.empty(), b -> headAction())
                .bounds(0, headY + 3, 10, 16)
                .build());

        Button close = Button.builder(Component.literal("✕"), b -> onClose())
                .bounds(inX1 - 6 - 16, crumbY - 3, 16, 14)
                .build();
        close.setTooltip(Tooltip.create(Component.translatable("guide.jj_giants.close")));
        addRenderableWidget(close);
        Button help = Button.builder(Component.literal("?"), b -> openIntro(0))
                .bounds(inX1 - 6 - 16 - 18, crumbY - 3, 16, 14)
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
        } else if (!creating() && !AdminPrefs.seenIntro() && modal == null) openIntro(0);
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

        /** (Part 2C) a click on the row itself (not its buttons); top: where the row is on the screen now */
        boolean click(double mx, double my, int top) {
            return false;
        }

        <T extends AbstractWidget> T add(T w, int dx, int dy, boolean right) {
            ws.add(new Placed(addRenderableWidget(w), dx, dy, right));
            return w;
        }
    }

    /**
     * A section's (or a giant's, in a search) heading. (Part 2C) With a fold key it is a group that folds shut and
     * opens again when clicked (▶ shut, ▼ open), with how many settings it holds.
     */
    private final class Head extends Row {
        final Component title;
        final @Nullable String giant;
        final List<FormattedCharSequence> note;
        @Nullable String fold, pic;
        int count = -1;

        Head(Component title, @Nullable Component note, @Nullable String giant) {
            this.title = title;
            this.giant = giant;
            this.note = note == null ? List.of() : font.split(note, lw - 4);
            h = HEAD_H + this.note.size() * 10 + (this.note.isEmpty() ? 0 : 4);
        }

        Head folding(String key, String pic, int count) {
            this.fold = key;
            this.pic = pic;
            this.count = count;
            return this;
        }

        Head pic(String pic) {
            this.pic = pic;
            return this;
        }

        boolean shut() {
            return fold != null && folded.contains(fold);
        }

        @Override
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
            int tx = x;
            boolean over = fold != null && mx >= x && mx < x + w && my >= Math.max(y, ly0) && my < Math.min(y + HEAD_H, ly1);
            if (over) g.fill(x - 3, y + 2, x + w, y + HEAD_H - 2, AdminStyle.SHADE);
            if (fold != null) {
                // a small triangle: pointing right when shut, down when open
                int ax = x + 1, ay = y + 8;
                if (shut()) for (int i = 0; i < 4; i++) g.fill(ax + i, ay + i, ax + i + 1, ay + 7 - i, HEAD);
                else for (int i = 0; i < 4; i++) g.fill(ax + i, ay + i + 1, ax + 7 - i, ay + i + 2, HEAD);
                tx += 11;
            }
            if (giant != null) {
                icon(g, giant, tx, y + 4, 14);
                tx += 18;
            } else if (pic != null) {
                AdminStyle.icon(g, pic, tx, y + 4, 14);
                tx += 18;
            }
            int nx = g.drawString(font, title, tx, y + 8, HEAD, false);
            if (count >= 0) g.drawString(font, "(" + count + ")", nx + 4, y + 8, DIM, false);
            g.fill(x, y + HEAD_H - 3, x + w, y + HEAD_H - 2, AdminStyle.RULE);
            for (int i = 0; i < note.size(); i++)
                g.drawString(font, note.get(i), x, y + HEAD_H + 1 + i * 10, GREY, false);
            if (over) setTooltipForNextRenderPass(font.split(tr(shut() ? "fold.open" : "fold.shut"), 200));
        }

        @Override
        boolean click(double mx, double my, int top) {
            if (fold == null || my >= top + HEAD_H) return false;
            if (!folded.remove(fold)) folded.add(fold);
            double sc = scroll;
            build();
            scroll = sc;
            place();
            return true;
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

    /**
     * One setting: its name, what it does (up to two lines) and where its value comes from on the left; its control and
     * ↺ on the right. (Part 2C) On a giant's General half, a line when his value isn't the one most giants have.
     */
    private final class SetRow extends Row {
        final AdminSetting s;
        final boolean size;
        final List<FormattedCharSequence> whatLines;
        /** (General) what most giants have, when his is different; null when it's the same or nobody else has it */
        final @Nullable String most;

        SetRow(AdminSetting s) {
            this(s, null);
        }

        SetRow(AdminSetting s, @Nullable String most) {
            this.s = s;
            this.most = most;
            this.size = SIZES.contains(s.id());
            int tw = textW() - (size ? previewW() + 6 : 0);
            List<FormattedCharSequence> wl = what(s).getString().isEmpty() ? List.of() : font.split(what(s), tw);
            if (wl.size() > 2) {
                // (two lines, the rest on hover)
                String second = cut(font.split(what(s), tw).subList(1, wl.size()).stream()
                        .map(AdminBookScreen::plainText).reduce("", (a, b) -> a + (a.isEmpty() ? "" : " ") + b), tw);
                wl = List.of(wl.get(0), Component.literal(second).getVisualOrderText());
            }
            whatLines = wl;
            h = Math.max(size ? SIZE_H : SET_H, 4 + 11 + whatLines.size() * 10 + 10 + (most != null ? 10 : 0)
                    + (size ? 10 : 0) + 5);
            int cw = controlW(), dy = (h - 16) / 2;
            List<AbstractWidget> c = controls(s, cw);
            if (c.size() == 2) {
                // a range: the low end on the left, the high end on the right
                add(c.get(0), 18 + lockW(s) + c.get(1).getWidth() + 2, dy, true);
                add(c.get(1), 18 + lockW(s), dy, true);
            } else for (AbstractWidget w : c) add(w, 18 + lockW(s), dy, true);
            reset = add(resetButton(List.of(s)), 0, dy, true);
            if (lockW(s) > 0) lock = add(lockButton(s), 18, dy, true);
            if (lockedForMe(s))
                for (Placed p : ws)
                    if (p.w() != lock) {
                        p.w().active = false;
                        p.w().setTooltip(Tooltip.create(tr("lock.locked.tip", lockedBy(s))));
                    }
        }

        @Nullable Button lock;

        int textW() {
            return lw - controlW() - 26 - lockW(s);
        }

        @Override
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
            boolean ch = changed(s), can = editable(s), overPicture = false;
            if (ch) {
                g.fill(x - 4, y + 1, x + w, y + h - 1, AdminStyle.TINT);
                g.fill(x - 4, y + 2, x - 2, y + h - 2, GOLD);
            }
            int tw = textW();
            // line 1: the name, "?", and when it takes effect
            Component marker = marker(s);
            String full = name(s).getString();
            if (!marker.getString().isEmpty() && font.width(full) + 12 + font.width(marker) + 4 > tw)
                marker = shortMarker(s);
            int markerW = marker.getString().isEmpty() ? 0 : font.width(marker) + 6;
            String nm = cut(full, Math.max(30, tw - 12 - markerW));
            int nx = g.drawString(font, nm, x, y + 4, !can ? GREY : ch ? GOLD : TEXT, false);
            int qx = nx + 3;
            boolean overQ = mx >= qx - 1 && mx < qx + 8 && my >= y + 3 && my < y + 13;
            g.fill(qx - 1, y + 3, qx + 7, y + 13, overQ ? 0xFF2A5E9A : 0x302A5E9A);
            g.drawString(font, "?", qx + 1, y + 4, overQ ? 0xFFFFFFFF : BLUE, false);
            if (markerW > 0) {
                int mx0 = qx + 11, mw0 = font.width(marker) + 4;
                g.fill(mx0 - 1, y + 3, mx0 + mw0 - 1, y + 13, (markerColour(s) & 0x00FFFFFF) | 0x22000000);
                g.drawString(font, marker, mx0 + 1, y + 4, markerColour(s), false);
            }
            // what it does, up to two lines (a size's leave room for its picture)
            int lineW = size ? tw - previewW() - 6 : tw;
            int ty = y + 15;
            for (FormattedCharSequence l : whatLines) {
                g.drawString(font, l, x, ty, GREY, false);
                ty += 10;
            }
            // where the value comes from: the server default, or what this world keeps
            MutableComponent l2 = line2(s, false);
            if (font.width(l2) > lineW) l2 = line2(s, true);
            String l2s = cut(l2.getString(), lineW);
            if (l2s.equals(l2.getString())) g.drawString(font, l2, x, ty, DIM, false);
            else g.drawString(font, l2s, x, ty, DIM, false);
            ty += 10;
            if (most != null) {
                g.drawString(font, cut(tr("general.differs", most).getString(), lineW), x, ty, BLUE, false);
                ty += 10;
            }
            if (size) {
                double v = number(shown(s));
                String key = s.giant();
                double tall = (shared(s) ? 200 : body(key)[0]) * v;
                int bx = x + tw - previewW();
                // (under the name's line, so its marker has the whole width)
                preview(g, bx, y + 15, previewW(), h - 20, shared(s) ? "" : key, v);
                String t = tr(shared(s) ? "size.all" : "size.tall", AdminSetting.plain(Math.round(tall))).getString();
                if (ty + 8 <= y + h) g.drawString(font, cut(t, lineW), x, ty, GREY, false);
                if (mx >= bx && mx < bx + previewW() && my >= Math.max(y + 15, ly0) && my < Math.min(y + h - 4, ly1)) {
                    setTooltipForNextRenderPass(font.split(tr(shared(s) ? "size.tip.all" : "size.tip",
                            AdminSetting.plain(Math.round(tall))), 220));
                    overPicture = true;
                }
            }
            AdminStyle.dots(g, x, y + h - 1, w);
            if (ch || pending.containsKey(s.giant() + "/" + s.id()))
                for (Placed p : ws)
                    if (p.w() != reset && p.w() != lock && p.w().visible)
                        g.renderOutline(p.w().getX() - 1, p.w().getY() - 1, p.w().getWidth() + 2, 18,
                                pending.containsKey(s.giant() + "/" + s.id()) ? GREY : GOLD);
            if (!overPicture && mx >= x - 4 && mx < x + tw && my >= Math.max(y, ly0) && my < Math.min(y + h, ly1))
                setTooltipForNextRenderPass(tip(s));
        }
    }

    /** a line's plain words */
    static String plainText(FormattedCharSequence f) {
        StringBuilder b = new StringBuilder();
        f.accept((i, st, c) -> {
            b.appendCodePoint(c);
            return true;
        });
        return b.toString();
    }

    private int previewW() {
        return Mth.clamp((lw - controlW() - 22) / 4, 40, 64);
    }

    /** what line 2 says: where the value comes from */
    private MutableComponent line2(AdminSetting s, boolean narrow) {
        if (s.giant().equals(EVERY)) return everyLine(s);
        String n = narrow ? ".short" : "";
        if (allWorlds) {
            if (shared(s) && !AdminCatalog.systems(s) || move(s)) return tr("line.world_only" + n);
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
                : (mult(s) || move(s) && s.type() != AdminSetting.Type.BOOL ? "x" : "") + s.rangeText())
                .withStyle(ChatFormatting.GRAY), 260));
        t.addAll(font.split(tr("tip.values", show(s, s.value()), show(s, s.def()), show(s, s.jar()))
                .withStyle(ChatFormatting.GRAY), 260));
        t.addAll(font.split(tr(s.set() ? "tip.set" : "tip.unset").withStyle(s.set() ? ChatFormatting.GOLD
                : ChatFormatting.GRAY), 260));
        if (!s.whenNote().isEmpty())
            t.addAll(font.split(Component.literal(s.whenNote()).withStyle(s.creationOnly() ? ChatFormatting.GOLD
                    : ChatFormatting.AQUA), 260));
        if (!editable(s)) t.addAll(font.split(tr("tip.world_only").withStyle(ChatFormatting.RED), 260));
        if (lockedBy(s) != null)
            t.addAll(font.split(tr("lock.locked.tip", lockedBy(s)).withStyle(ChatFormatting.GOLD), 260));
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
            reset = add(resetButton(parts), 0, 3, true);
            int dx = 18;
            for (int i = parts.size() - 1; i >= 0; i--) {
                AdminSetting s = parts.get(i);
                int w = s.type() == AdminSetting.Type.BOOL ? onW : sw;
                List<AbstractWidget> c = controls(s, w);
                if (c.size() != 1) continue;
                add(c.get(0), dx, 3, true);
                dx += w + 2;
            }
        }

        @Override
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
            boolean any = false;
            for (AdminSetting s : parts) any |= changed(s);
            if (any) {
                g.fill(x - 4, y + 1, x + w, y + h - 1, AdminStyle.TINT);
                g.fill(x - 4, y + 2, x - 2, y + h - 2, GOLD);
            }
            int nameW = lw - moveW() - 22;
            g.drawString(font, cut(name, nameW), x, y + 7, any ? GOLD : TEXT, false);
            AdminStyle.dots(g, x, y + h - 1, w);
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
                if (s.giant().equals(EVERY)) {
                    if (allWorlds) AdminBookClient.setAll(s.id(), AdminSetting.text(s.jar()), true);
                    else AdminBookClient.resetAll(s.id());
                } else AdminBookClient.reset(s.giant(), s.id(), allWorlds);
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
            boolean sent = s.giant().equals(EVERY)
                    ? AdminBookClient.setAll(s.id(), AdminSetting.text(v), allWorlds)
                    : AdminBookClient.set(s.giant(), s.id(), AdminSetting.text(v), allWorlds);
            if (!sent) {
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
        // (a new world's draft: there is no land yet, nothing to warn about)
        if (creating()) {
            go.run();
            return;
        }
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
            boolean times = mult(s) || move(s);
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
                if (p.w() instanceof LockButton) continue;
                AdminSetting s = p.w() == r.reset ? null : r instanceof MoveRow mr ? mr.partOf(p.w()) : ((SetRow) r).s;
                if (s == null) {
                    // the ↺
                    boolean any = false;
                    for (AdminSetting x : ss) any |= changed(x);
                    boolean can = ss.stream().allMatch(this::editable) && ss.stream().noneMatch(this::lockedForMe);
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
        boolean admin = AdminBookClient.admin();
        // (a moderator: no search, no modes, no saving scope; Live control only)
        search.visible = admin;
        modeButton.visible = admin;
        scopeButton.visible = admin && !creating();
        layoutTabs();
        if (!q.isEmpty() && admin) searchRows(q);
        else if (pageKey.equals(HOME)) homeRows();
        else if (pageKey.equals(LIVE)) liveRows();
        else if (pageKey.equals(PRESETS)) presetRows();
        else if (pageKey.equals(CHANGES)) changeRows();
        else if (pageKey.equals(PLAYERS)) playersRows();
        else if (pageKey.equals(TOOLS)) toolsRows();
        else if (pageKey.equals(FUN)) funRows();
        else if (pageKey.equals(WORLD)) worldRows();
        else if (pageKey.equals(SYSTEMS)) systemsRows();
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
        if (pageKey.equals(LIVE) || pageKey.equals(PRESETS) || pageKey.equals(CHANGES) || pageKey.equals(PLAYERS)
                || pageKey.equals(TOOLS) || pageKey.equals(FUN) || pageKey.equals(WORLD) || pageKey.equals(SYSTEMS))
            return;
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

    // (Part 2C) the settings every giant shares, and the sections

    /** the "giant" of an All giants row that sets every giant's own value at once */
    static final String EVERY = "*every";
    /** each every-giant row's giants' own settings, by id (filled when the All giants page is built) */
    private final Map<String, List<AdminSetting>> everyRows = new LinkedHashMap<>();

    /** the giants' pages (not All giants) */
    private List<AdminBookClient.Page> giantPages() {
        List<AdminBookClient.Page> out = new ArrayList<>();
        for (AdminBookClient.Page p : AdminBookClient.pages()) if (!p.giant().equals(AdminCatalog.ALL)) out.add(p);
        return out;
    }

    /**
     * The settings the giants share: an id two or more giants have (one giant alone: all of his), never a move's.
     * In the order the first giant lists them.
     */
    private List<String> sharedIds() {
        List<AdminBookClient.Page> ps = giantPages();
        Map<String, Integer> n = new LinkedHashMap<>();
        for (AdminBookClient.Page p : ps)
            for (AdminSetting s : p.settings())
                if (s.group().isEmpty() && net.jj.giants.admin.AdminEvery.shareable(s.id()))
                    n.merge(s.id(), 1, Integer::sum);
        int need = Math.min(2, ps.size());
        List<String> out = new ArrayList<>();
        for (var e : n.entrySet()) if (e.getValue() >= need) out.add(e.getKey());
        return out;
    }

    /** each giant's row of a shared setting */
    private List<AdminSetting> everyOf(String id) {
        List<AdminSetting> out = new ArrayList<>();
        for (AdminBookClient.Page p : giantPages()) {
            AdminSetting s = p.setting(id);
            if (s != null) out.add(s);
        }
        return out;
    }

    /** the value most of them have (as text) */
    private static String most(List<AdminSetting> ss, java.util.function.Function<AdminSetting, JsonElement> f) {
        Map<String, Integer> n = new LinkedHashMap<>();
        for (AdminSetting s : ss) n.merge(AdminSetting.text(f.apply(s)), 1, Integer::sum);
        String best = "";
        int bn = -1;
        for (var e : n.entrySet())
            if (e.getValue() > bn) {
                best = e.getKey();
                bn = e.getValue();
            }
        return best;
    }

    private static @Nullable JsonElement valueOf(List<AdminSetting> ss, String text,
            java.util.function.Function<AdminSetting, JsonElement> f) {
        for (AdminSetting s : ss) if (AdminSetting.text(f.apply(s)).equals(text)) return f.apply(s);
        return ss.isEmpty() ? null : f.apply(ss.get(0));
    }

    /**
     * One row standing for every giant's own setting: the widest range any of them takes (each keeps to his own when
     * it is set), the options any of them has, the value most have.
     */
    private AdminSetting everyProxy(List<AdminSetting> ss) {
        AdminSetting a = ss.get(0);
        double min = a.min(), max = a.max(), step = step(a);
        List<String> options = new ArrayList<>(a.options());
        boolean set = false;
        for (AdminSetting s : ss) {
            min = Math.min(min, s.min());
            max = Math.max(max, s.max());
            step = Math.min(step, step(s));
            for (String o : s.options()) if (!options.contains(o)) options.add(o);
            set |= s.set();
        }
        java.util.function.Function<AdminSetting, JsonElement> value = AdminSetting::value, def = AdminSetting::def,
                jar = AdminSetting::jar;
        JsonElement v = valueOf(ss, most(ss, value), value), d = valueOf(ss, most(ss, def), def),
                j = valueOf(ss, most(ss, jar), jar);
        return new AdminSetting(EVERY, a.id(), a.cat(), a.type(), min, max, step, List.copyOf(options), d, v, j, set,
                a.when(), a.name(), a.what(), a.key(), "");
    }

    /** what an every-giant row says about the giants' values: all the same, or who differs */
    private MutableComponent everyLine(AdminSetting proxy) {
        List<AdminSetting> ss = everyRows.getOrDefault(proxy.id(), List.of());
        java.util.function.Function<AdminSetting, JsonElement> f = allWorlds ? AdminSetting::def : AdminSetting::value;
        String m = most(ss, f);
        List<String> odd = new ArrayList<>();
        for (AdminSetting s : ss)
            if (!AdminSetting.text(f.apply(s)).equals(m)) odd.add(giantName(s.giant()) + " " + show(s, f.apply(s)));
        long setN = ss.stream().filter(AdminSetting::set).count();
        MutableComponent c = odd.isEmpty() ? tr("every.same", ss.size(), m)
                : tr("every.mixed", String.join(", ", odd), m);
        if (!allWorlds && setN > 0) c.append(tr("every.set", setN));
        return c;
    }

    /** an all-giants multiplier (shown with an "x") */
    private static boolean multiplier(AdminSetting s) {
        return s.giant().equals(AdminCatalog.ALL);
    }

    /** a setting's section (ADMIN_BRIDGE.md 21.3) */
    private static String sectionOf(AdminSetting s) {
        return net.jj.giants.admin.AdminJson.section(s);
    }

    /** what a sectioned page shows: the settings in it (General/Special on a giant's page), by section */
    private Map<String, List<AdminSetting>> sectionsOf(String key) {
        Map<String, List<AdminSetting>> out = new LinkedHashMap<>();
        for (String sec : net.jj.giants.admin.AdminJson.SECTIONS) out.put(sec, new ArrayList<>());
        AdminBookClient.Page p = AdminBookClient.page(key);
        if (p == null) return out;
        if (key.equals(AdminCatalog.ALL)) {
            // the multipliers, then every shared setting (one row each, standing for every giant)
            everyRows.clear();
            for (AdminSetting s : p.settings())
                // (Part 5: the player settings have their own page)
                if (!AdminCatalog.plain(s)) out.get(sectionOf(s)).add(s);
            for (String id : sharedIds()) {
                List<AdminSetting> ss = everyOf(id);
                if (ss.isEmpty()) continue;
                everyRows.put(id, ss);
                out.get(sectionOf(ss.get(0))).add(everyProxy(ss));
            }
        } else {
            List<String> shared = sharedIds();
            for (AdminSetting s : p.settings()) {
                boolean general = shared.contains(s.id()) && s.group().isEmpty();
                if (general == part.equals(GENERAL)) out.get(sectionOf(s)).add(s);
            }
        }
        out.values().removeIf(List::isEmpty);
        return out;
    }

    /** the section tabs shown now: "all", then each section the page has */
    private List<String> sectionTabs() {
        if (pageKey.equals(TOOLS) && keptQuery.isBlank()) return TOOL_TABS;
        if (!sectioned()) return List.of();
        List<String> out = new ArrayList<>();
        out.add(ALL_SECTIONS);
        out.addAll(sectionsOf(pageKey).keySet());
        return out;
    }

    /** the section to show: the one picked if the page has it, else the first */
    private String sectionNow(List<String> tabs) {
        if (tabs.contains(section)) return section;
        if (pageKey.equals(TOOLS) && !tabs.isEmpty()) return tabs.get(0);
        return tabs.size() > 1 ? tabs.get(1) : ALL_SECTIONS;
    }

    /** a giant's page or All giants: the section picked (or every section, each folding), the moves one to a line */
    private void settingRows(String key) {
        AdminBookClient.Page p = AdminBookClient.page(key);
        if (p == null) {
            JsonObject g = homeGiant(key);
            boolean asking = AdminBookClient.home() == null || g != null && g.get("speaks") != null
                    && g.get("speaks").getAsBoolean();
            rows.add(new Note(asking ? tr("asking") : tr("no_settings", giantName(key)), GREY));
            return;
        }
        Map<String, List<AdminSetting>> secs = sectionsOf(key);
        List<String> tabs = new ArrayList<>(List.of(ALL_SECTIONS));
        tabs.addAll(secs.keySet());
        String now = sectionNow(tabs);
        boolean all = now.equals(ALL_SECTIONS);
        if (key.equals(AdminCatalog.ALL)) {
            if (all) rows.add(new Note(tr("all.page.note"), GREY));
        } else if (part.equals(SPECIAL) && secs.isEmpty())
            rows.add(new Note(tr("special.none", giantName(key)), GREY));
        int hidden = 0;
        for (var e : secs.entrySet()) {
            String sec = e.getKey();
            if (!all && !sec.equals(now)) continue;
            List<AdminSetting> plain = new ArrayList<>(), mult = new ArrayList<>(), every = new ArrayList<>();
            Map<String, List<AdminSetting>> groups = new LinkedHashMap<>();
            int shown = 0;
            for (AdminSetting s : e.getValue()) {
                boolean show = advanced || AdminInsight.simple(s.id());
                if (!show) {
                    hidden++;
                    continue;
                }
                shown++;
                if (multiplier(s)) mult.add(s);
                else if (s.giant().equals(EVERY)) every.add(s);
                else if (s.group().isEmpty()) plain.add(s);
                else groups.computeIfAbsent(s.group(), k -> new ArrayList<>()).add(s);
            }
            if (shown == 0) continue;
            String base = key + "/" + (key.equals(AdminCatalog.ALL) ? "" : part) + "/" + sec;
            Head head = new Head(tr("cat." + sec), null, null);
            if (all) {
                head.folding(base, sec, shown);
                rows.add(head);
                if (head.shut()) continue;
            } else rows.add(head.pic(sec));
            // All giants: the multipliers, then every giant's own value
            if (!mult.isEmpty()) {
                Head mh = new Head(tr("all.mult"), tr("all.mult.note"), null)
                        .folding(base + "/mult", "all", mult.size());
                rows.add(mh);
                if (!mh.shut()) for (AdminSetting s : mult) rows.add(new SetRow(s));
            }
            if (!every.isEmpty()) {
                Head eh = new Head(tr("all.every"), tr("all.every.note"), null)
                        .folding(base + "/every", "general", every.size());
                rows.add(eh);
                if (!eh.shut()) for (AdminSetting s : every) rows.add(new SetRow(s));
            }
            for (AdminSetting s : plain) rows.add(new SetRow(s, differs(s)));
            if (!groups.isEmpty()) {
                boolean wind = groups.values().stream()
                        .anyMatch(l -> l.stream().anyMatch(x -> x.id().endsWith(".wind")));
                Head gh = new Head(tr("moves.each"), tr(wind ? "moves.note" : "moves.note_nowind"), null)
                        .folding(base + "/each", "moves", groups.size());
                rows.add(gh);
                if (!gh.shut())
                    for (List<AdminSetting> gr : groups.values()) rows.add(new MoveRow(moveName(gr.get(0)), gr));
            }
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

    /** (General) what most giants have for this one when his own value is different; else null */
    private @Nullable String differs(AdminSetting s) {
        if (!part.equals(GENERAL) || s.giant().equals(AdminCatalog.ALL)) return null;
        List<AdminSetting> ss = everyOf(s.id());
        if (ss.size() < 2) return null;
        java.util.function.Function<AdminSetting, JsonElement> f = allWorlds ? AdminSetting::def : AdminSetting::value;
        String m = most(ss, f);
        if (AdminSetting.text(f.apply(s)).equals(m)) return null;
        JsonElement v = valueOf(ss, m, f);
        return v == null ? m : show(s, v);
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
                g.fill(bx, y + 3, bx + 11, y + 11, i < level ? colours[level - 1] : 0x30503010);
            }
            g.drawString(font, level + " · " + label, nx + 5 * 13 + 4, y + 4, colours[level - 1], false);
            for (int i = 0; i < reason.size(); i++)
                g.drawString(font, reason.get(i), x + 8, y + 16 + i * 10, TEXT, false);
            if (mx >= x && mx < x + w && my >= y && my < y + 14)
                setTooltipForNextRenderPass(font.split(tr("home.danger.tip"), 280));
        }));
        // (Part 6) what the server tools have on now
        if (!creating()) toolsHomeLine(h);
        // what clashes
        JsonArray warn = h.get("warnings") instanceof JsonArray a ? a : new JsonArray();
        Head clashes = new Head(tr("home.clashes"), null, null).folding("home/clashes", "behaviour", warn.size());
        rows.add(clashes);
        if (warn.isEmpty()) rows.add(new Note(tr("home.no_clashes"), GOOD));
        for (JsonElement e : clashes.shut() ? new JsonArray() : warn) {
            if (!e.isJsonObject()) continue;
            String text = AdminBookClient.AdminJsonBridge.str(e.getAsJsonObject(), "text");
            List<FormattedCharSequence> ls = font.split(Component.literal(text), lw - 12);
            rows.add(new Block(ls.size() * 10 + 4, (g, x, y, w, mx, my) -> {
                g.drawString(font, "⚠", x, y + 1, KEY, false);
                for (int i = 0; i < ls.size(); i++) g.drawString(font, ls.get(i), x + 11, y + 1 + i * 10, TEXT, false);
            }));
        }
        // the giants: a tile each, the all-giants one first
        rows.add(new Head(tr("home.pages"), null, null).pic("all"));
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
        rows.add(new Head(tr("home.quick"), null, null).pic("search"));
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
        } else if (!creating()) rows.add(new Note(tr("home.no_changes"), GREY));
        if (creating()) {
            rows.add(new Note(tr("creation.home.note"), GREY));
            return;
        }
        moderatorRows(h);
    }

    /** where Home's giant tiles are (for clicks) */
    private record TileLine(Row row, List<String> keys, int tileW, int tileH) {}

    private final List<TileLine> tiles = new ArrayList<>();

    private void tile(GuiGraphics g, String key, int x, int y, int w, int h, int mx, int my) {
        boolean over = mx >= x && mx < x + w && my >= y && my < y + h && my >= ly0 && my < ly1;
        AdminStyle.card(g, x, y, w, h, over);
        icon(g, key, x + 3, y + 3, h - 6);
        int tx = x + h, tw = w - h - 3;
        g.drawString(font, cut(giantName(key), tw), tx, y + 5, TEXT, false);
        int n = setCount(key);
        JsonObject gj = homeGiant(key);
        boolean speaks = key.equals(AdminCatalog.ALL) || gj == null || gj.get("speaks") == null
                || gj.get("speaks").getAsBoolean();
        Component sub = key.equals(TOOLS) ? tt("tile", "Pause, events, map, stats")
                : ownPage(key) ? tr(key + ".tile") : !speaks ? tr("tile.none") : n > 0 ? tr("tile.set", n)
                : tr("tile.default");
        g.drawString(font, cut(sub.getString(), tw), tx, y + 16, !speaks ? DIM : n > 0 ? GOLD : GREY, false);
    }

    /** a giant's picture (the book, or the Guide for "all giants"), size x size */
    private void icon(GuiGraphics g, String key, int x, int y, int size) {
        if (ownPage(key) || key.equals(AdminCatalog.ALL)) {
            // (Part 2C) the book's own little pictures: home, live, presets, changes, all (a crown)
            AdminStyle.icon(g, key, x, y, size);
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
        // (the server sends Live control once a second only while it is looked at)
        if (livePage(key) != livePage(pageKey)) AdminBookClient.watchLive(livePage(key));
        // (Part 6) the Server tools page comes every two seconds only while it is looked at
        if (key.equals(TOOLS) != pageKey.equals(TOOLS)) AdminBookClient.watchTools(key.equals(TOOLS));
        // (Part 8) and the Systems page
        if (key.equals(SYSTEMS) != pageKey.equals(SYSTEMS)) AdminBookClient.watchSystems(key.equals(SYSTEMS));
        pageKey = key;
        keptPage = key;
        loadTabs();
        keptQuery = "";
        search.setValue("");
        scroll = 0;
        build();
    }

    // (Part 2C) a giant's halves and every page's sections

    /** the half and section this page was last on */
    private void loadTabs() {
        String[] t = keptTabs.get(pageKey);
        part = t == null ? GENERAL : t[0].equals(SPECIAL) ? SPECIAL : GENERAL;
        section = t == null ? "" : t[1];
    }

    private void saveTabs() {
        keptTabs.put(pageKey, new String[] {part, section});
    }

    /** General or Special on a giant's page */
    private void pickPart(String p) {
        if (p.equals(part) && keptQuery.isBlank()) return;
        part = p;
        section = "";
        keptQuery = "";
        search.setValue("");
        saveTabs();
        scroll = 0;
        build();
    }

    /** one section (or "all") */
    private void pickSection(String sec) {
        section = sec;
        saveTabs();
        scroll = 0;
        build();
    }

    /** a page made of sections: a giant's, or All giants */
    private boolean sectioned() {
        return keptQuery.isBlank() && !ownPage(pageKey) && AdminBookClient.page(pageKey) != null;
    }

    /** a giant's own page (General and Special) */
    private boolean giantPage() {
        return sectioned() && !pageKey.equals(AdminCatalog.ALL);
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
            // (a moderator's book: Live control is its only page)
            if (!AdminBookClient.role().isEmpty() && !navKeys().contains(pageKey)) go(navKeys().get(0));
            rebuild();
        }
        // the Live control page, once a second while it is open (not while something is held or asked)
        if (AdminBookClient.liveVersion() != seenLive && !mouseDown && !typing()) {
            seenLive = AdminBookClient.liveVersion();
            if (livePage(pageKey) && keptQuery.isBlank()) rebuild();
        }
        // a preview from the server (a preset, an import, a snapshot): asked about over the page
        if (AdminBookClient.previewVersion() != seenPreview) {
            seenPreview = AdminBookClient.previewVersion();
            JsonObject pv = AdminBookClient.preview();
            if (pv != null) {
                modal = new Preview(pv);
                place();
            }
        }
    }

    private int seenLive = -1, seenPreview = -1;

    private boolean typing() {
        for (Row r : rows)
            for (Placed p : r.ws)
                if (p.w() instanceof EditBox b && b.isFocused()) return true;
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
        boolean there = switch (pageKey) {
            case LIVE -> AdminBookClient.live() != null;
            case PRESETS -> AdminBookClient.presets() != null;
            case CHANGES -> AdminBookClient.changes() != null;
            case PLAYERS -> AdminBookClient.players() != null && AdminBookClient.page(AdminCatalog.ALL) != null;
            case TOOLS -> AdminBookClient.tools() != null;
            case FUN, WORLD -> AdminBookClient.fun() != null && AdminBookClient.live() != null;
            case SYSTEMS -> AdminBookClient.page(AdminCatalog.ALL) != null;
            default -> AdminBookClient.home() != null
                    && (pageKey.equals(HOME) || AdminBookClient.page(pageKey) != null || !keptQuery.isBlank());
        };
        if (wantScroll >= 0 && there) {
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
        lastMx = mx;
        lastMy = my;
        super.render(g, held ? -1000 : mx, held ? -1000 : my, partial);
        if (modal != null) modal.render(g, mx, my, partial);
    }

    /** (Part 2C) a place that does something when clicked: the contents, the breadcrumb, the tabs */
    private record Zone(int x, int y, int w, int h, Runnable go) {}

    private final List<Zone> zones = new ArrayList<>();

    /** a section tab: where it is, and whether it shows its name (a narrow book shows only the picture) */
    private record TabBox(String sec, int x, int y, int w, boolean named) {}

    private final List<TabBox> tabBoxes = new ArrayList<>();

    private Component sectionName(String sec) {
        if (sec.startsWith("t_")) return toolTabName(sec);
        return sec.equals(ALL_SECTIONS) ? tr("section.all") : tr("cat." + sec);
    }

    /** the section tabs for this page, in one or two lines; where the list starts under them */
    private void layoutTabs() {
        tabBoxes.clear();
        List<String> tabs = keptQuery.isBlank() ? sectionTabs() : List.of();
        if (tabs.isEmpty()) {
            tabRows = 0;
            ly0 = headY + 25 + 10;
            return;
        }
        String now = sectionNow(tabs);
        for (int pass = 0; pass < 2; pass++) {
            tabBoxes.clear();
            int x = lx, row = 0;
            for (String sec : tabs) {
                boolean named = pass == 0 || sec.equals(now) || sec.equals(ALL_SECTIONS);
                int w = named ? 22 + font.width(sectionName(sec)) : 20;
                if (x > lx && x + w > lx + lw) {
                    x = lx;
                    row++;
                }
                tabBoxes.add(new TabBox(sec, x, tabY + row * 17, w, named));
                x += w + 2;
            }
            tabRows = row + 1;
            if (tabRows <= 2) break;
        }
        ly0 = tabY + tabRows * 17 + 7;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        super.renderBackground(g, mx, my, partial);
        zones.clear();
        // the cover, the contents' older paper, the page
        AdminStyle.frame(g, px0, py0, pw, ph);
        AdminStyle.index(g, inX0, inY0, pageX0 - inX0, inY1 - inY0);
        AdminStyle.page(g, pageX0, inY0, inX1 - pageX0, inY1 - inY0);
        // the title on a gilt label over the top edge
        label(g, font, title, px0 + pw / 2, py0);
        drawNav(g, mx, my);
        drawCrumb(g, mx, my);
        drawHead(g, mx, my);
        drawTabs(g, mx, my);
        drawList(g, mx, my);
        if (!status.getString().isEmpty() && Util.getMillis() - statusAt < 8000) {
            String line = cut(status.getString(), lw);
            g.fill(lx - 4, inY1 - 13, lx + lw + 4, inY1 - 2, 0x30FFF8E0);
            g.drawString(font, line, lx, inY1 - 11, statusColour, false);
            // (all of it on hover, when it didn't fit)
            if (!line.equals(status.getString()) && modal == null && mx >= lx && mx < lx + lw && my >= inY1 - 12
                    && my < inY1 - 2)
                setTooltipForNextRenderPass(font.split(status, 300));
        } else if (AdminBookClient.home() == null)
            g.drawString(font, tr("asking"), lx, inY1 - 11, GREY, false);
    }

    /** a gilt label with words on it, centred on x, over the cover's top edge at y */
    static void label(GuiGraphics g, net.minecraft.client.gui.Font font, Component t, int x, int y) {
        int tw = font.width(t) + 24, tx = x - tw / 2;
        g.fill(tx, y, tx + tw, y + 16, 0xFF140A0C);
        g.fill(tx + 1, y + 1, tx + tw - 1, y + 15, 0xFF5A1A30);
        g.renderOutline(tx + 2, y + 2, tw - 4, 12, 0xFFE8B840);
        g.drawString(font, t, x - font.width(t) / 2, y + 4, AdminStyle.GILT, true);
    }

    /** the contents down the left: the book's own pages, then the giants; the page open has a red ribbon */
    private void drawNav(GuiGraphics g, int mx, int my) {
        if (slim) AdminStyle.icon(g, "presets", navX + 3, inY0 + 2, 12);
        else g.drawString(font, tr("contents"), navX + 2, inY0 + 5, HEAD, false);
        g.fill(navX + 2, inY0 + 14, navX + navW - 4, inY0 + 15, AdminStyle.RULE);
        List<String> keys = navKeys();
        int y = navY;
        boolean giants = false;
        for (String key : keys) {
            if (!giants && !ownPage(key) && !key.equals(AdminCatalog.ALL)) {
                // (a gap and a word before the giants)
                giants = true;
                if (y + navRowH + 4 <= inY1 - 4) {
                    AdminStyle.dots(g, navX + 2, y + 2, navW - 6);
                    y += 5;
                }
            }
            if (y + navRowH > inY1 - 2) break;
            boolean sel = key.equals(pageKey) && keptQuery.isBlank();
            boolean over = modal == null && mx >= navX && mx < navX + navW && my >= y && my < y + navRowH - 1;
            if (sel) {
                // a ribbon from the contents into the page
                g.fill(navX - 3, y, pageX0 + 3, y + navRowH - 1, 0xFF7A2214);
                g.fill(navX - 3, y, pageX0 + 3, y + 1, 0xFFA8442A);
                g.fill(navX - 3, y + navRowH - 2, pageX0 + 3, y + navRowH - 1, 0xFF4A1208);
            } else if (over) g.fill(navX, y, navX + navW, y + navRowH - 1, AdminStyle.SHADE);
            int pic = navRowH - 5;
            icon(g, key, navX + 2, y + 2, pic);
            Component name = key.equals(HOME) ? tr("home") : Component.literal(giantName(key));
            JsonObject gj = homeGiant(key);
            boolean speaks = gj == null || gj.get("speaks") == null || gj.get("speaks").getAsBoolean();
            int n = key.equals(HOME) ? 0 : setCount(key);
            String count = n > 0 ? String.valueOf(n) : "";
            int cw = count.isEmpty() ? 0 : font.width(count) + 6;
            int tx = navX + pic + 6, tww = navW - pic - 9 - cw;
            int ty = y + (navRowH - 1 - 8) / 2;
            if (slim) {
                if (n > 0) g.fill(navX + navW - 5, y + 1, navX + navW - 1, y + 5, 0xFFA06208);
                if (over) {
                    List<FormattedCharSequence> tip = new ArrayList<>();
                    tip.add(name.copy().withStyle(ChatFormatting.YELLOW).getVisualOrderText());
                    if (n > 0) tip.addAll(font.split(tr("nav.set", giantName(key), n), 220));
                    setTooltipForNextRenderPass(tip);
                }
            } else
                g.drawString(font, cut(name.getString(), tww), tx, ty, sel ? AdminStyle.GILT : !speaks ? DIM : TEXT,
                        false);
            if (n > 0 && !slim) {
                int bx = navX + navW - 3 - font.width(count) - 2;
                g.fill(bx - 1, ty - 1, bx + font.width(count) + 3, ty + 9, sel ? 0xFFE8B840 : 0xFFA06208);
                g.drawString(font, count, bx + 1, ty, sel ? 0xFF3A1008 : 0xFFFFF4D0, false);
            }
            if (over && n > 0 && !slim) setTooltipForNextRenderPass(font.split(tr("nav.set", giantName(key), n), 220));
            String k = key;
            zones.add(new Zone(navX, y, navW, navRowH - 1, () -> go(k)));
            y += navRowH;
        }
    }

    /** "Home › Wreckback › Special › Moves": where you are; each step but the last takes you there */
    private void drawCrumb(GuiGraphics g, int mx, int my) {
        List<Component> words = new ArrayList<>();
        List<Runnable> goes = new ArrayList<>();
        words.add(tr("home"));
        goes.add(() -> go(HOME));
        String q = keptQuery.strip();
        if (!q.isEmpty()) {
            words.add(tr("crumb.search", q));
            goes.add(null);
        } else if (!pageKey.equals(HOME)) {
            words.add(Component.literal(giantName(pageKey)));
            goes.add(sectioned() ? () -> pickSection(ALL_SECTIONS) : null);
            if (pageKey.equals(TOOLS)) {
                // (Part 6) Home › Server tools › Map
                goes.set(goes.size() - 1, () -> pickSection(TOOL_TABS.get(0)));
                words.add(sectionName(sectionNow(TOOL_TABS)));
                goes.add(null);
            }
            if (giantPage()) {
                words.add(tr("part." + part));
                goes.add(() -> pickSection(ALL_SECTIONS));
            }
            if (sectioned()) {
                String now = sectionNow(sectionTabs());
                words.add(sectionName(now));
                goes.add(null);
            }
        }
        // the last step is where you are: nothing to click
        goes.set(goes.size() - 1, null);
        int right = inX1 - 6 - 16 - 18 - 6, x = lx;
        String sep = " › ";
        AdminStyle.icon(g, "home", lx - 1, crumbY - 2, 10);
        x += 12;
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i).getString();
            boolean last = i == words.size() - 1;
            int ww = font.width(w);
            if (x + ww > right) w = cut(w, Math.max(10, right - x));
            ww = font.width(w);
            Runnable r = goes.get(i);
            boolean over = r != null && modal == null && mx >= x && mx < x + ww && my >= crumbY - 1 && my < crumbY + 9;
            g.drawString(font, w, x, crumbY, last ? KEY : over ? 0xFFC0401C : HEAD, false);
            if (r != null) {
                g.fill(x, crumbY + 9, x + ww, crumbY + 10, over ? 0xFFC0401C : 0x608A2A14);
                zones.add(new Zone(x, crumbY - 1, ww, 11, r));
            }
            x += ww;
            if (!last) x = g.drawString(font, sep, x, crumbY, DIM, false);
            if (x >= right) break;
        }
    }

    /** the page's heading: its picture and words; on a giant's page, his General and Special halves as tabs */
    private void drawHead(GuiGraphics g, int mx, int my) {
        Component title, sub;
        String q = keptQuery.strip();
        String pic;
        if (!q.isEmpty()) {
            title = tr("search.title", q);
            sub = foundTotal > MOST_FOUND ? tr("search.sub.more", foundTotal, MOST_FOUND) : tr("search.sub", foundTotal);
            pic = "search";
        } else if (pageKey.equals(HOME)) {
            title = tr("home");
            sub = creating() ? tr("creation.home.sub") : tr("home.sub");
            pic = HOME;
        } else if (pageKey.equals(LIVE)) {
            title = tr("live");
            sub = tr("live.sub", liveCount());
            pic = LIVE;
        } else if (pageKey.equals(PRESETS)) {
            title = tr("presets");
            sub = tr("presets.sub");
            pic = PRESETS;
        } else if (pageKey.equals(CHANGES)) {
            title = tr("changes");
            sub = tr("changes.sub");
            pic = CHANGES;
        } else if (pageKey.equals(PLAYERS)) {
            title = tr("players");
            sub = tr("players.sub");
            pic = PLAYERS;
        } else if (pageKey.equals(TOOLS)) {
            title = tt("name", "Server tools");
            sub = tt("sub", "Pause, events, schedules, areas, the map, stats, alerts and respawn timers.");
            pic = "tools";
        } else if (pageKey.equals(SYSTEMS)) {
            title = tr("systems");
            sub = tr("systems.sub");
            pic = "systems";
        } else if (pageKey.equals(FUN) || pageKey.equals(WORLD)) {
            title = tr(pageKey);
            sub = tr(pageKey + ".sub");
            pic = pageKey;
        } else {
            title = Component.literal(giantName(pageKey));
            AdminBookClient.Page p = AdminBookClient.page(pageKey);
            int n = p == null ? 0 : p.settings().size(), set = setCount(pageKey);
            sub = pageKey.equals(AdminCatalog.ALL) ? tr("all.sub")
                    : p == null ? Component.empty() : set > 0 ? tr("page.sub.set", n, set) : tr("page.sub", n);
            pic = pageKey.equals(AdminCatalog.ALL) ? "all" : null;
        }
        int right = headButton.visible ? headButton.getX() - 6 : lx + lw;
        boolean banner = allWorlds && (!q.isEmpty() || pageKey.equals(HOME) || !ownPage(pageKey));
        Component line = banner ? tr("scope.all.banner") : sub;
        if (giantPage()) {
            // his picture, then General | Special
            icon(g, pageKey, lx + 1, headY + 2, 20);
            int x = lx + 27;
            for (String pt : List.of(GENERAL, SPECIAL)) {
                Component c = tr("part." + pt);
                int w = font.width(c) + 26;
                boolean sel = pt.equals(part), over = modal == null && mx >= x && mx < x + w && my >= headY + 2
                        && my < headY + 22;
                AdminStyle.tab(g, x, headY + 2, w, 20, sel, over);
                AdminStyle.icon(g, pt, x + 5, headY + 6, 12);
                g.drawString(font, c, x + 20, headY + 8, sel ? HEAD : TEXT, false);
                if (over) setTooltipForNextRenderPass(font.split(tr("part." + pt + ".tip", giantName(pageKey)), 240));
                zones.add(new Zone(x, headY + 2, w, 20, () -> pickPart(pt)));
                x += w + 3;
            }
            int sx = x + 6;
            if (sx + 40 < right) {
                g.drawString(font, cut(title.getString(), right - sx), sx, headY + 3, HEAD, false);
                g.drawString(font, cut(line.getString(), right - sx), sx, headY + 13, banner ? ORANGE : GREY, false);
            }
        } else {
            int tx = lx;
            if (pic != null) AdminStyle.icon(g, pic, lx, headY + 2, 18);
            else icon(g, pageKey, lx, headY + 2, 18);
            tx += 24;
            g.drawString(font, cut(title.getString(), right - tx), tx, headY + 3, HEAD, false);
            g.drawString(font, cut(line.getString(), right - tx), tx, headY + 13, banner ? ORANGE : GREY, false);
        }
        if (allWorlds && mx >= lx && mx < right && my >= headY + 11 && my < headY + 22)
            setTooltipForNextRenderPass(font.split(tr("scope.all.tip"), 260));
    }

    /** the section tabs (or, with none, a rubric line) over the list */
    private void drawTabs(GuiGraphics g, int mx, int my) {
        if (tabBoxes.isEmpty()) {
            AdminStyle.flourish(g, lx, ly0 - 9, lw);
            return;
        }
        String now = sectionNow(sectionTabs());
        int base = tabY + tabRows * 17;
        g.fill(lx - 4, base - 1, lx + lw + 4, base, AdminStyle.RUBRIC);
        TabBox picked = null;
        for (TabBox t : tabBoxes) {
            boolean sel = t.sec().equals(now);
            if (sel) {
                picked = t;
                continue;
            }
            tabAt(g, t, false, mx, my);
        }
        if (picked != null) tabAt(g, picked, true, mx, my);
    }

    private void tabAt(GuiGraphics g, TabBox t, boolean sel, int mx, int my) {
        boolean over = modal == null && mx >= t.x() && mx < t.x() + t.w() && my >= t.y() && my < t.y() + 16;
        // (the picked tab joins the page under it)
        AdminStyle.tab(g, t.x(), t.y(), t.w(), sel ? 17 : 15, sel, over);
        AdminStyle.icon(g, t.sec().equals(ALL_SECTIONS) ? "all" : t.sec(), t.x() + 4, t.y() + 2, 12);
        if (t.named()) g.drawString(font, sectionName(t.sec()), t.x() + 18, t.y() + 4, sel ? HEAD : TEXT, false);
        if (over) {
            List<FormattedCharSequence> tip = new ArrayList<>();
            tip.add(sectionName(t.sec()).copy().withStyle(ChatFormatting.YELLOW).getVisualOrderText());
            tip.addAll(font.split(tr(t.sec().equals(ALL_SECTIONS) ? "section.all.tip" : "section.tip"), 220));
            setTooltipForNextRenderPass(tip);
        }
        zones.add(new Zone(t.x(), t.y(), t.w(), 16, () -> pickSection(t.sec())));
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        g.enableScissor(lx - 5, ly0, lx + lw + 6, ly1);
        for (Row r : rows) {
            int y = ly0 + r.y - (int) scroll;
            if (y + r.h < ly0 || y > ly1) continue;
            r.draw(g, lx, y, lw, mx, my);
        }
        g.disableScissor();
        // (a soft edge where the list goes under the tabs and the bottom)
        for (int i = 0; i < 4; i++) {
            g.fill(lx - 5, ly0 + i, lx + lw + 6, ly0 + i + 1, (4 - i) * 10 << 24);
            g.fill(lx - 5, ly1 - i - 1, lx + lw + 6, ly1 - i, (4 - i) * 8 << 24);
        }
        if (contentH > ly1 - ly0) {
            int track = ly1 - ly0, bar = Math.max(16, track * track / contentH);
            int by = ly0 + (int) ((track - bar) * scroll / Math.max(1, contentH - track));
            g.fill(lx + lw + 3, ly0, lx + lw + 6, ly1, 0x30503010);
            g.fill(lx + lw + 3, by, lx + lw + 6, by + bar, 0xFF8A2A14);
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
        // (a little dusk-blue window in a brown frame, so the figures stand out on the parchment)
        g.fill(x, y, x + w, y + h, 0xFF8A6A40);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF3A5068, 0xFF5E7A8E);
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
        // (Part 2C) the contents, the breadcrumb, General/Special and the section tabs
        if (button == 0)
            for (Zone z : List.copyOf(zones))
                if (mx >= z.x() && mx < z.x() + z.w() && my >= z.y() && my < z.y() + z.h()) {
                    z.go().run();
                    return true;
                }
        // a click on a row itself (a group's heading folds it)
        if (button == 0 && mx >= lx - 4 && mx < lx + lw && my >= ly0 && my < ly1)
            for (Row r : List.copyOf(rows)) {
                int top = ly0 + r.y - (int) scroll;
                if (my >= top && my < top + r.h && r.click(mx, my, top)) return true;
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
        if (modal instanceof SizeAsk sa && sa.sliding) return sa.slider.mouseDragged(mx, my, button, dx, dy);
        if (modal != null) return modal.dragged(mx, my, button, dx, dy) || true;
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
        if (modal instanceof SizeAsk sa && sa.sliding) {
            sa.sliding = false;
            sa.slider.onRelease(mx, my);
        }
        if (modal != null) {
            modal.released(mx, my);
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void dragTo(double my) {
        double f = Mth.clamp((my - ly0) / Math.max(1, ly1 - ly0), 0, 1);
        scroll = f * Math.max(0, contentH - (ly1 - ly0));
        place();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (modal != null) {
            modal.scrolled(sy);
            return true;
        }
        if (mx >= lx - 5 && mx < lx + lw + 8 && my >= ly0 && my < ly1) {
            scroll -= sy * 24;
            place();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        // (Part 7) a question with boxes to type in
        if (modal != null) return modal.charTyped(c, mods);
        return super.charTyped(c, mods);
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

    // Part 2B: Live control

    private static String str(JsonObject o, String k) {
        return AdminBookClient.AdminJsonBridge.str(o, k);
    }

    private static double dbl(JsonObject o, String k, double or) {
        try {
            return o.has(k) ? o.get(k).getAsDouble() : or;
        } catch (RuntimeException e) {
            return or;
        }
    }

    private static boolean bool(JsonObject o, String k) {
        try {
            return o.has(k) && o.get(k).getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static List<JsonObject> objects(@Nullable JsonObject o, String k) {
        List<JsonObject> out = new ArrayList<>();
        if (o != null && o.get(k) instanceof JsonArray a)
            for (JsonElement e : a) if (e.isJsonObject()) out.add(e.getAsJsonObject());
        return out;
    }

    private static List<String> strings(@Nullable JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        if (o != null && o.get(k) instanceof JsonArray a)
            for (JsonElement e : a)
                try {
                    out.add(e.getAsString());
                } catch (RuntimeException bad) {
                    // left out
                }
        return out;
    }

    private int liveCount() {
        int n = 0;
        for (JsonObject g : objects(AdminBookClient.live(), "giants")) n += objects(g, "rows").size();
        return n;
    }

    private static String dimName(String dim) {
        return switch (dim) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
        };
    }

    /** what the summon bar has picked (kept while the game runs) */
    private static String summonKind = "";
    private static double summonSize = 1;

    /** the Live control page: a summon bar (admins), then every giant of every kind, each with its buttons */
    private void liveRows() {
        JsonObject live = AdminBookClient.live();
        if (live == null) {
            rows.add(new Note(tr("asking"), GREY));
            return;
        }
        List<JsonObject> kinds = objects(live, "giants");
        List<JsonObject> speaking = kinds.stream().filter(k -> bool(k, "speaks")).toList();
        if (AdminBookClient.admin() && !speaking.isEmpty()) summonRow(speaking);
        List<String> none = new ArrayList<>(), old = new ArrayList<>();
        for (JsonObject k : kinds) {
            String name = str(k, "name");
            if (!bool(k, "speaks")) {
                old.add(name);
                continue;
            }
            List<JsonObject> list = objects(k, "rows");
            if (list.isEmpty()) {
                none.add(name);
                continue;
            }
            rows.add(new Head(Component.literal(name + "  (" + list.size() + ")"), null, str(k, "key")));
            for (int i = 0; i < list.size(); i++) rows.add(new LiveRow(k, list.get(i), i, list.size()));
        }
        if (liveCount() == 0) rows.add(new Note(tr("live.empty"), GREY));
        if (!none.isEmpty()) rows.add(new Note(tr("live.none", String.join(", ", none)), DIM));
        if (!old.isEmpty()) rows.add(new Note(tr("live.old", String.join(", ", old)), DIM));
        if (AdminBookClient.role().equals("moderator")) rows.add(new Note(tr("live.moderator"), DIM));
    }

    /** Summon here: which giant, what size, at your feet (admins only; his own summon command, run as you) */
    private void summonRow(List<JsonObject> kinds) {
        JsonObject pick = kinds.get(0);
        for (JsonObject k : kinds) if (str(k, "key").equals(summonKind)) pick = k;
        summonKind = str(pick, "key");
        JsonObject kind = pick;
        List<String> sz = strings(kind, "size");
        double lo = sz.size() == 2 ? Double.parseDouble(sz.get(0)) : 0.1, hi = sz.size() == 2
                ? Double.parseDouble(sz.get(1)) : 2;
        summonSize = Mth.clamp(summonSize, lo, hi);
        Block b = new Block(24, (g, x, y, w, mx, my) -> {
            AdminStyle.card(g, x - 2, y, w + 2, 22, false);
            g.drawString(font, tr("live.summon"), x + 2, y + 7, KEY, false);
        });
        int lx0 = font.width(tr("live.summon")) + 8;
        int kindW = Mth.clamp(lw / 4, 70, 110);
        Button kindB = Button.builder(Component.literal(str(kind, "name")), x -> {
                    int i = 0;
                    for (int k = 0; k < kinds.size(); k++) if (str(kinds.get(k), "key").equals(summonKind)) i = k;
                    summonKind = str(kinds.get((i + 1) % kinds.size()), "key");
                    summonSize = 1;
                    build();
                })
                .bounds(0, 0, kindW, 16)
                .build();
        kindB.setTooltip(Tooltip.create(tr("live.summon.kind.tip")));
        b.add(kindB, lx0, 3, false);
        Component go = tr("live.summon.go");
        int goW = font.width(go) + 14;
        int sliderW = Mth.clamp(lw - lx0 - kindW - goW - 10, 50, 140);
        AbstractSliderButton size = new AbstractSliderButton(0, 0, sliderW, 16, Component.empty(),
                (summonSize - lo) / Math.max(1e-6, hi - lo)) {
            {
                updateMessage();
            }

            double now() {
                double v = lo + value * (hi - lo);
                return Math.round(v * 20) / 20.0 < lo ? lo : Math.min(hi, Math.round(v * 20) / 20.0);
            }

            @Override
            protected void updateMessage() {
                setMessage(tr("live.summon.size", AdminSetting.plain(now())));
            }

            @Override
            protected void applyValue() {
                summonSize = now();
            }
        };
        size.setTooltip(Tooltip.create(tr("live.summon.size.tip", AdminSetting.plain(lo), AdminSetting.plain(hi))));
        b.add(size, lx0 + kindW + 4, 3, false);
        Button goB = Button.builder(go, x -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("do", "summon");
                    o.addProperty("giant", summonKind);
                    o.addProperty("size", AdminSetting.plain(summonSize));
                    if (AdminBookClient.request(o)) say(tr("sent"), GREY);
                })
                .bounds(0, 0, goW, 16)
                .build();
        goB.setTooltip(Tooltip.create(tr("live.summon.tip")));
        b.add(goB, lx0 + kindW + 4 + sliderW + 4, 3, false);
        rows.add(b);
    }

    /** an action on one giant, by his id */
    private void liveAct(String giant, String id, String act, String value) {
        JsonObject o = new JsonObject();
        o.addProperty("do", "live");
        o.addProperty("giant", giant);
        o.addProperty("id", id);
        o.addProperty("act", act);
        o.addProperty("value", value);
        if (AdminBookClient.request(o)) say(tr("sent"), GREY);
        else say(tr("no_server"), WARN);
    }

    private static final List<String> HERE_ONLY = List.of("heal", "size", "sleep", "wake", "calm", "forgive");

    /** one giant: who and where he is, how he is, and a button for each thing that can be done to him */
    private final class LiveRow extends Row {
        final JsonObject kind, r;
        final String key, id, state, label;

        LiveRow(JsonObject kind, JsonObject r, int i, int of) {
            this.kind = kind;
            this.r = r;
            key = str(kind, "key");
            id = str(r, "id");
            state = str(r, "state");
            String own = str(r, "name");
            label = !own.isEmpty() ? own : str(kind, "name") + (of > 1 ? " " + (i + 1) : "");
            List<String> does = strings(kind, "does");
            boolean here = state.equals("here"), asleep = bool(r, "asleep");
            List<Object[]> bs = new ArrayList<>();
            bs.add(new Object[] {"tp", ""});
            bs.add(new Object[] {"heal", ""});
            bs.add(new Object[] {"size", ""});
            bs.add(new Object[] {asleep ? "wake" : "sleep", ""});
            bs.add(new Object[] {"calm", ""});
            bs.add(new Object[] {"forgive", ""});
            bs.add(new Object[] {"owner", ""});
            bs.add(new Object[] {"home", ""});
            bs.add(new Object[] {"remove", ""});
            bs.add(new Object[] {"kill", ""});
            int bx = 30, by = 26;
            h = 46;
            for (Object[] o : bs) {
                String act = (String) o[0];
                Component c = tr("live.act." + act);
                int w = font.width(c) + 10;
                if (bx > 30 && bx + w > lw) {
                    bx = 30;
                    by += 18;
                    h += 18;
                }
                Button b = Button.builder(c, x -> press(act)).bounds(0, 0, w, 16).build();
                boolean can = does.contains(act) && (here || !HERE_ONLY.contains(act));
                b.active = can;
                b.setTooltip(Tooltip.create(!does.contains(act) ? tr("live.cant", str(kind, "name"))
                        : !can ? tr("live.away_only") : tr("live.act." + act + ".tip")));
                add(b, bx, by, false);
                bx += w + 3;
            }
        }

        void press(String act) {
            String name = label;
            switch (act) {
                case "size" -> {
                    List<String> sz = strings(kind, "size");
                    double lo = sz.size() == 2 ? Double.parseDouble(sz.get(0)) : 0.1,
                            hi = sz.size() == 2 ? Double.parseDouble(sz.get(1)) : 2;
                    modal = new SizeAsk(name, lo, hi, dbl(r, "size", 1), v -> liveAct(key, id, "size",
                            AdminSetting.plain(v)));
                    place();
                }
                case "owner" -> {
                    modal = new PickPlayer(tr("live.owner.title", name), tr("live.owner.body"),
                            objects(AdminBookClient.live(), "players"),
                            p -> liveAct(key, id, "owner", str(p, "id")));
                    place();
                }
                case "remove", "kill" -> confirm(tr("live." + act + ".title", name), tr("live." + act + ".body",
                        name), tr("live.act." + act), () -> liveAct(key, id, act, ""));
                default -> liveAct(key, id, act, "");
            }
        }

        @Override
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
            AdminStyle.card(g, x - 2, y + 1, w + 2, h - 3, false);
            icon(g, key, x + 2, y + 4, 20);
            int tx = x + 30;
            // line 1: his name, then what he is up to
            int nx = g.drawString(font, cut(label, w / 3), tx, y + 4, KEY, false) + 6;
            List<Object[]> tags = new ArrayList<>();
            String mood = str(r, "mood");
            if (!mood.isEmpty()) tags.add(new Object[] {Component.translatableWithFallback(
                    "admin.jj_giants.book.live.mood." + mood,
                    Character.toUpperCase(mood.charAt(0)) + mood.substring(1)).getString(),
                    mood.equals("hunting") ? WARN : mood.equals("guardian") ? KEY : GOOD});
            if (bool(r, "own")) tags.add(new Object[] {tr("live.own").getString(), BLUE});
            if (state.equals("away")) tags.add(new Object[] {tr("live.state.away").getString(), ORANGE});
            if (state.equals("unloaded")) tags.add(new Object[] {tr("live.state.unloaded").getString(), DIM});
            if (bool(r, "asleep")) tags.add(new Object[] {tr("live.asleep").getString(), BLUE});
            if (bool(r, "fighting")) tags.add(new Object[] {tr("live.fighting").getString(), WARN});
            // (the health on the right of line 1)
            int barW = Mth.clamp(w / 6, 40, 90), barX = x + w - barW - 2;
            boolean hp = r.has("health") && r.has("max");
            String hpText = hp ? Math.round(dbl(r, "health", 0)) + " / " + Math.round(dbl(r, "max", 0)) : "";
            int hpTextX = barX - font.width(hpText) - 4;
            for (Object[] t : tags) {
                String s = (String) t[0];
                int tw = font.width(s) + 6;
                if (nx + tw > (hp ? hpTextX - 4 : x + w)) break;
                g.fill(nx, y + 3, nx + tw, y + 13, 0x30000000);
                g.renderOutline(nx, y + 3, tw, 10, ((int) t[1] & 0x00FFFFFF) | 0x80000000);
                g.drawString(font, s, nx + 3, y + 4, (int) t[1], false);
                nx += tw + 3;
            }
            if (hp) {
                double f = Mth.clamp(dbl(r, "health", 0) / Math.max(1, dbl(r, "max", 1)), 0, 1);
                g.fill(barX, y + 5, barX + barW, y + 11, 0x60000000);
                g.fill(barX, y + 5, barX + (int) Math.round(barW * f), y + 11,
                        f > 0.5 ? 0xFF60C060 : f > 0.25 ? 0xFFE0C050 : 0xFFE04040);
                g.renderOutline(barX - 1, y + 4, barW + 2, 8, 0x80503010);
                g.drawString(font, hpText, hpTextX, y + 4, TEXT, false);
            }
            // line 2: where, size, owner
            StringBuilder l2 = new StringBuilder();
            l2.append(Math.round(dbl(r, "x", 0)));
            if (r.has("y")) l2.append(", ").append(Math.round(dbl(r, "y", 0)));
            l2.append(", ").append(Math.round(dbl(r, "z", 0))).append(" · ").append(dimName(str(r, "dim")));
            if (r.has("size")) l2.append(" · ").append(tr("live.size", AdminSetting.plain(dbl(r, "size", 1)))
                    .getString());
            String owner = str(r, "owner");
            l2.append(" · ").append(owner.isEmpty() ? tr("live.no_owner").getString()
                    : tr("live.owner", owner).getString());
            g.drawString(font, cut(l2.toString(), w - 32), tx, y + 15, GREY, false);
            if (mx >= x && mx < x + w && my >= Math.max(y, ly0) && my < Math.min(y + 25, ly1)) {
                List<FormattedCharSequence> t = new ArrayList<>();
                t.add(Component.literal(label).withStyle(ChatFormatting.YELLOW).getVisualOrderText());
                t.addAll(font.split(Component.literal(l2.toString()), 260));
                t.addAll(font.split(tr("live.state." + state + ".tip").withStyle(ChatFormatting.GRAY), 260));
                setTooltipForNextRenderPass(t);
            }
        }
    }

    /** a size for one giant: a slider in his range */
    private final class SizeAsk extends Modal {
        final Component title;
        final AbstractSliderButton slider;
        double value;

        SizeAsk(String name, double lo, double hi, double now, Consumer<Double> go) {
            title = tr("live.size.title", name);
            value = Mth.clamp(now, lo, hi);
            slider = new AbstractSliderButton(0, 0, 160, 16, Component.empty(), (value - lo) / Math.max(1e-6, hi - lo)) {
                {
                    updateMessage();
                }

                @Override
                protected void updateMessage() {
                    setMessage(Component.literal(AdminSetting.plain(cur())));
                }

                double cur() {
                    return Mth.clamp(Math.round((lo + this.value * (hi - lo)) * 100) / 100.0, lo, hi);
                }

                @Override
                protected void applyValue() {
                    SizeAsk.this.value = cur();
                }
            };
            button(tr("live.size.yes"), () -> {
                closeModal();
                go.accept(value);
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
            return font.split(tr("live.size.body"), Math.min(pw - 40, 300) - 16);
        }

        @Override
        void layout(int buttonsW) {
            super.layout(buttonsW);
            mh += 22;
            my0 = (height - mh) / 2;
            for (Button b : buttons) b.setY(my0 + mh - 24);
            slider.setX(mx0 + 8);
            slider.setY(my0 + mh - 46);
            slider.setWidth(mw - 16);
        }

        @Override
        void extra(GuiGraphics g) {
            slider.render(g, lastMx, lastMy, 0);
        }

        @Override
        boolean mouseClicked(double mx, double my, int b) {
            if (slider.mouseClicked(mx, my, b)) {
                sliding = true;
                return true;
            }
            return super.mouseClicked(mx, my, b);
        }

        boolean sliding;

        @Override
        void cancel() {
            closeModal();
        }
    }

    private int lastMx, lastMy;

    /** pick one of the players online */
    private final class PickPlayer extends Modal {
        final Component title, body;

        PickPlayer(Component title, Component body, List<JsonObject> players, Consumer<JsonObject> go) {
            this.title = title;
            this.body = players.isEmpty() ? tr("live.owner.nobody") : body;
            for (JsonObject p : players.subList(0, Math.min(8, players.size())))
                button(Component.literal(str(p, "name")), () -> {
                    closeModal();
                    go.accept(p);
                });
            button(tr("cancel"), this::cancel);
            layout(buttonsW());
        }

        @Override
        void layout(int buttonsW) {
            super.layout(buttonsW);
            // (the names wrap onto more lines when they don't fit on one)
            int x = mx0 + 8, y = my0 + mh - 24, rowsN = 1;
            for (Button b : buttons) {
                if (x > mx0 + 8 && x + b.getWidth() > mx0 + mw - 8) {
                    x = mx0 + 8;
                    rowsN++;
                }
                x += b.getWidth() + 4;
            }
            mh += (rowsN - 1) * 20;
            my0 = (height - mh) / 2;
            x = mx0 + 8;
            y = my0 + mh - 24 - (rowsN - 1) * 20;
            for (Button b : buttons) {
                if (x > mx0 + 8 && x + b.getWidth() > mx0 + mw - 8) {
                    x = mx0 + 8;
                    y += 20;
                }
                b.setX(x);
                b.setY(y);
                x += b.getWidth() + 4;
            }
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
    }

    // Part 2B: the Changes page

    private static String when(long t) {
        return new java.text.SimpleDateFormat("MMM d HH:mm", Locale.ROOT).format(new java.util.Date(t));
    }

    private static long lng(JsonObject o, String k) {
        try {
            return o.has(k) ? o.get(k).getAsLong() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private void request(String what, Object... kv) {
        JsonObject o = new JsonObject();
        o.addProperty("do", what);
        for (int i = 0; i < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v instanceof Number n) o.addProperty((String) kv[i], n);
            else o.addProperty((String) kv[i], String.valueOf(v));
        }
        if (AdminBookClient.request(o)) say(tr("sent"), GREY);
        else say(tr("no_server"), WARN);
    }

    /** a line of words on the left, buttons on the right */
    private final class LineRow extends Row {
        final Component main, sub;
        final int mainColour;
        final List<FormattedCharSequence> tip;
        /** (Part 7) the grey line wrapped onto as many lines as it needs instead of cut off */
        final @Nullable List<FormattedCharSequence> subLines;

        LineRow(Component main, int mainColour, Component sub, List<Object[]> buttons) {
            this(main, mainColour, sub, buttons, false);
        }

        LineRow(Component main, int mainColour, Component sub, List<Object[]> buttons, boolean wrap) {
            this.main = main;
            this.sub = sub;
            this.mainColour = mainColour;
            List<Button> bts = new ArrayList<>();
            int dx = 0;
            for (int i = buttons.size() - 1; i >= 0; i--) {
                Object[] b = buttons.get(i);
                Component c = (Component) b[0];
                Button bt = Button.builder(c, x -> ((Runnable) b[1]).run())
                        .bounds(0, 0, font.width(c) + 12, 16)
                        .build();
                if (b.length > 2 && b[2] != null) bt.setTooltip(Tooltip.create((Component) b[2]));
                if (b.length > 3) bt.active = (boolean) b[3];
                bts.add(bt);
                dx += bt.getWidth() + 3;
            }
            textW = lw - dx - 6;
            subLines = wrap && !sub.getString().isEmpty() ? font.split(sub, Math.max(40, textW)) : null;
            h = sub.getString().isEmpty() ? 18 : subLines != null ? 16 + 10 * subLines.size() : 26;
            int at = 0;
            for (Button bt : bts) {
                add(bt, at, (h - 16) / 2, true);
                at += bt.getWidth() + 3;
            }
            List<FormattedCharSequence> t = new ArrayList<>(font.split(main, 280));
            if (!sub.getString().isEmpty()) t.addAll(font.split(sub.copy().withStyle(ChatFormatting.GRAY), 280));
            tip = t;
        }

        int textW;

        @Override
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
            String m = cut(main.getString(), textW);
            g.drawString(font, m, x, y + (h == 18 ? 5 : 3), mainColour, false);
            if (subLines != null) {
                for (int i = 0; i < subLines.size(); i++)
                    g.drawString(font, subLines.get(i), x, y + 14 + 10 * i, GREY, false);
            } else if (!sub.getString().isEmpty()) g.drawString(font, cut(sub.getString(), textW), x, y + 14, GREY,
                    false);
            AdminStyle.dots(g, x, y + h - 1, w);
            boolean cutOff = !m.equals(main.getString()) || (subLines == null && font.width(sub) > textW);
            if (cutOff && mx >= x && mx < x + textW && my >= Math.max(y, ly0) && my < Math.min(y + h, ly1))
                setTooltipForNextRenderPass(tip);
        }
    }

    /** the change log (newest first) with undo, the snapshots, and the locked settings */
    private void changeRows() {
        JsonObject c = AdminBookClient.changes();
        if (c == null) {
            rows.add(new Note(tr("asking"), GREY));
            return;
        }
        List<JsonObject> log = objects(c, "log");
        rows.add(new Head(tr("changes.log"), tr("changes.log.note"), null));
        if (log.isEmpty()) rows.add(new Note(tr("home.no_changes"), GREY));
        int newer = 0;
        for (JsonObject e : log) {
            int i = (int) dbl(e, "i", -1), count = newer + 1;
            long t = lng(e, "time");
            boolean can = bool(e, "undo");
            String text = str(e, "text");
            List<Object[]> bs = new ArrayList<>();
            bs.add(new Object[] {tr("changes.undo"), (Runnable) () -> request("undo", "i", i, "time", t),
                can ? tr("changes.undo.tip") : tr("changes.cant"), can});
            bs.add(new Object[] {tr("changes.back"), (Runnable) () -> confirm(tr("changes.back.title"),
                    tr("changes.back.body", count, when(t)), tr("changes.back.yes"),
                    () -> request("undoto", "i", i, "time", t)), tr("changes.back.tip", count), true});
            rows.add(new LineRow(Component.literal(text), TEXT,
                    Component.literal(when(t) + " · " + str(e, "who")), bs));
            newer++;
        }
        rows.add(new Head(tr("changes.snapshots"), tr("changes.snapshots.note"), null));
        List<JsonObject> snaps = objects(c, "snapshots");
        if (snaps.isEmpty()) rows.add(new Note(tr("changes.snapshots.none"), GREY));
        for (JsonObject s : snaps) {
            int i = (int) dbl(s, "i", -1);
            long t = lng(s, "time");
            rows.add(new LineRow(Component.literal(str(s, "why")), TEXT,
                    tr("changes.snapshot.sub", when(t), str(s, "who"), (int) dbl(s, "count", 0)),
                    List.<Object[]>of(new Object[] {tr("changes.restore"),
                        (Runnable) () -> request("restore", "i", i, "time", t), tr("changes.restore.tip")})));
        }
        rows.add(new Head(tr("changes.locks"), tr("changes.locks.note"), null));
        List<JsonObject> locks = objects(c, "locks");
        if (locks.isEmpty()) rows.add(new Note(tr("changes.locks.none"), GREY));
        for (JsonObject l : locks) {
            String giant = str(l, "giant"), id = str(l, "id");
            rows.add(new LineRow(Component.literal(str(l, "gname") + " " + str(l, "name")), GOLD,
                    tr("changes.lock.sub", str(l, "by"), when(lng(l, "time"))),
                    List.<Object[]>of(new Object[] {tr("lock.unlock"),
                        (Runnable) () -> request("unlock", "giant", giant, "id", id), tr("lock.unlock.tip")})));
        }
    }

    // Part 2B: the Presets page

    private static String presetName = "", importText = "";

    /** the suggested presets still to come (ADMIN_BOOK.md's picker layout), shown greyed: My Giant (Part 5) */
    private static final List<String> SUGGESTED = List.of();

    private void presetRows() {
        JsonObject p = AdminBookClient.presets();
        if (p == null) {
            rows.add(new Note(tr("asking"), GREY));
            return;
        }
        rows.add(new Block(16, (g, x, y, w, mx, my) -> {
            int nx = g.drawString(font, tr("home.preset"), x, y + 3, GREY, false);
            g.drawString(font, Component.literal(str(p, "current")), nx + 4, y + 3, KEY, false);
        }));
        // Classic first, marked as the default
        for (JsonObject b : objects(p, "builtin")) if (bool(b, "default")) builtInRow(b);
        // the suggested ones (Part 4), then the one still to come: greyed, nothing to press
        rows.add(new Head(tr("presets.suggested"), tr("presets.suggested.note"), null));
        for (JsonObject b : objects(p, "builtin")) if (str(b, "group").equals("suggested")) builtInRow(b);
        List<String> chips = SUGGESTED;
        int[] layout = chipRows(chips);
        rows.add(new Block(layout[0] * 16 + 2, (g, x, y, w, mx, my) -> {
            int cx = x, cy = y + 1;
            for (String s : chips) {
                int cw = font.width(s) + 10;
                if (cx > x && cx + cw > x + w) {
                    cx = x;
                    cy += 16;
                }
                g.fill(cx, cy, cx + cw, cy + 13, 0x14503010);
                g.renderOutline(cx, cy, cw, 13, 0x50503010);
                g.drawString(font, s, cx + 5, cy + 3, DIM, false);
                if (mx >= cx && mx < cx + cw && my >= cy && my < cy + 13 && my >= ly0 && my < ly1)
                    setTooltipForNextRenderPass(font.split(tr("presets.soon.tip"), 220));
                cx += cw + 4;
            }
        }));
        // (Part 3) every other built-in preset, nothing hidden
        List<JsonObject> others = objects(p, "builtin").stream()
                .filter(b -> !bool(b, "default") && !str(b, "group").equals("suggested"))
                .toList();
        if (!others.isEmpty()) {
            rows.add(new Head(tr("presets.all"), tr("presets.all.note"), null));
            for (JsonObject b : others) builtInRow(b);
        }
        // saved on this server
        rows.add(new Head(tr("presets.saved"), tr("presets.saved.note"), null));
        List<JsonObject> saved = objects(p, "saved");
        if (saved.isEmpty()) rows.add(new Note(tr("presets.saved.none"), GREY));
        for (JsonObject s : saved) {
            String name = str(s, "name");
            boolean mine = bool(s, "mine");
            List<Object[]> bs = new ArrayList<>();
            bs.add(new Object[] {tr("presets.preview"), (Runnable) () -> request("preset", "name", name),
                tr("presets.preview.tip")});
            bs.add(new Object[] {tr("presets.delete"), (Runnable) () -> confirm(tr("presets.delete.title", name),
                    tr("presets.delete.body", name), tr("presets.delete"),
                    () -> request("deletepreset", "name", name)),
                mine ? tr("presets.delete.tip") : tr("presets.delete.not_mine", str(s, "by")), mine});
            rows.add(new LineRow(Component.literal(name), KEY, tr("presets.saved.sub", str(s, "by"),
                    (int) dbl(s, "count", 0), when(lng(s, "time"))), bs));
        }
        // save this world's settings
        Block save = new Block(22, (g, x, y, w, mx, my) -> {});
        Component sv = tr("presets.save");
        int svW = font.width(sv) + 14;
        EditBox nameBox = new EditBox(font, 0, 0, Math.max(60, lw - svW - 4), 16, tr("presets.save.hint"));
        nameBox.setMaxLength(32);
        nameBox.setHint(tr("presets.save.hint").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setValue(presetName);
        nameBox.setResponder(t -> presetName = t);
        save.add(nameBox, 0, 3, false);
        Button svB = Button.builder(sv, x -> {
                    if (presetName.isBlank()) {
                        say(tr("presets.save.empty"), WARN);
                        return;
                    }
                    request("savepreset", "name", presetName.strip());
                    presetName = "";
                })
                .bounds(0, 0, svW, 16)
                .build();
        svB.setTooltip(Tooltip.create(tr("presets.save.tip")));
        save.add(svB, 0, 3, true);
        rows.add(new Head(tr("presets.save.head"), null, null));
        rows.add(save);
        // export and import
        rows.add(new Head(tr("presets.transfer"), tr("presets.transfer.note"), null));
        Block ex = new Block(22, (g, x, y, w, mx, my) -> {});
        Component exC = tr("presets.export");
        Button exB = Button.builder(exC, x -> request("export")).bounds(0, 0, font.width(exC) + 14, 16).build();
        exB.setTooltip(Tooltip.create(tr("presets.export.tip")));
        ex.add(exB, 0, 3, false);
        rows.add(ex);
        Block im = new Block(22, (g, x, y, w, mx, my) -> {});
        Component imC = tr("presets.import");
        int imW = font.width(imC) + 14;
        EditBox paste = new EditBox(font, 0, 0, Math.max(60, lw - imW - 4), 16, tr("presets.import.hint"));
        paste.setMaxLength(net.jj.giants.net.AdminBookChangePayload.MOST);
        paste.setHint(tr("presets.import.hint").withStyle(ChatFormatting.DARK_GRAY));
        paste.setValue(importText);
        paste.setResponder(t -> importText = t);
        im.add(paste, 0, 3, false);
        Button imB = Button.builder(imC, x -> {
                    if (importText.isBlank()) {
                        say(tr("presets.import.empty"), WARN);
                        return;
                    }
                    request("import", "text", importText.strip());
                })
                .bounds(0, 0, imW, 16)
                .build();
        imB.setTooltip(Tooltip.create(tr("presets.import.tip")));
        im.add(imB, 0, 3, true);
        rows.add(im);
    }

    /** a built-in preset's line: its name (Default), its danger once applied, the creation marker; Preview */
    private void builtInRow(JsonObject b) {
        String id = str(b, "id");
        rows.add(new LineRow(presetTitle(b), KEY, Component.literal(str(b, "about")), List.<Object[]>of(
                new Object[] {tr("presets.preview"), (Runnable) () -> request("preset", "name", id),
                    tr("presets.preview.tip")})));
    }

    /** a preset's name with its marks: Default, the danger rating, "World creation only" */
    static MutableComponent presetTitle(JsonObject b) {
        MutableComponent name = Component.literal(str(b, "name"));
        if (bool(b, "default")) name.append(tr("presets.default").withStyle(st -> st.withColor(GOOD)));
        int level = (int) dbl(b, "level", 0);
        if (level > 0) name.append(tr("presets.danger", level, str(b, "label")).withStyle(st -> st.withColor(
                level >= 4 ? WARN : level <= 2 ? GOOD : GREY)));
        if (bool(b, "creation")) name.append(tr("presets.creation").withStyle(st -> st.withColor(ORANGE)));
        return name;
    }

    /** how many lines a row of chips takes */

    private int[] chipRows(List<String> chips) {
        int x = 0, n = 1;
        for (String s : chips) {
            int cw = font.width(s) + 10;
            if (x > 0 && x + cw > lw) {
                x = 0;
                n++;
            }
            x += cw + 4;
        }
        return new int[] {n};
    }

    /** a preset's, an import's or a snapshot's changes, and the rating before and after: Apply or Cancel */
    private final class Preview extends Modal {
        final JsonObject p;
        List<FormattedCharSequence> lines;

        Preview(JsonObject p) {
            this.p = p;
            int n = (int) dbl(p, "count", 0);
            if (n > 0) button(tr("preview.apply", n), () -> {
                closeModal();
                AdminBookClient.clearPreview();
                request("apply");
            });
            // (Part 2C, JJ: every change, not only the first few)
            if (n > 0) button(tr("preview.all", n), () -> {
                modal = new AllChanges(this);
                place();
            });
            button(tr(n > 0 ? "cancel" : "preview.ok"), this::cancel);
            layout(buttonsW());
        }

        @Override
        int modalW() {
            return Math.min(pw - 24, 380);
        }

        @Override
        Component title() {
            return Component.literal(str(p, "title"));
        }

        @Override
        List<FormattedCharSequence> body() {
            int w = modalW() - 16;
            List<FormattedCharSequence> out = new ArrayList<>();
            String about = str(p, "about");
            if (!about.isEmpty()) out.addAll(font.split(Component.literal(about).withStyle(st -> st.withColor(GREY)), w));
            int n = (int) dbl(p, "count", 0);
            out.addAll(font.split(n == 0 ? tr("preview.nothing") : tr("preview.changes", n), w));
            // (the ones that only reach new land first: the note below speaks of them)
            List<JsonObject> ch = new ArrayList<>(objects(p, "changes"));
            ch.sort(java.util.Comparator.comparing(c -> !bool(c, "creation")));
            // (as many as fit; the rest counted)
            int room = Math.max(3, bodyRoom() - out.size() - 6);
            for (int i = 0; i < ch.size(); i++) {
                if (i >= room && ch.size() - i > 1) {
                    out.add(tr("preview.more", ch.size() - i).withStyle(st -> st.withColor(GREY)).getVisualOrderText());
                    break;
                }
                out.add(cut(changeLine(ch.get(i), true), w));
            }
            List<String> locked = strings(p, "locked"), skipped = strings(p, "skipped"),
                    creation = strings(p, "creation");
            if (!creation.isEmpty())
                out.addAll(font.split(tr("preview.creation.note").withStyle(st -> st.withColor(ORANGE)), w));
            if (!locked.isEmpty())
                out.addAll(font.split(tr("preview.locked", String.join(", ", locked))
                        .withStyle(st -> st.withColor(GOLD)), w));
            if (!skipped.isEmpty())
                out.addAll(font.split(tr("preview.skipped", skipped.size()).withStyle(st -> st.withColor(GREY)), w));
            JsonObject b = p.get("before") instanceof JsonObject x ? x : new JsonObject(),
                    a = p.get("after") instanceof JsonObject x ? x : new JsonObject();
            int lb = (int) dbl(b, "level", 3), la = (int) dbl(a, "level", 3);
            out.add(FormattedCharSequence.EMPTY);
            out.add(tr("preview.danger", lb + " " + str(b, "label"), la + " " + str(a, "label"))
                    .withStyle(st -> st.withColor(la > lb ? WARN : la < lb ? GOOD : TEXT)).getVisualOrderText());
            return out;
        }

        private FormattedCharSequence cut(MutableComponent c, int w) {
            if (font.width(c) <= w) return c.getVisualOrderText();
            return Component.literal(AdminBookScreen.this.cut(c.getString(), w)).withStyle(st -> st.withColor(TEXT))
                    .getVisualOrderText();
        }

        @Override
        void cancel() {
            AdminBookClient.clearPreview();
            closeModal();
        }

        @Override
        boolean keyPressed(int key) {
            if (key == 257 || key == 335) buttons.get(0).onPress();
            return true;
        }
    }

    /** one change of a preview: "  Pitchgut Most at once: 1 (default) → 6", the giant's name left out when grouped */
    private static MutableComponent changeLine(JsonObject c, boolean withGiant) {
        MutableComponent l = Component.literal("  " + (withGiant ? str(c, "gname") + " " : "") + str(c, "name") + ": ")
                .withStyle(st -> st.withColor(TEXT))
                .append(Component.literal(str(c, "old")).withStyle(st -> st.withColor(GREY)))
                .append(Component.literal(" → ").withStyle(st -> st.withColor(DIM)))
                .append(Component.literal(str(c, "now")).withStyle(st -> st.withColor(GOLD)));
        if (bool(c, "creation")) l.append(tr("preview.creation").withStyle(st -> st.withColor(ORANGE)));
        return l;
    }

    /**
     * (Part 2C, JJ Nov 2) "See all changes": every change a preset, an import or a snapshot would make, giant by giant,
     * scrolling; Back to the preview (Apply is there).
     */
    private final class AllChanges extends Modal {
        final Preview back;
        final List<FormattedCharSequence> lines = new ArrayList<>();

        AllChanges(Preview back) {
            this.back = back;
            int w = modalW() - 20;
            String giant = null;
            for (JsonObject c : objects(back.p, "changes")) {
                String g = str(c, "gname");
                if (!g.equals(giant)) {
                    giant = g;
                    if (!lines.isEmpty()) lines.add(FormattedCharSequence.EMPTY);
                    long n = objects(back.p, "changes").stream().filter(x -> str(x, "gname").equals(g)).count();
                    lines.add(Component.literal(g + " (" + n + ")").withStyle(st -> st.withColor(HEAD))
                            .getVisualOrderText());
                }
                lines.addAll(font.split(changeLine(c, false), w));
            }
            button(tr("preview.all.back"), this::cancel);
            layout(buttonsW());
        }

        @Override
        int modalW() {
            return Math.min(pw - 24, 420);
        }

        @Override
        Component title() {
            return tr("preview.all.title", str(back.p, "title"), (int) dbl(back.p, "count", 0));
        }

        @Override
        List<FormattedCharSequence> body() {
            return lines;
        }

        @Override
        void cancel() {
            modal = back;
            place();
        }

        @Override
        boolean keyPressed(int key) {
            if (key == 264) bodyScroll++;
            else if (key == 265) bodyScroll--;
            else if (key == 267) bodyScroll += bodyRoom();
            else if (key == 266) bodyScroll -= bodyRoom();
            return true;
        }
    }

    // Part 2B: locks on the setting rows

    /** a small padlock button: closed and gold when the setting is locked */
    private final class LockButton extends Button {
        private static final String[] SHUT = {".###.", "#...#", "#...#", "#####", "##.##", "##.##", "#####"},
                OPEN = {".###.", "#...#", "#....", "#####", "##.##", "##.##", "#####"};
        final boolean locked;

        LockButton(boolean locked, Runnable r) {
            super(0, 0, 16, 16, Component.empty(), b -> r.run(), DEFAULT_NARRATION);
            this.locked = locked;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            super.renderWidget(g, mx, my, partial);
            int c = !active ? 0xFF606060 : locked ? GOLD : isHoveredOrFocused() ? 0xFFFFFFA0 : 0xFFB0B0B0;
            String[] px = locked ? SHUT : OPEN;
            int x0 = getX() + 5, y0 = getY() + 4;
            for (int r = 0; r < px.length; r++)
                for (int k = 0; k < px[r].length(); k++)
                    if (px[r].charAt(k) == '#') g.fill(x0 + k, y0 + r, x0 + k + 1, y0 + r + 1, c);
        }
    }

    /** room for the padlock on this setting's line (none on an every-giant row) */
    private int lockW(AdminSetting s) {
        return s.giant().equals(EVERY) ? 0 : lockW();
    }

    /** room for the padlock on a setting's line (admins) */
    private int lockW() {
        return AdminBookClient.admin() && !creating() ? 18 : 0;
    }

    /** who locked this setting (null: nobody) */
    private @Nullable String lockedBy(AdminSetting s) {
        AdminBookClient.Page p = AdminBookClient.page(s.giant());
        return p == null ? null : p.lockedBy(s.id());
    }

    /** locked by someone else: its controls can't be used */
    private boolean lockedForMe(AdminSetting s) {
        String by = lockedBy(s);
        return by != null && !by.equals(AdminBookClient.myName());
    }

    private Button lockButton(AdminSetting s) {
        String by = lockedBy(s);
        LockButton b = new LockButton(by != null, () -> {
            if (by != null)
                confirm(tr("lock.unlock.title", name(s)), tr("lock.unlock.body", name(s), by), tr("lock.unlock"),
                        () -> request("unlock", "giant", s.giant(), "id", s.id()));
            else
                confirm(tr("lock.title", name(s)), tr("lock.body", name(s), show(s, s.value())), tr("lock.yes"),
                        () -> request("lock", "giant", s.giant(), "id", s.id()));
        });
        b.setTooltip(Tooltip.create(by != null ? tr("lock.locked.tip", by) : tr("lock.tip")));
        return b;
    }

    // Part 2B: moderators, on Home

    private void moderatorRows(JsonObject h) {
        rows.add(new Head(tr("mods"), tr("mods.note"), null));
        List<JsonObject> mods = objects(h, "mods");
        if (mods.isEmpty()) rows.add(new Note(tr("mods.none"), GREY));
        for (JsonObject m : mods) {
            String id = str(m, "id");
            rows.add(new LineRow(Component.literal(str(m, "name")), TEXT,
                    bool(m, "online") ? tr("mods.online") : tr("mods.offline"),
                    List.<Object[]>of(new Object[] {tr("mods.remove"), (Runnable) () -> confirm(
                            tr("mods.remove.title", str(m, "name")), tr("mods.remove.body", str(m, "name")),
                            tr("mods.remove"), () -> request("mod", "player", id, "on", "false")),
                        tr("mods.remove.tip")})));
        }
        List<JsonObject> can = objects(h, "players").stream()
                .filter(p -> !bool(p, "admin") && !bool(p, "mod")).toList();
        if (can.isEmpty()) {
            rows.add(new Note(tr("mods.nobody"), DIM));
            return;
        }
        Block b = new Block(22, (g, x, y, w, mx, my) -> g.drawString(font, tr("mods.make"), x, y + 6, GREY, false));
        int bx = font.width(tr("mods.make")) + 6, by = 2;
        for (JsonObject p : can) {
            Component c = Component.literal("+ " + str(p, "name"));
            int bw = font.width(c) + 12;
            if (bx > 0 && bx + bw > lw) {
                bx = 0;
                by += 20;
                b.h += 20;
            }
            Button bt = Button.builder(c, x -> confirm(tr("mods.add.title", str(p, "name")),
                            tr("mods.add.body", str(p, "name")), tr("mods.add.yes"),
                            () -> request("mod", "player", str(p, "id"), "on", "true")))
                    .bounds(0, 0, bw, 16)
                    .build();
            bt.setTooltip(Tooltip.create(tr("mods.add.tip")));
            b.add(bt, bx, by, false);
            bx += bw + 4;
        }
        rows.add(b);
    }

    // (Part 5, ADMIN_BRIDGE.md 20.9) the Players page: the player settings, each player, the vote, the leaderboards

    /** the player settings on their page, by sub-heading */
    private static final String[][] PLAYER_GROUPS = {
        {"players.pages", "all.myGiant", "all.rulesPage", "all.votes", "all.boards", "all.boardSlayers",
            "all.boardOwners", "all.boardFriends", "all.achievements"},
        {"players.kit", "all.kitGuide", "all.kitFinders", "all.kitFinder", "all.kitArmour", "all.kitBook"},
        {"players.features", "all.sit", "all.ride", "all.stand", "all.armourPowers", "all.bossBars",
            "all.fightMusic", "all.chatMeetings", "all.chatMoves", "all.chatBackDown", "all.books", "all.bookOrders",
            "all.findersCraftable"}};

    /** the vote being made: the presets ticked, how long, whether the winner goes on by itself */
    private static final java.util.LinkedHashSet<String> voteTicked = new java.util.LinkedHashSet<>();
    private static int voteMinutes = 5;
    private static boolean voteAuto = true;
    private static final int[] VOTE_MINUTES = {1, 2, 5, 10, 15, 30, 60};

    private int playersSet() {
        AdminBookClient.Page p = AdminBookClient.page(AdminCatalog.ALL);
        if (p == null) return 0;
        int n = 0;
        for (AdminSetting s : p.settings()) if (s.set() && AdminCatalog.player(s) && !AdminCatalog.server(s)) n++;
        return n;
    }

    private void playersRows() {
        AdminBookClient.Page p = AdminBookClient.page(AdminCatalog.ALL);
        if (p == null) {
            rows.add(new Note(tr("asking"), GREY));
            return;
        }
        rows.add(new Note(tr("players.note"), GREY));
        String[] pics = {"players", "loot", "taming"};
        for (int gi = 0; gi < PLAYER_GROUPS.length; gi++) {
            String[] grp = PLAYER_GROUPS[gi];
            List<AdminSetting> in = new ArrayList<>();
            for (int i = 1; i < grp.length; i++)
                for (AdminSetting s : p.settings()) if (s.id().equals(grp[i])) in.add(s);
            Head head = new Head(tr(grp[0]), null, null).folding(PLAYERS + "/" + grp[0], pics[gi], in.size());
            rows.add(head);
            if (!head.shut()) for (AdminSetting s : in) rows.add(new SetRow(s));
        }
        if (creating()) return;
        JsonObject pl = AdminBookClient.players();
        if (pl == null) {
            rows.add(new Note(tr("asking"), GREY));
            return;
        }
        // each player
        List<JsonObject> people = objects(pl, "rules");
        Head each = new Head(tr("players.each"), tr("players.each.note"), null)
                .folding(PLAYERS + "/each", "general", people.size());
        rows.add(each);
        if (people.isEmpty()) rows.add(new Note(tr("players.nobody"), GREY));
        for (JsonObject j : each.shut() ? List.<JsonObject>of() : people) {
            String id = str(j, "id"), name = str(j, "name");
            boolean own = bool(j, "own"), ignored = bool(j, "ignored"), nokit = bool(j, "nokit");
            String pick = str(j, "pick");
            Component sub = pick.isEmpty() ? (bool(j, "online") ? tr("players.online") : tr("players.offline"))
                    : tr("players.picked", pick, tr("players.outcome." + (str(j, "outcome").isEmpty() ? "going"
                            : str(j, "outcome"))));
            List<Object[]> bs = new ArrayList<>();
            bs.add(new Object[] {tr(own ? "players.own.yes" : "players.own.no"),
                (Runnable) () -> request("prule", "player", id, "rule", "own", "on", String.valueOf(!own)),
                tr("players.own.tip")});
            bs.add(new Object[] {tr(ignored ? "players.ignored.yes" : "players.ignored.no"),
                (Runnable) () -> request("prule", "player", id, "rule", "ignored", "on", String.valueOf(!ignored)),
                tr("players.ignored.tip")});
            bs.add(new Object[] {tr(nokit ? "players.nokit.yes" : "players.nokit.no"),
                (Runnable) () -> request("prule", "player", id, "rule", "nokit", "on", String.valueOf(!nokit)),
                tr("players.nokit.tip")});
            if (!pick.isEmpty())
                bs.add(new Object[] {tr("players.repick"), (Runnable) () -> confirm(tr("players.repick.title", name),
                    tr("players.repick.body", name), tr("players.repick"), () -> request("repick", "player", id)),
                    tr("players.repick.tip")});
            rows.add(new LineRow(Component.literal(name), !own || ignored || nokit ? GOLD : TEXT, sub, bs));
        }
        // the vote
        Head voteHead = new Head(tr("players.vote"), tr("players.vote.note"), null)
                .folding(PLAYERS + "/vote", "presets", -1);
        rows.add(voteHead);
        JsonObject v = pl.get("vote") instanceof JsonObject vo ? vo : new JsonObject();
        if (voteHead.shut()) {
            // (folded shut: nothing of the vote)
        } else if (v.has("on") && !v.get("on").getAsBoolean()) rows.add(new Note(tr("players.vote.off"), GREY));
        else if (v.get("open") instanceof JsonObject open) {
            long secs = lng(open, "secondsLeft");
            rows.add(new Note(tr("players.vote.open", secs / 60, secs % 60, str(open, "by"),
                    tr(bool(open, "auto") ? "players.vote.auto.on" : "players.vote.auto.off")), TEXT));
            for (JsonObject op : objects(open, "options"))
                rows.add(new LineRow(Component.literal(str(op, "name")), KEY,
                        tr("players.vote.count", (int) dbl(op, "votes", 0)), List.of()));
            rows.add(new LineRow(Component.empty(), TEXT, Component.empty(), List.<Object[]>of(
                    new Object[] {tr("players.vote.cancel"), (Runnable) () -> confirm(tr("players.vote.cancel.title"),
                        tr("players.vote.cancel.body"), tr("players.vote.cancel"), () -> request("votecancel"))},
                    new Object[] {tr("players.vote.close"), (Runnable) () -> request("voteclose"),
                        tr("players.vote.close.tip")})));
        } else {
            List<JsonObject> presets = objects(pl, "presets");
            Block b = new Block(22, (g, x, y, w, mx, my) -> g.drawString(font, tr("players.vote.pick"), x, y + 6,
                    GREY, false));
            int bx = font.width(tr("players.vote.pick")) + 6, by = 2;
            for (JsonObject pr : presets) {
                String pid = str(pr, "id");
                boolean on = voteTicked.contains(pid);
                Component c = Component.literal((on ? "✔ " : "") + str(pr, "name"));
                int bw = font.width(c) + 12;
                if (bx > 0 && bx + bw > lw) {
                    bx = 0;
                    by += 20;
                    b.h += 20;
                }
                Button bt = Button.builder(c, x -> {
                            if (!voteTicked.remove(pid) && voteTicked.size() < 6) voteTicked.add(pid);
                            build();
                        })
                        .bounds(0, 0, bw, 16)
                        .build();
                b.add(bt, bx, by, false);
                bx += bw + 4;
            }
            rows.add(b);
            int ticked = voteTicked.size();
            rows.add(new LineRow(tr("players.vote.ticked", ticked), ticked >= 2 ? TEXT : GREY,
                    tr("players.vote.ticked.note"), List.<Object[]>of(
                    new Object[] {tr("players.vote.minutes", voteMinutes), (Runnable) () -> {
                        int i = 0;
                        while (i < VOTE_MINUTES.length && VOTE_MINUTES[i] != voteMinutes) i++;
                        voteMinutes = VOTE_MINUTES[(i + 1) % VOTE_MINUTES.length];
                        build();
                    }, tr("players.vote.minutes.tip")},
                    new Object[] {tr(voteAuto ? "players.vote.auto.on" : "players.vote.auto.off"), (Runnable) () -> {
                        voteAuto = !voteAuto;
                        build();
                    }, tr("players.vote.auto.tip")},
                    new Object[] {tr("players.vote.go"), (Runnable) () -> {
                        request("voteopen", "options", String.join(",", voteTicked), "minutes", voteMinutes, "auto",
                                String.valueOf(voteAuto));
                        voteTicked.clear();
                    }, tr("players.vote.go.tip"), ticked >= 2})));
        }
        if (v.has("last") && !voteHead.shut()) rows.add(new Note(Component.literal(str(v, "last")), GREY));
        // the leaderboards
        Head boardsHead = new Head(tr("players.boards"), tr("players.boards.note"), null)
                .folding(PLAYERS + "/boards", "stats", -1);
        rows.add(boardsHead);
        if (boardsHead.shut()) return;
        JsonObject bo = pl.get("boards") instanceof JsonObject x ? x : new JsonObject();
        if (bo.has("on") && !bo.get("on").getAsBoolean()) rows.add(new Note(tr("players.boards.off"), GREY));
        for (JsonObject board : objects(bo, "boards")) {
            List<JsonObject> rs = objects(board, "rows");
            StringBuilder sb = new StringBuilder();
            for (JsonObject r : rs)
                sb.append(sb.length() == 0 ? "" : "   ").append((int) dbl(r, "place", 0)).append(" ")
                        .append(str(r, "name")).append(" ").append(str(r, "text"));
            rows.add(new LineRow(Component.literal(str(board, "title")), KEY,
                    sb.length() == 0 ? tr("players.boards.nobody") : Component.literal(sb.toString()), List.of()));
        }
        List<JsonObject> signs = objects(pl, "signs");
        rows.add(new Note(signs.isEmpty() ? tr("players.signs.none") : tr("players.signs", signs.size()), GREY));
        for (int i = 0; i < signs.size(); i++) {
            JsonObject s = signs.get(i);
            int at = i;
            rows.add(new LineRow(Component.literal(str(s, "title")), TEXT,
                    Component.literal((int) dbl(s, "x", 0) + " " + (int) dbl(s, "y", 0) + " " + (int) dbl(s, "z", 0)
                            + "  " + dimName(str(s, "dim"))),
                    List.<Object[]>of(new Object[] {tr("players.signs.remove"),
                        (Runnable) () -> request("boardremove", "i", at), tr("players.signs.remove.tip")})));
        }
    }

    // (Part 6, ADMIN_BRIDGE.md §22) the Server tools page: pause and events, schedules, areas, the map, the stats,
    // the alerts, the respawn timers, the world's profile and the modpack. Every setting on it is an ordinary
    // all-giants setting (category "server"); every button is a request the server checks, the twin of a command.

    /** the Server tools page */
    public static final String TOOLS = "tools";
    /** its tabs (the section tab bar), in order */
    private static final List<String> TOOL_TABS = List.of("t_now", "t_schedules", "t_areas", "t_map", "t_stats",
            "t_alerts", "t_respawns", "t_server");

    /** a Server tools line: its lang key (admin.jj_giants.tools.*) or these words */
    static MutableComponent tt(String key, String fallback, Object... args) {
        return Component.translatableWithFallback("admin.jj_giants.tools." + key, fallback, args);
    }

    private static Component toolTabName(String sec) {
        return switch (sec) {
            case "t_now" -> tt("tab.now", "Pause & events");
            case "t_schedules" -> tt("tab.schedules", "Schedules");
            case "t_areas" -> tt("tab.areas", "Areas");
            case "t_map" -> tt("tab.map", "Map");
            case "t_stats" -> tt("tab.stats", "Stats");
            case "t_alerts" -> tt("tab.alerts", "Alerts");
            case "t_respawns" -> tt("tab.respawns", "Respawns");
            default -> tt("tab.server", "Profile & pack");
        };
    }

    /** how many server tools settings the world set (the contents' count) */
    private int toolsSet() {
        AdminBookClient.Page p = AdminBookClient.page(AdminCatalog.ALL);
        if (p == null) return 0;
        int n = 0;
        for (AdminSetting s : p.settings()) if (s.set() && AdminCatalog.server(s)) n++;
        return n;
    }

    private @Nullable AdminSetting allSetting(String id) {
        AdminBookClient.Page p = AdminBookClient.page(AdminCatalog.ALL);
        return p == null ? null : p.setting(id);
    }

    /** setting rows for these all-giants ids, under a folding heading */
    private void toolSettings(String fold, Component title, @Nullable Component note, String pic, String... ids) {
        List<AdminSetting> in = new ArrayList<>();
        for (String id : ids) {
            AdminSetting s = allSetting(id);
            if (s != null) in.add(s);
        }
        if (in.isEmpty()) return;
        Head head = new Head(title, note, null).folding(TOOLS + "/" + fold, pic, in.size());
        rows.add(head);
        if (!head.shut()) for (AdminSetting s : in) rows.add(new SetRow(s));
    }

    private void toolsRows() {
        JsonObject t = AdminBookClient.tools();
        if (t == null || AdminBookClient.page(AdminCatalog.ALL) == null) {
            rows.add(new Note(tr("asking"), GREY));
            return;
        }
        switch (sectionNow(TOOL_TABS)) {
            case "t_schedules" -> toolsSchedules(t);
            case "t_areas" -> toolsAreas(t);
            case "t_map" -> toolsMap(t);
            case "t_stats" -> toolsStats(t);
            case "t_alerts" -> toolsAlerts(t);
            case "t_respawns" -> toolsRespawns(t);
            case "t_server" -> toolsServer(t);
            default -> toolsNow(t);
        }
    }

    /** a card with a few lines of words and buttons under them */
    private final class Card extends Row {
        final List<FormattedCharSequence> lines = new ArrayList<>();
        final int colour;
        final Component title;
        final @Nullable String pic;

        Card(Component title, @Nullable String pic, Component body, int colour, List<Object[]> buttons) {
            this.title = title;
            this.pic = pic;
            this.colour = colour;
            lines.addAll(font.split(body, lw - 34));
            h = 22 + lines.size() * 10 + (buttons.isEmpty() ? 4 : 24);
            int bx = 26, by = h - 22;
            for (Object[] b : buttons) {
                Component c = (Component) b[0];
                int bw = font.width(c) + 16;
                if (bx > 26 && bx + bw > lw - 4) {
                    bx = 26;
                    by += 20;
                    h += 20;
                }
                Button bt = Button.builder(c, x -> ((Runnable) b[1]).run()).bounds(0, 0, bw, 18).build();
                if (b.length > 2 && b[2] != null) bt.setTooltip(Tooltip.create((Component) b[2]));
                if (b.length > 3) bt.active = (boolean) b[3];
                add(bt, bx, by, false);
                bx += bw + 4;
            }
        }

        @Override
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
            AdminStyle.card(g, x - 2, y + 2, w + 2, h - 5, false);
            g.fill(x - 2, y + 2, x, y + h - 3, colour);
            if (pic != null) AdminStyle.icon(g, pic, x + 4, y + 6, 16);
            g.drawString(font, cut(title.getString(), w - 34), x + 26, y + 7, colour, false);
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x + 26, y + 19 + i * 10, TEXT, false);
        }
    }

    // the first tab: pause, quiet, events

    /** the minutes picked for each event (kept while the game runs) */
    private static final Map<String, Integer> eventMinutes = new HashMap<>();

    private void toolsNow(JsonObject t) {
        AdminSetting paused = allSetting("all.paused");
        boolean on = bool(t, "paused");
        if (paused != null) {
            boolean can = !lockedForMe(paused);
            rows.add(new Card(on ? tt("paused.on", "The giants are paused") : tt("paused.off",
                    "The giants are moving freely"), "t_now",
                    on ? tt("paused.on.body", "Every giant in loaded land is standing still: no moves, no walking, no"
                            + " fights. One out of the world stops as soon as he comes back.")
                            : tt("paused.off.body", "Pause stops every giant where he is, at once, until you let them"
                                    + " carry on. Nothing else changes."),
                    on ? WARN : GOOD,
                    List.<Object[]>of(new Object[] {on ? tt("paused.resume", "Let them carry on")
                            : tt("paused.pause", "Pause all giants"),
                        (Runnable) () -> commit(paused, new JsonPrimitive(!on)), can ? null
                                : tr("lock.locked.tip", lockedBy(paused)), can})));
        }
        toolSettings("now/settings", tt("settings", "Settings"), null, "misc", "all.paused", "all.quiet");
        // events
        rows.add(new Head(tt("events", "Events"), tt("events.note", "An event changes the giants for a while, then"
                + " everything it changed goes back by itself. One at a time; everyone is told (not in quiet mode)."),
                null).pic("t_events"));
        JsonObject ev = t.get("event") instanceof JsonObject e ? e : null;
        if (ev != null) {
            long s = lng(ev, "seconds");
            rows.add(new Card(tt("event.on", "%s is on", str(ev, "name")), "t_events",
                    tt("event.on.body", "%s:%s left · started by %s · %s settings changed until it ends", s / 60,
                            String.format("%02d", s % 60), str(ev, "by"), (int) dbl(ev, "changes", 0)), ORANGE,
                    List.<Object[]>of(new Object[] {tt("event.stop", "End it now"), (Runnable) () -> confirm(
                            tt("event.stop.title", "End %s now?", str(ev, "name")), tt("event.stop.body",
                                    "Everything it changed goes back now (anything changed since stays)."),
                            tt("event.stop", "End it now"), () -> request("eventstop"))})));
        }
        for (JsonObject k : objects(t, "events")) {
            String id = str(k, "id");
            int m = eventMinutes.getOrDefault(id, (int) dbl(k, "minutes", 30));
            rows.add(new LineRow(Component.literal(str(k, "name")), KEY, Component.literal(str(k, "about")),
                    List.<Object[]>of(
                            new Object[] {tt("event.minutes", "%s min", m), (Runnable) () -> {
                                int[] mm = {5, 10, 20, 30, 60, 120};
                                int i = 0;
                                while (i < mm.length && mm[i] != m) i++;
                                eventMinutes.put(id, mm[(i + 1) % mm.length]);
                                build();
                            }, tt("event.minutes.tip", "How long it lasts (click for the next)")},
                            new Object[] {tt("event.start", "Start"), (Runnable) () -> confirm(
                                    tt("event.start.title", "Start %s?", str(k, "name")),
                                    tt("event.start.body", "%s For %s minutes, then it all goes back by itself.",
                                            str(k, "about"), m), tt("event.start", "Start"),
                                    () -> request("eventstart", "kind", id, "minutes", m)),
                                tt("event.start.tip", "Starts it now for everyone"), ev == null})));
        }
    }

    // schedules

    private static int schedPreset = 0, schedKind = 0;
    private static final java.util.TreeSet<Integer> schedDays = new java.util.TreeSet<>(List.of(6, 7));
    private static String schedDate = "";
    private static final String[] SCHED_KINDS = {"weekends", "weekdays", "every", "days", "fullmoon", "date"};
    private static final String[] DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    private Component schedKindName(String k) {
        return switch (k) {
            case "weekends" -> tt("sched.weekends", "Weekends");
            case "weekdays" -> tt("sched.weekdays", "Weekdays");
            case "every" -> tt("sched.every", "Every day");
            case "days" -> tt("sched.days", "These days");
            case "fullmoon" -> tt("sched.fullmoon", "Full-moon nights");
            default -> tt("sched.date", "A date");
        };
    }

    private void toolsSchedules(JsonObject t) {
        rows.add(new Note(tt("sched.note", "A schedule puts a preset on by itself at certain times (days by the"
                + " server's clock, full moons in the game) and takes it off after: what it changed goes back, and"
                + " anything an admin changed meanwhile stays. The first one due goes on."), GREY));
        JsonObject pack = t.get("pack") instanceof JsonObject p ? p : new JsonObject();
        if (bool(pack, "lock"))
            rows.add(new Note(tt("sched.pack", "%s keeps the %s preset, so schedules don't go on.", str(pack, "name"),
                    str(pack, "presetName")), WARN));
        JsonObject run = t.get("schedule") instanceof JsonObject r ? r : null;
        if (run != null)
            rows.add(new Card(tt("sched.on", "Now on: %s", str(run, "name")), "t_schedules",
                    tt("sched.on.body", "It puts back %s changes when its time is over.", (int) dbl(run, "changes", 0)),
                    ORANGE, List.<Object[]>of(new Object[] {tt("sched.stop", "Stop it now"),
                        (Runnable) () -> confirm(tt("sched.stop.title", "Stop %s now?", str(run, "name")),
                                tt("sched.stop.body", "What it changed goes back now, and the schedule is turned off"
                                        + " (turn it on again below)."), tt("sched.stop", "Stop it now"),
                                () -> request("schedstop"))})));
        List<JsonObject> list = objects(t, "schedules");
        Head head = new Head(tt("sched.list", "Schedules"), null, null).folding(TOOLS + "/sched/list", "t_schedules",
                list.size());
        rows.add(head);
        if (list.isEmpty()) rows.add(new Note(tt("sched.none", "None yet: make one below."), GREY));
        for (JsonObject s : head.shut() ? List.<JsonObject>of() : list) {
            boolean on = bool(s, "on"), now = bool(s, "now");
            String id = str(s, "id");
            rows.add(new LineRow(Component.literal(str(s, "name")), now ? GOLD : on ? TEXT : DIM,
                    Component.literal(str(s, "when") + (now ? " · " + tt("sched.now", "on now").getString()
                            : on ? "" : " · " + tt("sched.off", "turned off").getString())),
                    List.<Object[]>of(
                            new Object[] {on ? tt("sched.turnoff", "Turn off") : tt("sched.turnon", "Turn on"),
                                (Runnable) () -> request("schedturn", "id", id, "on", String.valueOf(!on))},
                            new Object[] {tt("remove", "Remove"), (Runnable) () -> confirm(
                                    tt("sched.remove.title", "Remove this schedule?"), tt("sched.remove.body",
                                            "%s on %s. If it is on now, what it changed goes back.", str(s, "name"),
                                            str(s, "when")), tt("remove", "Remove"),
                                    () -> request("schedremove", "id", id))})));
        }
        // a new one
        rows.add(new Head(tt("sched.new", "New schedule"), null, null).pic("presets"));
        List<JsonObject> presets = objects(t, "presets");
        if (presets.isEmpty()) return;
        schedPreset = Math.floorMod(schedPreset, presets.size());
        JsonObject pick = presets.get(schedPreset);
        String kind = SCHED_KINDS[Math.floorMod(schedKind, SCHED_KINDS.length)];
        Block b = new Block(24, (g, x, y, w, mx, my) -> {});
        int bx = 0;
        Component pc = tt("sched.preset", "Preset: %s", str(pick, "name"));
        Button pb = Button.builder(pc, x -> {
                    schedPreset++;
                    build();
                })
                .bounds(0, 0, Math.min(lw / 2 - 4, font.width(pc) + 16), 18)
                .build();
        pb.setTooltip(Tooltip.create(tt("sched.preset.tip", "Click for the next preset")));
        b.add(pb, bx, 3, false);
        bx += pb.getWidth() + 4;
        Component kc = tt("sched.when", "When: %s", schedKindName(kind));
        Button kb = Button.builder(kc, x -> {
                    schedKind++;
                    build();
                })
                .bounds(0, 0, font.width(kc) + 16, 18)
                .build();
        kb.setTooltip(Tooltip.create(tt("sched.when.tip", "Click for the next choice")));
        if (bx + kb.getWidth() > lw) {
            bx = 0;
            b.h += 22;
        }
        b.add(kb, bx, b.h - 21, false);
        rows.add(b);
        Block extra = new Block(24, (g, x, y, w, mx, my) -> {});
        boolean ready = true;
        if (kind.equals("days")) {
            int dx = 0;
            for (int i = 1; i <= 7; i++) {
                int day = i;
                Component c = Component.literal((schedDays.contains(i) ? "✔ " : "") + DAY_NAMES[i - 1]);
                Button d = Button.builder(c, x -> {
                            if (!schedDays.remove(day)) schedDays.add(day);
                            build();
                        })
                        .bounds(0, 0, font.width(c) + 12, 18)
                        .build();
                if (dx > 0 && dx + d.getWidth() > lw) {
                    dx = 0;
                    extra.h += 22;
                }
                extra.add(d, dx, extra.h - 21, false);
                dx += d.getWidth() + 3;
            }
            ready = !schedDays.isEmpty();
            rows.add(extra);
        } else if (kind.equals("date")) {
            EditBox date = new EditBox(font, 0, 0, 120, 18, tt("sched.date.hint", "MM-DD"));
            date.setHint(tt("sched.date.hint", "MM-DD or YYYY-MM-DD").withStyle(ChatFormatting.DARK_GRAY));
            date.setMaxLength(10);
            date.setValue(schedDate);
            date.setResponder(v -> schedDate = v.strip());
            extra.add(date, 0, 3, false);
            rows.add(extra);
            ready = schedDate.matches("\\d{2}-\\d{2}|\\d{4}-\\d{2}-\\d{2}");
        }
        String when = switch (kind) {
            case "days" -> {
                StringBuilder sb = new StringBuilder();
                for (int d : schedDays) sb.append(sb.length() == 0 ? "" : ",").append(d);
                yield sb.toString();
            }
            case "date" -> schedDate;
            case "weekdays", "every" -> kind;
            default -> "";
        };
        String sendKind = kind.equals("weekdays") || kind.equals("every") ? "days" : kind;
        boolean go = ready;
        rows.add(new LineRow(tt("sched.add.line", "%s on %s", str(pick, "name"), schedKindName(kind)), TEXT,
                Component.empty(), List.<Object[]>of(new Object[] {tt("sched.add", "Add schedule"),
                    (Runnable) () -> request("schedadd", "preset", str(pick, "id"), "kind", sendKind, "when", when),
                    tt("sched.add.tip", "It goes on by itself when due, and back off after"), go})));
    }

    // areas

    private static int areaKind = 0, areaR = 64;
    private static String areaName = "";
    private static final int[] AREA_R = {16, 32, 64, 128, 256, 512, 1000};

    private void toolsAreas(JsonObject t) {
        toolSettings("areas/rules", tt("areas.rules", "Spawn and dimensions"), tt("areas.rules.note",
                "Giants near spawn are sent home, and taken away if they stay a minute. A dimension with no giants"
                        + " has every giant in it taken away."), "worldgen", "all.spawnClear", "all.dimOverworld",
                "all.dimNether", "all.dimEnd");
        List<JsonObject> areas = objects(t, "areas");
        Head head = new Head(tt("areas.list", "Areas"), tt("areas.note", "Circles round where you stood. No giants:"
                + " giants that come in are sent home, and taken away if they stay a minute. Safe: the giants leave"
                + " players standing in it alone."), null).folding(TOOLS + "/areas/list", "t_areas", areas.size());
        rows.add(head);
        if (areas.isEmpty()) rows.add(new Note(tt("areas.none", "None yet: make one below."), GREY));
        for (JsonObject a : head.shut() ? List.<JsonObject>of() : areas) {
            String id = str(a, "id");
            boolean safe = str(a, "kind").equals("safe");
            int r = (int) dbl(a, "r", 64);
            rows.add(new LineRow(Component.literal(str(a, "name")), safe ? GOOD : WARN,
                    tt("areas.line", "%s · %s blocks round %s, %s · %s", safe ? tt("areas.safe", "Safe")
                                    : tt("areas.keepout", "No giants"), r, (int) dbl(a, "x", 0), (int) dbl(a, "z", 0),
                            dimName(str(a, "dim"))),
                    List.<Object[]>of(
                            new Object[] {Component.literal("−"), (Runnable) () -> request("areaedit", "id", id, "r",
                                    Math.max(8, (int) Math.round(r / 1.5))), tt("areas.smaller", "Smaller")},
                            new Object[] {Component.literal("+"), (Runnable) () -> request("areaedit", "id", id, "r",
                                    Math.min(2000, (int) Math.round(r * 1.5))), tt("areas.bigger", "Bigger")},
                            new Object[] {safe ? tt("areas.tokeepout", "Make no giants") : tt("areas.tosafe",
                                    "Make safe"), (Runnable) () -> request("areaedit", "id", id, "kind",
                                    safe ? "keepout" : "safe")},
                            new Object[] {tt("remove", "Remove"), (Runnable) () -> confirm(tt("areas.remove.title",
                                    "Remove %s?", str(a, "name")), tt("areas.remove.body",
                                    "Giants may come there again."), tt("remove", "Remove"),
                                    () -> request("arearemove", "id", id))})));
        }
        // a new one round where you stand
        var me = net.minecraft.client.Minecraft.getInstance().player;
        rows.add(new Head(tt("areas.new", "New area round you"), me == null ? null : tt("areas.new.note",
                "You're at %s, %s (%s): the area's middle.", me.getBlockX(), me.getBlockZ(),
                dimName(me.level().dimension().location().toString())), null).pic("t_areas"));
        Block b = new Block(24, (g, x, y, w, mx, my) -> {});
        String kind = areaKind % 2 == 0 ? "keepout" : "safe";
        Component kc = kind.equals("safe") ? tt("areas.safe", "Safe") : tt("areas.keepout", "No giants");
        Button kb = Button.builder(kc, x -> {
                    areaKind++;
                    build();
                })
                .bounds(0, 0, Math.max(70, font.width(kc) + 16), 18)
                .build();
        kb.setTooltip(Tooltip.create(tt("areas.kind.tip", "No giants or safe (click to change)")));
        b.add(kb, 0, 3, false);
        Component rc = tt("areas.radius", "%s blocks", areaR);
        Button rb = Button.builder(rc, x -> {
                    int i = 0;
                    while (i < AREA_R.length && AREA_R[i] != areaR) i++;
                    areaR = AREA_R[(i + 1) % AREA_R.length];
                    build();
                })
                .bounds(0, 0, Math.max(70, font.width(rc) + 16), 18)
                .build();
        rb.setTooltip(Tooltip.create(tt("areas.radius.tip", "How far it reaches from you (click for the next)")));
        b.add(rb, kb.getWidth() + 4, 3, false);
        int nx = kb.getWidth() + rb.getWidth() + 8;
        Component add = tt("areas.add", "Add area");
        int aw = font.width(add) + 16;
        EditBox name = new EditBox(font, 0, 0, Math.max(60, lw - nx - aw - 6), 18, tt("areas.name", "Name"));
        name.setHint(tt("areas.name", "Name (optional)").withStyle(ChatFormatting.DARK_GRAY));
        name.setMaxLength(32);
        name.setValue(areaName);
        name.setResponder(v -> areaName = v);
        b.add(name, nx, 3, false);
        Button ab = Button.builder(add, x -> request("areaadd", "kind", kind, "r", areaR, "name", areaName.strip()))
                .bounds(0, 0, aw, 18)
                .build();
        ab.setTooltip(Tooltip.create(tt("areas.add.tip", "A circle round where you stand now")));
        b.add(ab, 0, 3, true);
        rows.add(b);
    }

    // the map

    private static String mapDim = "minecraft:overworld";
    /** blocks per pixel (-1: fit everything) and the middle */
    private static double mapScale = -1, mapCx, mapCz;

    /** each giant's own colour (his book cover's mark), darkened to read on parchment */
    static int giantColour(String key) {
        return switch (key) {
            case "pitchgut" -> 0xFFC86878;
            case "furrowmaw" -> 0xFFD04A1C;
            case "cerberus" -> 0xFF3A94D0;
            case "hollowbell" -> 0xFF20A898;
            case "lanternwillow" -> 0xFF5AA02A;
            case "wreckback" -> 0xFF8A8270;
            case "swarmforge" -> 0xFFD0761E;
            case "gloamwick" -> 0xFF8A66D8;
            default -> 0xFF806040;
        };
    }

    private void toolsMap(JsonObject t) {
        // the dimension and the zoom
        Block bar = new Block(24, (g, x, y, w, mx, my) -> {});
        int bx = 0;
        for (String d : List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")) {
            Component c = Component.literal((d.equals(mapDim) ? "● " : "") + dimName(d));
            Button b = Button.builder(c, x -> {
                        mapDim = d;
                        mapScale = -1;
                        build();
                    })
                    .bounds(0, 0, font.width(c) + 12, 18)
                    .build();
            bar.add(b, bx, 3, false);
            bx += b.getWidth() + 3;
        }
        int rx = 0;
        for (Object[] z : new Object[][] {
            {tt("map.fit", "Fit"), (Runnable) () -> mapScale = -1, tt("map.fit.tip", "Everything on the map")},
            {tt("map.me", "Me"), (Runnable) () -> {
                var me = net.minecraft.client.Minecraft.getInstance().player;
                if (me == null) return;
                mapDim = me.level().dimension().location().toString();
                mapCx = me.getX();
                mapCz = me.getZ();
                if (mapScale < 0) mapScale = 4;
            }, tt("map.me.tip", "The map round you")},
            {Component.literal("+"), (Runnable) () -> mapScale = Math.max(0.25, (mapScale < 0 ? 8 : mapScale) / 2),
                tt("map.in", "Closer")},
            {Component.literal("−"), (Runnable) () -> mapScale = Math.min(512, (mapScale < 0 ? 8 : mapScale) * 2),
                tt("map.out", "Further out")}}) {
            Component c = (Component) z[0];
            Runnable r = (Runnable) z[1];
            Button b = Button.builder(c, x -> {
                        r.run();
                        build();
                    })
                    .bounds(0, 0, font.width(c) + 12, 18)
                    .build();
            b.setTooltip(Tooltip.create((Component) z[2]));
            bar.add(b, rx, 3, true);
            rx += b.getWidth() + 3;
        }
        rows.add(bar);
        // (the whole map in view under its bar, at every GUI scale; the notes under it scroll)
        int mh = Mth.clamp(Math.min((int) (lw * 0.62), ly1 - ly0 - 28), 60, 340);
        rows.add(new MapRow(t, mh));
        rows.add(new Note(tt("map.note", "Click a giant to go to him, his ground's middle to go there; anywhere else"
                + " moves the map there. Circles: the giants' grounds (their colours), areas (red: no giants, green:"
                + " safe). ★ spawn, ■ players; a faded giant is out of the world or not loaded, a red edge fighting, a blue one"
                + " asleep."), GREY));
    }

    /** the map itself: the giants, their grounds, the areas, spawn and the players in one dimension, from above */
    private final class MapRow extends Row {
        final JsonObject t;
        /** what can be clicked or hovered: x, y (on the screen, from the row's top), radius, tip, what it does */
        record Spot(int x, int y, int r, List<FormattedCharSequence> tip, @Nullable Runnable go) {}

        final List<Spot> spots = new ArrayList<>();
        double scale, cx, cz;
        int drawnX, drawnW;

        MapRow(JsonObject t, int mh) {
            this.t = t;
            h = mh;
            // fit: every thing in this dimension, with room round it
            if (mapScale < 0) {
                double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
                List<double[]> pts = new ArrayList<>();
                for (JsonObject g : objects(t, "giants"))
                    if (str(g, "dim").equals(mapDim)) pts.add(new double[] {dbl(g, "x", 0), dbl(g, "z", 0), 16});
                if (mapDim.equals("minecraft:overworld")) {
                    for (JsonObject g : objects(t, "grounds"))
                        pts.add(new double[] {dbl(g, "x", 0), dbl(g, "z", 0), dbl(g, "r", 0)});
                    JsonObject sp = t.get("spawn") instanceof JsonObject s ? s : new JsonObject();
                    pts.add(new double[] {dbl(sp, "x", 0), dbl(sp, "z", 0), Math.max(32, dbl(sp, "clear", 0))});
                }
                for (JsonObject p : objects(t, "players"))
                    if (str(p, "dim").equals(mapDim)) pts.add(new double[] {dbl(p, "x", 0), dbl(p, "z", 0), 16});
                for (JsonObject a : objects(t, "areas"))
                    if (str(a, "dim").equals(mapDim)) pts.add(new double[] {dbl(a, "x", 0), dbl(a, "z", 0), dbl(a, "r", 0)});
                for (double[] p : pts) {
                    x0 = Math.min(x0, p[0] - p[2]);
                    x1 = Math.max(x1, p[0] + p[2]);
                    z0 = Math.min(z0, p[1] - p[2]);
                    z1 = Math.max(z1, p[1] + p[2]);
                }
                if (pts.isEmpty()) {
                    x0 = z0 = -256;
                    x1 = z1 = 256;
                }
                mapCx = (x0 + x1) / 2;
                mapCz = (z0 + z1) / 2;
                double sx = (x1 - x0) / Math.max(40, lw - 20), sz = (z1 - z0) / Math.max(40, mh - 20);
                scale = Math.max(0.25, Math.max(sx, sz) * 1.1);
            } else scale = mapScale;
            cx = mapCx;
            cz = mapCz;
        }

        int sx(double wx, int x, int w) {
            return (int) Math.round(x + w / 2.0 + (wx - cx) / scale);
        }

        int sy(double wz, int y) {
            return (int) Math.round(y + h / 2.0 + (wz - cz) / scale);
        }

        void circle(GuiGraphics g, int x, int y, int r, int fill, int edge) {
            if (r < 1) r = 1;
            for (int dy = -r; dy <= r; dy++) {
                int dx = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
                g.fill(x - dx, y + dy, x + dx + 1, y + dy + 1, fill);
                g.fill(x - dx, y + dy, x - dx + 1, y + dy + 1, edge);
                g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, edge);
            }
            int top = (int) Math.round(Math.sqrt((double) r * r - (double) (r - 1) * (r - 1)));
            g.fill(x - top, y - r, x + top + 1, y - r + 1, edge);
            g.fill(x - top, y + r, x + top + 1, y + r + 1, edge);
        }

        @Override
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
            spots.clear();
            drawnX = x;
            drawnW = w;
            int top = y;
            // the paper, its edge
            g.fill(x - 2, y, x + w, y + h, 0xFFE4D2A2);
            g.renderOutline(x - 2, y, w + 2, h, 0xFF8A6A40);
            g.enableScissor(x - 1, Math.max(y + 1, ly0), x + w - 1, Math.min(y + h - 1, ly1));
            // a grid every round number of blocks
            double step = 16;
            while (step / scale < 40) step *= 2;
            double gx0 = Math.floor((cx - (w / 2.0) * scale) / step) * step;
            for (double gx = gx0; gx < cx + (w / 2.0) * scale; gx += step) {
                int px = sx(gx, x, w);
                g.fill(px, y, px + 1, y + h, gx == 0 ? 0x50703010 : 0x22503010);
            }
            double gz0 = Math.floor((cz - (h / 2.0) * scale) / step) * step;
            for (double gz = gz0; gz < cz + (h / 2.0) * scale; gz += step) {
                int py = sy(gz, y);
                g.fill(x, py, x + w, py + 1, gz == 0 ? 0x50703010 : 0x22503010);
            }
            boolean over = mapDim.equals("minecraft:overworld");
            // the grounds
            if (over)
                for (JsonObject gr : objects(t, "grounds")) {
                    String key = str(gr, "key");
                    int c = giantColour(key);
                    int gx = sx(dbl(gr, "x", 0), x, w), gy = sy(dbl(gr, "z", 0), y), r = (int) (dbl(gr, "r", 0) / scale);
                    circle(g, gx, gy, r, (c & 0x00FFFFFF) | 0x30000000, (c & 0x00FFFFFF) | 0xA0000000);
                    g.fill(gx - 1, gy - 1, gx + 2, gy + 2, c);
                    String gname = giantName(key);
                    int copy = (int) dbl(gr, "copy", 0);
                    List<FormattedCharSequence> tip = new ArrayList<>();
                    tip.add(tt("map.ground", "%s's ground", gname).withStyle(ChatFormatting.YELLOW)
                            .getVisualOrderText());
                    tip.addAll(font.split(tt("map.ground.tip", "%s, %s · %s blocks across%s · click: take me to its"
                            + " middle", (int) dbl(gr, "x", 0), (int) dbl(gr, "z", 0), (int) dbl(gr, "r", 0) * 2,
                            copy > 0 ? " · copy " + copy : ""), 240));
                    spots.add(new Spot(gx - x, gy - top, 5, tip, () -> request("mapground", "giant", key)));
                }
            // the areas and spawn
            for (JsonObject a : objects(t, "areas")) {
                if (!str(a, "dim").equals(mapDim)) continue;
                boolean safe = str(a, "kind").equals("safe");
                int c = safe ? 0x2E7A2E : 0xB82818;
                int ax = sx(dbl(a, "x", 0), x, w), ay = sy(dbl(a, "z", 0), y);
                circle(g, ax, ay, (int) (dbl(a, "r", 0) / scale), c | 0x28000000, c | 0xC0000000);
                List<FormattedCharSequence> tip = new ArrayList<>();
                tip.add(Component.literal(str(a, "name")).withStyle(ChatFormatting.YELLOW).getVisualOrderText());
                tip.add((safe ? tt("areas.safe", "Safe") : tt("areas.keepout", "No giants")).getVisualOrderText());
                spots.add(new Spot(ax - x, ay - top, 4, tip, null));
            }
            // the giants
            for (JsonObject gi : objects(t, "giants")) {
                if (!str(gi, "dim").equals(mapDim)) continue;
                String key = str(gi, "key"), state = str(gi, "state");
                int gx = sx(dbl(gi, "x", 0), x, w), gy = sy(dbl(gi, "z", 0), y);
                int edge = bool(gi, "fighting") ? 0xFFE03020 : bool(gi, "asleep") ? 0xFF2A5E9A : giantColour(key);
                g.fill(gx - 7, gy - 7, gx + 7, gy + 7, edge);
                icon(g, key, gx - 6, gy - 6, 12);
                if (!state.equals("here")) g.fill(gx - 6, gy - 6, gx + 6, gy + 6, 0x90E4D2A2);
                List<FormattedCharSequence> tip = new ArrayList<>();
                tip.add(Component.literal(str(gi, "name")).withStyle(ChatFormatting.YELLOW).getVisualOrderText());
                StringBuilder sb = new StringBuilder();
                sb.append(Math.round(dbl(gi, "x", 0))).append(", ").append(Math.round(dbl(gi, "z", 0)));
                if (gi.has("health"))
                    sb.append(" · ").append(Math.round(dbl(gi, "health", 0))).append("/")
                            .append(Math.round(dbl(gi, "max", 0)));
                if (!str(gi, "mood").isEmpty()) sb.append(" · ").append(str(gi, "mood"));
                if (bool(gi, "fighting")) sb.append(" · ").append(tr("live.fighting").getString());
                if (bool(gi, "asleep")) sb.append(" · ").append(tr("live.asleep").getString());
                if (!state.equals("here")) sb.append(" · ").append(tr("live.state." + state).getString());
                if (!str(gi, "owner").isEmpty()) sb.append(" · ").append(tr("live.owner", str(gi, "owner")).getString());
                tip.addAll(font.split(Component.literal(sb.toString()), 240));
                tip.add(tt("map.giant.tip", "Click: take me to him").withStyle(ChatFormatting.GRAY)
                        .getVisualOrderText());
                String id = str(gi, "id");
                spots.add(new Spot(gx - x, gy - top, 7, tip, () -> request("maptp", "giant", key, "id", id)));
            }
            if (over) {
                JsonObject sp = t.get("spawn") instanceof JsonObject s ? s : new JsonObject();
                int px = sx(dbl(sp, "x", 0), x, w), py = sy(dbl(sp, "z", 0), y);
                int clear = (int) dbl(sp, "clear", 0);
                if (clear > 0) circle(g, px, py, (int) (clear / scale), 0x20B82818, 0x90B82818);
                g.drawString(font, "★", px - 3, py - 4, 0xFFC08A10, false);
                List<FormattedCharSequence> tip = new ArrayList<>();
                tip.add(tt("map.spawn", "The world's spawn").withStyle(ChatFormatting.YELLOW).getVisualOrderText());
                if (clear > 0) tip.add(tt("map.spawn.clear", "No giants within %s blocks", clear).getVisualOrderText());
                spots.add(new Spot(px - x, py - top, 4, tip, null));
            }
            // the players
            for (JsonObject p : objects(t, "players")) {
                if (!str(p, "dim").equals(mapDim)) continue;
                int px = sx(dbl(p, "x", 0), x, w), py = sy(dbl(p, "z", 0), y);
                g.fill(px - 2, py - 2, px + 3, py + 3, 0xFF202020);
                g.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFFFFF);
                spots.add(new Spot(px - x, py - top, 3, List.of(Component.literal(str(p, "name"))
                        .withStyle(ChatFormatting.YELLOW).getVisualOrderText()), null));
            }
            g.disableScissor();
            // a scale bar and north
            int barPx = (int) Math.round(step / scale);
            int by = y + h - 8;
            g.fill(x + 4, by, x + 4 + barPx, by + 1, 0xFF3A2814);
            g.fill(x + 4, by - 2, x + 5, by + 2, 0xFF3A2814);
            g.fill(x + 3 + barPx, by - 2, x + 4 + barPx, by + 2, 0xFF3A2814);
            g.drawString(font, tt("map.blocks", "%s blocks", (int) step), x + 8 + barPx, by - 4, GREY, false);
            g.drawString(font, "N ↑", x + w - 22, y + 4, HEAD, false);
            // what the mouse is over
            Spot best = null;
            double bd = Double.MAX_VALUE;
            for (Spot s : spots) {
                double d = Math.hypot(mx - (x + s.x()), my - (top + s.y()));
                if (d <= s.r() + 1 && d < bd) {
                    bd = d;
                    best = s;
                }
            }
            if (best != null && my >= ly0 && my < ly1) setTooltipForNextRenderPass(best.tip());
        }

        @Override
        boolean click(double mx, double my, int top) {
            if (mx < drawnX || mx >= drawnX + drawnW) return false;
            Spot best = null;
            double bd = Double.MAX_VALUE;
            for (Spot s : spots) {
                if (s.go() == null) continue;
                double d = Math.hypot(mx - (drawnX + s.x()), my - (top + s.y()));
                if (d <= s.r() + 1 && d < bd) {
                    bd = d;
                    best = s;
                }
            }
            if (best != null) {
                best.go().run();
                return true;
            }
            // anywhere else: the map moves there
            mapScale = scale;
            mapCx = cx + (mx - (drawnX + drawnW / 2.0)) * scale;
            mapCz = cz + (my - (top + h / 2.0)) * scale;
            double sc = scroll;
            build();
            scroll = sc;
            place();
            return true;
        }
    }

    // stats

    private void toolsStats(JsonObject t) {
        long since = lng(t, "statsSince");
        rows.add(new Note(tt("stats.note", "Counted in this world%s. What each kind costs the server is the time his"
                + " own entities (him, his parts, his creatures) take to tick, each tick, over the last ten seconds.",
                since > 0 ? " since " + when(since) : ""), GREY));
        List<JsonObject> stats = objects(t, "stats");
        double most = 0.05;
        for (JsonObject s : stats) most = Math.max(most, dbl(s, "ms", 0));
        double top = most;
        for (JsonObject s : stats) {
            String key = str(s, "key");
            int here = (int) dbl(s, "here", 0), away = (int) dbl(s, "away", 0);
            String owners = str(s, "owners");
            List<FormattedCharSequence> l2 = font.split(tt("stats.line", "Fell %s (%s by players) · killed %s players ·"
                    + " %s damage to players · %s blocks broken%s", (int) dbl(s, "deaths", 0), (int) dbl(s, "slain", 0),
                    (int) dbl(s, "killed", 0), (long) dbl(s, "damage", 0), (long) dbl(s, "blocks", 0),
                    owners.isEmpty() ? "" : " · owned by " + owners), lw - 30);
            double ms = dbl(s, "ms", 0);
            rows.add(new Block(16 + l2.size() * 10 + 6, (g, x, y, w, mx, my) -> {
                AdminStyle.card(g, x - 2, y + 1, w + 2, 14 + l2.size() * 10 + 3, false);
                icon(g, key, x + 2, y + 3, 18);
                int nx = g.drawString(font, str(s, "name"), x + 26, y + 4, KEY, false) + 6;
                String standing = !bool(s, "listed") ? tt("stats.unlisted", "not listed").getString()
                        : tt("stats.standing", "%s standing", here).getString()
                                + (away > 0 ? " · " + tt("stats.away", "%s away", away).getString() : "");
                g.drawString(font, standing, nx, y + 4, GREY, false);
                int barW = Mth.clamp(w / 5, 40, 90), bx = x + w - barW - 4;
                String mt = String.format(Locale.ROOT, "%.2f ms", ms);
                g.drawString(font, mt, bx - font.width(mt) - 4, y + 4, ms > 2 ? WARN : TEXT, false);
                g.fill(bx, y + 6, bx + barW, y + 11, 0x40503010);
                g.fill(bx, y + 6, bx + (int) Math.round(barW * Math.min(1, ms / top)), y + 11,
                        ms > 2 ? 0xFFE04040 : ms > 0.5 ? 0xFFE0C050 : 0xFF60C060);
                for (int i = 0; i < l2.size(); i++) g.drawString(font, l2.get(i), x + 26, y + 15 + i * 10, TEXT, false);
            }));
        }
        rows.add(new LineRow(tt("stats.server", "The whole server: %s ms per tick (50 is the most it has)",
                dbl(t, "tickMs", 0)), TEXT, Component.empty(), List.<Object[]>of(new Object[] {tt("stats.reset",
                "Clear the counts"), (Runnable) () -> confirm(tt("stats.reset.title", "Clear the giants' counts?"),
                tt("stats.reset.body", "Falls, players killed, damage and blocks start again from now."),
                tt("stats.reset", "Clear the counts"), () -> request("statsreset"))})));
    }

    // alerts

    private void toolsAlerts(JsonObject t) {
        toolSettings("alerts/which", tt("alerts.which", "Which alerts"), tt("alerts.which.note", "Told in chat to the"
                + " admins online (not to the one it is about), with a link to go there. Quiet mode keeps them in"
                + " the book only."), "t_alerts", "all.alertFights", "all.alertRadius", "all.alertGrief",
                "all.alertGriefBlocks", "all.alertDeaths", "all.alertAreas");
        List<JsonObject> alerts = objects(t, "alerts");
        rows.add(new Head(tt("alerts.list", "Alerts (%s)", alerts.size()), null, null).pic("t_alerts"));
        if (alerts.isEmpty()) {
            rows.add(new Note(tt("alerts.none", "Nothing yet."), GREY));
            return;
        }
        rows.add(new LineRow(tt("alerts.newest", "Newest first"), GREY, Component.empty(), List.<Object[]>of(
                new Object[] {tt("alerts.clear", "Clear"), (Runnable) () -> request("alertsclear")})));
        for (JsonObject a : alerts) {
            String giant = str(a, "giant"), id = str(a, "id"), kind = str(a, "kind");
            int colour = kind.equals("death") || kind.equals("fight") ? WARN : kind.equals("grief") ? ORANGE : KEY;
            List<Object[]> bs = new ArrayList<>();
            if (!giant.isEmpty() && !id.isEmpty())
                bs.add(new Object[] {tt("alerts.go", "Go there"), (Runnable) () -> request("maptp", "giant", giant,
                        "id", id), tt("alerts.go.tip", "Takes you to him, if he's still there")});
            String where = str(a, "dim").isEmpty() ? "" : " · " + (int) dbl(a, "x", 0) + ", " + (int) dbl(a, "z", 0)
                    + " " + dimName(str(a, "dim"));
            rows.add(new LineRow(Component.literal(str(a, "text")), colour,
                    Component.literal(when(lng(a, "time")) + where), bs));
        }
    }

    // respawn timers

    private static final long[] WAITS = {1200 * 5, 24000, 72000, 120000, 168000, 336000, 720000};

    private static String waitWords(long ticks) {
        if (ticks >= 24000) {
            long d = Math.round(ticks / 24000.0);
            return tt(d == 1 ? "wait.day" : "wait.days", d == 1 ? "about %s day" : "about %s days", d).getString();
        }
        long m = Math.max(1, Math.round(ticks / 1200.0));
        return tt(m == 1 ? "wait.minute" : "wait.minutes", m == 1 ? "about %s minute" : "about %s minutes", m)
                .getString();
    }

    private void toolsRespawns(JsonObject t) {
        rows.add(new Note(tt("resp.note", "When each giant's own one (the one that comes on his own) comes back after"
                + " he falls or is taken away. Bring him back now, or set how long until he comes: he still comes"
                + " his own way, to his own spot, and never past Most at once. A game day is 20 minutes."), GREY));
        for (JsonObject r : objects(t, "respawns")) {
            String key = str(r, "key"), state = str(r, "state");
            boolean speaks = bool(r, "speaks");
            long ticks = lng(r, "ticks");
            Component sub = !speaks ? tt("resp.old", "This version doesn't say.")
                    : switch (state) {
                        case "standing" -> tt("resp.standing", "Out there now%s", r.has("x") ? " (" + (int) dbl(r, "x",
                                0) + ", " + (int) dbl(r, "z", 0) + ")" : "");
                        case "due" -> ticks <= 100 ? tt("resp.soon", "Comes back within a few seconds")
                                : tt("resp.due", "Comes back in %s", waitWords(ticks));
                        case "gone" -> tt("resp.gone", "Fallen for good (Comes back after he falls is off)");
                        case "off" -> tt("resp.off", "Doesn't come on his own (Comes on his own is off)");
                        default -> tt("resp.waiting", "Not counting yet (nobody has been in the world)");
                    };
            boolean can = speaks && (state.equals("due") || state.equals("gone") || state.equals("waiting"));
            int colour = state.equals("standing") ? GOOD : state.equals("due") ? KEY : DIM;
            String name = str(r, "name");
            rows.add(new LineRow(Component.literal(name), colour, sub, List.<Object[]>of(
                    new Object[] {tt("resp.now", "Bring back now"), (Runnable) () -> request("respawn", "giant", key,
                            "ticks", 0), tt("resp.now.tip", "He comes within a few seconds, once someone is in the"
                                    + " world"), can},
                    new Object[] {tt("resp.set", "Set the wait…"), (Runnable) () -> {
                        List<JsonObject> opts = new ArrayList<>();
                        for (long w : WAITS) {
                            JsonObject o = new JsonObject();
                            o.addProperty("id", String.valueOf(w));
                            o.addProperty("name", waitWords(w));
                            opts.add(o);
                        }
                        modal = new PickPlayer(tt("resp.set.title", "When does the %s come back?", name),
                                tt("resp.set.body", "From now. He comes his own way when the time is up."), opts,
                                o -> request("respawn", "giant", key, "ticks", Long.parseLong(str(o, "id"))));
                        place();
                    }, tt("resp.set.tip", "How long until he comes back, from now"), can})));
        }
    }

    // the world's profile, the modpack

    private void toolsServer(JsonObject t) {
        JsonObject p = t.get("profile") instanceof JsonObject o ? o : new JsonObject();
        boolean exists = bool(p, "exists"), changedFile = bool(p, "changed");
        long at = lng(p, "applied");
        rows.add(new Head(tt("profile", "This world's profile"), tt("profile.note", "A file of this world's settings"
                + " in the server's config folder. When the world starts and the file has changed since, the world"
                + " takes it (like a preset, a snapshot first). Copy or edit it to set this world up again."), null)
                .pic("t_server"));
        rows.add(new Card(Component.literal(str(p, "file")), "t_server",
                !exists ? tt("profile.none", "No file yet. Save writes this world's settings there.")
                        : changedFile ? tt("profile.changed", "It has changed since the world last took it: it goes in"
                                + " when the world next starts, or press Put it in now.")
                        : tt("profile.same", "The world has what the file says%s.", at > 0 ? " (since " + when(at) + ")"
                                : ""),
                exists ? KEY : GREY, List.<Object[]>of(
                        new Object[] {tt("profile.save", "Save this world's settings"), (Runnable) () -> {
                            if (exists) confirm(tt("profile.save.title", "Write over the profile?"), tt(
                                    "profile.save.body", "The file gets this world's settings as they are now."),
                                    tt("profile.save", "Save this world's settings"), () -> request("profilesave"));
                            else request("profilesave");
                        }},
                        new Object[] {tt("profile.apply", "Put it in now"), (Runnable) () -> confirm(
                                tt("profile.apply.title", "Put the profile in now?"), tt("profile.apply.body",
                                        "This world's settings become what the file says (a snapshot first)."),
                                tt("profile.apply", "Put it in now"), () -> request("profileapply")), null, exists},
                        new Object[] {tt("profile.delete", "Delete the file"), (Runnable) () -> confirm(
                                tt("profile.delete.title", "Delete the profile file?"), tt("profile.delete.body",
                                        "The world keeps the settings it has."), tt("profile.delete",
                                        "Delete the file"), () -> request("profiledelete")), null, exists})));
        JsonObject pack = t.get("pack") instanceof JsonObject o ? o : new JsonObject();
        rows.add(new Head(tt("pack", "Modpack"), tt("pack.note", "A modpack sets the preset its worlds play with in"
                + " %s: {\"name\":..., \"preset\":..., \"lock\":true|false}. New worlds start with it; locked, it stays.",
                str(pack, "file")), null).pic("presets"));
        rows.add(new Card(bool(pack, "on") ? Component.literal(str(pack, "name")) : tt("pack.none", "No modpack"),
                "presets", !bool(pack, "on") ? tt("pack.none.body", "Every world picks its own preset.")
                        : tt(bool(pack, "lock") ? "pack.locked" : "pack.open", bool(pack, "lock")
                                ? "Plays with the %s preset and keeps it: other presets, imports, snapshots, schedules"
                                        + " and votes can't be applied. Single settings can still change."
                                : "Plays with the %s preset: new worlds start with it, and admins may change it.",
                                str(pack, "presetName")),
                bool(pack, "lock") ? WARN : bool(pack, "on") ? KEY : GREY, List.of()));
    }

    /** (1.12.0) Part 6's Pause all giants is on (Home's server tools line says so) */
    private static boolean allPaused() {
        JsonObject h = AdminBookClient.home();
        return h != null && h.get("tools") instanceof JsonObject t && bool(t, "paused");
    }

    private static final String PAUSED_FUN = "All giants are paused right now. Unpause them first (Server tools).";

    /** Home's line about the server tools: what is on now (paused, an event, a schedule, quiet, a modpack) */
    private void toolsHomeLine(JsonObject h) {
        JsonObject t = h.get("tools") instanceof JsonObject o ? o : null;
        if (t == null) return;
        List<String> on = new ArrayList<>();
        if (bool(t, "paused")) on.add(tt("home.paused", "Paused").getString());
        if (t.get("event") instanceof JsonObject e) on.add(tt("home.event", "Event: %s", str(e, "name")).getString());
        if (t.get("schedule") instanceof JsonObject s)
            on.add(tt("home.schedule", "Schedule: %s", str(s, "name")).getString());
        if (bool(t, "quiet")) on.add(tt("home.quiet", "Quiet mode").getString());
        if (t.get("pack") instanceof JsonObject p && bool(p, "on"))
            on.add(tt("home.pack", "Modpack: %s (%s)", str(p, "name"), str(p, "presetName")).getString());
        if (on.isEmpty()) return;
        rows.add(new LineRow(tt("home.now", "Now: %s", String.join(" · ", on)), bool(t, "paused") ? WARN : KEY,
                Component.empty(), List.<Object[]>of(new Object[] {tt("name", "Server tools"),
                    (Runnable) () -> go(TOOLS)})));
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

        int modalW() {
            return Math.min(pw - 40, 300);
        }

        /** (Part 2C) the first body line shown (a long body scrolls) */
        int bodyScroll;

        /** how many body lines fit on the screen */
        int bodyRoom() {
            return Math.max(4, (height - 90) / 10);
        }

        void layout(int buttonsW) {
            mw = modalW();
            List<FormattedCharSequence> b = body();
            mh = 30 + Math.min(b.size(), bodyRoom()) * 10 + 28;
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
            AdminStyle.frame(g, mx0 - 8, my0 - 8, mw + 16, mh + 16);
            AdminStyle.page(g, mx0 + 2, my0 + 2, mw - 4, mh - 4);
            g.drawString(font, title(), mx0 + 8, my0 + 8, HEAD, false);
            g.fill(mx0 + 8, my0 + 19, mx0 + mw - 8, my0 + 20, AdminStyle.RULE);
            List<FormattedCharSequence> b = body();
            int room = Math.min(b.size(), bodyRoom());
            bodyScroll = Mth.clamp(bodyScroll, 0, Math.max(0, b.size() - room));
            for (int i = 0; i < room; i++)
                g.drawString(font, b.get(i + bodyScroll), mx0 + 8, my0 + 26 + i * 10, TEXT, false);
            if (b.size() > room) {
                // a scroll bar on the right
                int top = my0 + 26, track = room * 10, bar = Math.max(8, track * room / b.size());
                int by = top + (track - bar) * bodyScroll / Math.max(1, b.size() - room);
                g.fill(mx0 + mw - 6, top, mx0 + mw - 4, top + track, 0x30503010);
                g.fill(mx0 + mw - 6, by, mx0 + mw - 4, by + bar, 0xFF8A2A14);
            }
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

        /** (Part 7) typing, dragging a slider, letting go */
        boolean charTyped(char c, int mods) {
            return true;
        }

        boolean dragged(double mx, double my, int button, double dx, double dy) {
            return false;
        }

        void released(double mx, double my) {}

        /** the wheel over a long body */
        void scrolled(double sy) {
            bodyScroll -= (int) Math.signum(sy) * 3;
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
        static final int PAGES = 5;
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

    // (Part 7, ADMIN_BRIDGE.md 23.4) the Fun and World tools pages

    public static final String FUN = "fun", WORLD = "world";

    /** pages that show the giants from Live control (the server sends it once a second while one is open) */
    private static boolean livePage(String key) {
        return key.equals(LIVE) || key.equals(FUN) || key.equals(WORLD);
    }

    /** (the picture-taking) a Fun page question open: "name" or "loot" (the first giant's), "tagedit" (the first tag) */
    public static boolean testFunAsk(String what) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof AdminBookScreen b)) return false;
        if (what.equals("tagedit")) {
            List<JsonObject> tags = objects(AdminBookClient.fun(), "tags");
            if (tags.isEmpty()) return false;
            b.openTagEdit(tags.get(0));
            return true;
        }
        List<JsonObject[]> gs = b.funGiants(true);
        if (gs.isEmpty()) return false;
        FunRow r = b.new FunRow(gs.get(0)[0], gs.get(0)[1]);
        for (Placed p : r.ws) b.removeWidget(p.w());
        r.press(what);
        return true;
    }

    /** what the fight pickers, the build and mixing have picked (kept while the game runs) */
    private static String fightA = "", fightB = "", denKind = "", settleId = "", settleWhere = "here";
    private static double fightSizeA = 0.5, fightSizeB = 0.5, denSize = 0.5;
    private static boolean fightLook;
    private static String arenaSize = "medium";

    /** each giant from Live control: {kind, row}, standing ones first */
    private List<JsonObject[]> funGiants(boolean standingOnly) {
        List<JsonObject[]> out = new ArrayList<>();
        for (JsonObject k : objects(AdminBookClient.live(), "giants")) {
            if (!bool(k, "speaks")) continue;
            List<JsonObject> rs = objects(k, "rows");
            for (int i = 0; i < rs.size(); i++) {
                JsonObject r = rs.get(i).deepCopy();
                if (standingOnly && !str(r, "state").equals("here")) continue;
                r.addProperty("label", !str(r, "name").isEmpty() ? str(r, "name")
                        : str(k, "name") + (rs.size() > 1 ? " " + (i + 1) : ""));
                out.add(new JsonObject[] {k, r});
            }
        }
        return out;
    }

    private @Nullable JsonObject funMark(String id) {
        JsonObject f = AdminBookClient.fun();
        return f != null && f.get("marks") instanceof JsonObject m && m.get(id) instanceof JsonObject j ? j : null;
    }

    private List<String> funDoes(String kind) {
        for (JsonObject k : objects(AdminBookClient.fun(), "kinds")) if (str(k, "key").equals(kind)) return strings(k,
                "does");
        return List.of();
    }

    private List<String> funPlaces(String kind) {
        JsonObject f = AdminBookClient.fun();
        return f != null && f.get("places") instanceof JsonObject p ? strings(p, kind) : List.of();
    }

    private void funRequest(String what, JsonObject o) {
        o.addProperty("do", what);
        if (AdminBookClient.request(o)) say(tr("sent"), GREY);
        else say(tr("no_server"), WARN);
    }

    private static JsonObject giantJson(String kind, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("giant", kind);
        o.addProperty("id", id);
        return o;
    }

    /** the Fun page: you, each giant, a fight, the tags */
    private void funRows() {
        JsonObject f = AdminBookClient.fun();
        if (f == null || AdminBookClient.live() == null) {
            rows.add(new Note(tr("asking"), GREY));
            return;
        }
        rows.add(new Note(tr("fun.note"), GREY));
        // you: watching or playing
        JsonObject me = f.get("me") instanceof JsonObject m ? m : new JsonObject();
        if (me.get("watching") instanceof JsonObject w || me.get("playing") instanceof JsonObject) {
            rows.add(new Head(tr("fun.you"), null, null).pic("watch"));
            if (me.get("watching") instanceof JsonObject w)
                rows.add(new LineRow(tr("fun.you.watching", str(w, "label")), KEY,
                        tr(str(w, "mode").equals("eyes") ? "fun.you.eyes" : "fun.you.free"),
                        List.<Object[]>of(new Object[] {tr("fun.watch.stop"), (Runnable) () -> funRequest(
                                "spectatestop", new JsonObject()), tr("fun.watch.stop.tip")}), true));
            if (me.get("playing") instanceof JsonObject pl)
                rows.add(new LineRow(tr("fun.you.playing", str(pl, "label")), KEY, tr("fun.you.playing.sub"),
                        List.<Object[]>of(new Object[] {tr("fun.play.stop"), (Runnable) () -> funRequest(
                                "playasstop", new JsonObject()), tr("fun.play.stop.tip")}), true));
        }
        // each giant
        List<JsonObject[]> all = funGiants(false);
        Head each = new Head(tr("fun.each"), tr("fun.each.note"), null).folding(FUN + "/each", "live", all.size());
        rows.add(each);
        if (!each.shut()) {
            if (all.isEmpty()) rows.add(new Note(tr("live.empty"), GREY));
            for (JsonObject[] g : all) rows.add(new FunRow(g[0], g[1]));
        }
        // stage a fight
        Head fight = new Head(tr("fun.fight"), tr("fun.fight.note"), null).folding(FUN + "/fight", "fight", -1);
        rows.add(fight);
        if (!fight.shut()) fightRows();
        // giant tags
        List<JsonObject> tags = objects(f, "tags");
        Head th = new Head(tr("fun.tags"), tr("fun.tags.note"), null).folding(FUN + "/tags", "tag", tags.size());
        rows.add(th);
        if (!th.shut()) {
            for (JsonObject t : tags) {
                String name = str(t, "name");
                rows.add(new LineRow(Component.literal(name), KEY, Component.literal(tagWords(t)), List.<Object[]>of(
                        new Object[] {tr("fun.tags.edit"), (Runnable) () -> openTagEdit(t), tr("fun.tags.edit.tip")},
                        new Object[] {tr("fun.tags.delete"), (Runnable) () -> confirm(tr("fun.tags.delete.title", name),
                                tr("fun.tags.delete.body", name, (int) dbl(t, "wearing", 0)), tr("fun.tags.delete"),
                                () -> {
                                    JsonObject o = new JsonObject();
                                    o.addProperty("name", name);
                                    funRequest("tagdelete", o);
                                }), tr("fun.tags.delete.tip")}), true));
            }
            rows.add(new LineRow(Component.empty(), TEXT, Component.empty(), List.<Object[]>of(new Object[] {
                tr("fun.tags.new"), (Runnable) () -> openTagEdit(null), tr("fun.tags.new.tip")})));
        }
    }

    private String tagWords(JsonObject t) {
        List<String> w = new ArrayList<>();
        w.add(tr("fun.tags.stats", AdminSetting.plain(dbl(t, "health", 1)), AdminSetting.plain(dbl(t, "damage", 1)),
                AdminSetting.plain(dbl(t, "speed", 1))).getString());
        if (dbl(t, "size", 0) > 0) w.add(tr("fun.tags.size", AdminSetting.plain(dbl(t, "size", 0))).getString());
        if (!str(t, "title").isEmpty()) w.add("\"" + str(t, "title") + "\"");
        if (bool(t, "glow")) w.add(tr("fun.tags.glow").getString());
        if (t.get("loot") instanceof JsonObject) w.add(str(t, "lootWords"));
        int wearing = (int) dbl(t, "wearing", 0);
        w.add(tr(wearing == 0 ? "fun.tags.wearing.none" : wearing == 1 ? "fun.tags.wearing.one" : "fun.tags.wearing",
                wearing).getString());
        return String.join(" · ", w);
    }

    /** one giant on the Fun page: who he is, his tag and loot, and the things that can be done with him */
    private final class FunRow extends Row {
        final JsonObject kind, r;
        final String key, id, label;
        final boolean here;

        FunRow(JsonObject kind, JsonObject r) {
            this.kind = kind;
            this.r = r;
            key = str(kind, "key");
            id = str(r, "id");
            label = str(r, "label");
            here = str(r, "state").equals("here");
            List<String> does = funDoes(key);
            List<Object[]> bs = new ArrayList<>();
            bs.add(new Object[] {"watch", true});
            bs.add(new Object[] {"eyes", true});
            bs.add(new Object[] {"play", does.contains("playas")});
            bs.add(new Object[] {"name", true});
            bs.add(new Object[] {"tag", true});
            bs.add(new Object[] {"loot", true});
            int bx = 30, by = 26;
            h = 46;
            for (Object[] o : bs) {
                String act = (String) o[0];
                Component c = tr("fun.act." + act);
                int w = font.width(c) + 10;
                if (bx > 30 && bx + w > lw) {
                    bx = 30;
                    by += 18;
                    h += 18;
                }
                Button b = Button.builder(c, x -> press(act)).bounds(0, 0, w, 16).build();
                // (1.12.0) while every giant is paused (Server tools) none can be played
                boolean paused = act.equals("play") && allPaused();
                b.active = here && (boolean) o[1] && !paused;
                b.setTooltip(Tooltip.create(!here ? tr("fun.away") : !(boolean) o[1] ? tr("fun.cant", str(kind, "name"))
                        : paused ? tt("paused.fun", PAUSED_FUN) : tr("fun.act." + act + ".tip")));
                add(b, bx, by, false);
                bx += w + 3;
            }
        }

        void press(String act) {
            switch (act) {
                case "watch", "eyes" -> {
                    JsonObject o = giantJson(key, id);
                    o.addProperty("mode", act.equals("eyes") ? "eyes" : "free");
                    funRequest("spectate", o);
                }
                case "play" -> funRequest("playas", giantJson(key, id));
                case "name" -> openNameAsk(key, id, label);
                case "tag" -> {
                    List<Object[]> cs = new ArrayList<>();
                    for (JsonObject t : objects(AdminBookClient.fun(), "tags")) {
                        String n = str(t, "name");
                        cs.add(new Object[] {Component.literal(n), (Runnable) () -> {
                            JsonObject o = giantJson(key, id);
                            o.addProperty("tag", n);
                            funRequest("tag", o);
                        }});
                    }
                    cs.add(new Object[] {tr("fun.tag.none"), (Runnable) () -> {
                        JsonObject o = giantJson(key, id);
                        o.addProperty("tag", "");
                        funRequest("tag", o);
                    }});
                    modal = new Choices(tr("fun.tag.title", label), tr("fun.tag.body"), cs);
                    place();
                }
                case "loot" -> {
                    List<Object[]> cs = new ArrayList<>();
                    for (String mode : List.of("add", "replace"))
                        cs.add(new Object[] {tr("fun.loot." + mode), (Runnable) () -> {
                            JsonObject o = giantJson(key, id);
                            o.addProperty("mode", mode);
                            o.addProperty("from", "chest");
                            funRequest("loot", o);
                        }});
                    cs.add(new Object[] {tr("fun.loot.clear"), (Runnable) () -> {
                        JsonObject o = giantJson(key, id);
                        o.addProperty("mode", "clear");
                        funRequest("loot", o);
                    }});
                    JsonObject m = funMark(id);
                    modal = new Choices(tr("fun.loot.title", label), tr("fun.loot.body", m == null
                            ? tr("fun.loot.own").getString() : str(m, "lootWords")), cs);
                    place();
                }
                default -> {
                }
            }
        }

        @Override
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
            AdminStyle.card(g, x - 2, y + 1, w + 2, h - 3, false);
            icon(g, key, x + 2, y + 4, 20);
            int tx = x + 30;
            int nx = g.drawString(font, cut(label, w / 2), tx, y + 4, KEY, false) + 6;
            JsonObject m = funMark(id);
            List<Object[]> tags = new ArrayList<>();
            if (!here) tags.add(new Object[] {tr(str(r, "state").equals("away") ? "live.state.away"
                    : "live.state.unloaded").getString(), ORANGE});
            if (m != null && !str(m, "tag").isEmpty()) tags.add(new Object[] {str(m, "tag"), BLUE});
            if (bool(r, "fighting")) tags.add(new Object[] {tr("live.fighting").getString(), WARN});
            for (Object[] t : tags) {
                String s = (String) t[0];
                int tw = font.width(s) + 6;
                if (nx + tw > x + w) break;
                g.fill(nx, y + 3, nx + tw, y + 13, 0x30000000);
                g.renderOutline(nx, y + 3, tw, 10, ((int) t[1] & 0x00FFFFFF) | 0x80000000);
                g.drawString(font, s, nx + 3, y + 4, (int) t[1], false);
                nx += tw + 3;
            }
            StringBuilder l2 = new StringBuilder();
            if (r.has("size")) l2.append(tr("live.size", AdminSetting.plain(dbl(r, "size", 1))).getString());
            l2.append(l2.length() > 0 ? " · " : "").append(dimName(str(r, "dim")));
            if (m != null && m.get("loot") instanceof JsonObject) l2.append(" · ").append(str(m, "lootWords"));
            g.drawString(font, cut(l2.toString(), w - 32), tx, y + 15, GREY, false);
        }
    }

    /** the fight: A and B each a fresh one of a kind (and a size) or one out there; the spot; go */
    private void fightRows() {
        List<JsonObject> kinds = objects(AdminBookClient.live(), "giants").stream().filter(k -> bool(k, "speaks"))
                .toList();
        if (kinds.isEmpty()) {
            rows.add(new Note(tr("live.empty"), GREY));
            return;
        }
        // the choices: each kind fresh, then each one standing out there
        List<String[]> opts = new ArrayList<>();
        for (JsonObject k : kinds) opts.add(new String[] {str(k, "key"), tr("fun.fight.fresh", str(k, "name"))
                .getString(), "", str(k, "key")});
        for (JsonObject[] g : funGiants(true)) opts.add(new String[] {"id:" + str(g[1], "id"), str(g[1], "label"),
            str(g[1], "id"), str(g[0], "key")});
        if (fightA.isEmpty() || opts.stream().noneMatch(o -> o[0].equals(fightA))) fightA = opts.get(0)[0];
        if (fightB.isEmpty() || opts.stream().noneMatch(o -> o[0].equals(fightB)))
            fightB = opts.get(Math.min(1, opts.size() - 1))[0];
        for (int side = 0; side < 2; side++) {
            boolean a = side == 0;
            String now = a ? fightA : fightB;
            String[] pick = opts.stream().filter(o -> o[0].equals(now)).findFirst().orElse(opts.get(0));
            Component lab = tr(a ? "fun.fight.a" : "fun.fight.b");
            Block b = new Block(22, (g, x, y, w, mx, my) -> g.drawString(font, lab, x + 2, y + 7, KEY, false));
            int x0 = font.width(lab) + 8;
            int kw = Mth.clamp(lw / 3, 90, 150);
            Button kb = Button.builder(Component.literal(cut(pick[1], kw - 10)), x -> {
                        int i = 0;
                        for (int k = 0; k < opts.size(); k++) if (opts.get(k)[0].equals(now)) i = k;
                        String next = opts.get((i + 1) % opts.size())[0];
                        if (a) fightA = next;
                        else fightB = next;
                        build();
                    })
                    .bounds(0, 0, kw, 16)
                    .build();
            kb.setTooltip(Tooltip.create(tr("fun.fight.pick.tip")));
            b.add(kb, x0, 3, false);
            if (pick[2].isEmpty()) {
                JsonObject k = kinds.stream().filter(q -> str(q, "key").equals(pick[3])).findFirst().orElse(kinds.get(0));
                List<String> sz = strings(k, "size");
                double lo = sz.size() == 2 ? Double.parseDouble(sz.get(0)) : 0.1,
                        hi = Math.min(2, sz.size() == 2 ? Double.parseDouble(sz.get(1)) : 2);
                double cur = Mth.clamp(a ? fightSizeA : fightSizeB, lo, hi);
                AbstractSliderButton s = new AbstractSliderButton(0, 0, Mth.clamp(lw - x0 - kw - 8, 60, 150), 16,
                        Component.empty(), (cur - lo) / Math.max(1e-6, hi - lo)) {
                    {
                        updateMessage();
                    }

                    double v() {
                        double v = lo + value * (hi - lo);
                        return Mth.clamp(Math.round(v * 20) / 20.0, lo, hi);
                    }

                    @Override
                    protected void updateMessage() {
                        setMessage(tr("live.summon.size", AdminSetting.plain(v())));
                    }

                    @Override
                    protected void applyValue() {
                        if (a) fightSizeA = v();
                        else fightSizeB = v();
                    }
                };
                s.setTooltip(Tooltip.create(tr("fun.fight.size.tip")));
                b.add(s, x0 + kw + 4, 3, false);
            }
            rows.add(b);
        }
        rows.add(new LineRow(tr(fightLook ? "fun.fight.look" : "fun.fight.here"), TEXT, tr("fun.fight.spot.sub"),
                List.<Object[]>of(new Object[] {tr("fun.fight.spot"), (Runnable) () -> {
                    fightLook = !fightLook;
                    build();
                }, tr("fun.fight.spot.tip")}, new Object[] {tr("fun.fight.go"), (Runnable) () -> {
                    JsonObject o = new JsonObject();
                    o.add("a", fightSide(fightA, fightSizeA));
                    o.add("b", fightSide(fightB, fightSizeB));
                    o.addProperty("spot", fightLook ? "look" : "here");
                    funRequest("fight", o);
                }, allPaused() ? tt("paused.fun", PAUSED_FUN) : tr("fun.fight.go.tip"),
                    (!fightA.equals(fightB) || !fightA.startsWith("id:")) && !allPaused()}), true));
    }

    private static JsonObject fightSide(String pick, double size) {
        JsonObject o = new JsonObject();
        if (pick.startsWith("id:")) o.addProperty("id", pick.substring(3));
        else {
            o.addProperty("kind", pick);
            o.addProperty("size", size);
        }
        return o;
    }

    /** the World tools page: the biome brush, building, mixing */
    private void worldRows() {
        JsonObject f = AdminBookClient.fun();
        if (f == null || AdminBookClient.live() == null) {
            rows.add(new Note(tr("asking"), GREY));
            return;
        }
        rows.add(new Note(tr("world.note"), GREY));
        List<JsonObject> kinds = objects(f, "kinds");
        // the brush
        Head bh = new Head(tr("world.brush"), tr("world.brush.note"), null).folding(WORLD + "/brush", "brush", -1);
        rows.add(bh);
        JsonObject br = f.get("brush") instanceof JsonObject b ? b : new JsonObject();
        String bg = str(br, "giant").isEmpty() ? "erase" : str(br, "giant");
        int radius = (int) dbl(br, "radius", 32);
        String bmode = str(br, "mode").isEmpty() ? "biome" : str(br, "mode");
        if (!bh.shut()) {
            List<String> choices = new ArrayList<>();
            choices.add("erase");
            for (JsonObject k : kinds) choices.add(str(k, "key"));
            Block b = new Block(22, (g, x, y, w, mx, my) -> g.drawString(font, tr("world.brush.what"), x + 2, y + 7,
                    KEY, false));
            int x0 = font.width(tr("world.brush.what")) + 8;
            int kw = Mth.clamp(lw / 4, 80, 120);
            Button which = Button.builder(bg.equals("erase") ? tr("world.brush.erase") : Component.literal(giantName(
                    bg)), x -> {
                        int i = Math.max(0, choices.indexOf(bg));
                        sendBrush(choices.get((i + 1) % choices.size()), radius, bmode);
                    })
                    .bounds(0, 0, kw, 16)
                    .build();
            which.setTooltip(Tooltip.create(tr("world.brush.what.tip")));
            b.add(which, x0, 3, false);
            int sw = Mth.clamp(lw - x0 - kw - 100, 60, 150);
            AbstractSliderButton rs = new AbstractSliderButton(0, 0, sw, 16, Component.empty(),
                    (radius - 16) / 240.0) {
                {
                    updateMessage();
                }

                int v() {
                    return (int) Math.round((16 + value * 240) / 4) * 4;
                }

                @Override
                protected void updateMessage() {
                    setMessage(tr("world.brush.radius", Mth.clamp(v(), 16, 256)));
                }

                @Override
                protected void applyValue() {
                }

                @Override
                public void onRelease(double mx, double my) {
                    super.onRelease(mx, my);
                    sendBrush(bg, Mth.clamp(v(), 16, 256), bmode);
                }
            };
            rs.setTooltip(Tooltip.create(tr("world.brush.radius.tip")));
            b.add(rs, x0 + kw + 4, 3, false);
            Button mode = Button.builder(tr(bmode.equals("full") ? "world.brush.full" : "world.brush.biome"), x -> {
                        if (bmode.equals("full")) sendBrush(bg, radius, "biome");
                        else confirm(tr("world.brush.full.title"), tr("world.brush.full.body"), tr("world.brush.full"),
                                () -> sendBrush(bg, radius, "full"));
                    })
                    .bounds(0, 0, 90, 16)
                    .build();
            mode.active = !bg.equals("erase");
            mode.setTooltip(Tooltip.create(tr("world.brush.mode.tip")));
            b.add(mode, 0, 3, true);
            rows.add(b);
            // (1.12.0) the brush's words on a line of their own, the buttons under them: at GUI scale 3 the three
            // buttons left too little room beside the words
            rows.add(new LineRow(tr("world.brush.now", bg.equals("erase") ? tr("world.brush.erase").getString()
                    : giantName(bg), radius), TEXT, tr(Boolean.TRUE.equals(bool(f, "erasing")) ? "world.brush.erasing"
                    : "world.brush.sub"), List.of(), true));
            rows.add(new LineRow(Component.empty(), TEXT, Component.empty(), List.<Object[]>of(
                    new Object[] {tr("world.brush.get"), (Runnable) () -> funRequest("brushget", new JsonObject()),
                        tr("world.brush.get.tip")},
                    new Object[] {tr("world.brush.paint"), (Runnable) () -> {
                        JsonObject o = new JsonObject();
                        o.addProperty("spot", "here");
                        funRequest("paintat", o);
                    }, tr("world.brush.paint.tip"), !bg.equals("erase")},
                    new Object[] {tr("world.brush.eraseat"), (Runnable) () -> {
                        JsonObject o = new JsonObject();
                        o.addProperty("spot", "here");
                        funRequest("eraseat", o);
                    }, tr("world.brush.eraseat.tip")})));
        }
        // building
        List<JsonObject> builds = objects(f, "builds");
        Head bu = new Head(tr("world.build"), tr("world.build.note"), null).folding(WORLD + "/build", "build",
                builds.size());
        rows.add(bu);
        if (!bu.shut()) {
            rows.add(new LineRow(tr("world.build.arena", tr("world.build.size." + arenaSize)), TEXT,
                    tr("world.build.arena.sub"), List.<Object[]>of(
                    new Object[] {tr("world.build.size." + arenaSize), (Runnable) () -> {
                        List<String> s = List.of("small", "medium", "large");
                        arenaSize = s.get((s.indexOf(arenaSize) + 1) % 3);
                        build();
                    }, tr("world.build.size.tip")},
                    new Object[] {tr("world.build.go"), (Runnable) () -> confirm(tr("world.build.arena.title"),
                            tr("world.build.arena.body"), tr("world.build.go"), () -> {
                                JsonObject o = new JsonObject();
                                o.addProperty("kind", "arena");
                                o.addProperty("style", arenaSize);
                                funRequest("build", o);
                            }), tr("world.build.go.tip")}), true));
            List<String> ks = new ArrayList<>();
            for (JsonObject k : kinds) ks.add(str(k, "key"));
            if (!ks.isEmpty()) {
                if (!ks.contains(denKind)) denKind = ks.get(0);
                Block b = new Block(22, (g, x, y, w, mx, my) -> g.drawString(font, tr("world.build.den"), x + 2, y + 7,
                        TEXT, false));
                int x0 = font.width(tr("world.build.den")) + 8, kw = Mth.clamp(lw / 4, 80, 120);
                Button kb = Button.builder(Component.literal(giantName(denKind)), x -> {
                            denKind = ks.get((ks.indexOf(denKind) + 1) % ks.size());
                            build();
                        })
                        .bounds(0, 0, kw, 16)
                        .build();
                kb.setTooltip(Tooltip.create(tr("world.build.den.kind.tip")));
                b.add(kb, x0, 3, false);
                AbstractSliderButton ds = new AbstractSliderButton(0, 0, Mth.clamp(lw - x0 - kw - 90, 60, 140), 16,
                        Component.empty(), (denSize - 0.05) / 1.95) {
                    {
                        updateMessage();
                    }

                    double v() {
                        return Mth.clamp(Math.round((0.05 + value * 1.95) * 20) / 20.0, 0.05, 2);
                    }

                    @Override
                    protected void updateMessage() {
                        setMessage(tr("live.summon.size", AdminSetting.plain(v())));
                    }

                    @Override
                    protected void applyValue() {
                        denSize = v();
                    }
                };
                ds.setTooltip(Tooltip.create(tr("world.build.den.size.tip")));
                b.add(ds, x0 + kw + 4, 3, false);
                Button go = Button.builder(tr("world.build.go"), x -> confirm(tr("world.build.den.title", giantName(
                        denKind)), tr("world.build.den.body"), tr("world.build.go"), () -> {
                            JsonObject o = new JsonObject();
                            o.addProperty("kind", "den");
                            o.addProperty("style", denKind);
                            o.addProperty("size", denSize);
                            funRequest("build", o);
                        }))
                        .bounds(0, 0, font.width(tr("world.build.go")) + 12, 16)
                        .build();
                go.setTooltip(Tooltip.create(tr("world.build.den.tip")));
                b.add(go, 0, 3, true);
                rows.add(b);
            }
            if (builds.isEmpty()) rows.add(new Note(tr("world.build.none"), GREY));
            for (JsonObject bj : builds) {
                int n = (int) dbl(bj, "n", 0);
                String st = str(bj, "state");
                String what = str(bj, "kind").equals("arena") ? tr("world.build.arena.named", tr("world.build.size."
                        + str(bj, "style"))).getString() : tr("world.build.den.named", giantName(str(bj, "giant")))
                        .getString();
                int done = (int) dbl(bj, "done", 0), total = Math.max(1, (int) dbl(bj, "total", 1));
                String sub = (int) dbl(bj, "x", 0) + " " + (int) dbl(bj, "y", 0) + " " + (int) dbl(bj, "z", 0) + " · "
                        + dimName(str(bj, "dim")) + " · " + tr("world.build.state." + st.replace(' ', '_'),
                                done * 100 / total).getString() + (bool(bj, "undo") ? "" : " · "
                                + tr("world.build.noundo").getString());
                List<Object[]> bs = new ArrayList<>();
                bs.add(new Object[] {tr("world.build.goto"), (Runnable) () -> request("buildgo", "n", n),
                    tr("world.build.goto.tip")});
                if (st.equals("building")) bs.add(new Object[] {tr("world.build.stop"), (Runnable) () -> funRequest(
                        "buildstop", new JsonObject()), tr("world.build.stop.tip")});
                else bs.add(new Object[] {tr("world.build.down"), (Runnable) () -> confirm(tr("world.build.down.title",
                        n), tr("world.build.down.body"), tr("world.build.down"), () -> request("takedown", "n", n)),
                    tr("world.build.down.tip"), bool(bj, "undo") && !st.equals("taking down")});
                rows.add(new LineRow(Component.literal(n + ". " + what), KEY, Component.literal(sub), bs, true));
            }
        }
        // mixing
        List<JsonObject[]> standing = funGiants(true);
        Head mh = new Head(tr("world.mix"), tr("world.mix.note"), null).folding(WORLD + "/mix", "mix", -1);
        rows.add(mh);
        if (mh.shut()) return;
        if (standing.isEmpty()) {
            rows.add(new Note(tr("live.empty"), GREY));
            return;
        }
        JsonObject[] who = standing.get(0);
        for (JsonObject[] g : standing) if (str(g[1], "id").equals(settleId)) who = g;
        settleId = str(who[1], "id");
        String kind = str(who[0], "key");
        List<String> places = funPlaces(kind);
        List<String> wheres = new ArrayList<>(List.of("here", "look", "sea", "cave", "sky"));
        for (JsonObject k : kinds) if (!str(k, "key").equals(kind)) wheres.add("ground:" + str(k, "key"));
        if (!wheres.contains(settleWhere)) settleWhere = "here";
        JsonObject[] cur = who;
        Block b = new Block(22, (g, x, y, w, mx, my) -> g.drawString(font, tr("world.mix.who"), x + 2, y + 7, KEY,
                false));
        int x0 = font.width(tr("world.mix.who")) + 8, kw = Mth.clamp(lw / 3, 90, 150);
        Button wb = Button.builder(Component.literal(cut(str(who[1], "label"), kw - 10)), x -> {
                    int i = 0;
                    for (int k = 0; k < standing.size(); k++) if (standing.get(k) == cur) i = k;
                    settleId = str(standing.get((i + 1) % standing.size())[1], "id");
                    build();
                })
                .bounds(0, 0, kw, 16)
                .build();
        wb.setTooltip(Tooltip.create(tr("world.mix.who.tip")));
        b.add(wb, x0, 3, false);
        String wl = whereWords(settleWhere);
        Button whereB = Button.builder(Component.literal(cut(wl, 150)), x -> {
                    settleWhere = wheres.get((wheres.indexOf(settleWhere) + 1) % wheres.size());
                    build();
                })
                .bounds(0, 0, Math.min(160, font.width(wl) + 14), 16)
                .build();
        whereB.setTooltip(Tooltip.create(tr("world.mix.where.tip")));
        b.add(whereB, x0 + kw + 4, 3, false);
        rows.add(b);
        String need = switch (settleWhere) {
            case "sea" -> "sea";
            case "cave" -> "cave";
            case "sky" -> "sky";
            default -> "";
        };
        boolean can = need.isEmpty() || places.contains(need);
        rows.add(new LineRow(tr("world.mix.places", str(who[0], "name"), places.isEmpty() ? "-" : String.join(", ",
                places.stream().map(p -> tr("world.mix.place." + p).getString()).toList())), can ? TEXT : WARN,
                can ? tr("world.mix.sub") : tr("world.mix.cant", str(who[0], "name"), whereWords(settleWhere)),
                List.<Object[]>of(new Object[] {tr("world.mix.go"), (Runnable) () -> {
                    JsonObject o = giantJson(kind, settleId);
                    String w = settleWhere.startsWith("ground:") ? "ground" : settleWhere;
                    o.addProperty("where", w);
                    if (w.equals("ground")) o.addProperty("ground", settleWhere.substring(7));
                    funRequest("settle", o);
                }, tr("world.mix.go.tip"), can}), true));
    }

    private String whereWords(String w) {
        return w.startsWith("ground:") ? tr("world.mix.where.ground", giantName(w.substring(7))).getString()
                : tr("world.mix.where." + w).getString();
    }

    private void sendBrush(String giant, int radius, String mode) {
        JsonObject o = new JsonObject();
        o.addProperty("giant", giant);
        o.addProperty("radius", radius);
        o.addProperty("mode", mode);
        funRequest("brush", o);
    }

    // the questions

    /** name, title, colour and whether it shows over his head */
    private void openNameAsk(String kind, String id, String label) {
        JsonObject m = funMark(id);
        Form fm = new Form(tr("fun.name.title", label), tr("fun.name.body"));
        EditBox name = fm.box(tr("fun.name.name"), m == null ? "" : str(m, "name"), 32);
        EditBox title = fm.box(tr("fun.name.titleField"), m == null ? "" : str(m, "title"), 32);
        String[] colour = {m == null || str(m, "colour").isEmpty() ? "white" : str(m, "colour")};
        boolean[] shown = {m != null && bool(m, "shown")};
        fm.cycle(tr("fun.name.colour"), () -> tr("fun.colour." + colour[0]), () -> {
            List<String> cs = List.of("white", "gold", "red", "purple", "aqua", "green", "yellow");
            colour[0] = cs.get((cs.indexOf(colour[0]) + 1) % cs.size());
        });
        fm.cycle(tr("fun.name.shown"), () -> tr(shown[0] ? "fun.name.shown.on" : "fun.name.shown.off"),
                () -> shown[0] = !shown[0]);
        fm.button(tr("fun.name.save"), () -> {
            closeModal();
            JsonObject o = giantJson(kind, id);
            o.addProperty("name", name.getValue());
            o.addProperty("title", title.getValue());
            o.addProperty("colour", colour[0]);
            o.addProperty("shown", String.valueOf(shown[0]));
            funRequest("name", o);
        });
        fm.button(tr("fun.name.clear"), () -> {
            closeModal();
            JsonObject o = giantJson(kind, id);
            o.addProperty("name", "");
            o.addProperty("title", "");
            o.addProperty("colour", "white");
            o.addProperty("shown", "false");
            funRequest("name", o);
        });
        fm.button(tr("cancel"), fm::cancel);
        fm.done();
        modal = fm;
        place();
    }

    /** a tag's form: new (t null) or changing one */
    private void openTagEdit(@Nullable JsonObject t) {
        Form fm = new Form(t == null ? tr("fun.tagedit.new") : tr("fun.tagedit.title", str(t, "name")),
                tr("fun.tagedit.body"));
        EditBox name = fm.box(tr("fun.tagedit.name"), t == null ? "" : str(t, "name"), 24);
        double[] health = {t == null ? 1 : dbl(t, "health", 1)}, damage = {t == null ? 1 : dbl(t, "damage", 1)},
                speed = {t == null ? 1 : dbl(t, "speed", 1)}, size = {t == null ? 0 : dbl(t, "size", 0)};
        fm.slider(tr("fun.tagedit.health"), 0.1, 10, 0.1, health, false);
        fm.slider(tr("fun.tagedit.damage"), 0.1, 10, 0.1, damage, false);
        fm.slider(tr("fun.tagedit.speed"), 0.25, 4, 0.05, speed, false);
        fm.slider(tr("fun.tagedit.size"), 0, 2, 0.05, size, true);
        EditBox title = fm.box(tr("fun.tagedit.titleField"), t == null ? "" : str(t, "title"), 32);
        String[] colour = {t == null || str(t, "colour").isEmpty() ? "white" : str(t, "colour")};
        boolean[] glow = {t != null && bool(t, "glow")};
        fm.cycle(tr("fun.name.colour"), () -> tr("fun.colour." + colour[0]), () -> {
            List<String> cs = List.of("white", "gold", "red", "purple", "aqua", "green", "yellow");
            colour[0] = cs.get((cs.indexOf(colour[0]) + 1) % cs.size());
        });
        fm.cycle(tr("fun.tagedit.glow"), () -> tr(glow[0] ? "fun.name.shown.on" : "fun.name.shown.off"),
                () -> glow[0] = !glow[0]);
        boolean hasLoot = t != null && t.get("loot") instanceof JsonObject;
        List<String> lootChoices = hasLoot ? List.of("keep", "none", "add", "replace") : List.of("none", "add",
                "replace");
        String[] loot = {lootChoices.get(0)};
        fm.cycle(tr("fun.tagedit.loot"), () -> tr("fun.tagedit.loot." + loot[0], hasLoot ? str(t, "lootWords") : ""),
                () -> loot[0] = lootChoices.get((lootChoices.indexOf(loot[0]) + 1) % lootChoices.size()));
        fm.button(tr("fun.tagedit.save"), () -> {
            closeModal();
            JsonObject tag = new JsonObject();
            tag.addProperty("name", name.getValue().strip());
            tag.addProperty("health", health[0]);
            tag.addProperty("damage", damage[0]);
            tag.addProperty("speed", speed[0]);
            tag.addProperty("size", size[0]);
            tag.addProperty("title", title.getValue());
            tag.addProperty("colour", colour[0]);
            tag.addProperty("glow", glow[0]);
            JsonObject o = new JsonObject();
            o.add("tag", tag);
            o.addProperty("old", t == null ? "" : str(t, "name"));
            o.addProperty("lootFrom", loot[0].equals("add") || loot[0].equals("replace") ? "chest" : loot[0]);
            o.addProperty("lootMode", loot[0].equals("replace") ? "replace" : "add");
            funRequest("tagsave", o);
        });
        fm.button(tr("cancel"), fm::cancel);
        fm.done();
        modal = fm;
        place();
    }

    /** a few choices as buttons (wrapping onto more lines), and Cancel */
    private final class Choices extends Modal {
        final Component title, body;

        Choices(Component title, Component body, List<Object[]> choices) {
            this.title = title;
            this.body = body;
            for (Object[] c : choices) button((Component) c[0], () -> {
                closeModal();
                ((Runnable) c[1]).run();
            });
            button(tr("cancel"), this::cancel);
            layout(buttonsW());
        }

        @Override
        void layout(int buttonsW) {
            super.layout(buttonsW);
            int x = mx0 + 8, rowsN = 1;
            for (Button b : buttons) {
                if (x > mx0 + 8 && x + b.getWidth() > mx0 + mw - 8) {
                    x = mx0 + 8;
                    rowsN++;
                }
                x += b.getWidth() + 4;
            }
            mh += (rowsN - 1) * 20;
            my0 = (height - mh) / 2;
            x = mx0 + 8;
            int y = my0 + mh - 24 - (rowsN - 1) * 20;
            for (Button b : buttons) {
                if (x > mx0 + 8 && x + b.getWidth() > mx0 + mw - 8) {
                    x = mx0 + 8;
                    y += 20;
                }
                b.setX(x);
                b.setY(y);
                x += b.getWidth() + 4;
            }
        }

        @Override
        int modalW() {
            return Math.min(pw - 40, 340);
        }

        @Override
        Component title() {
            return title;
        }

        @Override
        List<FormattedCharSequence> body() {
            return font.split(body, modalW() - 16);
        }

        @Override
        void cancel() {
            closeModal();
        }
    }

    /** a question with fields: words to type, sliders and choices, each on its own line with its name */
    private final class Form extends Modal {
        final Component title, note;
        final List<Component> labels = new ArrayList<>();
        final List<AbstractWidget> fields = new ArrayList<>();
        final List<Runnable> refresh = new ArrayList<>();
        @Nullable EditBox typing;
        @Nullable AbstractSliderButton sliding;
        int labelW;

        Form(Component title, Component note) {
            this.title = title;
            this.note = note;
        }

        EditBox box(Component label, String value, int most) {
            EditBox b = new EditBox(font, 0, 0, 100, 16, label);
            b.setMaxLength(most);
            b.setValue(value);
            field(label, b);
            if (typing == null) {
                typing = b;
                b.setFocused(true);
            }
            return b;
        }

        void cycle(Component label, java.util.function.Supplier<Component> now, Runnable next) {
            Button[] b = new Button[1];
            b[0] = Button.builder(now.get(), x -> {
                next.run();
                x.setMessage(now.get());
            }).bounds(0, 0, 100, 16).build();
            field(label, b[0]);
        }

        void slider(Component label, double lo, double hi, double step, double[] v, boolean zeroLeaves) {
            AbstractSliderButton s = new AbstractSliderButton(0, 0, 100, 16, Component.empty(),
                    (Mth.clamp(v[0], lo, hi) - lo) / (hi - lo)) {
                {
                    updateMessage();
                }

                double cur() {
                    return Mth.clamp(Math.round((lo + value * (hi - lo)) / step) * step, lo, hi);
                }

                @Override
                protected void updateMessage() {
                    double c = cur();
                    setMessage(zeroLeaves && c <= 0 ? tr("fun.tagedit.size.leave") : Component.literal(
                            (zeroLeaves ? "" : "x") + AdminSetting.plain(Math.round(c * 100) / 100.0)));
                }

                @Override
                protected void applyValue() {
                    v[0] = Math.round(cur() * 100) / 100.0;
                }
            };
            field(label, s);
        }

        private void field(Component label, AbstractWidget w) {
            labels.add(label);
            fields.add(w);
        }

        void done() {
            labelW = 0;
            for (Component c : labels) labelW = Math.max(labelW, font.width(c) + 8);
            layout(buttonsW());
        }

        @Override
        int modalW() {
            return Math.min(pw - 40, 360);
        }

        @Override
        void layout(int buttonsW) {
            super.layout(buttonsW);
            mh += fields.size() * 20 + 4;
            my0 = (height - mh) / 2;
            int y = my0 + 26 + Math.min(body().size(), bodyRoom()) * 10 + 4;
            for (AbstractWidget w : fields) {
                w.setX(mx0 + 8 + labelW);
                w.setY(y);
                w.setWidth(mw - 16 - labelW);
                y += 20;
            }
            int x = mx0 + mw - 8 - buttonsW;
            for (Button b : buttons) {
                b.setX(x);
                b.setY(my0 + mh - 24);
                x += b.getWidth() + 4;
            }
        }

        @Override
        void extra(GuiGraphics g) {
            for (int i = 0; i < fields.size(); i++) {
                AbstractWidget w = fields.get(i);
                g.drawString(font, labels.get(i), mx0 + 8, w.getY() + 4, TEXT, false);
                w.render(g, lastMx, lastMy, 0);
            }
        }

        @Override
        boolean mouseClicked(double mx, double my, int b) {
            for (AbstractWidget w : fields)
                if (w.isMouseOver(mx, my)) {
                    if (typing != null) typing.setFocused(false);
                    typing = w instanceof EditBox e ? e : typing;
                    if (typing != null) typing.setFocused(w == typing);
                    if (w instanceof AbstractSliderButton s) sliding = s;
                    w.mouseClicked(mx, my, b);
                    return true;
                }
            return super.mouseClicked(mx, my, b);
        }

        @Override
        boolean dragged(double mx, double my, int b, double dx, double dy) {
            return sliding != null && sliding.mouseDragged(mx, my, b, dx, dy);
        }

        @Override
        void released(double mx, double my) {
            if (sliding != null) sliding.onRelease(mx, my);
            sliding = null;
        }

        @Override
        boolean keyPressed(int key) {
            if (key == 258) {
                // Tab: the next box
                List<EditBox> boxes = fields.stream().filter(w -> w instanceof EditBox).map(w -> (EditBox) w).toList();
                if (!boxes.isEmpty()) {
                    int i = typing == null ? -1 : boxes.indexOf(typing);
                    if (typing != null) typing.setFocused(false);
                    typing = boxes.get((i + 1) % boxes.size());
                    typing.setFocused(true);
                }
                return true;
            }
            if (key == 257 || key == 335) {
                buttons.get(0).onPress();
                return true;
            }
            if (typing != null) typing.keyPressed(key, 0, 0);
            return true;
        }

        @Override
        boolean charTyped(char c, int mods) {
            if (typing != null) typing.charTyped(c, mods);
            return true;
        }

        @Override
        Component title() {
            return title;
        }

        @Override
        List<FormattedCharSequence> body() {
            return font.split(note, modalW() - 16);
        }

        @Override
        void cancel() {
            closeModal();
        }
    }

    // (Part 8, ADMIN_BRIDGE.md 24.8) the Systems page: what the new systems are doing now, each system's settings in
    // its own folding group, and each giant they follow

    public static final String SYSTEMS = "systems";

    private int systemsSet() {
        AdminBookClient.Page p = AdminBookClient.page(AdminCatalog.ALL);
        if (p == null) return 0;
        int n = 0;
        for (AdminSetting s : p.settings()) if (s.set() && AdminCatalog.systems(s)) n++;
        return n;
    }

    private void systemsRows() {
        AdminBookClient.Page p = AdminBookClient.page(AdminCatalog.ALL);
        if (p == null) {
            rows.add(new Note(tr("asking"), GREY));
            return;
        }
        rows.add(new Note(tr("systems.note"), GREY));
        JsonObject sy = creating() ? null : AdminBookClient.systems();
        if (sy != null && sy.get("now") instanceof JsonObject now) systemsNow(now);
        for (String[] grp : net.jj.giants.systems.SystemsCatalog.GROUPS) {
            // (Part 9) new systems B under their own heading
            if (grp[0].equals(net.jj.giants.systems.SystemsCatalog.FIRST_B)) systemsB();
            List<AdminSetting> in = new ArrayList<>();
            for (int i = 1; i < grp.length; i++)
                for (AdminSetting s : p.settings()) if (s.id().equals(grp[i])) in.add(s);
            boolean on = false;
            for (AdminSetting s : in) if (!AdminSetting.same(s.value(), s.jar())) on = true;
            MutableComponent title = tr("systems." + grp[0]);
            if (on) title.append(Component.literal("  • ").append(tr("systems.on")).withStyle(
                    st -> st.withColor(GOOD)));
            Head head = new Head(title, tr("systems." + grp[0] + ".note"), null)
                    .folding(SYSTEMS + "/" + grp[0], grp[0], in.size());
            rows.add(head);
            if (head.shut()) continue;
            for (AdminSetting s : in) rows.add(new SetRow(s));
            // (the weak points: where each giant's are)
            if (grp[0].equals("weak") && sy != null && sy.get("weak") instanceof JsonObject weak)
                for (var e : weak.entrySet())
                    if (e.getValue() instanceof JsonArray a && !a.isEmpty()) {
                        List<String> w = new ArrayList<>();
                        for (JsonElement x : a) w.add(x.getAsString());
                        rows.add(new LineRow(Component.literal(giantName(e.getKey())), TEXT,
                                Component.literal(String.join(", ", w)), List.of(), true));
                    }
        }
        if (sy == null) return;
        List<JsonObject> gs = objects(sy, "giants");
        Head each = new Head(tr("systems.each"), tr("systems.each.note"), null)
                .folding(SYSTEMS + "/each", "general", gs.size());
        rows.add(each);
        if (each.shut()) return;
        if (gs.isEmpty()) rows.add(new Note(tr("systems.each.none"), GREY));
        for (JsonObject j : gs) rows.add(systemsGiant(j));
    }

    /** the "Right now" card: the season, the moon, the scaling, what waits to grow back or rot */
    private void systemsNow(JsonObject n) {
        List<Component> lines = new ArrayList<>();
        String season = str(n, "season");
        if (!season.isEmpty())
            lines.add(tr("systems.now.season", tr("systems.season." + season), (int) dbl(n, "seasonDay", 1),
                    (int) dbl(n, "seasonDays", 7)));
        lines.add(bool(n, "fullMoon") ? tr("systems.now.fullmoon")
                : tr("systems.now.moon", tr("systems.moon." + (int) dbl(n, "moonPhase", 0))));
        double scale = dbl(n, "scale", 1);
        if (scale != 1)
            lines.add(tr("systems.now.scale", AdminSetting.plain(scale), (int) dbl(n, "players", 0),
                    (int) dbl(n, "days", 0), Math.round(dbl(n, "armour", 0) * 100)));
        lines.add(tr("systems.now.sent", AdminSetting.plain(dbl(n, "health", 1)),
                AdminSetting.plain(dbl(n, "damage", 1)), AdminSetting.plain(dbl(n, "speed", 1)),
                AdminSetting.plain(dbl(n, "healing", 1))));
        int grief = (int) dbl(n, "grief", 0), rot = (int) dbl(n, "rotting", 0);
        if (grief > 0) lines.add(tr("systems.now.grief", grief));
        if (rot > 0) lines.add(tr("systems.now.rot", rot));
        systemsNowB(n, lines);
        List<FormattedCharSequence> split = new ArrayList<>();
        for (Component c : lines) split.addAll(font.split(c, lw - 34));
        int h = 22 + split.size() * 10;
        rows.add(new Block(h + 6, (g, x, y, w, mx, my) -> {
            AdminStyle.card(g, x, y + 2, w, h, false);
            AdminStyle.icon(g, "systems", x + 5, y + 6, 16);
            g.drawString(font, tr("systems.now"), x + 26, y + 10, HEAD, false);
            for (int i = 0; i < split.size(); i++)
                g.drawString(font, split.get(i), x + 26, y + 22 + i * 10, TEXT, false);
        }));
    }

    /** one giant the systems follow: his picture, his size against where he started, his food and loyalty bars */
    private Row systemsGiant(JsonObject j) {
        String kind = str(j, "kind");
        double size = dbl(j, "size", 0), start = dbl(j, "start", 0), food = dbl(j, "food", 100),
                loyal = dbl(j, "loyalty", -1);
        boolean here = bool(j, "here"), titan = bool(j, "titan");
        String name = str(j, "name");
        Component line = here && start > 0
                ? tr("systems.each.size", AdminSetting.plain(size), AdminSetting.plain(start),
                        AdminSetting.plain(Math.round(size / start * 100) / 100.0),
                        AdminSetting.plain(dbl(j, "days", 0)), (int) dbl(j, "wins", 0))
                : tr("systems.each.away", AdminSetting.plain(dbl(j, "days", 0)), (int) dbl(j, "wins", 0));
        int belly = (int) dbl(j, "belly", 0);
        String owner = str(j, "owner");
        int rh = loyal >= 0 ? 54 : 44;
        return new Block(rh, (g, x, y, w, mx, my) -> {
            AdminStyle.card(g, x, y + 2, w, rh - 4, false);
            icon(g, kind, x + 6, y + 8, 26);
            int tx = x + 40, tw = w - 46;
            int nx = g.drawString(font, cut(name, tw), tx, y + 7, TEXT, false);
            if (titan) g.drawString(font, tr("systems.titan"), nx + 6, y + 7, WARN, false);
            String l2 = line.getString() + (belly > 0 ? "  " + tr("systems.each.belly", belly).getString() : "");
            g.drawString(font, cut(l2, tw), tx, y + 18, GREY, false);
            int bw = Math.max(40, Math.min(110, tw / 2 - 40));
            bar(g, tx, y + 30, bw, tr("systems.each.food").getString(), food / 100.0, 0xFFB86A20);
            if (loyal >= 0) {
                String who = owner.isEmpty() ? "" : " (" + owner + ")";
                bar(g, tx, y + 41, bw, tr("systems.each.loyalty").getString() + who, loyal / 100.0,
                        bool(j, "turned") ? WARN : bool(j, "sulking") ? ORANGE : 0xFFB83040);
            }
        });
    }

    /** a labelled bar on the parchment */
    private void bar(GuiGraphics g, int x, int y, int w, String label, double f, int colour) {
        int lwid = Math.max(54, font.width(label) + 6);
        g.drawString(font, label, x, y, DIM, false);
        int bx = x + lwid;
        g.fill(bx, y + 1, bx + w, y + 8, 0xFF8A6A40);
        g.fill(bx + 1, y + 2, bx + w - 1, y + 7, 0xFFEADBB8);
        int fill = (int) Math.round((w - 2) * Math.max(0, Math.min(1, f)));
        if (fill > 0) g.fill(bx + 1, y + 2, bx + 1 + fill, y + 7, colour);
        g.drawString(font, Math.round(f * 100) + "%", bx + w + 4, y, GREY, false);
    }

    // (Part 9, ADMIN_BRIDGE.md 25.11) new systems B on the Systems page

    /** the heading the new systems B start under */
    private void systemsB() {
        int h = 30;
        rows.add(new Block(h + 4, (g, x, y, w, mx, my) -> {
            g.fill(x + 2, y + 3, x + w - 2, y + 4, 0x55804020);
            AdminStyle.icon(g, "systems_b", x + 4, y + 8, 16);
            g.drawString(font, tr("systems.b"), x + 25, y + 8, HEAD, false);
            g.drawString(font, cut(tr("systems.b.note").getString(), w - 30), x + 25, y + 19, GREY, false);
        }));
    }

    /** the "Right now" lines for new systems B */
    private void systemsNowB(JsonObject n, List<Component> lines) {
        int weather = (int) dbl(n, "weatherPlayers", -1);
        if (weather > 0) lines.add(tr("systems.now.weather", weather));
        int followers = (int) dbl(n, "followers", 0);
        if (followers > 0) lines.add(tr("systems.now.followers", followers));
        if (n.get("underworld") instanceof JsonArray u && !u.isEmpty()) {
            // (grouped: who is there, who comes back when, who fell for good, who waits for a player)
            List<String> there = new ArrayList<>(), back = new ArrayList<>(), never = new ArrayList<>(),
                    waits = new ArrayList<>();
            for (JsonElement e : u) {
                if (!(e instanceof JsonObject j)) continue;
                String who = giantName(str(j, "kind")) + (str(j, "dim").endsWith("end") ? " (End)" : " (Nether)");
                switch (str(j, "state")) {
                    case "standing" -> there.add(who);
                    case "never" -> never.add(who);
                    case "waits" -> back.add(who + " in " + (int) dbl(j, "minutes", 0) + " min");
                    default -> waits.add(who);
                }
            }
            List<String> parts = new ArrayList<>();
            if (!there.isEmpty()) parts.add("there: " + String.join(", ", there));
            if (!back.isEmpty()) parts.add("back: " + String.join(", ", back));
            if (!never.isEmpty()) parts.add("fell for good: " + String.join(", ", never));
            if (!waits.isEmpty()) parts.add(waits.size() == u.size() ? "each kind comes when a player is there"
                    : "come when a player is there: " + String.join(", ", waits));
            lines.add(tr("systems.now.under", String.join("; ", parts)));
        }
        if (n.get("sacred") instanceof JsonArray sa) {
            List<String> names = new ArrayList<>();
            for (JsonElement e : sa) names.add(giantName(e.getAsString()));
            lines.add(names.isEmpty() ? tr("systems.now.sacred.none")
                    : tr("systems.now.sacred", String.join(", ", names)));
        }
        if (n.get("mirror") instanceof JsonObject m) {
            List<String> parts = new ArrayList<>();
            String[] tactics = {"melee", "arrows", "magic", "blasts"};
            for (var e : m.entrySet()) {
                if (!(e.getValue() instanceof JsonObject k)) continue;
                for (String t : tactics) {
                    double f = dbl(k, t, 1);
                    if (f < 1) parts.add(giantName(e.getKey()) + ": " + t + " x" + AdminSetting.plain(f));
                }
            }
            lines.add(parts.isEmpty() ? tr("systems.now.mirror.none")
                    : tr("systems.now.mirror", String.join(", ", parts)));
        }
        if (n.get("chaos") instanceof JsonObject c && c.has("day") && dbl(c, "day", -1) >= 0) {
            lines.add(tr("systems.now.chaos", (long) dbl(c, "day", 0), (long) dbl(c, "next", 0)));
            if (c.get("rests") instanceof JsonObject r && !r.entrySet().isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (var e : r.entrySet())
                    if (e.getValue() instanceof JsonArray a) parts.add(giantName(e.getKey()) + " " + a.size());
                lines.add(tr("systems.now.chaos.rest", String.join(", ", parts)));
            }
        }
    }
}
