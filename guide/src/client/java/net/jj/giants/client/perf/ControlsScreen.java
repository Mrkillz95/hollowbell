package net.jj.giants.client.perf;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.jj.giants.Giants;
import net.jj.giants.controls.ControlKey;
import net.jj.giants.controls.ControlsCatalog;
import net.jj.giants.controls.ControlsJson;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Giants Performance's Controls tab: every key of every installed giant (and the guide's own), grouped by giant,
 * each with what it does in plain words, its key, a button back to its default, and any gesture of his that goes
 * with it (crouch three times to sit, and so on) as an on/off toggle. A click on a key and then a key press sets
 * it, exactly as Minecraft's own Controls screen does, so both always agree. Keys that clash are shown red, the
 * way Minecraft shows them; the same job on two giants (every armour power on R) is meant to share and isn't.
 * Only the player's own game: nothing here goes to the server.
 */
public class ControlsScreen extends Screen {
    private static final int GOLD = 0xFFE0B050,
            TITLE = 0xFFE9C9BC,
            TEXT = 0xFFD8D8D8,
            GREY = 0xFF9A9A9A,
            DIM = 0xFF6E6E6E,
            WARN = 0xFFFF7060,
            CLASH = 0xFFFF5555,
            GOOD = 0xFF80E080,
            PANEL = 0xE8141210,
            EDGE = 0xFF6B5A44,
            LINE = 0x30FFFFFF;
    private static final int HEADER = 0, KEY = 1, GESTURE = 2, NOTE = 3;
    private static final int KEY_H = 26, HEADER_H = 22, RESET_W = 20;

    private static double keptScroll;
    private static String keptQuery = "";

    private final @Nullable Screen back;
    private final List<Row> rows = new ArrayList<>();
    private final List<Row> shown = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();
    private final List<Button> nav = new ArrayList<>();
    private List<ControlKey> all = List.of();
    private final Map<String, ControlsCatalog.Clash> clashes = new HashMap<>();
    private Map<String, String> extra = Map.of();
    private double scroll;
    private int contentH;
    private boolean dragging, confirmReset;
    private @Nullable KeyMapping selected;
    private Component status = Component.empty();
    private int statusColor = GREY;
    private long statusAt;
    private EditBox search;
    private Button resetAll;
    private int tabsEnd;

    private int px0, py0, pw, ph, navX, navW, lx, lw, ly0, ly1;

    /** open next time scrolled this far, with this search (the picture-taking uses this) */
    public static void showAt(double scroll, String query) {
        keptScroll = scroll;
        keptQuery = query == null ? "" : query;
    }

    public ControlsScreen(@Nullable Screen back) {
        super(Component.translatable("perf.jj_giants.title"));
        this.back = back;
    }

    /** one line of the list: a giant's heading, one of his keys, a gesture of his with no key, or a note */
    private final class Row {
        final int type;
        final String group;
        final @Nullable KeyMapping km;
        final @Nullable ControlsJson.Gesture gesture;
        final Component name, what;
        @Nullable Button keyButton, resetButton, gestureButton;
        int h, y;
        String find = "";

        Row(int type, String group, @Nullable KeyMapping km, @Nullable ControlsJson.Gesture gesture, Component name,
                Component what) {
            this.type = type;
            this.group = group;
            this.km = km;
            this.gesture = gesture;
            this.name = name;
            this.what = what;
        }

        List<AbstractWidget> widgets() {
            List<AbstractWidget> out = new ArrayList<>();
            if (resetButton != null) out.add(resetButton);
            if (keyButton != null) out.add(keyButton);
            if (gestureButton != null) out.add(gestureButton);
            return out;
        }

        int widgetsW() {
            int w = 0;
            for (AbstractWidget x : widgets()) w += x.getWidth() + 2;
            return w;
        }
    }

    // words

    private static boolean has(String key) {
        return Language.getInstance().has(key);
    }

