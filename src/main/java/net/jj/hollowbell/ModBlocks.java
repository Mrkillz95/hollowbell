package net.jj.hollowbell;

import net.jj.hollowbell.block.CrownBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    /** his crown, set down as a trophy: it glows */
    public static final Block CROWN = Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "hollowbell_crown"),
            new CrownBlock(BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.AMETHYST).lightLevel(s -> 15).noOcclusion()));

    // ---- the blocks of his ground, the Bell Hollows (nothing here spreads, ticks or grows)

    /** calcite with pale green veins, like the ribs of his bell: the den's ribs and rim, fallen ribs of shards */
    public static final Block BELL_CALCITE = reg("bell_calcite",
            new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE).strength(1.0f)));
    /** see-through green glass with glowing veins, from his reefs */
    public static final Block TENDRIL_GLASS = reg("tendril_glass",
            new TransparentBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LIME_STAINED_GLASS).strength(0.5f).lightLevel(s -> 6)));
    /** cracked, cloudy glass from an old bell */
    public static final Block BELL_SHARD = reg("bell_shard",
            new TransparentBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).strength(0.6f)));
    /** a thin, soft layer of glowing moss, in the gardens of the hollows */
    public static final Block SPORE_MOSS = reg("spore_moss",
            new CarpetBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.MOSS_CARPET).lightLevel(s -> 3)));

    /** every block that has an item of its own, in the order the creative tab shows them */
    public static final Block[] GROUND = {BELL_CALCITE, TENDRIL_GLASS, BELL_SHARD, SPORE_MOSS};

    private static Block reg(String name, Block b) {
        return Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, name), b);
    }

    public static void init() {}
}
