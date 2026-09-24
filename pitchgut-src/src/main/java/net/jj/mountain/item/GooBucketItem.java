package net.jj.mountain.item;

import net.jj.mountain.ModBlocks;
import net.jj.mountain.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** A bucket of what runs out of him. Tip it out and it spreads over the ground where it lands. */
public class GooBucketItem extends Item {
    public GooBucketItem(Properties p) { super(p); }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        Level lvl = c.getLevel();
        BlockPos at = c.getClickedPos().relative(c.getClickedFace());
        // both sides work out whether anything would be laid, so the client never eats a bucket the server kept
        int laid = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos p = at.offset(dx, 0, dz);
            for (int dy = 0; dy >= -2; dy--) {
                BlockPos q = p.below(-dy);
                if (lvl.getBlockState(q).canBeReplaced()) {
                    if (!lvl.isClientSide && !(dx != 0 && dz != 0 && lvl.random.nextBoolean()))
                        lvl.setBlockAndUpdate(q, ModBlocks.GOO.defaultBlockState());
                    laid++;
                    break;
                }
            }
        }
        if (laid == 0) return InteractionResult.PASS;
        if (!lvl.isClientSide) lvl.playSound(null, at, ModSounds.GOO, SoundSource.PLAYERS, ModSounds.vol(1.2f), 0.9f);
        if (c.getPlayer() != null && !c.getPlayer().getAbilities().instabuild) {
            ItemStack held = c.getItemInHand();
            held.shrink(1);
            if (held.isEmpty()) c.getPlayer().setItemInHand(c.getHand(), new ItemStack(Items.BUCKET));
            else c.getPlayer().getInventory().add(new ItemStack(Items.BUCKET));
        }
        return InteractionResult.sidedSuccess(lvl.isClientSide);
    }
}
