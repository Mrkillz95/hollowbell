package net.jj.giants.client.admin;

import java.util.Map;
import java.util.WeakHashMap;
import net.jj.giants.admin.AdminCreation;
import net.jj.giants.admin.AdminDraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

/**
 * The Create World screen's giants (ADMIN_BRIDGE.md §18.6-18.7): one draft per open Create World screen (a new one
 * starts at Classic), the "Giants…" button's words, and the choice armed for the new world when Create New World is
 * pressed.
 */
public final class CreationClient {
    private CreationClient() {}

    private static final Map<Screen, AdminDraft> DRAFTS = new WeakHashMap<>();

    /** this Create World screen's draft */
    public static AdminDraft draft(Screen createWorld) {
        return DRAFTS.computeIfAbsent(createWorld, s -> new AdminDraft());
    }

    /** "Giants…", or "Giants: Far Frontier…" once something is chosen */
    public static Component buttonLabel(Screen createWorld) {
        AdminDraft d = DRAFTS.get(createWorld);
        if (d == null || d.label().equals("Classic")) return Component.translatable("admin.jj_giants.creation.button");
        return Component.translatable("admin.jj_giants.creation.button.chosen", d.label());
    }

    /** Create New World was pressed: the draft waits for its world (nothing for an untouched Classic) */
    public static void arm(CreateWorldScreen s) {
        AdminDraft d = DRAFTS.get(s);
        if (d == null || d.empty()) {
            AdminCreation.arm(null);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String who = mc.getUser() != null ? mc.getUser().getName() : "Player";
        AdminCreation.arm(new AdminCreation.Pending(s.getUiState().getTargetFolder(), d.bundle(), d.label(), who));
    }
}
