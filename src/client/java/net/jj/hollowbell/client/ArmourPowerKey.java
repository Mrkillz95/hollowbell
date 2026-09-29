package net.jj.hollowbell.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.item.BellArmorItem;
import net.jj.hollowbell.net.ArmourPowerPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The "Armour power" key (R to start with, changeable in Controls) and the little chestplate by the hotbar that
 * shows the wait. The key is read straight off the keyboard, and only asks when you're wearing the bell glass set
 * (the other giants' sets use R too). The server checks the set and the wait; this only asks.
 */
public final class ArmourPowerKey {
    private ArmourPowerKey() {}

    public static KeyMapping KEY;

    public static void init() {
        KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hollowbell.armour_power", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
                "key.categories.hollowbell"));
    }

    private static boolean wasDown;

    /** the key's own state, read off the keyboard: a key shared by two mods only reaches one of them the normal way */
    private static boolean down(Minecraft mc) {
        InputConstants.Key k = KeyBindingHelper.getBoundKeyOf(KEY);
        if (k == InputConstants.UNKNOWN) return false;
        long w = mc.getWindow().getWindow();
        return k.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(w, k.getValue()) == GLFW.GLFW_PRESS
                : InputConstants.isKeyDown(w, k.getValue());
    }

    public static void tick(Minecraft mc) {
        while (KEY.consumeClick()) {
            // (read below instead, so a key shared with another giant's armour still works)
        }
        boolean now = mc.screen == null && mc.player != null && down(mc);
        // only when wearing this set: one set can be worn at a time, so only one mod acts on the key
        if (now && !wasDown && mc.player != null && !mc.player.isPassenger() && !BeingHim.inside() && BellArmorItem.fullSet(mc.player))
            ClientPlayNetworking.send(new ArmourPowerPayload());
        wasDown = now;
        motes(mc);
    }

    /** a few pale motes drift up off anyone in the full set, now and then */
    private static void motes(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        var r = mc.level.random;
        for (var p : mc.level.players()) {
            if (p.isInvisible() || p.distanceToSqr(mc.player) > 48 * 48 || !BellArmorItem.fullSet(p)) continue;
            if (r.nextInt(6) != 0) continue;
            double a = r.nextDouble() * Math.PI * 2;
            mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.7f, 0.95f, 0.7f), 0.6f),
                    p.getX() + Math.cos(a) * 0.45, p.getY() + 0.3 + r.nextDouble() * 1.5, p.getZ() + Math.sin(a) * 0.45, 0, 0.02, 0);
        }
    }

    /** the chestplate icon right of the hotbar, with the wait drawn over it like any item cooldown */
    public static void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || BeingHim.inside() || !BellArmorItem.fullSet(mc.player)) return;
        ItemStack st = new ItemStack(ModItems.BELL_CHESTPLATE);
        int x = g.guiWidth() / 2 + 91 + 8;
        boolean left = mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
        if (left && !mc.player.getOffhandItem().isEmpty()) x += 29;                   // the off-hand slot is on this side
        if (!left && mc.options.attackIndicator().get() == net.minecraft.client.AttackIndicatorStatus.HOTBAR) x += 22;   // so is the swing meter
        int y = g.guiHeight() - 20;
        boolean ready = !mc.player.getCooldowns().isOnCooldown(ModItems.BELL_CHESTPLATE);
        g.fill(x - 2, y - 2, x + 18, y + 18, ready ? 0x6040A060 : 0x60202020);
        g.renderItem(st, x, y);
        g.renderItemDecorations(mc.font, st, x, y);
        if (ready) g.renderOutline(x - 2, y - 2, 20, 20, 0xA0B8F0B8);
    }
}