    /** a language key, or plain words */
    private static Component words(String keyOrText, Object... args) {
        if (keyOrText == null || keyOrText.isEmpty()) return Component.empty();
        return has(keyOrText) ? Component.translatable(keyOrText, args) : Component.literal(keyOrText);
    }

    static String ownerName(String owner) {
        if (owner.equals(ControlsCatalog.GUIDE)) return Component.translatable("controls.jj_giants.guide").getString();
        String k = "guide.jj_giants." + owner + ".name";
        return has(k) ? Language.getInstance().getOrDefault(k) : owner;
    }

    /**
     * What a key does: his giants_controls.json first, then the guide's own words for that key, for its category,
     * then for its job ("armour_power") with his name in; else nothing (its name says it).
     */
    private Component what(String owner, KeyMapping km) {
        ControlsJson.Controls c = ControlsClient.files().get(owner);
        String mine = c != null ? c.keys().get(km.getName()) : null;
        if (mine != null) return words(mine);
        String k = "controls.jj_giants.what." + km.getName();
        if (has(k)) return Component.translatable(k);
        k = "controls.jj_giants.what." + km.getCategory();
        if (has(k)) return Component.translatable(k);
        k = "controls.jj_giants.role." + ControlsCatalog.role(km.getName());
        if (has(k)) return Component.translatable(k, inLine(ownerName(owner)));
        return Component.empty();
    }

    /** a giant's name mid-sentence: "The Hollowbell" becomes "the Hollowbell" */
    private static String inLine(String name) {
        return name.startsWith("The ") ? "the " + name.substring(4) : name;
    }

