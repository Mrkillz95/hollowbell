package net.jj.hollowbell.solid.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.jj.hollowbell.solid.SitHost;
import net.jj.hollowbell.solid.SitPayload;
import net.jj.hollowbell.solid.SitSeat;
import net.jj.hollowbell.solid.SitSettings;
import net.jj.hollowbell.solid.Sitting;
import net.jj.hollowbell.solid.TripleCrouch;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import org.lwjgl.glfw.GLFW;

/**
 * Sitting on him, your side: the triple crouch (three crouch presses within two seconds, standing on him) and the "Sit on
 * giant" key (unbound to start with) ask the server to sit you down where you stand; the key again, or jumping, gets
 * you up. The server checks who may. Both read the keys off the keyboard, as the other giants use the same ones.
 */
public final class SitClient {
    private SitClient() {}

    public static KeyMapping KEY;
    public static final TripleCrouch COUNTER = new TripleCrouch();

    private static boolean shiftWas, keyWas, seatedWas;
    private static int countingOn = -1;

    public static void init(EntityType<SitSeat> seat) {
        EntityRendererRegistry.register(seat, NoopRenderer::new);
        KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.hollowbell.sit_on_giant",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                "key.categories.hollowbell"));
        ClientTickEvents.END_CLIENT_TICK.register(SitClient::tick);
    }

    /** a key as set in Controls, held down now (read off the keyboard or mouse) */
    static boolean down(Minecraft mc, KeyMapping k) {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(k);
        if (key == InputConstants.UNKNOWN) return false;
        long w = mc.getWindow().getWindow();
        return key.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(w, key.getValue()) == GLFW.GLFW_PRESS
                : InputConstants.isKeyDown(w, key.getValue());
    }

    static void tick(Minecraft mc) {
        while (KEY.consumeClick()) {}
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            COUNTER.reset();
            shiftWas = keyWas = seatedWas = false;
            return;
        }
        boolean seated = p.getVehicle() instanceof SitSeat;
        // (the game says "press crouch to get off"; here it's jump)
        if (seated && !seatedWas)
            mc.gui.setOverlayMessage(Component.translatable("message.hollowbell.sit.down"), false);
        seatedWas = seated;
        boolean free = mc.screen == null;
        boolean shift = free && down(mc, mc.options.keyShift);
        boolean key = free && down(mc, KEY);
        if (shift && !shiftWas) crouched(p);
        if (key && !keyWas) {
            if (seated) ClientPlayNetworking.send(new SitPayload(-1, false));
            else if (!p.isPassenger()) {
                SitHost h = Sitting.standingOn(p);
                if (h != null) ClientPlayNetworking.send(new SitPayload(h.solidSelf().getId(), true));
            }
        }
        shiftWas = shift;
        keyWas = key;
    }

    /** one crouch press: counted only standing on him (the same one of him), not riding anything */
    static void crouched(LocalPlayer p) {
        if (!SitSettings.tripleCrouchSit() || p.isPassenger() || p.isSpectator() || p.getAbilities().flying) {
            COUNTER.reset();
            return;
        }
        SitHost h = Sitting.standingOn(p);
        if (h == null) {
            COUNTER.reset();
            countingOn = -1;
            return;
        }
        int id = h.solidSelf().getId();
        if (id != countingOn) COUNTER.reset();
        countingOn = id;
        if (COUNTER.press(Util.getMillis())) ClientPlayNetworking.send(new SitPayload(id, true));
    }
}
