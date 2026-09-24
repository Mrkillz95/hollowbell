package net.jj.mountain.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.mountain.entity.MountainAttacks;
import net.jj.mountain.entity.MountainEntity;
import net.jj.mountain.net.DrivePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Being him. Your body stays standing where you left it and the view moves into him: the keys drive him about,
 * the number keys and a few letters set off his attacks, and G puts you back in yourself. The list of what each
 * key does sits down the left of the screen with its own clock on each line.
 */
public final class InsideHim {
    private InsideHim() {}

    /** the attacks you can set off, in the order they are listed, and the key for each */
    private static final int[] SLOT = {1, 2, 3, 12, 7, 5, 4, 10, 6, 13, 8, 9, 11, 14};
    private static final int[] KEY = {
            GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5,
            GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_0,
            GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V};
    private static final String[] KEY_NAME = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "Z", "X", "C", "V"};
    private static final int LEAVE_KEY = GLFW.GLFW_KEY_G;

    /** the same numbers the server keeps */
    private static final int ATTACK_COOL = 400, ANY_COOL = 60;
    private static final long[] used = new long[MountainAttacks.NAMES.length];
    static { java.util.Arrays.fill(used, Long.MIN_VALUE / 4); }

    private static int inId = -1;
    /** held on to rather than looked up each tick */
    private static @Nullable MountainEntity inHim;
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

    public static @Nullable MountainEntity him() {
        Minecraft mc = Minecraft.getInstance();
        if (inId < 0 || mc.level == null) return null;
        if (inHim != null && !inHim.isRemoved() && inHim.level() == mc.level) return inHim;
        inHim = mc.level.getEntity(inId) instanceof MountainEntity m ? m : null;
        return inHim;
    }

    /** the server says you are in him now, or back in yourself */
    public static void set(int id, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (on) {
            inId = id;
            inHim = null;
            if (mc.player != null) heldSlot = mc.player.getInventory().selected;
            MountainEntity m = him();
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
        return Math.max(0, Math.max(ATTACK_COOL - (now() - used[attack]), ANY_COOL - (now() - used[0])));
    }

    public static void tick(Minecraft mc) {
        if (inId < 0) return;
        MountainEntity m = him();
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
        if (auto != null) { f = auto[0]; s = auto[1]; yaw = auto[2]; }
        ClientPlayNetworking.send(new DrivePayload(DrivePayload.DRIVE, 0, f, s, yaw));
        mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
    }

    /** the list of keys down the left of the screen */
    public static void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (inId < 0 || mc.player == null) return;
        MountainEntity m = him();
        int x = 6, y = 24;          // high enough up that all fourteen lines clear the hotbar and the chat
        g.drawString(mc.font, Component.translatable("inside.mountain_breathes.title").withStyle(ChatFormatting.GOLD), x, y, 0xFFFFFF, true);
        if (m != null) {
            int pct = Math.round(100f * m.healthNow() / Math.max(1f, m.healthMax()));
            g.drawString(mc.font, Component.literal(pct + "%  ").append(Component.translatable("inside.mountain_breathes.held"))
                    .withStyle(ChatFormatting.GRAY), x, y + 10, 0xBBBBBB, true);
        }
        y += 24;
        for (int i = 0; i < KEY.length; i++) {
            int which = SLOT[i];
            long left = coolLeft(which);
            String name = Component.translatable("attack.mountain_breathes." + MountainAttacks.NAMES[which]).getString();
            String label = "[" + KEY_NAME[i] + "] " + name;
            int col = left > 0 ? 0x666666 : 0xE9C9BC;
            if (left > 0) label += "  " + (int) Math.ceil(left / 20.0) + "s";
            g.drawString(mc.font, label, x, y + i * 11, col, true);
        }
        g.drawString(mc.font, Component.translatable("inside.mountain_breathes.leave").withStyle(ChatFormatting.YELLOW),
                x, y + KEY.length * 11 + 6, 0xFFFF88, true);
        if (now() - zoomShownAt < 60)
            g.drawString(mc.font, Component.translatable("inside.mountain_breathes.zoom", Math.round(zoom * 100)).withStyle(ChatFormatting.GRAY),
                    x, y + KEY.length * 11 + 17, 0xBBBBBB, true);
    }
}
