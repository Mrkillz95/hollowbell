package net.jj.giants.mixin.client;

import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** a Create World tab's grid, so the More tab can take the Giants button (Part 3) */
@Mixin(GridLayoutTab.class)
public interface GridLayoutTabAccess {
    @Accessor("layout")
    GridLayout giants$layout();
}
