package net.jj.mountain;

import net.jj.mountain.block.GooBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    public static final Block GOO = Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "goo"),
            new GooBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.2f).sound(SoundType.HONEY_BLOCK)
                    .speedFactor(0.35f).jumpFactor(0.55f).noOcclusion().randomTicks().replaceable().pushReaction(PushReaction.DESTROY)
                    .noLootTable()));

    /** the one thing you can only ever get by putting him down: a lump of him that never stopped beating */
    public static final Block HEART = Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "mountain_heart"),
            new net.jj.mountain.block.HeartTrophyBlock(BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_HYPHAE)
                    .strength(4.0f, 1200f).sound(SoundType.HONEY_BLOCK).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(net.jj.mountain.block.HeartTrophyBlock::lightOf)));

    public static void init() {}
}
