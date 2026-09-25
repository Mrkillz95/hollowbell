package net.jj.hollowbell;

import net.jj.hollowbell.block.CrownBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    /** his crown, set down as a trophy: it glows */
    public static final Block CROWN = Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "hollowbell_crown"),
            new CrownBlock(BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.AMETHYST).lightLevel(s -> 15).noOcclusion()));

    public static void init() {}
}
