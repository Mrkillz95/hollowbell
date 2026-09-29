package net.jj.hollowbell.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The Hollowbell Codex, open. The same frame as the other giants' books: the title and a close button, a status
 * line, his health and pods, his wind and grudge, and four pages (orders, moves, him, and the safe list).
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
    /** his wind and how much he holds against you, as last heard (for the riding screen too) */
    public static float wind() { return mWind; }
    public static float grudge() { return mSour; }
    private static int mStage, mHuntDays, mFlags;

    public static void mood(MoodPayload p) {
        boolean moved = mStage != p.stage() || mFlags != p.flags();
        mWind = p.wind(); mSour = p.sour(); mStage = p.stage(); mHuntDays = p.huntDays(); mFlags = p.flags();
        if (moved && Minecraft.getInstance().screen instanceof CodexScreen c) c.rebuild();
    }

    /** the server's own switches, as it last said (never this game's settings file: on a server they differ) */
    private static boolean flag(int f) { return (mFlags & f) != 0; }

    public static void forgetEverything() {
        java.util.Arrays.fill(used, Long.MIN_VALUE / 4);
        mWind = 1f; mSour = 0f; mStage = 0; mHuntDays = 0; mFlags = 0;
        safeNames = java.util.List.of(); safeIds = java.util.List.of(); safeFrom = 0;
    }

    private static final long[] used = new long[Moves.NAMES.length];
    static { java.util.Arrays.fill(used, Long.MIN_VALUE / 4); }

    private @Nullable EditBox boxX, boxZ, boxR;
    private final java.util.List<Button> moveLines = new java.util.ArrayList<>();
    private final java.util.List<Integer> moveIds = new java.util.ArrayList<>();
    private static String lastX = "", lastZ = "", lastR = "200";
    private int sendLabel = -1, areaLabel = -1;

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
        // the orders that need you to look at something close the book; everything else leaves it open
        boolean leaves = a == CodexPayload.RIDE || a == CodexPayload.GO_THERE || a == CodexPayload.ATTACK_THAT || a == CodexPayload.WHERE
                || a == CodexPayload.SPARE_LOOK || a == CodexPayload.SPARE_KIND;
        if (leaves) onClose(); else rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private int left() { return this.width / 2 - W - GAP / 2; }
    private int top() { return Math.max(92, this.height / 2 - 80); }
    /** how far apart the rows are: a little tighter on a short screen, so the last row still fits */
    private int pitch() { return Mth.clamp((this.height - top() - 22) / 7, 21, 24); }

    private Button at(int col, int row, Component label, Button.OnPress press) {
        return Button.builder(label, press).bounds(left() + col * (W + GAP), top() + row * pitch(), W, H).build();
    }

    /** an order button, with what it does and what it costs him in its tooltip */
    private Button line(int col, int row, String key, String tip, CodexPayload p) {
        Button b = at(col, row, Component.translatable("codex.hollowbell." + key), x -> send(p));
        b.setTooltip(Tooltip.create(orderTip(tip, p.action(), p.arg())));
        return b;
    }

    private static Component orderTip(String key, int action, int arg) {
        Component t = Component.translatable("codex.hollowbell.order_tip." + key);
        float cost = CodexOrders.windCost(action, arg);
        if (cost > 0f) t = t.copy().append(CommonComponents.NEW_LINE)
                .append(Component.translatable("codex.hollowbell.costs", Math.round(cost * 100f)).withStyle(ChatFormatting.GRAY));
        return t;
    }

    @Override
    protected void init() {
        HollowbellEntity m = him();
        sendLabel = -1; areaLabel = -1;
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
        else if (page == 2) himPage(m);
        else safePage();
        // close: a small ✕ at the top right (Escape works too)
        Button close = Button.builder(Component.literal("✕"), b -> onClose()).bounds(left() + W * 2 + GAP - 20, top() - 90, 20, 20).build();
        close.setTooltip(Tooltip.create(Component.translatable("codex.hollowbell.close")));
        addRenderableWidget(close);
    }

    @Override
    public void added() {
        super.added();
        // the safe list and the server's switches, fresh each time the book opens
        ClientPlayNetworking.send(new CodexPayload(CodexPayload.SAFE_GET));
    }

    private void ordersPage(@Nullable HollowbellEntity m) {
        addRenderableWidget(line(0, 0, "come", "come", new CodexPayload(CodexPayload.COME)));
        addRenderableWidget(line(1, 0, "go_there", "go_there", new CodexPayload(CodexPayload.GO_THERE)));
        addRenderableWidget(line(0, 1, "attack_that", "attack_that", new CodexPayload(CodexPayload.ATTACK_THAT)));
        addRenderableWidget(line(1, 1, "call_off", "call_off", new CodexPayload(CodexPayload.CALL_OFF)));
        addRenderableWidget(line(0, 2, m != null && m.staying() ? "let_go_of_him" : "stay", "stay", new CodexPayload(CodexPayload.STAY)));
        addRenderableWidget(line(1, 2, "drop_all", "drop_all", new CodexPayload(CodexPayload.LET_GO)));
        addRenderableWidget(line(0, 3, m != null && m.ridden() ? "get_off" : "ride", "ride", new CodexPayload(CodexPayload.RIDE)));
        addRenderableWidget(line(1, 3, "where", "where", new CodexPayload(CodexPayload.WHERE)));
        // send him to a spot: type an X and a Z (or fill them with where you're standing)
        int bw = 90;
        int y = top() + 5 * pitch();
        sendLabel = y - 11;
        boxX = new EditBox(this.font, left(), y, bw, H, Component.literal("X"));
        boxZ = new EditBox(this.font, left() + bw + GAP, y, bw, H, Component.literal("Z"));
        boxX.setHint(Component.literal("X")); boxZ.setHint(Component.literal("Z"));
        boxX.setValue(lastX); boxZ.setValue(lastZ);
        boxX.setFilter(CodexScreen::number); boxZ.setFilter(CodexScreen::number);
        boxX.setResponder(v -> lastX = v); boxZ.setResponder(v -> lastZ = v);
        addRenderableWidget(boxX); addRenderableWidget(boxZ);
        Button here = Button.builder(Component.translatable("codex.hollowbell.here"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            lastX = String.valueOf(Mth.floor(mc.player.getX()));
            lastZ = String.valueOf(Mth.floor(mc.player.getZ()));
            if (boxX != null) boxX.setValue(lastX);
            if (boxZ != null) boxZ.setValue(lastZ);
        }).bounds(left() + 2 * (bw + GAP), y, 68, H).build();
        here.setTooltip(Tooltip.create(Component.translatable("codex.hollowbell.order_tip.here")));
        addRenderableWidget(here);
        int sx = left() + 2 * (bw + GAP) + 72;
        Button go = Button.builder(Component.translatable("codex.hollowbell.send"), b -> sendToSpot())
                .bounds(sx, y, left() + W * 2 + GAP - sx, H).build();
        go.setTooltip(Tooltip.create(orderTip("send", CodexPayload.GO_TO_XZ, 0)));
        addRenderableWidget(go);
    }

    private static boolean number(String s) { return s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,8}"); }

    private void sendToSpot() {
        if (lastX.isEmpty() || lastZ.isEmpty() || lastX.equals("-") || lastZ.equals("-")) return;
        try {
            send(new CodexPayload(CodexPayload.GO_TO_XZ, 0, Double.parseDouble(lastX) + 0.5, Double.parseDouble(lastZ) + 0.5));
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
            float cost = CodexOrders.windCost(CodexPayload.ATTACK_MOVE, which);
            Component tip = Component.translatable("codex.hollowbell.move_tip." + Moves.NAMES[which]).copy().append(CommonComponents.NEW_LINE)
                    .append(Component.translatable("codex.hollowbell.costs", Math.round(cost * 100f)).withStyle(ChatFormatting.GRAY));
            if (CodexOrders.takesItBadly(CodexPayload.ATTACK_MOVE, which))
                tip = tip.copy().append(CommonComponents.NEW_LINE).append(Component.translatable("codex.hollowbell.takes_it_badly").withStyle(ChatFormatting.RED));
            b.setTooltip(Tooltip.create(tip));
            addRenderableWidget(b);
            moveLines.add(b); moveIds.add(which);
        }
        tickMoveLines();
    }

    /** a move line goes dark (and can't be pressed) when: he's out of reach, cooling down, busy, worn out, out of wind */
    private void tickMoveLines() {
        HollowbellEntity m = him();
        boolean have = m != null;
        for (int k = 0; k < moveLines.size(); k++) {
            int which = moveIds.get(k);
            long left = coolLeft(which);
            Component name = Component.translatable("move.hollowbell." + Moves.NAMES[which]);
            float cost = CodexOrders.windCost(CodexPayload.ATTACK_MOVE, which);
            Component dim = null;
            if (!have) dim = name;
            else if (left > 0) dim = Component.literal(name.getString() + "  " + (int) Math.ceil(left / 20.0) + "s");
            else if (m.moveNow() != Moves.NONE) dim = name.copy().append(Component.translatable("codex.hollowbell.busy"));
            else if (m.tired()) dim = name.copy().append(Component.translatable("codex.hollowbell.worn_out"));
            else if (mWind + 1.0E-4f < cost) dim = name.copy().append(Component.translatable("codex.hollowbell.no_wind"));
            Button b = moveLines.get(k);
            b.setMessage(dim == null ? name : dim.copy().withStyle(ChatFormatting.DARK_GRAY));
            b.active = dim == null;
        }
    }

    private void himPage(@Nullable HollowbellEntity m) {
        addRenderableWidget(line(0, 0, "calm", "calm", new CodexPayload(CodexPayload.CALM)));
        addRenderableWidget(line(1, 0, "hunting", "hunting", new CodexPayload(CodexPayload.HUNTER)));
        addRenderableWidget(line(0, 1, "guardian", "guardian", new CodexPayload(CodexPayload.GUARDIAN)));
        addRenderableWidget(line(1, 1, "forgive", "forgive", new CodexPayload(CodexPayload.FORGIVE)));
        boolean grief = flag(MoodPayload.GRIEF), harvest = flag(MoodPayload.HARVEST);
        addRenderableWidget(line(0, 2, grief ? "break_stop" : "break_start", "blocks", new CodexPayload(CodexPayload.BREAK_BLOCKS, grief ? 0 : 1)));
        addRenderableWidget(line(1, 2, harvest ? "harvest_stop" : "harvest_start", "harvest", new CodexPayload(CodexPayload.HARVEST, harvest ? 0 : 1)));
        boolean asleep = flag(MoodPayload.ASLEEP);
        addRenderableWidget(line(0, 3, asleep ? "wake" : "sleep", asleep ? "wake" : "sleep", new CodexPayload(CodexPayload.SLEEP)));
        // keep him to an area: how far, then round where you stand (or let him roam again)
        int y = top() + 5 * pitch();
        areaLabel = y - 11;
        boxR = new EditBox(this.font, left(), y, 60, H, Component.literal("R"));
        boxR.setValue(lastR);
        boxR.setFilter(s -> s.isEmpty() || s.matches("\\d{0,6}"));
        boxR.setResponder(v -> lastR = v);
        boxR.setTooltip(Tooltip.create(Component.translatable("codex.hollowbell.order_tip.area_blocks")));
        addRenderableWidget(boxR);
        Button keep = Button.builder(Component.translatable("codex.hollowbell.keep_here"), b -> {
            int r = 200;
            try { if (!lastR.isEmpty()) r = Integer.parseInt(lastR); } catch (NumberFormatException ignored) {}
            send(new CodexPayload(CodexPayload.BIND_HERE, Math.max(32, Math.min(100000, r))));
        }).bounds(left() + 64, y, 132, H).build();
        keep.setTooltip(Tooltip.create(orderTip("keep_here", CodexPayload.BIND_HERE, 200)));
        addRenderableWidget(keep);
        Button roam = Button.builder(Component.translatable("codex.hollowbell.let_roam"), b -> send(new CodexPayload(CodexPayload.FREE_ROAM)))
                .bounds(left() + 200, y, 132, H).build();
        roam.setTooltip(Tooltip.create(orderTip("let_roam", CodexPayload.FREE_ROAM, 0)));
        addRenderableWidget(roam);
    }

    private void safePage() {
        addRenderableWidget(line(0, 0, "spare_look", "spare_look", new CodexPayload(CodexPayload.SPARE_LOOK)));
        addRenderableWidget(line(1, 0, "spare_kind", "spare_kind", new CodexPayload(CodexPayload.SPARE_KIND)));
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
        addRenderableWidget(line(0, 5, "spare_me", "spare_me", new CodexPayload(CodexPayload.SPARE_ME)));
        addRenderableWidget(line(1, 5, "spare_near", "spare_near", new CodexPayload(CodexPayload.SPARE_NEAR)));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        if (page == 1) tickMoveLines();
        renderBackground(g, mx, my, partial);
        super.render(g, mx, my, partial);
        g.drawCenteredString(this.font, this.title, this.width / 2, top() - 88, 0xFFE9C9BC);
        Minecraft mc = Minecraft.getInstance();
        HollowbellEntity m = him();
        Component where;
        if (m == null || mc.player == null) where = Component.translatable("codex.hollowbell.far").withStyle(ChatFormatting.GRAY);
        else {
            int d = (int) Math.sqrt(m.distanceToSqr(mc.player));
            int hpPct = Math.round(100f * m.healthNow() / Math.max(1f, m.healthMax()));
            where = Component.translatable("codex.hollowbell.status", d, hpPct, Component.translatable("mode.hollowbell." + m.variant()),
                    Component.translatable("doing.hollowbell." + CodexOrders.doing(m))).withStyle(ChatFormatting.GRAY);
            if (mStage > 0) where = where.copy().append(Component.literal("  "))
                    .append(Component.translatable("mood.hollowbell." + mStage).withStyle(mStage >= 3 ? ChatFormatting.RED : ChatFormatting.GOLD));
        }
        g.drawCenteredString(this.font, where, this.width / 2, top() - 76, 0xFFBBBBBB);
        if (m != null) {
            int by = top() - 62;
            float hp = m.healthNow() / Math.max(1f, m.healthMax());
            meter(g, left(), by, W, Component.translatable("codex.hollowbell.health"), hp, 0xFFB0432A);
            meter(g, left() + W + GAP, by, W, Component.translatable("codex.hollowbell.pods"), m.podsLeft() / (float) Math.max(1, m.rig.pods.length), 0xFFD8A63A);
            meter(g, left(), by + 12, W, Component.translatable("codex.hollowbell.wind"), mWind, mWind > 0.5f ? 0xFF5FBF6A : mWind > 0.2f ? 0xFFD8A63A : 0xFFB0432A);
            meter(g, left() + W + GAP, by + 12, W, Component.translatable("codex.hollowbell.grudge"), mSour, mSour < 0.5f ? 0xFF8A4A3A : 0xFFD03018);
        }
        if (mHuntDays > 0)
            g.drawCenteredString(this.font, Component.translatable("codex.hollowbell.hunted", mHuntDays).withStyle(ChatFormatting.RED), this.width / 2, top() - 36, 0xFFFF5555);
        if (page == 0 && sendLabel >= 0)
            g.drawString(this.font, Component.translatable("codex.hollowbell.send_label").withStyle(ChatFormatting.GRAY), left() + 2, sendLabel, 0xFFBBBBBB, false);
        if (page == 2 && areaLabel >= 0)
            g.drawString(this.font, Component.translatable("codex.hollowbell.area_label").withStyle(ChatFormatting.GRAY), left() + 2, areaLabel, 0xFFBBBBBB, false);
        if (page == 3) {
            Component note = !safeHasBook ? Component.translatable("codex.hollowbell.list_nobook").withStyle(ChatFormatting.RED)
                    : safeInForce ? Component.translatable("codex.hollowbell.list_in_force", safeNames.size()).withStyle(ChatFormatting.GREEN)
                    : Component.translatable("codex.hollowbell.list_off").withStyle(ChatFormatting.RED);
            int ny = top() + 6 * pitch() + 4;
            g.drawString(this.font, note, left() + 2, ny, 0xFFBBBBBB, false);
            g.drawString(this.font, Component.translatable("codex.hollowbell.list_pets").withStyle(ChatFormatting.DARK_GRAY), left() + 2, ny + 11, 0xFF777777, false);
        }
    }

    private void meter(GuiGraphics g, int x, int y, int w, Component lab, float v, int colour) {
        g.drawString(this.font, lab, x, y, 0xFF999999, false);
        int lw = this.font.width(lab) + 4;
        int bx = x + lw, bw = Math.max(8, w - lw);
        g.fill(bx, y - 1, bx + bw, y + 9, 0xFF1A1A1A);
        int fill = (int) ((bw - 2) * Mth.clamp(v, 0f, 1f));
        if (fill > 0) g.fill(bx + 1, y, bx + 1 + fill, y + 8, colour);
    }

    @Override public boolean isPauseScreen() { return false; }
}
