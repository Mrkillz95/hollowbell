package net.jj.giants.minigames;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (Part 10A) What players get in a minigame: each mode's kit (put on and into their emptied pack; it never leaves the
 * round's world: their own things come back when they go home), and Giant Royale's chests round the field.
 */
public final class MgKits {
    private MgKits() {}

    static void wear(ServerPlayer p, Item head, Item chest, Item legs, Item feet) {
        if (head != Items.AIR) p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(head));
        if (chest != Items.AIR) p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
        if (legs != Items.AIR) p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(legs));
        if (feet != Items.AIR) p.setItemSlot(EquipmentSlot.FEET, new ItemStack(feet));
    }

    static void give(ServerPlayer p, ItemStack... items) {
        for (ItemStack s : items) if (!p.getInventory().add(s)) p.drop(s, false);
    }

    static ItemStack potion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> what, Item kind) {
        ItemStack s = new ItemStack(kind);
        s.set(DataComponents.POTION_CONTENTS, new PotionContents(what));
        return s;
    }

    /** Giant Royale: light (the chests hold better) */
    public static void royale(ServerPlayer p) {
        wear(p, Items.AIR, Items.LEATHER_CHESTPLATE, Items.AIR, Items.LEATHER_BOOTS);
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        give(p, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 16),
                new ItemStack(Items.COOKED_BEEF, 6));
        p.inventoryMenu.broadcastChanges();
    }

    /** the fighting kit: the co-operative modes (giants are the enemy) */
    public static void fighter(ServerPlayer p) {
        wear(p, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        give(p, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 48),
                new ItemStack(Items.IRON_AXE), new ItemStack(Items.COOKED_BEEF, 16),
                new ItemStack(Items.GOLDEN_APPLE, 2), potion(Potions.HEALING, Items.SPLASH_POTION));
        p.inventoryMenu.broadcastChanges();
    }

    /** Siege: bows from the walls, a sword for the yard */
    public static void archer(ServerPlayer p) {
        wear(p, Items.CHAINMAIL_HELMET, Items.IRON_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.IRON_BOOTS);
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        ItemStack bow = new ItemStack(Items.BOW);
        give(p, bow, new ItemStack(Items.CROSSBOW), new ItemStack(Items.ARROW, 64), new ItemStack(Items.ARROW, 64),
                new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BREAD, 12), new ItemStack(Items.GOLDEN_APPLE, 2),
                potion(Potions.HEALING, Items.SPLASH_POTION));
        p.inventoryMenu.broadcastChanges();
    }

    /** a light kit (Possession, Bounty Board: fights on foot) */
    public static void light(ServerPlayer p) {
        wear(p, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
        give(p, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 24),
                new ItemStack(Items.COOKED_BEEF, 8));
        p.inventoryMenu.broadcastChanges();
    }

    /** a stronger kit (Legends, Boss Rush's later waves) */
    public static void champion(ServerPlayer p) {
        wear(p, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.IRON_LEGGINGS, Items.DIAMOND_BOOTS);
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        give(p, new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 64),
                new ItemStack(Items.CROSSBOW), new ItemStack(Items.COOKED_BEEF, 16),
                new ItemStack(Items.GOLDEN_APPLE, 4), potion(Potions.HEALING, Items.SPLASH_POTION),
                potion(Potions.HEALING, Items.SPLASH_POTION), potion(Potions.STRENGTH, Items.POTION));
        p.inventoryMenu.broadcastChanges();
    }

    /** Giant Royale's chests: one or two good things each, round the field (never in a den or the arena) */
    public static void royaleChests(MgGame g, int n) {
        ServerLevel l = g.level;
        if (l == null || g.map == null) return;
        RandomSource r = l.random;
        int[] ring = Royale.ring(g.sizeIndex, Minigames.kinds().size());
        int inner = net.jj.giants.fun.MgShapes.arena(Royale.ARENA[g.sizeIndex]).reach() + 4;
        List<Vec3> dens = new ArrayList<>(g.map.spots.values());
        int placed = 0;
        for (int tries = 0; placed < n && tries < n * 30; tries++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = inner + r.nextDouble() * (g.map.half - 8 - inner);
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            boolean nearDen = false;
            for (Vec3 v : dens) if (Math.hypot(v.x - x, v.z - z) < ring[1] + 4) nearDen = true;
            if (nearDen) continue;
            int y = (int) MgGiants.ground(l, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (!l.getBlockState(at).isAir() || !l.getBlockState(at.below()).isSolidRender(l, at.below())) continue;
            l.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,
                    Direction.from2DDataValue(r.nextInt(4))), Block.UPDATE_ALL);
            if (l.getBlockEntity(at) instanceof ChestBlockEntity c) {
                List<ItemStack> loot = royaleLoot(r);
                for (int i = 0; i < loot.size(); i++) c.setItem(r.nextInt(27), loot.get(i));
            }
            placed++;
        }
    }

    static List<ItemStack> royaleLoot(RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        ItemStack[] good = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_AXE),
            new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_LEGGINGS),
            new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.ARROW, 16),
            new ItemStack(Items.CROSSBOW), new ItemStack(Items.DIAMOND_SWORD),
            potion(Potions.HEALING, Items.SPLASH_POTION), potion(Potions.SWIFTNESS, Items.POTION),
            new ItemStack(Items.COOKED_BEEF, 4), new ItemStack(Items.SHIELD)};
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            int k = r.nextInt(good.length);
            // (a diamond sword is rare)
            if (k == 9 && r.nextInt(3) != 0) k = 0;
            out.add(good[k].copy());
        }
        return out;
    }
}
