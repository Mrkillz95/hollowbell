package net.jj.giants.client.admin;

import java.util.List;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * (Part 6) The Server tools page's own pictures (tools/tools_icons.py): its contents entry and each of its tabs, in
 * the section icons' style.
 */
final class ToolsArt {
    private ToolsArt() {}

    static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "textures/gui/admin/tools_icons.png");
    /** the pictures, in the order tools/tools_icons.py draws them */
    static final List<String> NAMES = List.of("tools", "t_now", "t_schedules", "t_areas", "t_map", "t_stats",
            "t_alerts", "t_respawns", "t_server", "t_events");

    /** draws one of these if it is one; false if it isn't */
    static boolean icon(GuiGraphics g, String name, int x, int y, int size) {
        int i = NAMES.indexOf(name);
        if (i < 0) return false;
        g.blit(ICONS, x, y, size, size, i * 16, 0, 16, 16, NAMES.size() * 16, 16);
        return true;
    }
}
