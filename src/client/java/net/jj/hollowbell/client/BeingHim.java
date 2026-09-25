package net.jj.hollowbell.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.net.DrivePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Being him, from his crown. The view swings out behind him and the keys drive him about: W pushes him on, A/D
 * and S steer, the number keys set off his moves, and G gets you off. The list of what each key does sits down
 * the left of the screen with its own clock on each line. Copied from the Mountain's so the two work the same.
 */
public final class BeingHim {
    private BeingHim() {}

    /** the attacks you can set off, in the order they are listed (light, medium, heavy), and the key for each */
    private static final int[] SLOT = Moves.ORDER;
    private static final int[] KEY = {
            GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5,
            GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_0,
            GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M,
            GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_U};
    private static final String[] KEY_NAME = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "Z", "X", "C", "V", "B", "N", "M", "R", "H", "J", "K", "U"};
    private static final int LEAVE_KEY = GLFW.GLFW_KEY_G;

    /** the same numbers the server keeps */
    private static final int ANY_COOL = net.jj.hollowbell.net.CodexOrders.ANY_COOL;
    private static final long[] used = new long[Moves.NAMES.length];
    static { java.util.Arrays.fill(used, Long.MIN_VALUE / 4); }

    private static int inId = -1;
    /** held on to rather than looked up each tick */
    private static @Nullable HollowbellEntity inHim;
    private static int heldSlot = -1;
    private static net.minecraft.client.CameraType wasView;

    /** how far the view sits back from him, as a share of the usual: the scroll wheel and [ ] change it */
    private static final int CLOSER_KEY = GLFW.GLFW_KEY_LEFT_BRACKET, FARTHER_KEY = GLFW.GLFW_KEY_RIGHT_BRACKET;
    private static float zoom = 1f;
    private static long zoomShownAt = Long.MIN_VALUE / 4;

    /** the scroll wheel, one notch at a time */
    public static void zoomBy(double notches) {
        setZoom(zoom * (float) Math.pow(1.12, notches > 0 ? -Math.min(4, notches) : Math.max(-4, -notches)));
    }

    private static void setZoom(float z) {
        zoom = net.minecraft.util.Mth.clamp(z, 0.15f, 6f);
        zoomShownAt = now();
    }

    /** what the camera should multiply its usual distance by */
    public static float zoom() { return zoom; }
    private static final boolean[] wasDown = new boolean[KEY.length + 1];

    public static boolean inside() { return inId >= 0; }

    /** the automated test driving him instead of the keyboard */
    private static float @Nullable [] auto;
    public static void autoDrive(float f, float s, float yaw) { auto = new float[]{f, s, yaw}; }

    public static @Nullable HollowbellEntity him() {
        Minecraft mc = Minecraft.getInstance();
        if (inId < 0 || mc.level == null) return null;
        if (inHim != null && !inHim.isRemoved() && inHim.level() == mc.level) return inHim;
        inHim = mc.level.getEntity(inId) instanceof HollowbellEntity m ? m : null;
        return inHim;
    }

    /** the server says you are in him now, or back in yourself */
    public static void set(int id, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (on) {
            inId = id;
            inHim = null;
            if (mc.player != null) heldSlot = mc.player.getInventory().selected;
            HollowbellEntity m = him();
            if (m != null) mc.setCameraEntity(m);
            // out of your own eyes: in close the game hides whatever the camera is sitting in, and what the
            // camera is sitting in is him
            if (wasView == null) wasView = mc.options.getCameraType();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            zoomShownAt = now() + 100;          // say how to move the view for the first few seconds
            java.util.Arrays.fill(wasDown, false);
        } else {
            inId = -1;
            inHim = null;
            if (mc.player != null) {
                mc.setCameraEntity(mc.player);
                if (heldSlot >= 0) mc.player.getInventory().selected = heldSlot;
            }
            if (wasView != null) { mc.options.setCameraType(wasView); wasView = null; }
            heldSlot = -1;
        }
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    private static long coolLeft(int attack) {
        return Math.max(0, Math.max(net.jj.hollowbell.net.CodexOrders.attackCool(attack) - (now() - used[attack]), ANY_COOL - (now() - used[0])));
    }

    public static void tick(Minecraft mc) {
        if (inId < 0) return;
        HollowbellEntity m = him();
        if (m == null || m.isRemoved() || mc.player == null) { set(-1, false); return; }
        // his own eyes: keep the view on him even if something else grabbed the camera
        if (mc.getCameraEntity() != m) mc.setCameraEntity(m);

        long window = mc.getWindow().getWindow();
        boolean leave = InputConstants.isKeyDown(window, LEAVE_KEY);
        if (leave && !wasDown[KEY.length]) {
            ClientPlayNetworking.send(new DrivePayload(DrivePayload.LEAVE, 0, 0, 0, 0));
            set(-1, false);
            wasDown[KEY.length] = true;
            return;
        }
        wasDown[KEY.length] = leave;

        if (InputConstants.isKeyDown(window, CLOSER_KEY)) setZoom(zoom * 0.96f);
        if (InputConstants.isKeyDown(window, FARTHER_KEY)) setZoom(zoom * 1.04f);

        for (int i = 0; i < KEY.length; i++) {
            boolean down = InputConstants.isKeyDown(window, KEY[i]);
            if (down && !wasDown[i]) {
                int which = SLOT[i];
                if (coolLeft(which) <= 0) {
                    ClientPlayNetworking.send(new DrivePayload(DrivePayload.ATTACK, which, 0, 0, 0));
                    used[which] = now(); used[0] = now();
                }
            }
            wasDown[i] = down;
        }

        // your own body stays put: the keys go to him instead
        float f = 0, s = 0, yaw = mc.player.getYRot();
        if (mc.options.keyUp.isDown()) f += 1;
        if (mc.options.keyDown.isDown()) f -= 1;
        if (mc.options.keyLeft.isDown()) s += 1;
        if (mc.options.keyRight.isDown()) s -= 1;
        // jump takes him up, sneak takes him down
        int up = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
        if (auto != null) { f = auto[0]; s = auto[1]; yaw = auto[2]; up = auto.length > 3 ? (int) auto[3] : 0; }
        ClientPlayNetworking.send(new DrivePayload(DrivePayload.DRIVE, up, f, s, yaw));
        mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
    }

    /**
     * The riding screen, top left: his health, his wind and grudge bars, then his moves in two columns (light and
     * medium, then heavy) with a clock on each, and the keys to get off and go up and down. If the window is too
     * short for all of it, it's drawn smaller so none of it is cut off.
     */
    public static void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (inId < 0 || mc.player == null) return;
        HollowbellEntity m = him();
        var font = mc.font;

        // how big it all is, so it can be fitted to the window
        int col1 = 0, col2 = 0, rows1 = 0, rows2 = 0;
        String[] labels = new String[KEY.length];
        for (int i = 0; i < KEY.length; i++) {
            int which = SLOT[i];
            long left = coolLeft(which);
            String label = "[" + KEY_NAME[i] + "] " + Component.translatable("move.hollowbell." + Moves.NAMES[which]).getString();
            if (left > 0) label += "  " + (int) Math.ceil(left / 20.0) + "s";
            labels[i] = label;
            // the width is taken with a clock on, so the columns don't shift about as the clocks come and go
            int w = font.width("[" + KEY_NAME[i] + "] " + Component.translatable("move.hollowbell." + Moves.NAMES[which]).getString() + "  00s");
            if (Moves.tier(which) == Moves.HEAVY) { col2 = Math.max(col2, w); rows2++; } else { col1 = Math.max(col1, w); rows1++; }
        }
        int bars = 112;
        int width = Math.max(bars, col1 + 10 + col2);
        int height = 22 + 26 + 11 + Math.max(rows1 + 1, rows2) * 10 + 4 + 20 + (now() - zoomShownAt < 60 ? 10 : 0);
        // (kept clear of the chat and hotbar at the bottom)
        float fit = Math.min(1f, Math.min((g.guiHeight() - 64f) / height, (g.guiWidth() * 0.45f) / width));

        g.pose().pushPose();
        g.pose().translate(6, 6, 0);
        g.pose().scale(fit, fit, 1f);
        int x = 0, y = 0;
        g.drawString(font, Component.translatable("being.hollowbell.title").withStyle(ChatFormatting.GOLD), x, y, 0xFFFFFF, true);
        if (m != null) {
            int pct = Math.round(100f * m.healthNow() / Math.max(1f, m.healthMax()));
            g.drawString(font, Component.literal(pct + "%  ").append(Component.translatable("being.hollowbell.held"))
                    .withStyle(ChatFormatting.GRAY), x, y + 10, 0xBBBBBB, true);
        }
        y += 22;
        // wind (what the moves cost) and grudge (how much he holds against you), like the book's
        float wind = CodexScreen.wind(), grudge = CodexScreen.grudge();
        meter(g, x, y, bars, "wind", wind, wind > 0.5f ? 0xFF5FBF6A : wind > 0.2f ? 0xFFD8A63A : 0xFFB0432A);
        meter(g, x, y + 12, bars, "grudge", grudge, grudge < 0.5f ? 0xFF8A4A3A : 0xFFD03018);
        y += 26;

        // the moves: light (pale) and medium (orange) down the first column, heavy (red) down the second
        g.drawString(font, Component.translatable("being.hollowbell.moves").withStyle(ChatFormatting.GRAY), x, y, 0xBBBBBB, true);
        y += 11;
        int r1 = 0, r2 = 0, lastTier = -1;
        for (int i = 0; i < KEY.length; i++) {
            int which = SLOT[i];
            int tier = Moves.tier(which);
            long left = coolLeft(which);
            int col = left > 0 ? 0x666666 : tier == Moves.LIGHT ? 0xE9E3D0 : tier == Moves.MEDIUM ? 0xF0B060 : 0xF06050;
            if (tier == Moves.HEAVY) {
                g.drawString(font, labels[i], x + col1 + 10, y + r2 * 10, col, true);
                r2++;
            } else {
                if (lastTier >= 0 && tier != lastTier) r1++;          // a gap between light and medium
                lastTier = tier;
                g.drawString(font, labels[i], x, y + r1 * 10, col, true);
                r1++;
            }
        }
        y += Math.max(r1, r2) * 10 + 4;
        g.drawString(font, Component.translatable("being.hollowbell.updown").withStyle(ChatFormatting.GRAY), x, y, 0xBBBBBB, true);
        g.drawString(font, Component.translatable("being.hollowbell.leave").withStyle(ChatFormatting.YELLOW), x, y + 10, 0xFFFF88, true);
        if (now() - zoomShownAt < 60)
            g.drawString(font, Component.translatable("being.hollowbell.zoom", Math.round(zoom * 100)).withStyle(ChatFormatting.GRAY),
                    x, y + 20, 0xBBBBBB, true);
        g.pose().popPose();
    }

    private static void meter(GuiGraphics g, int x, int y, int w, String key, float v, int colour) {
        var font = Minecraft.getInstance().font;
        Component lab = Component.translatable("codex.hollowbell." + key);
        g.drawString(font, lab, x, y, 0xFFBBBBBB, true);
        int lw = 44;
        int bx = x + lw, bw = Math.max(8, w - lw);
        g.fill(bx, y - 1, bx + bw, y + 9, 0xFF1A1A1A);
        int fill = (int) ((bw - 2) * net.minecraft.util.Mth.clamp(v, 0f, 1f));
        if (fill > 0) g.fill(bx + 1, y, bx + 1 + fill, y + 8, colour);
    }
}
