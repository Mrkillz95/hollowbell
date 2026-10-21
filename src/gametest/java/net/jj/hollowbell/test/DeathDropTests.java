package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModBlocks;
import net.jj.hollowbell.block.LootCacheBlockEntity;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.test.mixin.MobAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Every time he is killed he drops enough of his own things to make everything in the mod once. */
public class DeathDropTests implements FabricGameTest {
    private static final int KILLS = 5;

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "drops_small")
    public void aSmallOneAlwaysDropsEnoughForAllHisThings(GameTestHelper h) {
        killAndCount(h, 0.1f, 700);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "drops_full")
    public void aFullSizedOneAlwaysDropsEnoughForAllHisThings(GameTestHelper h) {
        killAndCount(h, 1f, 706);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "drops_big")
    public void aDoubleSizedOneAlwaysDropsEnoughForAllHisThings(GameTestHelper h) {
        killAndCount(h, 2f, 712);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "drops_craft")
    public void whatHeAlwaysDropsMakesEveryRecipeOnce(GameTestHelper h) {
        Map<Item, Integer> least = need(h);
        h.assertFalse(least.isEmpty(), "no recipe uses anything of his");
        craftAll(h, least, "his least drop");
        h.succeed();
    }

    private static void killAndCount(GameTestHelper h, float scale, int slot) {
        Map<Item, Integer> need = need(h);
        h.assertFalse(need.isEmpty(), "no recipe uses anything of his");
        List<HollowbellEntity> all = new ArrayList<>();
        for (int i = 0; i < KILLS; i++) all.add(spawnAway(h, scale, HollowbellEntity.CALM, slot + i));
        Vec3[] at = new Vec3[KILLS];
        h.runAfterDelay(20, () -> {
            for (int i = 0; i < KILLS; i++) {
                HollowbellEntity e = all.get(i);
                ((MobAccess) e).hollowbell$setLootSeed(1 + i);
                at[i] = e.position();
                e.applyDamage(e.damageSources().generic(), e.healthMax() * 100f, -1, false);
                h.assertTrue(e.isDeadOrDying(), "he didn't die at size " + scale);
            }
        });
        h.runAfterDelay(20 + HollowbellEntity.DEATH_LENGTH + 20, () -> {
            for (int i = 0; i < KILLS; i++) {
                HollowbellEntity e = all.get(i);
                h.assertTrue(e.isRemoved(), "he should be gone after his death");
                Map<Item, Integer> got = takeDrops(h, at[i]);
                String which = "size " + scale + ", seed " + (1 + i);
                for (var n : need.entrySet()) {
                    int had = got.getOrDefault(n.getKey(), 0);
                    h.assertTrue(
                            had >= n.getValue(),
                            which + ": " + had + " " + name(n.getKey()) + ", the recipes need " + n.getValue());
                }
                craftAll(h, got, which);
                release(h, e);
            }
            h.succeed();
        });
    }

    /** everything in the cache he left and lying round it, taken away */
    private static Map<Item, Integer> takeDrops(GameTestHelper h, Vec3 at) {
        var l = h.getLevel();
        Map<Item, Integer> got = new HashMap<>();
        BlockPos c = BlockPos.containing(at);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-8, -80, -8), c.offset(8, 80, 8))) {
            if (!l.getBlockState(p).is(ModBlocks.LOOT_CACHE)) continue;
            if (!(l.getBlockEntity(p) instanceof LootCacheBlockEntity be)) continue;
            for (int i = 0; i < be.getContainerSize(); i++) {
                ItemStack st = be.getItem(i);
                if (!st.isEmpty()) got.merge(st.getItem(), st.getCount(), Integer::sum);
                be.setItem(i, ItemStack.EMPTY);
            }
            l.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
        for (ItemEntity d : l.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(24, 100, 24))) {
            got.merge(d.getItem().getItem(), d.getItem().getCount(), Integer::sum);
            d.discard();
        }
        return got;
    }

    private static boolean his(Item it) {
        return BuiltInRegistries.ITEM.getKey(it).getNamespace().equals(HollowbellMod.MOD_ID);
    }

    private static String name(Item it) {
        return BuiltInRegistries.ITEM.getKey(it).toString();
    }

    private static List<RecipeHolder<?>> recipes(GameTestHelper h) {
        List<RecipeHolder<?>> out = new ArrayList<>();
        for (RecipeHolder<?> r : h.getLevel().getRecipeManager().getRecipes())
            if (r.id().getNamespace().equals(HollowbellMod.MOD_ID)) out.add(r);
        out.sort(Comparator.comparing(r -> r.id().toString()));
        return out;
    }

    /** which of his things each of his own items is made with, for items made from others of his */
    private static Map<Item, Recipe<?>> makers(GameTestHelper h) {
        Map<Item, Recipe<?>> out = new HashMap<>();
        for (RecipeHolder<?> r : recipes(h)) {
            Item made = r.value().getResultItem(h.getLevel().registryAccess()).getItem();
            if (his(made)) out.putIfAbsent(made, r.value());
        }
        return out;
    }

    /** how many of each of his own materials making every recipe of the mod once takes, down to what he drops */
    private static Map<Item, Integer> need(GameTestHelper h) {
        Map<Item, Recipe<?>> makers = makers(h);
        Map<Item, Integer> need = new HashMap<>();
        for (RecipeHolder<?> r : recipes(h)) {
            h.assertTrue(
                    !r.value().getIngredients().isEmpty() || r.value() instanceof CustomRecipe,
                    "can't tell what " + r.id() + " is made of");
            addUp(h, r.value(), makers, need, 0);
        }
        return need;
    }

    private static void addUp(
            GameTestHelper h, Recipe<?> r, Map<Item, Recipe<?>> makers, Map<Item, Integer> need, int depth) {
        h.assertTrue(depth < 8, "his recipes go round in a loop");
        for (Ingredient ing : r.getIngredients()) {
            if (ing.isEmpty()) continue;
            ItemStack[] can = ing.getItems();
            boolean onlyHis = true;
            for (ItemStack s : can) onlyHis &= his(s.getItem());
            if (!onlyHis) continue;
            Item it = can[0].getItem();
            Recipe<?> sub = makers.get(it);
            if (sub != null) addUp(h, sub, makers, need, depth + 1);
            else need.merge(it, 1, Integer::sum);
        }
    }

    /** makes every recipe of the mod once from the pool, with any vanilla things the player brings */
    private static void craftAll(GameTestHelper h, Map<Item, Integer> pool, String from) {
        Map<Item, Integer> left = new HashMap<>(pool);
        Map<Item, Recipe<?>> makers = makers(h);
        for (RecipeHolder<?> r : recipes(h)) make(h, r.value(), r.id().toString(), left, makers, from, 0);
    }

    private static void make(
            GameTestHelper h,
            Recipe<?> r,
            String id,
            Map<Item, Integer> left,
            Map<Item, Recipe<?>> makers,
            String from,
            int depth) {
        h.assertTrue(depth < 8, "his recipes go round in a loop");
        List<ItemStack> in = new ArrayList<>();
        for (Ingredient ing : r.getIngredients()) {
            if (ing.isEmpty()) {
                in.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack pick = null;
            for (ItemStack s : ing.getItems()) {
                Item it = s.getItem();
                if (!his(it)) {
                    pick = s.copyWithCount(1);
                    break;
                }
                Recipe<?> sub = makers.get(it);
                if (left.getOrDefault(it, 0) <= 0 && sub != null) {
                    make(h, sub, name(it), left, makers, from, depth + 1);
                }
                if (left.getOrDefault(it, 0) > 0) {
                    left.merge(it, -1, Integer::sum);
                    pick = s.copyWithCount(1);
                    break;
                }
            }
            h.assertTrue(pick != null, from + ": ran out of " + name(ing.getItems()[0].getItem()) + " making " + id);
            in.add(pick);
        }
        boolean fits;
        if (r instanceof CraftingRecipe cr) {
            CraftingInput grid = cr instanceof ShapedRecipe s
                    ? CraftingInput.of(s.getWidth(), s.getHeight(), in)
                    : CraftingInput.of(in.size(), 1, in);
            fits = cr.matches(grid, h.getLevel());
        } else if (r instanceof AbstractCookingRecipe cook) {
            fits = cook.matches(new SingleRecipeInput(in.get(0)), h.getLevel());
        } else if (r instanceof SingleItemRecipe cut) {
            fits = cut.matches(new SingleRecipeInput(in.get(0)), h.getLevel());
        } else {
            fits = false;
        }
        h.assertTrue(fits, from + ": what was put in doesn't make " + id);
        ItemStack made = r.getResultItem(h.getLevel().registryAccess());
        if (his(made.getItem())) left.merge(made.getItem(), made.getCount(), Integer::sum);
    }
}
