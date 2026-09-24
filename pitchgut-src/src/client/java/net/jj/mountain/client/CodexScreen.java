package net.jj.mountain.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.mountain.MountainConfig;
import net.jj.mountain.entity.MountainAttacks;
import net.jj.mountain.entity.MountainEntity;
import net.jj.mountain.net.CodexPayload;
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
 * The book, open. Three pages: what he is to do, which of his attacks to make him use, and what he is.
 * The attack page keeps its own clock so a line greys out while he is getting his breath back.
 */
public class CodexScreen extends Screen {
    private static final int W = 164, H = 20, GAP = 4;
    /** sixteen attacks will not sit on the ordinary grid, so that page gets shorter lines of its own */
    private static final int AH = 16, AGAP = 2;
    private static int page;                                   // stays put between openings
    public static void showPage(int p) { page = Math.max(0, Math.min(3, p)); }

    // ---- your own safe list, as the server last sent it
    private static java.util.List<String> safeNames = java.util.List.of(), safeIds = java.util.List.of();
    private static boolean safeInForce, safeHasBook;
    private static int safeFrom;                               // the first line shown, for lists past one page
    public static void safeList(net.jj.mountain.net.SafeListPayload p) {
        safeNames = java.util.List.copyOf(p.names());
        safeIds = java.util.List.copyOf(p.ids());
        safeInForce = p.inForce();
        safeHasBook = p.hasBook();
        if (safeFrom >= safeNames.size()) safeFrom = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CodexScreen c && page == 3) c.rebuild();
    }
    // ---- how he is taking it, as the server last told us
    private static float mWind = 1f, mSour;
    private static int mStage, mHuntDays, mEnd = 3;
    public static void mood(net.jj.mountain.net.MoodPayload p) {
        boolean moved = mStage != p.stage() || mEnd != p.endState();
        mWind = p.wind(); mSour = p.sour(); mStage = p.stage(); mHuntDays = p.huntDays();
        mEnd = p.endState();
        Minecraft mc = Minecraft.getInstance();
        // the bars are drawn fresh every frame, so only a change that moves a button is worth rebuilding the
        // page: doing it every second would take the typing boxes out from under your fingers
        if (moved && mc.screen instanceof CodexScreen c) c.rebuild();
    }

    /** leaving a world: his clocks belong to that world, not the next one */
    public static void forgetEverything() {
        java.util.Arrays.fill(used, Long.MIN_VALUE / 4);
        mWind = 1f; mSour = 0f; mStage = 0; mHuntDays = 0; mEnd = 3;
        safeNames = java.util.List.of(); safeIds = java.util.List.of();
        safeFrom = 0;
    }
    public static int endState() { return mEnd; }

    private static final long[] used = new long[MountainAttacks.NAMES.length];
    static { java.util.Arrays.fill(used, Long.MIN_VALUE / 4); }
    /** the same numbers the server keeps, so the pages can grey a line out */
    private static final int ATTACK_COOL = 400, ANY_COOL = 60;

    private @Nullable EditBox boxX, boxZ;
    private boolean asked;
    private final java.util.List<Button> attackLines = new java.util.ArrayList<>();
    private final java.util.List<Integer> attackIds = new java.util.ArrayList<>();
    private static String lastX = "", lastZ = "";

    public CodexScreen() { super(Component.translatable("item.mountain_breathes.mountain_codex")); }

    private @Nullable MountainEntity him() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        MountainEntity best = null;
        double bd = Double.MAX_VALUE;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof MountainEntity m) || m.isDeadOrDying()) continue;
            double d = m.distanceToSqr(mc.player);
            if (d < bd) { bd = d; best = m; }
        }
        return best;
    }

    private long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    private long coolLeft(int attack) {
        long mine = ATTACK_COOL - (now() - used[attack]);
        long any = ANY_COOL - (now() - used[0]);
        return Math.max(0, Math.max(mine, any));
    }

    private void send(CodexPayload p) {
        ClientPlayNetworking.send(p);
        int a = p.action();
        if (a == CodexPayload.ATTACK_MOVE) { used[p.arg()] = now(); used[0] = now(); }
        mWind = Math.max(0f, mWind - net.jj.mountain.net.CodexOrders.windCost(a, p.arg()));
        boolean leaves = a == CodexPayload.RIDE || a == CodexPayload.GO_THERE || a == CodexPayload.ATTACK_THAT
                || a == CodexPayload.BURROW || a == CodexPayload.BREATHE || a == CodexPayload.WHERE
                || a == CodexPayload.SPARE_LOOK;
        if (leaves) onClose(); else rebuild();
    }

    private void rebuild() {
        if (boxX != null) lastX = boxX.getValue();
        if (boxZ != null) lastZ = boxZ.getValue();
        clearWidgets();
        init();
    }

    private int left() { return this.width / 2 - W - GAP / 2; }
    private int top() { return Math.max(70, this.height / 2 - 86); }   // never so high the lines above it are off the screen

    private Button at(int col, int row, Component label, Button.OnPress press) {
        return Button.builder(label, press).bounds(left() + col * (W + GAP), top() + row * (H + GAP), W, H).build();
    }

    /** how many rows of two the attacks take up, whatever he has learned to do by now */
    private static int attackRows() { return MountainAttacks.NAMES.length / 2; }

    private Button atAttack(int col, int row, Component label, Button.OnPress press) {
        return Button.builder(label, press).bounds(left() + col * (W + GAP), top() - 2 + row * (AH + AGAP), W, AH).build();
    }

    private Button line(int col, int row, String key, CodexPayload p) {
        return at(col, row, Component.translatable("codex.mountain_breathes." + key), b -> send(p));
    }

    @Override
    protected void init() {
        MountainEntity m = him();
        // ---- the three page tabs
        int tw = (W * 2 + GAP - 3 * 2) / 4;
        for (int i = 0; i < 4; i++) {
            final int which = i;
            String[] tabs = {"tab_orders", "tab_attacks", "tab_him", "tab_safe"};
            Component name = Component.translatable("codex.mountain_breathes." + tabs[i]);
            Button b = Button.builder(i == page ? name.copy().withStyle(ChatFormatting.YELLOW) : name,
                    x -> { page = which; rebuild(); }).bounds(left() + i * (tw + 2), top() - 24, tw, H).build();
            addRenderableWidget(b);
        }

        if (page == 0) ordersPage(m);
        else if (page == 1) attacksPage();
        else if (page == 2) himPage(m);
        else safePage();

        // the attack page is on the tighter grid, so its close line has to be placed on that grid too
        Component shut = Component.translatable("codex.mountain_breathes.close");
        addRenderableWidget(page == 1 ? atAttack(1, attackRows(), shut, b -> onClose())
                : at(1, 6, shut, b -> onClose()));
        if (!asked) { asked = true; ClientPlayNetworking.send(new CodexPayload(CodexPayload.SAFE_GET)); }
    }

    private void ordersPage(@Nullable MountainEntity m) {
        addRenderableWidget(line(0, 0, "come", new CodexPayload(CodexPayload.COME)));
        addRenderableWidget(line(1, 0, "go_there", new CodexPayload(CodexPayload.GO_THERE)));
        addRenderableWidget(line(0, 1, "attack_that", new CodexPayload(CodexPayload.ATTACK_THAT)));
        addRenderableWidget(line(1, 1, "call_off", new CodexPayload(CodexPayload.CALL_OFF)));
        addRenderableWidget(line(0, 2, m != null && m.staying() ? "let_go" : "stay", new CodexPayload(CodexPayload.STAY)));
        addRenderableWidget(line(1, 2, m != null && m.sleeping() ? "wake" : "sleep", new CodexPayload(CodexPayload.SLEEP)));
        addRenderableWidget(line(0, 3, m == null ? "climb_on" : m.ridden() ? "get_off"
                : m.comingForSomebody() ? "never_mind" : "climb_on", new CodexPayload(CodexPayload.RIDE)));
        addRenderableWidget(line(1, 3, "burrow", new CodexPayload(CodexPayload.BURROW)));
        addRenderableWidget(at(0, 6, Component.translatable("codex.mountain_breathes.where"), b -> send(new CodexPayload(CodexPayload.WHERE))));

        // ---- somewhere in particular
        int bw = (W * 2 + GAP - 96) / 2;
        int y = top() + 4 * (H + GAP) + 4;
        boxX = new EditBox(this.font, left(), y, bw, H, Component.literal("X"));
        boxZ = new EditBox(this.font, left() + bw + GAP, y, bw, H, Component.literal("Z"));
        boxX.setHint(Component.literal("X"));
        boxZ.setHint(Component.literal("Z"));
        boxX.setValue(lastX); boxZ.setValue(lastZ);
        boxX.setFilter(CodexScreen::number); boxZ.setFilter(CodexScreen::number);
        addRenderableWidget(boxX);
        addRenderableWidget(boxZ);
        addRenderableWidget(Button.builder(Component.translatable("codex.mountain_breathes.send"), b -> sendToSpot(false))
                .bounds(left() + 2 * (bw + GAP), y, 92 - GAP, H).build());
        addRenderableWidget(at(0, 5, Component.translatable("codex.mountain_breathes.under_there"), b -> sendToSpot(true)));
        addRenderableWidget(at(1, 5, Component.translatable("codex.mountain_breathes.here"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            // straight into the boxes: going through a rebuild would read the empty boxes back over the top
            String px = String.valueOf((int) mc.player.getX()), pz = String.valueOf((int) mc.player.getZ());
            if (boxX != null) boxX.setValue(px);
            if (boxZ != null) boxZ.setValue(pz);
            lastX = px; lastZ = pz;
        }));
    }

    private static boolean number(String s) {
        return s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,8}");
    }

    private void sendToSpot(boolean under) {
        String xs = boxX == null ? lastX : boxX.getValue(), zs = boxZ == null ? lastZ : boxZ.getValue();
        try {
            double x = Double.parseDouble(xs.isEmpty() ? "0" : xs), z = Double.parseDouble(zs.isEmpty() ? "0" : zs);
            if (xs.isEmpty() || zs.isEmpty()) return;
            send(new CodexPayload(under ? CodexPayload.BURROW_XZ : CodexPayload.GO_TO_XZ, 0, x + 0.5, z + 0.5));
            onClose();
        } catch (NumberFormatException ignored) {}
    }

    private void attacksPage() {
        attackLines.clear(); attackIds.clear();
        for (int i = 1; i < MountainAttacks.NAMES.length; i++) {
            final int which = i;
            int idx = i - 1;
            Button b = atAttack(idx % 2, idx / 2, Component.empty(), x -> send(new CodexPayload(CodexPayload.ATTACK_MOVE, which)));
            float cost = net.jj.mountain.net.CodexOrders.windCost(CodexPayload.ATTACK_MOVE, which);
            Component tip = Component.translatable("codex.mountain_breathes.costs", Math.round(cost * 100f));
            if (net.jj.mountain.net.CodexOrders.takesItBadly(CodexPayload.ATTACK_MOVE, which))
                tip = tip.copy().append(CommonComponents.NEW_LINE)
                        .append(Component.translatable("codex.mountain_breathes.takes_it_badly").withStyle(ChatFormatting.RED));
            b.setTooltip(Tooltip.create(tip));
            addRenderableWidget(b);
            attackLines.add(b); attackIds.add(i);
        }
        tickAttackLines();
    }

    /** the clocks run while you watch them, rather than being whatever they were when the page opened */
    private void tickAttackLines() {
        for (int k = 0; k < attackLines.size(); k++) {
            int which = attackIds.get(k);
            long left = coolLeft(which);
            Component name = Component.translatable("attack.mountain_breathes." + MountainAttacks.NAMES[which]);
            Button b = attackLines.get(k);
            float cost = net.jj.mountain.net.CodexOrders.windCost(CodexPayload.ATTACK_MOVE, which);
            boolean winded = mWind + 1.0E-4f < cost;
            b.setMessage(left > 0
                    ? name.copy().append(Component.literal("  " + (int) Math.ceil(left / 20.0) + "s")).withStyle(ChatFormatting.DARK_GRAY)
                    : winded ? name.copy().append(Component.translatable("codex.mountain_breathes.no_wind")).withStyle(ChatFormatting.DARK_GRAY)
                    : name);
            b.active = left <= 0 && !winded;
        }
    }

    private void himPage(@Nullable MountainEntity m) {
        addRenderableWidget(line(0, 0, "calm", new CodexPayload(CodexPayload.CALM)));
        addRenderableWidget(line(1, 0, "hunting", new CodexPayload(CodexPayload.HUNTER)));
        addRenderableWidget(line(0, 1, "guardian", new CodexPayload(CodexPayload.GUARDIAN)));
        addRenderableWidget(line(1, 1, "breathe", new CodexPayload(CodexPayload.BREATHE)));
        boolean goo = MountainConfig.V.gooTrail, grief = MountainConfig.V.griefing;
        addRenderableWidget(line(0, 2, goo ? "goo_stop" : "goo_start", new CodexPayload(CodexPayload.GOO, goo ? 0 : 1)));
        addRenderableWidget(line(1, 2, grief ? "break_stop" : "break_start", new CodexPayload(CodexPayload.BREAK_BLOCKS, grief ? 0 : 1)));
        addRenderableWidget(line(0, 3, "cough", new CodexPayload(CodexPayload.COUGH)));
        addRenderableWidget(line(1, 3, "forgive", new CodexPayload(CodexPayload.FORGIVE)));
        addRenderableWidget(line(0, 4, "pop_eye", new CodexPayload(CodexPayload.POP_EYE, 1)));
        addRenderableWidget(line(1, 4, "spare_me", new CodexPayload(CodexPayload.SPARE_ME)));
        addRenderableWidget(line(0, 5, "spare_look", new CodexPayload(CodexPayload.SPARE_LOOK)));
        if (mEnd != 1) {                                      // switched off by whoever runs the world: no line at all
            Button b = at(0, 6, Component.translatable("codex.mountain_breathes.end").withStyle(ChatFormatting.DARK_RED),
                    x -> Minecraft.getInstance().setScreen(new DoomScreen(this)));
            b.active = mEnd == 0;
            addRenderableWidget(b);
        }
    }

    /** who you have told him to leave alone. Yours alone, and only worth anything while you have the book. */
    private void safePage() {
        addRenderableWidget(line(0, 0, "spare_me", new CodexPayload(CodexPayload.SPARE_ME)));
        addRenderableWidget(line(1, 0, "spare_look", new CodexPayload(CodexPayload.SPARE_LOOK)));
        int total = Math.min(safeNames.size(), safeIds.size());
        boolean paged = total > 10;
        int fit = paged ? 8 : 10;                              // the last row turns into back and on when there are more
        if (safeFrom >= total) safeFrom = 0;
        for (int i = 0; i < fit; i++) {
            int k = safeFrom + i;
            if (k >= total) break;
            final String id = safeIds.get(k);
            addRenderableWidget(at(i % 2, 1 + i / 2, Component.literal("\u2715  " + safeNames.get(k)),
                    b -> { ClientPlayNetworking.send(new net.jj.mountain.net.SafeDropPayload(id)); rebuild(); }));
        }
        if (paged) {
            int here = safeFrom;
            addRenderableWidget(at(0, 5, Component.translatable("codex.mountain_breathes.list_back"),
                    b -> { safeFrom = Math.max(0, here - 8); rebuild(); }));
            addRenderableWidget(at(1, 5, Component.translatable("codex.mountain_breathes.list_on",
                    Math.max(0, total - here - 8)), b -> { safeFrom = here + 8 >= total ? 0 : here + 8; rebuild(); }));
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        if (page == 1) tickAttackLines();
        renderBackground(g, mx, my, partial);
        super.render(g, mx, my, partial);
        if (page == 1) drawCosts(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, top() - 64, 0xFFE9C9BC);
        Minecraft mc = Minecraft.getInstance();
        MountainEntity m = him();
        Component where;
        if (m == null || mc.player == null) {
            where = Component.translatable("codex.mountain_breathes.far").withStyle(ChatFormatting.GRAY);
        } else {
            int d = (int) Math.sqrt(m.distanceToSqr(mc.player));
            int hpPct = Math.round(100f * m.healthNow() / Math.max(1f, m.healthMax()));
            String doing = m.ridden() ? "ridden" : m.digging() ? "under" : m.sleeping() ? "asleep"
                    : m.knockedDown() ? "down" : m.staying() ? "standing" : "awake";
            where = Component.translatable("codex.mountain_breathes.status", d, hpPct,
                    Component.translatable("mode.mountain_breathes." + m.variant()),
                    Component.translatable("doing.mountain_breathes." + doing)).withStyle(ChatFormatting.GRAY);
        }
        if (mStage > 0) where = where.copy().append(Component.literal("  "))
                .append(Component.translatable("mood.mountain_breathes." + mStage)
                        .withStyle(mStage >= 3 ? ChatFormatting.RED : ChatFormatting.GOLD));
        g.drawCenteredString(this.font, where, this.width / 2, top() - 52, 0xFFBBBBBB);
        int by = top() - 40;
        meter(g, left(), by, W, "wind", mWind, mWind > 0.5f ? 0xFF5FBF6A : mWind > 0.2f ? 0xFFD8A63A : 0xFFB0432A);
        meter(g, left() + W + GAP, by, W, "grudge", mSour, mSour < 0.5f ? 0xFF8A4A3A : 0xFFD03018);
        if (mHuntDays > 0)
            g.drawCenteredString(this.font, Component.translatable("codex.mountain_breathes.hunted", mHuntDays)
                    .withStyle(ChatFormatting.RED), this.width / 2, top() - 76, 0xFFFF5555);
        if (page == 3) {
            Component note = !safeHasBook ? Component.translatable("codex.mountain_breathes.list_nobook").withStyle(ChatFormatting.RED)
                    : safeInForce ? Component.translatable("codex.mountain_breathes.list_in_force", safeNames.size()).withStyle(ChatFormatting.GREEN)
                    : Component.translatable("codex.mountain_breathes.list_off").withStyle(ChatFormatting.RED);
            g.drawString(this.font, note, left() + 2, top() + 6 * (H + GAP) + 6, 0xFFBBBBBB, false);
        }
    }

    /**
     * A hair of a line under each attack: how much of his wind it takes, and whether he has that much in him.
     * Green he can do all day, yellow costs him, red is more wind than he has left.
     */
    private void drawCosts(GuiGraphics g) {
        for (int k = 0; k < attackLines.size(); k++) {
            Button b = attackLines.get(k);
            float cost = net.jj.mountain.net.CodexOrders.windCost(CodexPayload.ATTACK_MOVE, attackIds.get(k));
            if (cost <= 0f) continue;
            int x = b.getX() + 5, w = b.getWidth() - 10, y = b.getY() + b.getHeight() - 3;
            g.fill(x, y, x + w, y + 2, 0xB0101010);
            int fill = Math.max(1, Math.round(w * Math.min(1f, cost)));
            int colour = mWind + 1.0E-4f < cost ? 0xFF7A2E22 : cost >= 0.45f ? 0xFFD8A63A : 0xFF5FBF6A;
            g.fill(x, y, x + fill, y + 2, colour);
        }
    }

    /** one of the two little bars above the pages */
    private void meter(GuiGraphics g, int x, int y, int w, String key, float v, int colour) {
        Component lab = Component.translatable("codex.mountain_breathes." + key);
        g.drawString(this.font, lab, x, y, 0xFF999999, false);
        int lw = this.font.width(lab) + 4;
        int bx = x + lw, bw = Math.max(8, w - lw);
        g.fill(bx, y - 1, bx + bw, y + 9, 0xFF1A1A1A);
        int fill = (int) ((bw - 2) * net.minecraft.util.Mth.clamp(v, 0f, 1f));
        if (fill > 0) g.fill(bx + 1, y, bx + 1 + fill, y + 8, colour);
    }

    @Override public boolean isPauseScreen() { return false; }
}
