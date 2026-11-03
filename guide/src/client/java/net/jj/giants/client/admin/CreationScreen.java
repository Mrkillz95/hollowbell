package net.jj.giants.client.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminDraft;
import net.jj.giants.admin.AdminInsight;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The Create World screen's "Giants…" (ADMIN_BRIDGE.md §18.6): the preset picker, laid out as the admin book's Presets
 * page (Classic first as the default, the Suggested ones still to come, All presets, the ones saved on this game).
 * Picking one makes the new world's draft exactly that preset; Edit settings… opens the admin book's pages on the draft.
 */
public class CreationScreen extends Screen {
    private static final int GOLD = AdminBookScreen.GOLD, TEXT = AdminBookScreen.TEXT, GREY = AdminBookScreen.GREY,
            DIM = AdminBookScreen.DIM, KEY = AdminBookScreen.KEY, TITLE = AdminBookScreen.TITLE,
            PANEL = AdminBookScreen.PANEL, EDGE = AdminBookScreen.EDGE;
    /** the suggested presets still to come, greyed: My Giant (Part 5) */
    private static final List<String> SUGGESTED = List.of();

    private final Screen parent;
    private final AdminDraft draft;
    private @Nullable JsonObject presets;
    private boolean anyGiant;

    private final List<Item> items = new ArrayList<>();
    private double scroll;
    private int contentH;
    private int px0, py0, pw, ph, ly0, ly1;

    /** one line of the list: a head, a preset (pick = what picking it asks for), a note, or the greyed chips */
    private record Item(String kind, Component title, List<FormattedCharSequence> sub, @Nullable String pick,
            String name, int h) {}

    public CreationScreen(Screen parent) {
        super(Component.translatable("admin.jj_giants.creation.title"));
        this.parent = parent;
        this.draft = CreationClient.draft(parent);
    }

    /** the Create World screen this came from (it holds the draft) */
    public Screen parent() {
        return parent;
    }

    @Override
    protected void init() {
        if (presets == null) {
            // (each preset's rating is worked out once, as a world with nothing set would have it)
            presets = AdminBook.presetsJson(new AdminDraft(), "", "");
            for (Giant g : AdminServer.giants()) if (AdminServer.report(null, g) != null) anyGiant = true;
        }
        pw = Math.min(width - 16, 480);
        ph = height - 16;
        px0 = (width - pw) / 2;
        py0 = 8;
        ly0 = py0 + 24;
        ly1 = py0 + ph - 52;
        // (Part 2C) See all changes | Edit settings… | Done
        int bw = Math.min(140, (pw - 40) / 3);
        Button all = addRenderableWidget(Button.builder(Component.translatable("admin.jj_giants.creation.all"),
                        b -> minecraft.setScreen(new ChangesScreen(this, draft)))
                .bounds(width / 2 - bw * 3 / 2 - 6, py0 + ph - 26, bw, 20)
                .build());
        all.setTooltip(Tooltip.create(Component.translatable("admin.jj_giants.creation.all.tip")));
        Button edit = addRenderableWidget(Button.builder(Component.translatable("admin.jj_giants.creation.edit"),
                        b -> edit())
                .bounds(width / 2 - bw / 2, py0 + ph - 26, bw, 20)
                .build());
        edit.setTooltip(Tooltip.create(Component.translatable("admin.jj_giants.creation.edit.tip")));
        edit.active = anyGiant;
        addRenderableWidget(Button.builder(Component.translatable("admin.jj_giants.creation.done"), b -> onClose())
                .bounds(width / 2 + bw / 2 + 6, py0 + ph - 26, bw, 20)
                .build());
        build();
    }

