package net.jj.hollowbell.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.jj.hollowbell.net.MoodPayload;
import net.jj.hollowbell.net.SafeDropPayload;
import net.jj.hollowbell.net.SafeListPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Hollowbell Codex, open. Laid out like the Mountain's and Furrowmaw's: four pages (orders, moves, him, and
 * the safe list), with how far he is, his health, mood and what he's doing above them, and his wind and grudge bars.
 */
public class CodexScreen extends Screen {
    private static final int W = 164, H = 20, GAP = 4;
    private static int page;
    public static void showPage(int p) { page = Math.max(0, Math.min(3, p)); }

    private static java.util.List<String> safeNames = java.util.List.of(), safeIds = java.util.List.of();
    private static boolean safeInForce, safeHasBook;
    private static int safeFrom;

    public static void safeList(SafeListPayload p) {
        safeNames = java.util.List.copyOf(p.names());
        safeIds = java.util.List.copyOf(p.ids());
        safeInForce = p.inForce();
        safeHasBook = p.hasBook();
        if (safeFrom >= safeNames.size()) safeFrom = 0;
        if (Minecraft.getInstance().screen instanceof CodexScreen c && page == 3) c.rebuild();
    }

    private static float mWind = 1f, mSour;
    private static int mStage, mHuntDays;

    public static void mood(MoodPayload p) {
        boolean moved = mStage != p.stage();
        mWind = p.wind(); mSour = p.sour(); mStage = p.stage(); mHuntDays = p.huntDays();
        if (moved && Minecraft.getInstance().screen instanceof CodexScreen c) c.rebuild();
    }

    public static void forgetEverything() {
        java.util.Arrays.fill(used, Long.MIN_VALUE / 4);
        mWind = 1f; mSour = 0f; mStage = 0; mHuntDays = 0;
        safeNames = java.util.List.of(); safeIds = java.util.List.of(); safeFrom = 0;
    }

    private static final long[] used = new long[Moves.NAMES.length];
    static { java.util.Arrays.fill(used, Long.MIN_VALUE / 4); }

    private @Nullable EditBox boxX, boxZ;
    private boolean asked;
    private final java.util.List<Button> moveLines = new java.util.ArrayList<>();
    private final java.util.List<Integer> moveIds = new java.util.ArrayList<>();
    private static String lastX = "", lastZ = "";

    public CodexScreen() { super(Component.translatable("item.hollowbell.hollowbell_codex")); }

