package net.jj.giants.client.players;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * My Giant's pick screen (ADMIN_BRIDGE.md 20.7): every giant you can pick, with his picture, name, role, a line on
 * him, how dangerous he is in this world, and what picking him would mean now (someone has his book, the world is at
 * its limit). Pick one, then "Pick him"; "Later" closes it (the Guide's This world page opens it again).
 */
public class PickScreen extends Screen {
    private static final int GOLD = 0xFFE0B050, TITLE = 0xFFE9C9BC, TEXT = 0xFFD8D8D8, GREY = 0xFF9A9A9A,
            PANEL = 0xF0141210, EDGE = 0xFF6B5A44, NOTE = 0xFFFFC060;
    /** danger 1..5: green to red */
    static final int[] DANGER = {0xFF60C060, 0xFFA0C050, 0xFFE0C040, 0xFFE08030, 0xFFE04040};

    private final @Nullable Screen parent;
    private final List<JsonObject> giants = new ArrayList<>();
    private final boolean picked;
    private int chosen = -1;
    private double scroll;
    private int contentH;
    private Button pickButton;

    // where things go
    private int px0, py0, pw, ph, cols, cardW, cardH, gx, gy, gh;

    /** (the picture-taking) start with this one chosen */
    public static int startChosen = -1;

    public PickScreen(@Nullable Screen parent, JsonObject list) {
        super(Component.translatable("players.jj_giants.pick.title"));
        this.parent = parent instanceof PickScreen ps ? ps.parent : parent;
        if (list.get("giants") != null && list.get("giants").isJsonArray())
            for (JsonElement e : list.getAsJsonArray("giants")) if (e.isJsonObject()) giants.add(e.getAsJsonObject());
        picked = list.has("picked") && list.get("picked").getAsBoolean();
        if (startChosen >= 0 && startChosen < giants.size()) chosen = startChosen;
        startChosen = -1;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 470);
        ph = Math.min(height - 16, 320);
        px0 = (width - pw) / 2;
        py0 = (height - ph) / 2;
        cols = pw >= 380 ? 2 : 1;
        cardW = (pw - 16 - (cols - 1) * 6) / cols;
        // (tall enough for the longest card's words at this width: nothing cut off at any GUI scale)
        int most = 0;
        for (JsonObject o : giants) most = Math.max(most, lineCount(o, cardW - 66));
        cardH = Math.max(62, 27 + most * 9 + 3);
        gx = px0 + 8;
        gy = py0 + 40;
        gh = py0 + ph - 32 - gy;
        int rows = (giants.size() + cols - 1) / cols;
        contentH = rows * (cardH + 6);
        Component later = Component.translatable("players.jj_giants.pick.later");
        int lw = font.width(later) + 16;
        addRenderableWidget(Button.builder(later, b -> onClose()).bounds(px0 + 8, py0 + ph - 26, lw, 20).build())
                .setTooltip(Tooltip.create(Component.translatable("players.jj_giants.pick.later.tip")));
        pickButton = addRenderableWidget(Button.builder(Component.empty(), b -> choose())
                .bounds(px0 + pw - 8 - 160, py0 + ph - 26, 160, 20).build());
        updatePick();
    }

    private int lineCount(JsonObject o, int tw) {
        String lineKey = "guide.jj_giants." + str(o, "key") + ".pick";
        int n = Language.getInstance().has(lineKey) ? font.split(Component.translatable(lineKey), tw).size() : 0;
        String note = str(o, "note");
        if (!note.isEmpty()) n += font.split(Component.literal(note), tw).size();
        return n;
    }

    private void updatePick() {
        boolean ok = chosen >= 0 && !picked;
        pickButton.active = ok;
        pickButton.setMessage(ok ? Component.translatable("players.jj_giants.pick.go", str(giants.get(chosen), "name"))
                : Component.translatable(picked ? "players.jj_giants.pick.done" : "players.jj_giants.pick.choose"));
    }

    private void choose() {
        if (chosen < 0 || picked) return;
        PlayersClient.ask("choose", "giant", str(giants.get(chosen), "key"));
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int cardX(int i) {
        return gx + (i % cols) * (cardW + 6);
    }

    private int cardY(int i) {
        return gy + (i / cols) * (cardH + 6) - (int) scroll;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        super.renderBackground(g, mx, my, partial);
        g.fill(px0, py0, px0 + pw, py0 + ph, PANEL);
        g.renderOutline(px0, py0, pw, ph, EDGE);
        g.drawCenteredString(font, title, px0 + pw / 2, py0 + 7, TITLE);
        Component sub = Component.translatable(picked ? "players.jj_giants.pick.already"
                : "players.jj_giants.pick.sub");
        List<FormattedCharSequence> s = font.split(sub, pw - 20);
        for (int i = 0; i < s.size() && i < 2; i++)
            g.drawCenteredString(font, s.get(i), px0 + pw / 2, py0 + 19 + i * 10, GREY);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.enableScissor(gx, gy, gx + pw - 16, gy + gh);
        for (int i = 0; i < giants.size(); i++) {
            int x = cardX(i), y = cardY(i);
            if (y + cardH < gy || y > gy + gh) continue;
            card(g, giants.get(i), x, y, i == chosen, mx >= x && mx < x + cardW && my >= y && my < y + cardH
                    && my >= gy && my < gy + gh);
        }
        g.disableScissor();
        if (giants.isEmpty())
            g.drawCenteredString(font, Component.translatable("players.jj_giants.pick.none"), px0 + pw / 2,
                    gy + 20, GREY);
        if (contentH > gh) {
            int bx = px0 + pw - 6;
            g.fill(bx, gy, bx + 3, gy + gh, 0x40000000);
            int th = Math.max(12, gh * gh / contentH);
            int ty = gy + (int) ((gh - th) * (scroll / (contentH - gh)));
            g.fill(bx, ty, bx + 3, ty + th, 0xFFA09080);
        }
    }

    private void card(GuiGraphics g, JsonObject o, int x, int y, boolean sel, boolean over) {
        g.fill(x, y, x + cardW, y + cardH, sel ? 0xFF3A3020 : over ? 0x40FFFFFF : 0x30000000);
        g.renderOutline(x, y, cardW, cardH, sel ? TITLE : EDGE);
        String key = str(o, "key");
        g.fill(x + 4, y + 4, x + 4 + 52, y + 4 + 52, EDGE);
        g.blit(ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "textures/gui/giant/" + key + ".png"),
                x + 6, y + 6, 48, 48, 0, 0, 64, 64, 64, 64);
        int tx = x + 62, tw = cardW - 66;
        g.drawString(font, str(o, "name"), tx, y + 4, TITLE, false);
        String role = "guide.jj_giants." + key + ".role";
        if (Language.getInstance().has(role))
            g.drawString(font, Component.translatable(role), tx + font.width(str(o, "name")) + 6, y + 4, GOLD, false);
        // danger: five pips and the word
        int level = o.has("danger") ? Mth.clamp(o.get("danger").getAsInt(), 1, 5) : 3;
        int dx = tx;
        for (int i = 0; i < 5; i++) {
            g.fill(dx + i * 7, y + 15, dx + i * 7 + 5, y + 20, i < level ? DANGER[level - 1] : 0xFF3A3A3A);
        }
        g.drawString(font, Component.translatable("players.jj_giants.pick.danger", str(o, "label")), dx + 38, y + 14,
                DANGER[level - 1], false);
        String lineKey = "guide.jj_giants." + key + ".pick";
        Component line = Language.getInstance().has(lineKey) ? Component.translatable(lineKey) : Component.empty();
        List<FormattedCharSequence> ls = font.split(line, tw);
        String note = str(o, "note");
        for (int i = 0; i < ls.size(); i++) g.drawString(font, ls.get(i), tx, y + 25 + i * 9, TEXT, false);
        if (!note.isEmpty()) {
            List<FormattedCharSequence> ns = font.split(Component.literal(note), tw);
            for (int i = 0; i < ns.size(); i++)
                g.drawString(font, ns.get(i), tx, y + 25 + (ls.size() + i) * 9, NOTE, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && my >= gy && my < gy + gh && !picked)
            for (int i = 0; i < giants.size(); i++) {
                int x = cardX(i), y = cardY(i);
                if (mx >= x && mx < x + cardW && my >= y && my < y + cardH) {
                    if (chosen == i && minecraft != null && hasShiftDown()) choose();
                    chosen = i;
                    updatePick();
                    return true;
                }
            }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Mth.clamp(scroll - sy * 20, 0, Math.max(0, contentH - gh));
        return true;
    }
}
