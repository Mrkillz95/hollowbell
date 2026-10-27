package net.jj.giants.client.perf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;
import net.jj.giants.Giant;
import net.jj.giants.perf.PerfJson;
import net.jj.giants.perf.PerfPresets;
import net.jj.giants.perf.PerfProfile;
import net.jj.giants.perf.PerfSetting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Giants Performance: every effect, detail and feature setting of every installed giant that knows the protocol,
 * in one scrolling list. Up top, the preset (built-in or the player's own), what it turns down or removes, and a
 * search box; down the left, the sections. Each setting has its name, one line on what it is, its cost and what
 * lowering it loses. The world's Features are for operators only: everyone else sees them greyed out.
 */
public class PerfScreen extends Screen {
    private static final int GOLD = 0xFFE0B050,
            TITLE = 0xFFE9C9BC,
            TEXT = 0xFFD8D8D8,
            GREY = 0xFF9A9A9A,
            DIM = 0xFF6E6E6E,
            LOSE = 0xFFD8A070,
            WARN = 0xFFFF7060,
            GOOD = 0xFF80E080,
            PANEL = 0xE8141210,
            EDGE = 0xFF6B5A44,
            LINE = 0x30FFFFFF;
    private static final int HEADER = 0, SETTING = 1, NOTE = 2;
    private static final int SETTING_H = 36, HEADER_H = 22;

    /** kept while the game runs: where the list was, and what was searched */
    private static double keptScroll;
    private static String keptQuery = "";

    private final @Nullable Screen back;
    private PerfProfile profile;
    private List<PerfSetting> settings = List.of();
    /** the world's values as shown (the server's, with this player's changes not yet answered on top) */
    private final Map<String, Double> worldShown = new HashMap<>();
    private final Map<String, Double> worldPending = new LinkedHashMap<>();
    private int ticks, clientDirtyAt = -1, worldDirtyAt = -1;
    private boolean confirmDelete;
    /** what the server had said when the list was made (a change lays it out again) */
    private boolean builtKnown, builtOp;
    /** "save" or "rename" while a name is being typed, else null */
    private @Nullable String naming;

    private final List<Row> rows = new ArrayList<>();
    private final List<Row> shown = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();
    private final List<Button> nav = new ArrayList<>();
    private double scroll;
    private int contentH;
    private boolean dragging;
    private Component status = Component.empty();
    private int statusColor = GREY;
    private long statusAt;

    private EditBox search, nameBox;
    private Button presetButton, saveButton, renameButton, deleteButton, okButton, cancelButton, featuresButton;

    // where things go (set in init)
    private int px0, py0, pw, ph, navX, navW, lx, lw, ly0, ly1, tabsEnd;

    /** open next time scrolled this far, with this search (the picture-taking uses this) */
    public static void showAt(double scroll, String query) {
        keptScroll = scroll;
        keptQuery = query == null ? "" : query;
    }

    public PerfScreen(@Nullable Screen back) {
        super(Component.translatable("perf.jj_giants.title"));
        this.back = back;
    }

    /** one line of the list: a section's heading, a setting, or a note */
    private final class Row {
        final int type;
        final String group;
        final @Nullable PerfSetting s;
        final Component text;
        @Nullable AbstractWidget w;
        int h, y;
        final String find;

        Row(int type, String group, @Nullable PerfSetting s, Component text) {
            this.type = type;
            this.group = group;
            this.s = s;
            this.text = text;
            this.find = (s == null
                            ? text.getString()
                            : name(s).getString() + " " + what(s).getString() + " " + loses(s).getString() + " "
                                    + ownerName(s.owner()) + " " + groupTitle(group).getString())
                    .toLowerCase(Locale.ROOT);
        }
    }

    // words

    private static boolean has(String key) {
        return Language.getInstance().has(key);
    }

    /** a guide language key, or a giant's own plain words */
    private Component words(String keyOrText, PerfSetting s) {
        if (keyOrText.isEmpty()) return Component.empty();
        return has(keyOrText)
                ? Component.translatable(keyOrText, ownerName(s.owner()))
                : Component.literal(keyOrText);
    }

    private Component name(PerfSetting s) {
        return words(s.name(), s);
    }

    private Component what(PerfSetting s) {
        return words(s.what(), s);
    }

    private Component loses(PerfSetting s) {
        return words(s.loses(), s);
    }

    private static String ownerName(String key) {
        if (key == null || key.isEmpty()) return "";
        String k = "guide.jj_giants." + key + ".name";
        return has(k) ? Language.getInstance().getOrDefault(k) : key;
    }

    private static String group(PerfSetting s) {
        return s.section().equals(PerfSetting.GIANT) ? "giant:" + s.owner() : s.section();
    }

    private static Component groupTitle(String group) {
        if (group.startsWith("giant:")) return Component.literal(ownerName(group.substring(6)));
        return Component.translatable("perf.jj_giants.section." + group);
    }

    private static Component navTitle(String group) {
        if (group.startsWith("giant:")) return Component.literal(ownerName(group.substring(6)));
        return Component.translatable("perf.jj_giants.nav." + group);
    }

    /** the giants that read this setting (from their "uses" lists); null for a giant's own setting */
    private @Nullable List<String> readBy(PerfSetting s) {
        if (!s.owner().isEmpty() && !s.perGiant()) return null;
        List<String> out = new ArrayList<>();
        for (PerfJson.Report r : PerfClient.reports())
            if (r.uses().contains(s.plainId()) && (!s.perGiant() || r.giant().equals(s.owner())))
                out.add(ownerName(r.giant()));
        return out;
    }

    // values

    private double value(PerfSetting s) {
        if (!s.server()) return profile.get(s);
        return s.clamp(worldShown.getOrDefault(s.id(), s.def()));
    }

    /** whether this player may move it: their own always; the world's only as an operator on a server with the guide */
    private boolean editable(PerfSetting s) {
        return s.editable(PerfClient.world().known(), PerfClient.world().op());
    }

    private void change(PerfSetting s, double v) {
        v = s.clamp(v);
        if (s.server()) {
            if (!editable(s)) return;
            worldShown.put(s.id(), v);
            worldPending.put(s.id(), v);
            worldDirtyAt = ticks;
            profile.markCustom();
        } else {
            profile.set(s, v);
            clientDirtyAt = ticks;
        }
        confirmDelete = false;
        updatePresetBar();
    }

    // layout

    @Override
    protected void init() {
        profile = PerfClient.profile();
        settings = PerfClient.settings();
        worldShown.clear();
        worldShown.putAll(PerfClient.world().values());
        worldShown.putAll(worldPending);
        builtKnown = PerfClient.world().known();
        builtOp = PerfClient.world().op();

        pw = Math.min(width - 8, 620);
        ph = Math.min(height - 8, 420);
        px0 = (width - pw) / 2;
        py0 = (height - ph) / 2;
        navX = px0 + 6;
        navW = Mth.clamp(pw / 6, 64, 96);
        lx = navX + navW + 8;
        lw = px0 + pw - 6 - 6 - lx;
        ly0 = py0 + 84;
        ly1 = py0 + ph - 16;

        // the preset bar
        int y = py0 + 18, x = px0 + 6;
        // (the preset's own button gets what's left once the rest fit, up to 150)
        int pbw = Mth.clamp(pw - 12 - 224, 80, 150);
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycle(-1)).bounds(x, y, 16, 16).build());
        presetButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(1))
                .bounds(x + 18, y, pbw, 16)
                .build());
        presetButton.setTooltip(Tooltip.create(Component.translatable("perf.jj_giants.preset.tip")));
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycle(1)).bounds(x + 20 + pbw, y, 16, 16).build());
        int bx = x + 42 + pbw;
        saveButton = addRenderableWidget(Button.builder(Component.translatable("perf.jj_giants.save_as"), b -> {
                    naming = "save";
                    nameBox.setValue("");
                    updatePresetBar();
                    setFocused(nameBox);
                })
                .bounds(bx, y, 62, 16)
                .build());
        renameButton = addRenderableWidget(Button.builder(Component.translatable("perf.jj_giants.rename"), b -> {
                    naming = "rename";
                    nameBox.setValue(profile.preset());
                    updatePresetBar();
                    setFocused(nameBox);
                })
                .bounds(bx + 64, y, 62, 16)
                .build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("perf.jj_giants.delete"), b -> delete())
                .bounds(bx + 128, y, 54, 16)
                .build());
        nameBox = new EditBox(font, bx, y, 124, 16, Component.translatable("perf.jj_giants.name_hint"));
        nameBox.setMaxLength(PerfProfile.LONGEST_NAME);
        nameBox.setHint(Component.translatable("perf.jj_giants.name_hint"));
        addRenderableWidget(nameBox);
        okButton = addRenderableWidget(Button.builder(Component.translatable("perf.jj_giants.ok"), b -> named())
                .bounds(bx + 128, y, 30, 16)
                .build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("perf.jj_giants.cancel"), b -> {
                    naming = null;
                    updatePresetBar();
                })
                .bounds(bx + 160, y, 46, 16)
                .build());

        // search, and (for an operator) whether presets set the world's features too
        search = new EditBox(font, px0 + 6, py0 + 62, Math.min(200, pw / 3), 16, Component.translatable("perf.jj_giants.search"));
        search.setHint(Component.translatable("perf.jj_giants.search"));
        search.setValue(keptQuery);
        search.setResponder(q -> {
            keptQuery = q;
            scroll = 0;
            filter();
        });
        addRenderableWidget(search);
        Component fw = Component.translatable("perf.jj_giants.with_features", onOff(profile.withFeatures));
        int fwW = Math.min(font.width(fw) + 12, pw - search.getWidth() - 20);
        featuresButton = addRenderableWidget(Button.builder(fw, b -> {
                    profile.withFeatures = !profile.withFeatures;
                    clientDirtyAt = ticks;
                    b.setMessage(Component.translatable("perf.jj_giants.with_features", onOff(profile.withFeatures)));
                })
                .bounds(px0 + pw - 6 - fwW, py0 + 62, fwW, 16)
                .build());
        featuresButton.setTooltip(Tooltip.create(Component.translatable("perf.jj_giants.with_features.tip")));

        // Settings | Controls
        tabsEnd = px0 + 6;
        for (Button b : ControlsScreen.tabs(this, back, false, px0 + 6, py0 + 2)) {
            addRenderableWidget(b);
            tabsEnd = b.getX() + b.getWidth();
        }

        Button close = Button.builder(Component.literal("✕"), b -> onClose())
                .bounds(px0 + pw - 19, py0 + 2, 16, 14)
                .build();
        close.setTooltip(Tooltip.create(Component.translatable("guide.jj_giants.close")));
        addRenderableWidget(close);

        buildRows();
        updatePresetBar();
        scroll = keptScroll;
        filter();
    }

    private static Component onOff(boolean on) {
        return Component.literal(on ? "On" : "Off");
    }

    /** the rows (and their widgets), in section order: Effects, Detail, each giant, then the world's Features */
    private void buildRows() {
        rows.clear();
        nav.clear();
        groups.clear();
        groups.add(PerfSetting.EFFECTS);
        groups.add(PerfSetting.DETAIL);
        for (Giant g : PerfClient.giants())
            if (settings.stream().anyMatch(s -> group(s).equals("giant:" + g.key()))) groups.add("giant:" + g.key());
        groups.add(PerfSetting.FEATURES);
        for (String gr : groups) {
            Row head = new Row(HEADER, gr, null, groupTitle(gr));
            head.h = HEADER_H;
            Button reset = Button.builder(Component.translatable("perf.jj_giants.reset"), b -> reset(gr))
                    .bounds(0, 0, Math.min(100, lw / 3), 16)
                    .build();
            reset.setTooltip(Tooltip.create(Component.translatable("perf.jj_giants.reset.tip", profile.base())));
            head.w = addRenderableWidget(reset);
            rows.add(head);
            if (gr.equals(PerfSetting.FEATURES)) {
                String k = !PerfClient.world().known()
                        ? "perf.jj_giants.features.no_server"
                        : PerfClient.world().op() ? "perf.jj_giants.features.op" : "perf.jj_giants.features.not_op";
                note(gr, Component.translatable(k));
                if (!PerfClient.world().known() || !PerfClient.world().op()) reset.active = false;
            }
            for (PerfSetting s : settings) if (group(s).equals(gr)) rows.add(settingRow(gr, s));
        }
        List<Giant> old = PerfClient.leftOut();
        if (!old.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Giant g : old) names.add(ownerName(g.key()));
            note("", Component.translatable("perf.jj_giants.left_out", String.join(", ", names)));
        }
        for (String gr : groups) {
            Button b = Button.builder(navTitle(gr), x -> jump(gr)).bounds(navX, 0, navW, 16).build();
            if (gr.equals(PerfSetting.FEATURES)) b.setMessage(Component.literal("🔒 ").append(navTitle(gr)));
            nav.add(addRenderableWidget(b));
        }
    }

    private void note(String group, Component text) {
        Row r = new Row(NOTE, group, null, text);
        r.h = Math.max(1, font.split(text, lw).size()) * 10 + 4;
        rows.add(r);
    }

    private Row settingRow(String gr, PerfSetting s) {
        Row r = new Row(SETTING, gr, s, name(s));
        r.h = SETTING_H;
        int cw = controlW();
        AbstractWidget w;
        if (s.kind() == PerfSetting.Kind.SLIDER) {
            w = new Slider(cw, s, value(s), v -> change(s, v));
        } else {
            w = Button.builder(Component.literal(s.show(value(s))), b -> {
                        double v = value(s);
                        double next = s.kind() == PerfSetting.Kind.TOGGLE
                                ? (v >= 0.5 ? 0 : 1)
                                : (v + 1) % s.options().size();
                        change(s, next);
                        b.setMessage(label(s, next));
                    })
                    .bounds(0, 0, cw, 16)
                    .build();
            w.setMessage(label(s, value(s)));
        }
        if (!editable(s)) {
            w.active = false;
            w.setTooltip(Tooltip.create(Component.translatable(
                    PerfClient.world().known() ? "perf.jj_giants.features.not_op" : "perf.jj_giants.features.no_server")));
        }
        r.w = addRenderableWidget(w);
        return r;
    }

    private int controlW() {
        return Mth.clamp(lw / 3, 90, 140);
    }

    /** a toggle's or choice's button: on/off coloured, the rest plain */
    private static Component label(PerfSetting s, double v) {
        Component c = Component.literal(s.show(v));
        if (s.kind() == PerfSetting.Kind.TOGGLE)
            return c.copy().withStyle(v >= 0.5 ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED);
        return c;
    }

    /** the rows that match the search, and where each goes */
    private void filter() {
        shown.clear();
        String q = keptQuery.strip().toLowerCase(Locale.ROOT);
        for (String gr : groups) {
            List<Row> mine = new ArrayList<>();
            Row head = null;
            for (Row r : rows) {
                if (!r.group.equals(gr)) continue;
                if (r.type == HEADER) head = r;
                else if (q.isEmpty() || r.type == SETTING && r.find.contains(q)) mine.add(r);
            }
            if (head != null && (!mine.isEmpty() || q.isEmpty())) {
                shown.add(head);
                shown.addAll(mine);
            }
        }
        for (Row r : rows) if (r.group.isEmpty() && q.isEmpty()) shown.add(r);
        if (shown.isEmpty()) {
            Row none = new Row(NOTE, "", null, Component.translatable("perf.jj_giants.none_found", keptQuery.strip()));
            none.h = 14;
            shown.add(none);
        }
        int y = 0;
        for (Row r : shown) {
            r.y = y;
            y += r.h;
        }
        contentH = y + 4;
        // the section buttons: only the sections still in the list
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

    /** the list's widgets follow the scroll; any not wholly in view are hidden */
    private void place() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - (ly1 - ly0)));
        keptScroll = scroll;
        for (Row r : rows) if (r.w != null) r.w.visible = false;
        for (Row r : shown) {
            if (r.w == null) continue;
            int y = ly0 + r.y - (int) scroll + (r.type == HEADER ? 3 : 1);
            r.w.setX(lx + lw - r.w.getWidth());
            r.w.setY(y);
            r.w.visible = y >= ly0 && y + 16 <= ly1;
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

    // presets

    private void updatePresetBar() {
        String p = profile.preset();
        net.minecraft.ChatFormatting colour = profile.custom()
                ? net.minecraft.ChatFormatting.WHITE
                : PerfPresets.builtIn(p) >= 0 ? net.minecraft.ChatFormatting.YELLOW : net.minecraft.ChatFormatting.AQUA;
        presetButton.setMessage(Component.translatable("perf.jj_giants.preset", Component.literal(p).withStyle(colour)));
        boolean mine = profile.mine().contains(p);
        boolean typing = naming != null;
        saveButton.visible = renameButton.visible = deleteButton.visible = !typing;
        renameButton.active = deleteButton.active = mine;
        deleteButton.setMessage(Component.translatable(confirmDelete ? "perf.jj_giants.delete.sure" : "perf.jj_giants.delete"));
        nameBox.visible = okButton.visible = cancelButton.visible = typing;
        boolean op = PerfClient.world().known() && PerfClient.world().op();
        featuresButton.visible = op;
    }

    private void cycle(int dir) {
        List<String> all = profile.all();
        int i = all.indexOf(profile.custom() ? profile.base() : profile.preset());
        apply(all.get(i < 0 ? 0 : Math.floorMod(i + dir, all.size())));
    }

    private void apply(String name) {
        Map<String, Double> v = profile.apply(name, settings);
        if (v == null) return;
        clientDirtyAt = ticks;
        confirmDelete = false;
        if (profile.withFeatures && PerfClient.world().known() && PerfClient.world().op()) {
            for (PerfSetting s : settings)
                if (s.server()) {
                    worldShown.put(s.id(), v.get(s.id()));
                    worldPending.put(s.id(), v.get(s.id()));
                }
            worldDirtyAt = ticks - 100;
        }
        refreshValues();
        updatePresetBar();
        say(Component.translatable("perf.jj_giants.preset", profile.preset()), GOOD);
    }

    private void reset(String group) {
        Map<String, Double> v = profile.reset(s -> group(s).equals(group), settings);
        clientDirtyAt = ticks;
        for (PerfSetting s : settings)
            if (s.server() && v.containsKey(s.id()) && editable(s)) {
                worldShown.put(s.id(), v.get(s.id()));
                worldPending.put(s.id(), v.get(s.id()));
                worldDirtyAt = ticks - 100;
            }
        refreshValues();
        updatePresetBar();
    }

    private void named() {
        String n = nameBox.getValue();
        String bad;
        if ("rename".equals(naming)) bad = profile.rename(profile.preset(), n);
        else {
            Map<String, Double> all = new LinkedHashMap<>();
            for (PerfSetting s : settings) all.put(s.id(), value(s));
            bad = profile.saveAs(n, all);
        }
        if (bad != null) {
            say(Component.literal(bad), WARN);
            return;
        }
        naming = null;
        clientDirtyAt = ticks;
        updatePresetBar();
        say(Component.translatable("perf.jj_giants.preset", profile.preset()), GOOD);
    }

    private void delete() {
        if (!confirmDelete) {
            confirmDelete = true;
            updatePresetBar();
            return;
        }
        confirmDelete = false;
        String bad = profile.delete(profile.preset());
        if (bad != null) say(Component.literal(bad), WARN);
        clientDirtyAt = ticks;
        updatePresetBar();
    }

    /** every widget shows its setting's value again (after a preset, a reset or the server's word) */
    private void refreshValues() {
        for (Row r : rows) {
            if (r.s == null || r.w == null) continue;
            if (r.w instanceof Slider sl) sl.show(value(r.s));
            else r.w.setMessage(label(r.s, value(r.s)));
        }
    }

    private void say(Component c, int colour) {
        status = c;
        statusColor = colour;
        statusAt = Util.getMillis();
    }

    /** the server said something new: new values (or a new list, then everything is laid out again) */
    void worldChanged() {
        PerfClient.World w = PerfClient.world();
        List<String> had = settings.stream().filter(PerfSetting::server).map(PerfSetting::id).toList();
        List<String> now = PerfClient.settings().stream().filter(PerfSetting::server).map(PerfSetting::id).toList();
        if (!had.equals(now) || builtKnown != w.known() || builtOp != w.op()) {
            keptScroll = scroll;
            rebuild();
        } else {
            worldShown.clear();
            worldShown.putAll(w.values());
            worldShown.putAll(worldPending);
            refreshValues();
        }
        if (!w.message().isEmpty())
            say(Component.literal(w.message()), w.message().startsWith("Only") ? WARN : GOOD);
    }

    private String featuresNote() {
        return !PerfClient.world().known()
                ? "perf.jj_giants.features.no_server"
                : PerfClient.world().op() ? "perf.jj_giants.features.op" : "perf.jj_giants.features.not_op";
    }

    private void rebuild() {
        String typed = nameBox != null ? nameBox.getValue() : "";
        boolean searching = getFocused() == search;
        clearWidgets();
        nav.clear();
        init();
        nameBox.setValue(typed);
        if (searching) setFocused(search);
    }

    // time: changes go out a moment after the last move, not on every step of a drag

    @Override
    public void tick() {
        ticks++;
        if (clientDirtyAt >= 0 && ticks - clientDirtyAt >= 4) {
            clientDirtyAt = -1;
            PerfClient.push();
        }
        if (!worldPending.isEmpty() && ticks - worldDirtyAt >= 8) {
            if (PerfClient.request(worldPending)) say(Component.translatable("perf.jj_giants.sent"), GREY);
            worldPending.clear();
        }
    }

    @Override
    public void removed() {
        clientDirtyAt = -1;
        if (!worldPending.isEmpty()) PerfClient.request(worldPending);
        worldPending.clear();
        PerfClient.push();
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
        g.drawCenteredString(font, title, ControlsScreen.titleX(px0, pw, tabsEnd, font.width(title)), py0 + 5, TITLE);
        drawSummary(g, mx, my);
        g.fill(px0 + 6, ly0 - 4, px0 + pw - 6, ly0 - 3, EDGE);
        g.fill(lx - 5, ly0, lx - 4, ly1, EDGE);
        drawList(g, mx, my);
        if (!status.getString().isEmpty() && Util.getMillis() - statusAt < 8000)
            g.drawString(font, cut(status.getString(), pw - 12), px0 + 6, py0 + ph - 12, statusColor, false);
    }

    /** what the preset turns down or takes away, under the preset bar (hover for all of it) */
    private void drawSummary(GuiGraphics g, int mx, int my) {
        String p = profile.preset();
        Map<String, Double> v;
        if (profile.custom()) {
            v = new HashMap<>();
            for (PerfSetting s : settings) v.put(s.id(), value(s));
        } else v = profile.presetValues(p, settings);
        if (v == null) v = Map.of();
        List<PerfPresets.Change> ch = PerfPresets.changes(v, settings);
        int x = px0 + 6, y = py0 + 38, w = pw - 12;
        List<String> lines = new ArrayList<>();
        if (profile.custom()) lines.add(Component.translatable("perf.jj_giants.custom", profile.base()).getString());
        if (ch.isEmpty()) {
            lines.add(PerfPresets.builtIn(p) == PerfPresets.HIGHEST
                    ? Component.translatable("perf.jj_giants.highest").getString()
                    : Component.translatable("perf.jj_giants.same", p).getString());
        } else {
            List<String> down = new ArrayList<>(), gone = new ArrayList<>();
            for (PerfPresets.Change c : ch) {
                String n = name(c.setting()).getString() + (c.setting().server() ? " (" + world() + ")" : "");
                if (c.removes()) gone.add(n);
                else down.add(n + " " + c.setting().show(c.to()));
            }
            if (!down.isEmpty())
                lines.add(Component.translatable("perf.jj_giants.turns_down", String.join(", ", down)).getString());
            if (!gone.isEmpty())
                lines.add(Component.translatable("perf.jj_giants.removes", String.join(", ", gone)).getString());
        }
        boolean cutAny = false;
        for (int i = 0; i < lines.size() && i < 2; i++) {
            String s = cut(lines.get(i), w);
            cutAny |= !s.equals(lines.get(i));
            int colour = lines.get(i).startsWith(Component.translatable("perf.jj_giants.removes", "").getString())
                    ? LOSE
                    : GREY;
            g.drawString(font, s, x, y + i * 10, colour, false);
        }
        if (lines.size() > 2) cutAny = true;
        if (mx >= x && mx < x + w && my >= y && my < y + 20 && (cutAny || !ch.isEmpty())) {
            List<FormattedCharSequence> tip = new ArrayList<>();
            tip.add(Component.literal(profile.custom() ? PerfPresets.CUSTOM : p)
                    .withStyle(net.minecraft.ChatFormatting.YELLOW)
                    .getVisualOrderText());
            for (PerfPresets.Change c : ch) {
                PerfSetting s = c.setting();
                Component line = Component.literal((c.removes() ? "✖ " : "▼ ") + name(s).getString()
                                + (s.server() ? " (" + world() + ")" : "") + ": " + s.show(c.from()) + " → "
                                + s.show(c.to()))
                        .withStyle(c.removes() ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GRAY);
                tip.addAll(font.split(line, 300));
            }
            setTooltipForNextRenderPass(tip);
        }
    }

    private static String world() {
        return Component.translatable("perf.jj_giants.world_tag").getString();
    }

    private String cut(String s, int w) {
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, w - font.width("…")) + "…";
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        g.enableScissor(lx - 2, ly0, lx + lw + 8, ly1);
        int cw = controlW();
        for (Row r : shown) {
            int y = ly0 + r.y - (int) scroll;
            if (y + r.h < ly0 || y > ly1) continue;
            switch (r.type) {
                case HEADER -> {
                    g.drawString(font, r.text, lx, y + 7, GOLD, false);
                    g.fill(lx, y + HEADER_H - 2, lx + lw, y + HEADER_H - 1, LINE);
                }
                case NOTE -> {
                    int k = 0;
                    for (FormattedCharSequence l : font.split(r.text, lw))
                        g.drawString(font, l, lx, y + 2 + 10 * k++, GREY, false);
                }
                default -> drawSetting(g, r, y, cw, mx, my);
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

    private void drawSetting(GuiGraphics g, Row r, int y, int cw, int mx, int my) {
        PerfSetting s = r.s;
        boolean can = editable(s);
        int textW = lw - cw - 6;
        // line 1: the name, then small tags (whose it is, cost, operators, not read yet)
        StringBuilder tags = new StringBuilder();
        if (!s.owner().isEmpty() && !s.perGiant() && !r.group.startsWith("giant:"))
            tags.append(" · ").append(ownerName(s.owner()));
        List<String> readers = readBy(s);
        if (readers != null && readers.isEmpty())
            tags.append(" · ").append(Component.translatable("perf.jj_giants.unread").getString());
        String nm = cut(r.text.getString(), textW - 34);
        int nx = g.drawString(font, nm, lx, y + 2, can ? TEXT : GREY, false);
        String cost = Component.translatable("perf.jj_giants.cost." + s.cost().word()).getString();
        int costColour = switch (s.cost()) {
            case HIGH -> 0xFFFF8060;
            case MED -> 0xFFE8C050;
            case LOW -> 0xFF80C080;
        };
        nx = g.drawString(font, " [" + cost + "]", nx, y + 2, costColour, false);
        if (s.ops())
            nx = g.drawString(font, " 🔒" + Component.translatable("perf.jj_giants.ops_tag").getString(), nx, y + 2,
                    can ? WARN : DIM, false);
        if (tags.length() > 0) g.drawString(font, cut(tags.toString(), Math.max(0, lx + textW - nx)), nx, y + 2, DIM, false);
        // line 2: what it is
        g.drawString(font, cut(what(s).getString(), textW), lx, y + 13, can ? GREY : DIM, false);
        // line 3: what lowering it loses (for the world's, a warning)
        Component lose = s.server()
                ? Component.translatable("perf.jj_giants.warn", loses(s))
                : Component.translatable(
                        s.kind() == PerfSetting.Kind.TOGGLE ? "perf.jj_giants.off" : "perf.jj_giants.lower", loses(s));
        int loseW = lw;
        if (!can && s.server()) {
            // (greyed out, with who to ask at the end of the line)
            String ask = Component.translatable("perf.jj_giants.ask_op").getString();
            g.drawString(font, ask, lx + lw - font.width(ask), y + 24, GREY, false);
            loseW = lw - font.width(ask) - 8;
        }
        g.drawString(font, cut(lose.getString(), loseW), lx, y + 24, s.server() ? (can ? WARN : DIM) : (can ? LOSE : DIM), false);
        // hover: all of it
        if (mx >= lx && mx < lx + textW && my >= Math.max(y, ly0) && my < Math.min(y + r.h, ly1)) {
            List<FormattedCharSequence> tip = new ArrayList<>();
            tip.add(r.text.copy().withStyle(net.minecraft.ChatFormatting.YELLOW).getVisualOrderText());
            tip.addAll(font.split(what(s), 260));
            tip.addAll(font.split(Component.translatable("perf.jj_giants.cost", cost), 260));
            tip.addAll(font.split(lose, 260));
            if (readers != null)
                tip.addAll(font.split(
                        readers.isEmpty()
                                ? Component.translatable("perf.jj_giants.read_by.none")
                                : Component.translatable("perf.jj_giants.read_by", String.join(", ", readers)),
                        260));
            if (!can && s.server()) tip.addAll(font.split(Component.translatable(featuresNote()), 260));
            setTooltipForNextRenderPass(tip);
        }
    }

    // scrolling

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
    public boolean mouseClicked(double mx, double my, int button) {
        if (contentH > ly1 - ly0 && mx >= lx + lw + 2 && mx < lx + lw + 7 && my >= ly0 && my < ly1) {
            dragging = true;
            dragTo(my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
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

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (naming != null && getFocused() == nameBox && (key == 257 || key == 335)) {
            named();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** a slider that snaps to its setting's steps and shows its value in the setting's own words */
    private static final class Slider extends AbstractSliderButton {
        private final PerfSetting s;
        private final DoubleConsumer onChange;

        Slider(int w, PerfSetting s, double v, DoubleConsumer onChange) {
            super(0, 0, w, 16, Component.empty(), 0);
            this.s = s;
            this.onChange = onChange;
            show(v);
        }

        private double frac(double v) {
            return s.max() > s.min() ? (s.clamp(v) - s.min()) / (s.max() - s.min()) : 0;
        }

        private double current() {
            return s.clamp(s.min() + value * (s.max() - s.min()));
        }

        void show(double v) {
            value = frac(v);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(s.show(current())));
        }

        @Override
        protected void applyValue() {
            double v = current();
            onChange.accept(v);
        }
    }
}