    private @Nullable HollowbellEntity him() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        HollowbellEntity best = null; double bd = Double.MAX_VALUE;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof HollowbellEntity m) || m.isDeadOrDying()) continue;
            double d = m.distanceToSqr(mc.player);
            if (d < bd) { bd = d; best = m; }
        }
        return best;
    }

    private long now() { Minecraft mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime(); }

    private long coolLeft(int move) {
        return Math.max(0, Math.max(CodexOrders.attackCool(move) - (now() - used[move]), CodexOrders.ANY_COOL - (now() - used[0])));
    }

    private void send(CodexPayload p) {
        ClientPlayNetworking.send(p);
        int a = p.action();
        if (a == CodexPayload.ATTACK_MOVE) { used[p.arg()] = now(); used[0] = now(); }
        mWind = Math.max(0f, mWind - CodexOrders.windCost(a, p.arg()));
        boolean leaves = a == CodexPayload.RIDE || a == CodexPayload.GO_THERE || a == CodexPayload.ATTACK_THAT || a == CodexPayload.WHERE
                || a == CodexPayload.SPARE_LOOK || a == CodexPayload.ATTACK_MOVE;
        if (leaves) onClose(); else rebuild();
    }

    private void rebuild() {
        if (boxX != null) lastX = boxX.getValue();
        if (boxZ != null) lastZ = boxZ.getValue();
        clearWidgets();
        init();
    }

    private int left() { return this.width / 2 - W - GAP / 2; }
    private int top() { return Math.max(70, this.height / 2 - 86); }

    private Button at(int col, int row, Component label, Button.OnPress press) {
        return Button.builder(label, press).bounds(left() + col * (W + GAP), top() + row * (H + GAP), W, H).build();
    }

    private Button line(int col, int row, String key, CodexPayload p) {
        return at(col, row, Component.translatable("codex.hollowbell." + key), b -> send(p));
    }

    @Override
    protected void init() {
        HollowbellEntity m = him();
        int tw = (W * 2 + GAP - 3 * 2) / 4;
        String[] tabs = {"tab_orders", "tab_moves", "tab_him", "tab_safe"};
        for (int i = 0; i < 4; i++) {
            final int which = i;
            Component name = Component.translatable("codex.hollowbell." + tabs[i]);
            addRenderableWidget(Button.builder(i == page ? name.copy().withStyle(ChatFormatting.YELLOW) : name, x -> { page = which; rebuild(); })
                    .bounds(left() + i * (tw + 2), top() - 24, tw, H).build());
        }
        if (page == 0) ordersPage(m);
        else if (page == 1) movesPage();
        else if (page == 2) himPage();
        else safePage();
        addRenderableWidget(at(1, 6, Component.translatable("codex.hollowbell.close"), b -> onClose()));
        if (!asked) { asked = true; ClientPlayNetworking.send(new CodexPayload(CodexPayload.SAFE_GET)); }
    }

    private void ordersPage(@Nullable HollowbellEntity m) {
        addRenderableWidget(line(0, 0, "come", new CodexPayload(CodexPayload.COME)));
        addRenderableWidget(line(1, 0, "go_there", new CodexPayload(CodexPayload.GO_THERE)));
        addRenderableWidget(line(0, 1, "attack_that", new CodexPayload(CodexPayload.ATTACK_THAT)));
        addRenderableWidget(line(1, 1, "call_off", new CodexPayload(CodexPayload.CALL_OFF)));
        addRenderableWidget(line(0, 2, m != null && m.staying() ? "let_go_of_him" : "stay", new CodexPayload(CodexPayload.STAY)));
        addRenderableWidget(line(1, 2, m != null && m.ridden() ? "get_off" : "ride", new CodexPayload(CodexPayload.RIDE)));
        addRenderableWidget(line(0, 3, "drop_all", new CodexPayload(CodexPayload.LET_GO)));
        addRenderableWidget(at(1, 3, Component.translatable("codex.hollowbell.where"), b -> send(new CodexPayload(CodexPayload.WHERE))));
        int bw = (W * 2 + GAP - 96) / 2;
        int y = top() + 4 * (H + GAP) + 4;
        boxX = new EditBox(this.font, left(), y, bw, H, Component.literal("X"));
        boxZ = new EditBox(this.font, left() + bw + GAP, y, bw, H, Component.literal("Z"));
        boxX.setHint(Component.literal("X")); boxZ.setHint(Component.literal("Z"));
        boxX.setValue(lastX); boxZ.setValue(lastZ);
        boxX.setFilter(CodexScreen::number); boxZ.setFilter(CodexScreen::number);
        addRenderableWidget(boxX); addRenderableWidget(boxZ);
        addRenderableWidget(Button.builder(Component.translatable("codex.hollowbell.send"), b -> sendToSpot())
                .bounds(left() + 2 * (bw + GAP), y, 92 - GAP, H).build());
        addRenderableWidget(at(0, 5, Component.translatable("codex.hollowbell.here"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            String px = String.valueOf((int) mc.player.getX()), pz = String.valueOf((int) mc.player.getZ());
            if (boxX != null) boxX.setValue(px);
            if (boxZ != null) boxZ.setValue(pz);
            lastX = px; lastZ = pz;
        }));
    }

    private static boolean number(String s) { return s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,8}"); }

    private void sendToSpot() {
        String xs = boxX == null ? lastX : boxX.getValue(), zs = boxZ == null ? lastZ : boxZ.getValue();
        if (xs.isEmpty() || zs.isEmpty()) return;
        try {
            send(new CodexPayload(CodexPayload.GO_TO_XZ, 0, Double.parseDouble(xs) + 0.5, Double.parseDouble(zs) + 0.5));
            onClose();
        } catch (NumberFormatException ignored) {}
    }

    /** which strength of move the page shows: light, medium, heavy */
    private static int moveTier = Moves.LIGHT;

    private void movesPage() {
        moveLines.clear(); moveIds.clear();
        // light / medium / heavy along the top of the page
        int tw = (W * 2 + GAP - 2 * 2) / 3;
        for (int k = 0; k < 3; k++) {
            final int tier = k;
            Component name = Component.translatable("codex.hollowbell.tier." + Moves.TIER_NAMES[k]);
            ChatFormatting col = k == Moves.LIGHT ? ChatFormatting.WHITE : k == Moves.MEDIUM ? ChatFormatting.GOLD : ChatFormatting.RED;
            Button tb = Button.builder(k == moveTier ? name.copy().withStyle(col, ChatFormatting.UNDERLINE) : name.copy().withStyle(ChatFormatting.GRAY),
                    x -> { moveTier = tier; rebuild(); }).bounds(left() + k * (tw + 2), top(), tw, H).build();
            tb.setTooltip(Tooltip.create(Component.translatable("codex.hollowbell.tier_tip." + Moves.TIER_NAMES[k])));
            addRenderableWidget(tb);
        }
        int idx = 0;
        for (int which0 : Moves.ORDER) {
            if (Moves.tier(which0) != moveTier) continue;
            final int which = which0;
            Button b = at(idx % 2, 1 + idx / 2, Component.empty(), x -> send(new CodexPayload(CodexPayload.ATTACK_MOVE, which)));
            idx++;
            int i = which;
            float cost = CodexOrders.windCost(CodexPayload.ATTACK_MOVE, which);
            Component tip = Component.translatable("codex.hollowbell.move_tip." + Moves.NAMES[which]).copy().append(CommonComponents.NEW_LINE)
                    .append(Component.translatable("codex.hollowbell.costs", Math.round(cost * 100f)).withStyle(ChatFormatting.GRAY));
            if (CodexOrders.takesItBadly(CodexPayload.ATTACK_MOVE, which))
                tip = tip.copy().append(CommonComponents.NEW_LINE).append(Component.translatable("codex.hollowbell.takes_it_badly").withStyle(ChatFormatting.RED));
            b.setTooltip(Tooltip.create(tip));
            addRenderableWidget(b);
            moveLines.add(b); moveIds.add(i);
        }
        tickMoveLines();
    }

    private void tickMoveLines() {
        for (int k = 0; k < moveLines.size(); k++) {
            int which = moveIds.get(k);
            long left = coolLeft(which);
            Component name = Component.translatable("move.hollowbell." + Moves.NAMES[which]);
            float cost = CodexOrders.windCost(CodexPayload.ATTACK_MOVE, which);
            boolean winded = mWind + 1.0E-4f < cost;
            Button b = moveLines.get(k);
            b.setMessage(left > 0 ? name.copy().append(Component.literal("  " + (int) Math.ceil(left / 20.0) + "s")).withStyle(ChatFormatting.DARK_GRAY)
                    : winded ? name.copy().append(Component.translatable("codex.hollowbell.no_wind")).withStyle(ChatFormatting.DARK_GRAY) : name);
            b.active = left <= 0 && !winded;
        }
    }

    private void himPage() {
        addRenderableWidget(line(0, 0, "calm", new CodexPayload(CodexPayload.CALM)));
        addRenderableWidget(line(1, 0, "hunting", new CodexPayload(CodexPayload.HUNTER)));
        addRenderableWidget(line(0, 1, "guardian", new CodexPayload(CodexPayload.GUARDIAN)));
        addRenderableWidget(line(1, 1, "forgive", new CodexPayload(CodexPayload.FORGIVE)));
        boolean grief = HollowbellConfig.V.griefing, harvest = HollowbellConfig.V.harvest;
        addRenderableWidget(line(0, 2, grief ? "break_stop" : "break_start", new CodexPayload(CodexPayload.BREAK_BLOCKS, grief ? 0 : 1)));
        addRenderableWidget(line(1, 2, harvest ? "harvest_stop" : "harvest_start", new CodexPayload(CodexPayload.HARVEST, harvest ? 0 : 1)));
    }

    private void safePage() {
        addRenderableWidget(line(0, 0, "spare_look", new CodexPayload(CodexPayload.SPARE_LOOK)));
        addRenderableWidget(line(1, 0, "spare_near", new CodexPayload(CodexPayload.SPARE_NEAR)));
        int total = Math.min(safeNames.size(), safeIds.size());
        boolean paged = total > 8;
        int fit = paged ? 6 : 8;
        if (safeFrom >= total) safeFrom = 0;
        for (int i = 0; i < fit; i++) {
            int k = safeFrom + i;
            if (k >= total) break;
            final String id = safeIds.get(k);
            addRenderableWidget(at(i % 2, 1 + i / 2, Component.literal("✕  " + safeNames.get(k)),
                    b -> { ClientPlayNetworking.send(new SafeDropPayload(id)); rebuild(); }));
        }
        if (paged) {
            int here = safeFrom;
            addRenderableWidget(at(0, 4, Component.translatable("codex.hollowbell.list_back"), b -> { safeFrom = Math.max(0, here - 6); rebuild(); }));
            addRenderableWidget(at(1, 4, Component.translatable("codex.hollowbell.list_on", Math.max(0, total - here - 6)),
                    b -> { safeFrom = here + 6 >= total ? 0 : here + 6; rebuild(); }));
        }
        addRenderableWidget(line(0, 5, "spare_me", new CodexPayload(CodexPayload.SPARE_ME)));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        if (page == 1) tickMoveLines();
        renderBackground(g, mx, my, partial);
        super.render(g, mx, my, partial);
        g.drawCenteredString(this.font, this.title, this.width / 2, top() - 64, 0xFF9FE0C4);
        Minecraft mc = Minecraft.getInstance();
        HollowbellEntity m = him();
        Component where;
        if (m == null || mc.player == null) where = Component.translatable("codex.hollowbell.far").withStyle(ChatFormatting.GRAY);
        else {
            int d = (int) Math.sqrt(m.distanceToSqr(mc.player));
            int hpPct = Math.round(100f * m.healthNow() / Math.max(1f, m.healthMax()));
            where = Component.translatable("codex.hollowbell.status", d, hpPct, Component.translatable("mode.hollowbell." + m.variant()),
                    Component.translatable("doing.hollowbell." + CodexOrders.doing(m))).withStyle(ChatFormatting.GRAY);
        }
        if (mStage > 0) where = where.copy().append(Component.literal("  "))
                .append(Component.translatable("mood.hollowbell." + mStage).withStyle(mStage >= 3 ? ChatFormatting.RED : ChatFormatting.GOLD));
        g.drawCenteredString(this.font, where, this.width / 2, top() - 52, 0xFFBBBBBB);
        int by = top() - 40;
        meter(g, left(), by, W, "wind", mWind, mWind > 0.5f ? 0xFF5FBF6A : mWind > 0.2f ? 0xFFD8A63A : 0xFFB0432A);
        meter(g, left() + W + GAP, by, W, "grudge", mSour, mSour < 0.5f ? 0xFF8A4A3A : 0xFFD03018);
        if (mHuntDays > 0)
            g.drawCenteredString(this.font, Component.translatable("codex.hollowbell.hunted", mHuntDays).withStyle(ChatFormatting.RED), this.width / 2, top() - 76, 0xFFFF5555);
        if (m != null && page != 3) {
            // his pods, small, under the pages
            Component bars = Component.translatable(m.sunk() ? "codex.hollowbell.parts_sunk" : "codex.hollowbell.parts", m.podsLeft(), m.rig.pods.length);
            g.drawString(this.font, bars, left() + 2, top() + 6 * (H + GAP) + 6, 0xFF9FE0C4, false);
        }
        if (page == 3) {
            Component note = !safeHasBook ? Component.translatable("codex.hollowbell.list_nobook").withStyle(ChatFormatting.RED)
                    : safeInForce ? Component.translatable("codex.hollowbell.list_in_force", safeNames.size()).withStyle(ChatFormatting.GREEN)
                    : Component.translatable("codex.hollowbell.list_off").withStyle(ChatFormatting.RED);
            g.drawString(this.font, note, left() + 2, top() + 6 * (H + GAP) + 6, 0xFFBBBBBB, false);
        }
    }

    private void meter(GuiGraphics g, int x, int y, int w, String key, float v, int colour) {
        Component lab = Component.translatable("codex.hollowbell." + key);
        g.drawString(this.font, lab, x, y, 0xFF999999, false);
        int lw = this.font.width(lab) + 4;
        int bx = x + lw, bw = Math.max(8, w - lw);
        g.fill(bx, y - 1, bx + bw, y + 9, 0xFF1A1A1A);
        int fill = (int) ((bw - 2) * net.minecraft.util.Mth.clamp(v, 0f, 1f));
        if (fill > 0) g.fill(bx + 1, y, bx + 1 + fill, y + 8, colour);
    }

    @Override public boolean isPauseScreen() { return false; }
}