    /** the two tabs along the top, for this screen and the settings one */
    static List<Button> tabs(Screen screen, @Nullable Screen back, boolean controls, int x, int y) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        Component s = Component.translatable("controls.jj_giants.tab.settings");
        Component c = Component.translatable("controls.jj_giants.tab.controls");
        Button bs = Button.builder(s, b -> {
                    if (controls) net.minecraft.client.Minecraft.getInstance().setScreen(new PerfScreen(back));
                })
                .bounds(x, y, font.width(s) + 12, 14)
                .build();
        Button bc = Button.builder(c, b -> {
                    if (!controls) net.minecraft.client.Minecraft.getInstance().setScreen(new ControlsScreen(back));
                })
                .bounds(x + bs.getWidth() + 2, y, font.width(c) + 12, 14)
                .build();
        bs.setTooltip(Tooltip.create(Component.translatable("controls.jj_giants.tab.settings.tip")));
        bc.setTooltip(Tooltip.create(Component.translatable("controls.jj_giants.tab.controls.tip")));
        // the open tab: gold and underlined (clicking it does nothing)
        Button on = controls ? bc : bs;
        on.setMessage(on.getMessage().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE));
        return List.of(bs, bc);
    }

    /** where the title goes: the middle, unless the tabs reach it */
    static int titleX(int px0, int pw, int tabsEnd, int titleW) {
        return Math.max(px0 + pw / 2, tabsEnd + 6 + titleW / 2);
    }

    // layout

    @Override
    protected void init() {
        pw = Math.min(width - 8, 620);
        ph = Math.min(height - 8, 420);
        px0 = (width - pw) / 2;
        py0 = (height - ph) / 2;
        navX = px0 + 6;
        navW = Mth.clamp(pw / 6, 64, 96);
        lx = navX + navW + 8;
        lw = px0 + pw - 6 - 6 - lx;
        ly0 = py0 + 66;
        ly1 = py0 + ph - 16;

        tabsEnd = px0 + 6;
        for (Button b : tabs(this, back, true, px0 + 6, py0 + 2)) {
            addRenderableWidget(b);
            tabsEnd = b.getX() + b.getWidth();
        }
        Button close = Button.builder(Component.literal("✕"), b -> onClose())
                .bounds(px0 + pw - 19, py0 + 2, 16, 14)
                .build();
        close.setTooltip(Tooltip.create(Component.translatable("guide.jj_giants.close")));
        addRenderableWidget(close);

        search = new EditBox(font, px0 + 6, py0 + 20, Math.min(200, pw / 3), 16, Component.translatable("controls.jj_giants.search"));
        search.setHint(Component.translatable("controls.jj_giants.search"));
        search.setValue(keptQuery);
        search.setResponder(q -> {
            keptQuery = q;
            scroll = 0;
            filter();
        });
        addRenderableWidget(search);
        Component ra = Component.translatable("controls.jj_giants.reset_all.sure");
        int raW = Math.min(font.width(ra) + 14, pw - search.getWidth() - 20);
        resetAll = addRenderableWidget(Button.builder(Component.translatable("controls.jj_giants.reset_all"), b -> resetAll())
                .bounds(px0 + pw - 6 - raW, py0 + 20, raW, 16)
                .build());
        resetAll.setTooltip(Tooltip.create(Component.translatable("controls.jj_giants.reset_all.tip")));

        double wasAt = keptScroll;
        ControlsClient.readGestures();
        buildRows();
        refresh();
        scroll = wasAt;
        filter();
    }

    private void buildRows() {
        rows.clear();
        nav.clear();
        groups.clear();
        extra = ControlsClient.extraCategories();
        Map<String, List<ControlKey>> grouped = ControlsCatalog.group(ControlsClient.keys(), Giants.installed(), extra);
        Map<String, ControlsJson.Controls> files = ControlsClient.files();
        List<String> order = new ArrayList<>();
        order.add(ControlsCatalog.GUIDE);
        for (var g : Giants.installed()) order.add(g.key());
        for (String o : order) {
            List<ControlKey> keys = grouped.getOrDefault(o, List.of());
            ControlsJson.Controls file = files.get(o);
            List<ControlsJson.Gesture> loose = new ArrayList<>();
            if (file != null)
                for (ControlsJson.Gesture x : file.gestures())
                    if (x.key().isEmpty() || keys.stream().noneMatch(k -> k.name().equals(x.key()))) loose.add(x);
            if (keys.isEmpty() && loose.isEmpty()) continue;
            groups.add(o);
            Row head = new Row(HEADER, o, null, null, Component.literal(ownerName(o)), Component.empty());
            head.h = HEADER_H;
            head.find = ownerName(o).toLowerCase(Locale.ROOT);
            rows.add(head);
            for (ControlKey k : keys) {
                KeyMapping km = ControlsClient.mapping(k.name());
                if (km == null) continue;
                rows.add(keyRow(o, km, file != null ? file.gestureFor(k.name()) : null));
            }
            for (ControlsJson.Gesture x : loose) rows.add(gestureRow(o, x));
        }
        if (groups.isEmpty()) note(Component.translatable("controls.jj_giants.none"));
        else note(Component.translatable("controls.jj_giants.fixed"));
        for (String gr : groups) nav.add(addRenderableWidget(
                Button.builder(Component.literal(ownerName(gr)), x -> jump(gr)).bounds(navX, 0, navW, 16).build()));
    }

    private void note(Component text) {
        Row r = new Row(NOTE, "", null, null, text, Component.empty());
        r.h = Math.max(1, font.split(text, lw).size()) * 10 + 6;
        rows.add(r);
    }

    private Row keyRow(String owner, KeyMapping km, @Nullable ControlsJson.Gesture x) {
        Row r = new Row(KEY, owner, km, x, Component.translatable(km.getName()), what(owner, km));
        r.h = KEY_H;
        Component resetWord = Component.translatable("controls.jj_giants.reset");
        r.resetButton = addRenderableWidget(Button.builder(resetWord, b -> {
                    selected = null;
                    ControlsClient.bind(km, km.getDefaultKey());
                    say(Component.translatable("controls.jj_giants.set", r.name, km.getTranslatedKeyMessage()), GOOD);
                    refresh();
                })
                .bounds(0, 0, Math.max(RESET_W, font.width(resetWord) + 10), 16)
                .build());
        r.keyButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    selected = km;
                    say(Component.translatable("controls.jj_giants.press", r.name), GOLD);
                    refresh();
                })
                .bounds(0, 0, Mth.clamp(lw / 5, 70, 96), 16)
                .build());
        if (x != null) r.gestureButton = gestureButton(owner, x);
        r.find = (r.name.getString() + " " + r.what.getString() + " " + ownerName(owner)
                        + (x != null ? " " + words(x.name()).getString() + " " + words(x.what()).getString() : ""))
                .toLowerCase(Locale.ROOT);
        return r;
    }

    private Row gestureRow(String owner, ControlsJson.Gesture x) {
        Row r = new Row(GESTURE, owner, null, x, words(x.name()), words(x.what()));
        r.h = KEY_H;
        r.gestureButton = gestureButton(owner, x);
        r.find = (r.name.getString() + " " + r.what.getString() + " " + ownerName(owner)).toLowerCase(Locale.ROOT);
        return r;
    }

    private Button gestureButton(String owner, ControlsJson.Gesture x) {
        Button b = Button.builder(Component.empty(), btn -> {
                    Boolean now = ControlsClient.gesture(owner, x);
                    if (now == null) return;
                    if (!ControlsClient.setGesture(owner, x, !now))
                        say(Component.translatable("controls.jj_giants.gesture.failed", ownerName(owner)), WARN);
                    refresh();
                })
                .bounds(0, 0, 60, 16)
                .build();
        return addRenderableWidget(b);
    }

    /** a gesture's toggle: its name and On or Off */
    private static Component gestureLabel(ControlsJson.Gesture x, @Nullable Boolean on) {
        MutableComponent c = Component.empty().append(words(x.name())).append(": ");
        if (on == null) return c.append(Component.literal("?").withStyle(ChatFormatting.GRAY));
        return c.append(Component.translatable(on ? "controls.jj_giants.on" : "controls.jj_giants.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private ControlKey keyOf(KeyMapping km) {
        for (ControlKey k : all) if (k.name().equals(km.getName())) return k;
        return ControlsClient.of(km);
    }

    /** every key's label, clash and reset button, and every gesture's toggle, as they are now */
    private void refresh() {
        all = ControlsClient.keys();
        clashes.clear();
        List<net.jj.giants.Giant> giants = Giants.installed();
        for (Row r : rows) {
            if (r.km != null) {
                ControlKey k = keyOf(r.km);
                ControlsCatalog.Clash c = ControlsCatalog.clash(k, all, o -> ControlsCatalog.owner(o, giants, extra));
                clashes.put(k.name(), c);
                r.keyButton.setMessage(keyLabel(r.km, c));
                r.keyButton.setTooltip(Tooltip.create(keyTip(r, c)));
                r.resetButton.active = !r.km.isDefault();
                r.resetButton.setTooltip(Tooltip.create(Component.translatable(
                        "controls.jj_giants.reset_one", r.km.getDefaultKey().getDisplayName())));
            }
            if (r.gestureButton != null && r.gesture != null) {
                Boolean on = ControlsClient.gesture(r.group, r.gesture);
                Component label = gestureLabel(r.gesture, on);
                r.gestureButton.setMessage(label);
                r.gestureButton.setWidth(Mth.clamp(font.width(label) + 12, 60, Math.max(60, lw / 3)));
                r.gestureButton.active = on != null;
                List<Component> tip = new ArrayList<>();
                if (!r.gesture.what().isEmpty()) tip.add(words(r.gesture.what()));
                if (on == null) tip.add(Component.translatable("controls.jj_giants.gesture.unknown", ownerName(r.group)));
                else tip.add(Component.translatable("controls.jj_giants.gesture.default",
                        Component.translatable(r.gesture.def() ? "controls.jj_giants.on" : "controls.jj_giants.off")));
                r.gestureButton.setTooltip(Tooltip.create(join(tip)));
            }
        }
        place();
    }

    private static Component join(List<Component> lines) {
        MutableComponent c = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) c.append("\n");
            c.append(lines.get(i));
        }
        return c;
    }

    /** as Minecraft's Controls shows it: "> key <" while waiting for a key, "[ key ]" in red when it clashes */
    private Component keyLabel(KeyMapping km, ControlsCatalog.Clash c) {
        Component msg = km.getTranslatedKeyMessage();
        if (selected == km)
            return Component.literal("> ")
                    .append(msg.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                    .append(" <")
                    .withStyle(ChatFormatting.YELLOW);
        if (c.any())
            return Component.literal("[ ").append(msg.copy().withStyle(ChatFormatting.WHITE)).append(" ]").withStyle(ChatFormatting.RED);
        return msg;
    }

    private Component keyTip(Row r, ControlsCatalog.Clash c) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("controls.jj_giants.click"));
        if (c.any()) {
            tip.add(Component.translatable("controls.jj_giants.clash").withStyle(ChatFormatting.RED));
            for (ControlKey o : c.clashes()) tip.add(Component.literal(" • ").append(other(o)));
        }
        if (!c.shared().isEmpty()) {
            List<String> who = new ArrayList<>();
            List<net.jj.giants.Giant> giants = Giants.installed();
            for (ControlKey o : c.shared()) {
                String w = ControlsCatalog.owner(o, giants, extra);
                if (w != null && !who.contains(ownerName(w))) who.add(ownerName(w));
            }
            tip.add(Component.translatable("controls.jj_giants.shared", String.join(", ", who)).withStyle(ChatFormatting.GRAY));
        }
        return join(tip);
    }

    /** another key, with whose it is: "Jump (Movement)", "Armour power (Hollowbell)" */
    private Component other(ControlKey o) {
        String w = ControlsCatalog.owner(o, Giants.installed(), extra);
        Component where = w != null ? Component.literal(ownerName(w)) : Component.translatable(o.category());
        return Component.translatable(o.name()).append(" (").append(where).append(")");
    }

    /** the rows that match the search, and where each goes */
    private void filter() {
        shown.clear();
        String q = keptQuery.strip().toLowerCase(Locale.ROOT);
        for (String gr : groups) {
            Row head = null;
            List<Row> mine = new ArrayList<>();
            for (Row r : rows) {
                if (!r.group.equals(gr)) continue;
                if (r.type == HEADER) head = r;
                else if (q.isEmpty() || r.find.contains(q) || head != null && head.find.contains(q)) mine.add(r);
            }
            if (head != null && (!mine.isEmpty() || q.isEmpty())) {
                shown.add(head);
                shown.addAll(mine);
            }
        }
        for (Row r : rows) if (r.group.isEmpty() && q.isEmpty()) shown.add(r);
        if (shown.isEmpty()) {
            Row none = new Row(NOTE, "", null, null, Component.translatable("controls.jj_giants.none_found", keptQuery.strip()), Component.empty());
            none.h = 14;
            shown.add(none);
        }
        int y = 0;
        for (Row r : shown) {
            r.y = y;
            y += r.h;
        }
        contentH = y + 4;
        int ny = ly0;
        for (int i = 0; i < groups.size(); i++) {
            String gr = groups.get(i);
            boolean any = shown.stream().anyMatch(r -> r.group.equals(gr));
            Button b = nav.get(i);
            b.visible = any && ny + 16 <= ly1;
            b.setY(ny);
            if (any) ny += 18;
        }
        place();
    }

    /** the list's buttons follow the scroll, right to left on each row; any not wholly in view are hidden */
    private void place() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - (ly1 - ly0)));
        keptScroll = scroll;
        for (Row r : rows) for (AbstractWidget w : r.widgets()) w.visible = false;
        for (Row r : shown) {
            int y = ly0 + r.y - (int) scroll + (r.h - 16) / 2;
            int x = lx + lw;
            for (AbstractWidget w : r.widgets()) {
                x -= w.getWidth();
                w.setX(x);
                w.setY(y);
                w.visible = y >= ly0 && y + 16 <= ly1;
                x -= 2;
            }
        }
    }

    private void jump(String group) {
        for (Row r : shown)
            if (r.type == HEADER && r.group.equals(group)) {
                scroll = r.y;
                place();
                return;
            }
    }

    // changes

    private void resetAll() {
        if (!confirmReset) {
            confirmReset = true;
            resetAll.setMessage(Component.translatable("controls.jj_giants.reset_all.sure"));
            return;
        }
        confirmReset = false;
        resetAll.setMessage(Component.translatable("controls.jj_giants.reset_all"));
        selected = null;
        List<KeyMapping> kms = new ArrayList<>();
        for (Row r : rows) if (r.km != null) kms.add(r.km);
        ControlsClient.resetAll(kms);
        for (Row r : rows)
            if (r.gesture != null && r.gestureButton != null) {
                Boolean on = ControlsClient.gesture(r.group, r.gesture);
                if (on != null && on != r.gesture.def()) ControlsClient.setGesture(r.group, r.gesture, r.gesture.def());
            }
        say(Component.translatable("controls.jj_giants.reset_all.done"), GOOD);
        refresh();
    }

    private void bindSelected(InputConstants.Key key) {
        KeyMapping km = selected;
        selected = null;
        ControlsClient.bind(km, key);
        say(Component.translatable("controls.jj_giants.set", Component.translatable(km.getName()), km.getTranslatedKeyMessage()), GOOD);
        refresh();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (selected != null) {
            // (Escape: no key, as in Minecraft's Controls)
            bindSelected(key == 256 ? InputConstants.UNKNOWN : InputConstants.getKey(key, scan));
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (selected != null) {
            bindSelected(InputConstants.Type.MOUSE.getOrCreate(button));
            return true;
        }
        if (confirmReset && !resetAll.isMouseOver(mx, my)) {
            confirmReset = false;
            resetAll.setMessage(Component.translatable("controls.jj_giants.reset_all"));
        }
        if (contentH > ly1 - ly0 && mx >= lx + lw + 2 && mx < lx + lw + 7 && my >= ly0 && my < ly1) {
            dragging = true;
            dragTo(my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= lx - 4 && mx < lx + lw + 8 && my >= ly0 && my < ly1) {
            scroll -= sy * 24;
            place();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            dragTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    private void dragTo(double my) {
        double f = (my - ly0) / Math.max(1, ly1 - ly0);
        scroll = f * (contentH - (ly1 - ly0));
        place();
    }

    private void say(Component c, int colour) {
        status = c;
        statusColor = colour;
        statusAt = Util.getMillis();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(back);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // drawing

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        super.renderBackground(g, mx, my, partial);
        g.fill(px0, py0, px0 + pw, py0 + ph, PANEL);
        g.renderOutline(px0, py0, pw, ph, EDGE);
        g.drawCenteredString(font, title, titleX(px0, pw, tabsEnd, font.width(title)), py0 + 5, TITLE);
        int k = 0;
        for (FormattedCharSequence l : font.split(Component.translatable("controls.jj_giants.help"), pw - 12)) {
            if (k == 2) break;
            g.drawString(font, l, px0 + 6, py0 + 41 + 10 * k++, GREY, false);
        }
        g.fill(px0 + 6, ly0 - 4, px0 + pw - 6, ly0 - 3, EDGE);
        g.fill(lx - 5, ly0, lx - 4, ly1, EDGE);
        drawList(g, mx, my);
        if (!status.getString().isEmpty() && Util.getMillis() - statusAt < 8000)
            g.drawString(font, cut(status.getString(), pw - 12), px0 + 6, py0 + ph - 12, statusColor, false);
    }

    private String cut(String s, int w) {
        if (w <= 0) return "";
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width("…"))) + "…";
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        g.enableScissor(lx - 2, ly0, lx + lw + 8, ly1);
        for (Row r : shown) {
            int y = ly0 + r.y - (int) scroll;
            if (y + r.h < ly0 || y > ly1) continue;
            switch (r.type) {
                case HEADER -> {
                    g.drawString(font, r.name, lx, y + 7, GOLD, false);
                    g.fill(lx, y + HEADER_H - 2, lx + lw, y + HEADER_H - 1, LINE);
                }
                case NOTE -> {
                    int k = 0;
                    for (FormattedCharSequence l : font.split(r.name, lw))
                        g.drawString(font, l, lx, y + 3 + 10 * k++, DIM, false);
                }
                default -> drawRow(g, r, y, mx, my);
            }
        }
        g.disableScissor();
        if (contentH > ly1 - ly0) {
            int track = ly1 - ly0, bar = Math.max(16, track * track / contentH);
            int by = ly0 + (int) ((track - bar) * scroll / Math.max(1, contentH - track));
            g.fill(lx + lw + 3, ly0, lx + lw + 5, ly1, LINE);
            g.fill(lx + lw + 3, by, lx + lw + 5, by + bar, EDGE);
        }
    }

    private void drawRow(GuiGraphics g, Row r, int y, int mx, int my) {
        ControlsCatalog.Clash c = r.km != null ? clashes.get(r.km.getName()) : null;
        boolean clash = c != null && c.any();
        int textW = lw - r.widgetsW() - 8;
        int nx = g.drawString(font, cut(r.name.getString(), textW - 50), lx, y + 3, clash ? CLASH : TEXT, false);
        if (r.type == GESTURE)
            g.drawString(font, " · " + Component.translatable("controls.jj_giants.gesture.tag").getString(), nx, y + 3, DIM, false);
        else if (r.km != null && !r.km.isDefault())
            g.drawString(font, " · " + Component.translatable("controls.jj_giants.changed").getString(), nx, y + 3, DIM, false);
        String what = r.what.getString();
        if (what.isEmpty() && r.km != null) what = Component.translatable(r.km.getCategory()).getString();
        g.drawString(font, cut(what, textW), lx, y + 14, r.what.getString().isEmpty() ? DIM : GREY, false);
        if (clash && r.keyButton != null) {
            // the red mark Minecraft's Controls puts by a clashing key
            int bx = r.keyButton.getX() - 5;
            g.fill(bx, y + 5, bx + 3, y + 21, CLASH);
        }
        if (mx >= lx && mx < lx + textW && my >= Math.max(y, ly0) && my < Math.min(y + r.h, ly1)) {
            List<FormattedCharSequence> tip = new ArrayList<>();
            tip.add(r.name.copy().withStyle(ChatFormatting.YELLOW).getVisualOrderText());
            if (!r.what.getString().isEmpty()) tip.addAll(font.split(r.what, 260));
            if (r.km != null) {
                tip.addAll(font.split(Component.translatable("controls.jj_giants.default", r.km.getDefaultKey().getDisplayName())
                        .withStyle(ChatFormatting.GRAY), 260));
                tip.addAll(font.split(Component.translatable("controls.jj_giants.category", Component.translatable(r.km.getCategory()))
                        .withStyle(ChatFormatting.DARK_GRAY), 260));
            }
            if (r.gesture != null && r.type == KEY && !r.gesture.what().isEmpty())
                tip.addAll(font.split(Component.empty().append(words(r.gesture.name())).append(": ").append(words(r.gesture.what()))
                        .withStyle(ChatFormatting.GRAY), 260));
            setTooltipForNextRenderPass(tip);
        }
    }
}
