package net.jj.hollowbell.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.hollowbell.item.CodexItem;
import net.jj.hollowbell.net.CodexPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * The "Go after what I look at" key (V to start with, changeable in Controls), the same order as the book's button,
 * without opening the book. Riding him, V is one of his moves, so there the middle mouse button does it. Read off the
 * keyboard, as the other giants may use the same key.
 */
public final class LookKey {
    private LookKey() {}

    public static KeyMapping KEY;
    private static boolean wasDown;

    public static void init() {
        KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.hollowbell.attack_look",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "key.categories.hollowbell"));
    }

    private static boolean boundDown(Minecraft mc) {
        InputConstants.Key k = KeyBindingHelper.getBoundKeyOf(KEY);
        if (k == InputConstants.UNKNOWN) return false;
        long w = mc.getWindow().getWindow();
        return k.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(w, k.getValue()) == GLFW.GLFW_PRESS
                : InputConstants.isKeyDown(w, k.getValue());
    }

    public static void tick(Minecraft mc) {
        while (KEY.consumeClick()) {
            // (read below instead, so a key shared with another giant still works)
        }
        boolean now = false;
        if (mc.screen == null && mc.player != null && CodexItem.carriedBy(mc.player)) {
            now = BeingHim.inside()
                    ? GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                            == GLFW.GLFW_PRESS
                    : boundDown(mc);
        }
        if (now && !wasDown) ClientPlayNetworking.send(new CodexPayload(CodexPayload.ATTACK_THAT, LookAim.pick()));
        wasDown = now;
    }
}
