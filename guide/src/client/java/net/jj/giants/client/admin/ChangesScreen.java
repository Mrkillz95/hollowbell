package net.jj.giants.client.admin;

import java.util.ArrayList;
import java.util.List;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminDraft;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * (Part 2C, JJ Nov 2: "a See all changes button that lists every change") The Create World picker's list of every
 * setting the new world's choice changes, giant by giant: server default → what the new world will have.
 */
public class ChangesScreen extends Screen {
    private final Screen parent;
    private final AdminDraft draft;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private final List<Integer> colours = new ArrayList<>();
    private int px0, py0, pw, ph, ly0, ly1;
    private double scroll;

    public ChangesScreen(Screen parent, AdminDraft draft) {
        super(Component.translatable("admin.jj_giants.creation.all.title", draft.label()));
        this.parent = parent;
        this.draft = draft;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 480);
        ph = height - 16;
        px0 = (width - pw) / 2;
        py0 = 8;
        ly0 = py0 + 26;
        ly1 = py0 + ph - 34;
        addRenderableWidget(Button.builder(Component.translatable("admin.jj_giants.book.preview.all.back"),
                        b -> onClose())
                .bounds(width / 2 - 60, py0 + ph - 28, 120, 20)
                .build());
        lines.clear();
        colours.clear();
        int w = pw - 48, n = 0;
        // the all-giants multipliers, then each giant
        List<AdminSetting> all = new ArrayList<>();
        for (AdminSetting s : AdminServer.shared(draft.world())) if (s.set()) all.add(s);
        n += group(Component.translatable("admin.jj_giants.book.all").getString(), all, w);
        for (Giant g : AdminServer.giants()) {
            AdminJson.Report r = draft.report(g);
            if (r == null) continue;
            List<AdminSetting> set = r.settings().stream().filter(AdminSetting::set).toList();
            n += group(r.name(), set, w);
        }
        if (n == 0) add(Component.translatable("admin.jj_giants.creation.all.none"), AdminStyle.INK_SOFT, w);
    }

    private int group(String name, List<AdminSetting> set, int w) {
        if (set.isEmpty()) return 0;
        if (!lines.isEmpty()) add(Component.empty(), AdminStyle.INK, w);
        add(Component.literal(name + " (" + set.size() + ")"), AdminStyle.RUBRIC, w);
        for (AdminSetting s : set) {
            boolean x = s.giant().equals(AdminCatalog.ALL);
            String line = "  " + s.name() + ": " + (x ? "x" : "") + AdminSetting.text(s.def()) + " → " + (x ? "x" : "")
                    + AdminSetting.text(s.value()) + (s.creationOnly() ? "  (" + s.whenLabel() + ")" : "");
            add(Component.literal(line), AdminStyle.INK, w);
        }
        return set.size();
    }

    private void add(Component c, int colour, int w) {
        for (FormattedCharSequence f : font.split(c, w)) {
            lines.add(f);
            colours.add(colour);
        }
        if (c.getString().isEmpty()) {
            lines.add(FormattedCharSequence.EMPTY);
            colours.add(colour);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Mth.clamp(scroll - dy * 20, 0, Math.max(0, lines.size() * 10 - (ly1 - ly0)));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        super.renderBackground(g, mx, my, partial);
        AdminStyle.frame(g, px0, py0, pw, ph);
        AdminStyle.page(g, px0 + AdminStyle.EDGE, py0 + AdminStyle.EDGE, pw - 2 * AdminStyle.EDGE,
                ph - 2 * AdminStyle.EDGE);
        AdminBookScreen.label(g, font, title, px0 + pw / 2, py0);
        AdminStyle.flourish(g, px0 + 14, ly0 - 8, pw - 28);
        g.enableScissor(px0 + 12, ly0, px0 + pw - 12, ly1);
        int y = ly0 - (int) scroll;
        for (int i = 0; i < lines.size(); i++, y += 10)
            if (y + 10 >= ly0 && y < ly1) g.drawString(font, lines.get(i), px0 + 22, y, colours.get(i), false);
        g.disableScissor();
        int view = ly1 - ly0, content = lines.size() * 10;
        if (content > view) {
            int bh = Math.max(12, view * view / content);
            int by = ly0 + (int) ((view - bh) * scroll / (content - view));
            g.fill(px0 + pw - 14, by, px0 + pw - 12, by + bh, 0xFF8A2A14);
        }
    }
}