    private static List<JsonObject> objects(@Nullable JsonObject o, String k) {
        List<JsonObject> out = new ArrayList<>();
        if (o != null && o.get(k) instanceof JsonArray a)
            for (JsonElement e : a) if (e.isJsonObject()) out.add(e.getAsJsonObject());
        return out;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private void build() {
        items.clear();
        int tw = pw - 40 - 14;
        if (!anyGiant)
            items.add(new Item("note", Component.translatable("admin.jj_giants.creation.none"), List.of(), null, "",
                    14));
        List<JsonObject> built = objects(presets, "builtin");
        for (JsonObject b : built) if (b.has("default") && b.get("default").getAsBoolean()) preset(b, tw);
        items.add(new Item("head", Component.translatable("admin.jj_giants.creation.suggested"),
                List.of(Component.translatable("admin.jj_giants.creation.suggested.note").getVisualOrderText()), null,
                "", 16));
        for (JsonObject b : built) if (str(b, "group").equals("suggested")) preset(b, tw);
        items.add(new Item("chips", Component.empty(), List.of(), null, "", chipRows(tw) * 16 + 2));
        items.add(new Item("head", Component.translatable("admin.jj_giants.creation.all"), List.of(), null, "", 16));
        for (JsonObject b : built)
            if (!(b.has("default") && b.get("default").getAsBoolean()) && !str(b, "group").equals("suggested"))
                preset(b, tw);
        items.add(new Item("head", Component.translatable("admin.jj_giants.creation.saved"), List.of(), null, "",
                16));
        List<JsonObject> saved = objects(presets, "saved");
        if (saved.isEmpty())
            items.add(new Item("note", Component.translatable("admin.jj_giants.creation.saved.none"), List.of(), null,
                    "", 14));
        for (JsonObject s : saved) {
            MutableComponent t = AdminBookScreen.presetTitle(s);
            List<FormattedCharSequence> sub = font.split(Component.translatable("admin.jj_giants.creation.saved.sub",
                    str(s, "by"), str(s, "count")), tw);
            items.add(new Item("preset", t, sub, str(s, "name"), str(s, "name"), 14 + sub.size() * 10 + 4));
        }
        contentH = 0;
        for (Item i : items) contentH += i.h();
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - (ly1 - ly0)));
    }

    private void preset(JsonObject b, int tw) {
        List<FormattedCharSequence> sub = font.split(Component.literal(str(b, "about")), tw);
        if (sub.size() > 3) sub = sub.subList(0, 3);
        items.add(new Item("preset", AdminBookScreen.presetTitle(b), sub, str(b, "id"), str(b, "name"),
                14 + sub.size() * 10 + 4));
    }

    private int chipRows(int w) {
        int x = 0, n = 1;
        for (String s : SUGGESTED) {
            int cw = font.width(s) + 10;
            if (x > 0 && x + cw > w) {
                x = 0;
                n++;
            }
            x += cw + 4;
        }
        return n;
    }

    // picking

    /** the row's preset is the one chosen now */
    private boolean chosen(Item i) {
        return i.pick() != null && i.name().equals(draft.preset());
    }

    private void pick(Item i) {
        if (i.pick() == null) return;
        Runnable go = () -> {
            draft.pick(i.pick());
            build();
        };
        if (draft.edited() && !draft.empty()) {
            minecraft.setScreen(new ConfirmScreen(yes -> {
                        if (yes) go.run();
                        minecraft.setScreen(this);
                    }, Component.translatable("admin.jj_giants.creation.replace.title"),
                    Component.translatable("admin.jj_giants.creation.replace.body", i.name())));
            return;
        }
        go.run();
    }

    private void edit() {
        AdminBookScreen.showAt("home", 0, "");
        AdminBookClient.openLocal(draft, this);
    }

    @Override
    public void onClose() {
        AdminBookClient.endLocal();
        minecraft.setScreen(parent);
    }

    // (the picture-taking) the same as clicking

    /** picks a preset by id or saved name on the open picker */
    public static boolean testPick(String id) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof CreationScreen c)) return false;
        for (Item i : c.items)
            if (id.equals(i.pick())) {
                c.pick(i);
                return true;
            }
        return false;
    }

    /** Edit settings… on the open picker */
    public static boolean testEdit() {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof CreationScreen c)) return false;
        c.edit();
        return true;
    }

    // input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (mx < px0 + 14 || mx > px0 + pw - 14 || my < ly0 || my >= ly1) return false;
        int y = ly0 - (int) scroll;
        for (Item i : items) {
            if (my >= y && my < y + i.h()) {
                if (i.kind().equals("preset")) {
                    minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                    pick(i);
                    return true;
                }
                return false;
            }
            y += i.h();
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Mth.clamp(scroll - dy * 20, 0, Math.max(0, contentH - (ly1 - ly0)));
        return true;
    }

    // drawing

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        super.renderBackground(g, mx, my, partial);
        // (Part 2C) the admin book's leather and parchment
        AdminStyle.frame(g, px0, py0, pw, ph);
        AdminStyle.page(g, px0 + AdminStyle.EDGE, py0 + AdminStyle.EDGE, pw - 2 * AdminStyle.EDGE,
                ph - 2 * AdminStyle.EDGE);
        AdminBookScreen.label(g, font, title, px0 + pw / 2, py0);
        AdminStyle.flourish(g, px0 + 14, ly0 - 8, pw - 28);
        g.fill(px0 + 14, ly1 + 1, px0 + pw - 14, ly1 + 2, AdminStyle.RULE);
        int x = px0 + 20, w = pw - 40;
        g.enableScissor(px0 + 12, ly0, px0 + pw - 12, ly1);
        int y = ly0 - (int) scroll;
        for (Item i : items) {
            if (y + i.h() >= ly0 && y < ly1) drawItem(g, i, x, y, w, mx, my);
            y += i.h();
        }
        g.disableScissor();
        // (a scroll bar when it doesn't all fit)
        int view = ly1 - ly0;
        if (contentH > view) {
            int bh = Math.max(12, view * view / contentH);
            int by = ly0 + (int) ((view - bh) * scroll / (contentH - view));
            g.fill(px0 + pw - 14, by, px0 + pw - 12, by + bh, 0xFF8A2A14);
        }
        // the choice now
        MutableComponent now;
        if (draft.label().equals("Classic") && draft.empty())
            now = Component.literal(draft.label()).append(Component.translatable("admin.jj_giants.book.presets.default"));
        else {
            AdminInsight.Danger d = danger();
            now = Component.translatable("admin.jj_giants.creation.chosen", draft.label(), draft.count(), d.level(),
                    d.label());
        }
        g.drawString(font, now, px0 + pw / 2 - font.width(now) / 2, ly1 + 8, KEY, false);
    }

    /** the draft's danger rating as it stands */
    private AdminInsight.Danger danger() {
        AdminTransfer.Plan none = AdminTransfer.plan(draft, new AdminTransfer.Bundle("x", "", "", false,
                java.util.Map.of()));
        return AdminTransfer.dangerBeforeAfter(draft, none)[0];
    }

    private void drawItem(GuiGraphics g, Item i, int x, int y, int w, int mx, int my) {
        switch (i.kind()) {
            case "head" -> {
                int nx = g.drawString(font, i.title(), x, y + 5, AdminStyle.RUBRIC, false);
                if (!i.sub().isEmpty()) g.drawString(font, i.sub().get(0), nx + 6, y + 5, DIM, false);
            }
            case "note" -> g.drawString(font, i.title(), x + 4, y + 2, GREY, false);
            case "chips" -> {
                int cx = x, cy = y + 1;
                for (String s : SUGGESTED) {
                    int cw = font.width(s) + 10;
                    if (cx > x && cx + cw > x + w) {
                        cx = x;
                        cy += 16;
                    }
                    g.fill(cx, cy, cx + cw, cy + 13, 0x14503010);
                    g.renderOutline(cx, cy, cw, 13, 0x50503010);
                    g.drawString(font, s, cx + 5, cy + 3, DIM, false);
                    if (mx >= cx && mx < cx + cw && my >= cy && my < cy + 13 && my >= ly0 && my < ly1)
                        setTooltipForNextRenderPass(font.split(
                                Component.translatable("admin.jj_giants.creation.soon.tip"), 220));
                    cx += cw + 4;
                }
            }
            default -> {
                boolean on = chosen(i), over = mx >= x - 4 && mx < x + w + 4 && my >= y && my < y + i.h() - 2
                        && my >= ly0 && my < ly1;
                g.fill(x - 4, y, x + w + 4, y + i.h() - 2, on ? 0x30E0B050 : over ? 0x30FFF8E0 : 0x18FFF8E0);
                g.renderOutline(x - 4, y, w + 8, i.h() - 2, on ? AdminStyle.RUBRIC : over ? 0xA0705030 : 0x60705030);
                // a round mark: filled when chosen
                int rx = x, ry = y + 4;
                g.renderOutline(rx, ry, 7, 7, on ? GOLD : GREY);
                if (on) g.fill(rx + 2, ry + 2, rx + 5, ry + 5, GOLD);
                g.drawString(font, i.title(), x + 11, y + 4, KEY, false);
                for (int k = 0; k < i.sub().size(); k++)
                    g.drawString(font, i.sub().get(k), x + 11, y + 15 + k * 10, TEXT, false);
            }
        }
    }
}
