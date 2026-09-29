package net.jj.hollowbell.block;

import net.jj.hollowbell.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** the 27 slots of his loot cache; its beam stands while anything is in it */
public class LootCacheBlockEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

    public LootCacheBlockEntity(BlockPos pos, BlockState st) { super(ModBlocks.LOOT_CACHE_ENTITY, pos, st); }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> list) { items = list; }
    @Override protected Component getDefaultName() { return Component.translatable("block.hollowbell.loot_cache"); }
    @Override public int getContainerSize() { return 27; }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inv) { return ChestMenu.threeRows(id, inv, this); }

    /** put in what fits; returns what didn't */
    public ItemStack add(ItemStack st) {
        for (int i = 0; i < items.size() && !st.isEmpty(); i++) {
            ItemStack in = items.get(i);
            if (in.isEmpty()) { items.set(i, st.copy()); st = ItemStack.EMPTY; }
            else if (ItemStack.isSameItemSameComponents(in, st) && in.getCount() < in.getMaxStackSize()) {
                int n = Math.min(st.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(n); st.shrink(n);
            }
        }
        setChanged();
        return st;
    }

    /** the beam and the glow follow whether anything is left */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level == null || level.isClientSide) return;
        BlockState st = getBlockState();
        boolean full = !isEmpty();
        if (st.hasProperty(LootCacheBlock.LIT) && st.getValue(LootCacheBlock.LIT) != full)
            level.setBlock(worldPosition, st.setValue(LootCacheBlock.LIT, full), 3);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider p) {
        super.loadAdditional(tag, p);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items, p);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider p) {
        super.saveAdditional(tag, p);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items, p);
    }
}
