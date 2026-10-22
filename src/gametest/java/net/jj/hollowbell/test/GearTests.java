package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.ModBlocks;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.StingerHook;
import net.jj.hollowbell.item.BellArmorItem;
import net.jj.hollowbell.item.FinderItem;
import net.jj.hollowbell.item.Powers;
import net.jj.hollowbell.item.StingerItem;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The finder, bell glass, the stinger, the recipes and the gear advancements. */
public class GearTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "finder_here")
    public void theFinderPointsAtHim(GameTestHelper h) {
        clearAll(h);
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 207);
        ServerPlayer[] pl = new ServerPlayer[1];
        ItemStack st = new ItemStack(ModItems.FINDER);
        h.runAfterDelay(20, () -> {
            pl[0] = player(h, e.position().add(-200, 2, 0));
            var said = FinderItem.answer(h.getLevel(), pl[0]);
            h.assertTrue(key(said).equals("message.hollowbell.finder"), "far off the finder said " + key(said));
            h.assertTrue("east".equals(args(said)[1]), "he is east, the finder says " + args(said)[1]);
            h.assertTrue(((Number) args(said)[0]).intValue() == 200, "200 blocks, the finder says " + args(said)[0]);
            h.assertTrue(
                    ((Number) args(said)[2]).intValue() == Mth.floor(e.getX())
                            && ((Number) args(said)[3]).intValue() == Mth.floor(e.getZ()),
                    "the finder gives the wrong spot");
            h.assertTrue(
                    said.getString()
                            .equals("The Hollowbell is 200 blocks east of you, at " + Mth.floor(e.getX()) + ", "
                                    + Mth.floor(e.getZ()) + "."),
                    "the finder's words are off: " + said.getString());
            pl[0].teleportTo(e.getX() + 10, e.getY(), e.getZ() - 40);
            said = FinderItem.answer(h.getLevel(), pl[0]);
            h.assertTrue(key(said).equals("message.hollowbell.finder_close"), "close by the finder said " + key(said));
            h.assertTrue("south".equals(args(said)[1]), "he is south, the finder says " + args(said)[1]);
            h.assertTrue(
                    ((Number) args(said)[0]).intValue() == 40, "about 40 blocks, the finder says " + args(said)[0]);
        });
        for (int t = 21; t <= 45; t++) h.runAfterDelay(t, () -> st.inventoryTick(h.getLevel(), pl[0], 0, false));
        h.runAfterDelay(50, () -> {
            h.assertTrue(st.hasFoil(), "the finder doesn't shine with him close");
            drop(pl[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "finder_stored")
    public void theFinderKnowsWhereTheWorldKeepsHim(GameTestHelper h) {
        clearAll(h);
        var w = WorldOne.get(h.getLevel().getServer());
        w.clearForTests();
        boolean was = BellConfig.V.oneInTheWorld;
        BellConfig.V.oneInTheWorld = true;
        BlockPos o = h.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer p = player(h, Vec3.atCenterOf(o));
        var said = FinderItem.answer(h.getLevel(), p);
        h.assertTrue(
                key(said).equals("message.hollowbell.finder_none"), "with nothing picked the finder said " + key(said));
        // none out there, the next due in three days, 5000 blocks east
        w.noteSpot(o.getX() + 5000, o.getZ(), false, h.getLevel().getGameTime() + 3 * 24000L);
        said = FinderItem.answer(h.getLevel(), p);
        h.assertTrue(key(said).equals("message.hollowbell.finder_dead"), "waiting, the finder said " + key(said));
        h.assertTrue(((Number) args(said)[0]).intValue() == 3, "3 days, the finder says " + args(said)[0]);
        h.assertTrue(((Number) args(said)[1]).intValue() == 5000, "5000 blocks, the finder says " + args(said)[1]);
        h.assertTrue("east".equals(args(said)[2]), "east, the finder says " + args(said)[2]);
        h.assertTrue(
                said.getString()
                        .equals("The Hollowbell is dead. The next one comes down in 3 days, 5000 blocks east of you,"
                                + " in the Bell Hollows."),
                "the finder's words are off: " + said.getString());
        // due right now
        w.noteSpot(o.getX() + 5000, o.getZ(), false, h.getLevel().getGameTime());
        said = FinderItem.answer(h.getLevel(), p);
        h.assertTrue(key(said).equals("message.hollowbell.finder_due"), "due now, the finder said " + key(said));
        h.assertTrue(
                said.getString()
                        .equals("The Hollowbell is about to come down, 5000 blocks east of you, in the Bell Hollows."),
                "the finder's words are off: " + said.getString());
        // he is out there (not loaded): the world's note of him
        w.noteSpot(o.getX(), o.getZ() - 3000, true, -1);
        said = FinderItem.answer(h.getLevel(), p);
        h.assertTrue(key(said).equals("message.hollowbell.finder"), "with him out there the finder said " + key(said));
        h.assertTrue("north".equals(args(said)[1]), "he is north, the finder says " + args(said)[1]);
        // and in his ground, the finder says so
        w.claimHome(h.getLevel(), o.getX(), o.getZ() - 3000, 77L);
        said = FinderItem.answer(h.getLevel(), p);
        h.assertTrue(
                key(said).equals("message.hollowbell.finder_ground"),
                "with him in his ground the finder said " + key(said));
        // the book's "Where is he?" and /hollowbell natural use the same words
        w.dropHome();
        var natural = FinderItem.tell(h.getLevel(), p.position());
        h.assertTrue(
                natural.getString().equals(said.getString().replace(", in the Bell Hollows.", ".")),
                "the finder and the status disagree: " + natural.getString());
        // from the nether it can't say
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        if (nether != null) {
            var nw = FinderItem.tell(nether, p.position());
            h.assertTrue(
                    nw.getString().equals("The Hollowbell lives in the overworld. Use this there."),
                    "from the nether it said " + nw.getString());
        }
        drop(p);
        w.clearForTests();
        BellConfig.V.oneInTheWorld = was;
        h.succeed();
    }

    static void wearBellGlass(ServerPlayer p) {
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.BELL_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.BELL_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.BELL_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.BELL_BOOTS));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "armor_poison")
    public void bellGlassKeepsPoisonOff(GameTestHelper h) {
        ServerPlayer p = player(h, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
        wearBellGlass(p);
        h.assertTrue(BellArmorItem.fullSet(p), "four pieces on and it isn't a full set");
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
        float hp = p.getHealth();
        boolean bit = p.hurt(p.damageSources().magic(), 1f); // poison's own bite: one point a tick
        h.assertTrue(!bit && p.getHealth() == hp, "poison's bite got through the glass");
        h.assertTrue(!p.hasEffect(MobEffects.POISON), "the poison stayed on");
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
        BellArmorItem.abilities(p);
        h.assertTrue(!p.hasEffect(MobEffects.POISON), "the set didn't take the poison off");
        // other magic still lands: a potion of harming hurts through the glass
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
        float hp2 = p.getHealth();
        p.invulnerableTime = 0;
        h.assertTrue(
                p.hurt(p.damageSources().magic(), 6f) && p.getHealth() < hp2, "harming didn't hurt through the glass");
        p.removeEffect(MobEffects.POISON);
        // three pieces is not the set
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
        BellArmorItem.abilities(p);
        h.assertTrue(p.hasEffect(MobEffects.POISON), "three pieces kept poison off");
        drop(p);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "armor_fall")
    public void bellGlassCatchesAFastFall(GameTestHelper h) {
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        ServerPlayer p = player(h, at);
        ServerPlayer q = player(h, at.add(1, 0, 0));
        wearBellGlass(p);
        p.fallDistance = 12f;
        BellArmorItem.abilities(p);
        h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "falling fast in the full set, no slow falling");
        h.assertTrue(p.fallDistance < 1f, "the fall so far still counts: " + p.fallDistance);
        q.fallDistance = 12f;
        BellArmorItem.abilities(q);
        h.assertTrue(!q.hasEffect(MobEffects.SLOW_FALLING), "no armor, and slow falling anyway");
        // each piece says what the set does, in gold
        List<Component> tip = new ArrayList<>();
        for (var item :
                new Item[] {ModItems.BELL_HELMET, ModItems.BELL_CHESTPLATE, ModItems.BELL_LEGGINGS, ModItems.BELL_BOOTS
                }) {
            tip.clear();
            item.appendHoverText(new ItemStack(item), Item.TooltipContext.EMPTY, tip, TooltipFlag.NORMAL);
            h.assertTrue(
                    tip.size() >= 2
                            && key(tip.get(0)).equals("item.hollowbell.bell_glass.tip")
                            && tip.get(0).getStyle().getColor() == TextColor.fromLegacyFormat(ChatFormatting.GOLD),
                    "no gold set line on " + item);
        }
        drop(p);
        drop(q);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "lang_keys")
    public void everyNewLineHasItsWords(GameTestHelper h) {
        try (var in = HollowbellGameTests.class.getResourceAsStream("/assets/hollowbell/lang/en_us.json")) {
            h.assertTrue(in != null, "no lang file");
            var lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (String k : new String[] {
                "item.hollowbell.hollowbell_finder",
                "item.hollowbell.bell_glass.tip",
                "item.hollowbell.bell_glass.tip2",
                "biome.hollowbell.bell_hollows",
                "message.hollowbell.finder",
                "message.hollowbell.finder_ground",
                "message.hollowbell.finder_close",
                "message.hollowbell.finder_none",
                "message.hollowbell.finder_wrong_world",
                "message.hollowbell.finder_dead",
                "message.hollowbell.finder_dead_day",
                "message.hollowbell.finder_due",
                "message.hollowbell.made_way",
                "block.hollowbell.bell_calcite",
                "block.hollowbell.tendril_glass",
                "block.hollowbell.bell_shard",
                "block.hollowbell.spore_moss",
                "message.hollowbell.ward_refused",
                "message.hollowbell.ward_broken",
                "message.hollowbell.codex_bound",
                "message.hollowbell.codex_roam",
                "codex.hollowbell.keep_here",
                "codex.hollowbell.keep_here_tip",
                "codex.hollowbell.let_roam"
            }) h.assertTrue(lang.has(k), "no words for " + k);
        } catch (IOException ex) {
            h.fail("couldn't read the lang file: " + ex);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "recipes")
    public void everyRecipeIsThere(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var rm = server.getRecipeManager();
        String[] ids = {
            "hollowbell_codex",
            "bell_glass_helmet",
            "bell_glass_chestplate",
            "bell_glass_leggings",
            "bell_glass_boots",
            "hollowbell_finder",
            "pod_poison"
        };
        for (String id : ids) {
            var r = rm.byKey(ResourceLocation.fromNamespaceAndPath("hollowbell", id));
            h.assertTrue(r.isPresent(), "the recipe " + id + " is missing (a broken file?)");
        }
        long ours = rm.getRecipes().stream()
                .filter(r -> r.id().getNamespace().equals("hollowbell"))
                .count();
        h.assertTrue(ours == ids.length, "the mod ships " + ours + " recipes, the test knows " + ids.length);
        var finder = rm.byKey(ResourceLocation.fromNamespaceAndPath("hollowbell", "hollowbell_finder"))
                .get();
        h.assertTrue(
                finder.value().getResultItem(server.registryAccess()).is(ModItems.FINDER),
                "the finder recipe makes something else");
        var codex = rm.byKey(ResourceLocation.fromNamespaceAndPath("hollowbell", "hollowbell_codex"))
                .get();
        h.assertTrue(
                codex.value().getResultItem(server.registryAccess()).is(ModItems.CODEX),
                "the codex recipe makes something else");
        h.succeed();
    }

    /** open ground far from every test's barrier walls (the harpoon would stick in those), chunks held loaded */
    private static Vec3 openGround(GameTestHelper h, int slot) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 20000 + slot * 400, z = o.getZ() + 9000;
        for (int cx = (x >> 4) - 2; cx <= (x >> 4) + 2; cx++)
            for (int cz = (z >> 4) - 2; cz <= (z >> 4) + 2; cz++) {
                h.getLevel().setChunkForced(cx, cz, true);
                h.getLevel().getChunk(cx, cz);
            }
        runsEntities(h, x, z, 1);
        return new Vec3(x + 0.5, top(h, x, z) + 1, z + 0.5);
    }

    private static void letGo(GameTestHelper h, Vec3 at) {
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        for (int cx = (x >> 4) - 2; cx <= (x >> 4) + 2; cx++)
            for (int cz = (z >> 4) - 2; cz <= (z >> 4) + 2; cz++) h.getLevel().setChunkForced(cx, cz, false);
    }

    private static void face(ServerPlayer p, Vec3 at) {
        p.lookAt(EntityAnchorArgument.Anchor.EYES, at);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "stinger_reel_them")
    public void theStingerPinsAndPoisonsWhatItHits(GameTestHelper h) {
        Vec3 at = openGround(h, 316);
        ServerPlayer p = player(h, at);
        var pig = EntityType.PIG.create(h.getLevel());
        Vec3 pigAt = at.add(11, 0, 0);
        pig.moveTo(pigAt.x, pigAt.y, pigAt.z, 0, 0);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
        pig.setHealth(40);
        h.getLevel().addFreshEntity(pig);
        ItemStack st = new ItemStack(ModItems.STINGER);
        p.setItemInHand(InteractionHand.MAIN_HAND, st);
        h.runAfterDelay(2, () -> {
            face(p, pig.position().add(0, pig.getBbHeight() * 0.5, 0));
            var hook = StingerItem.throwIt(p, st, 1f, false);
            h.assertTrue(p.getCooldowns().isOnCooldown(ModItems.STINGER), "no wait after a throw");
            h.assertTrue(StingerItem.COOLDOWN == 6 * 20, "a 6 second wait");
            h.assertTrue(hook.isAlive(), "the strand end didn't fly");
        });
        double[] best = {99};
        boolean[] pinned = {false};
        for (int t = 4; t < 40; t++)
            h.runAfterDelay(t, () -> {
                best[0] = Math.min(best[0], pig.position().distanceTo(p.position()));
                var slow = pig.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                var poison = pig.getEffect(MobEffects.POISON);
                if (slow != null && slow.getAmplifier() >= 6 && poison != null && poison.getAmplifier() >= 2)
                    pinned[0] = true;
            });
        h.runAfterDelay(42, () -> {
            h.assertTrue(pinned[0], "the pig was never pinned and badly poisoned");
            h.assertTrue(pig.getHealth() <= 40 - 7, "the barb hurts about 8: " + pig.getHealth());
            h.assertTrue(best[0] > 8, "and it isn't reeled in any more: closest " + best[0]);
            pig.discard();
            drop(p);
            letGo(h, at);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "stinger_reel_me")
    public void theStingerReelsYouToAWall(GameTestHelper h) {
        Vec3 at = openGround(h, 317);
        ServerPlayer p = player(h, at);
        // a wall 9 blocks off
        BlockPos w = BlockPos.containing(at.add(9, 0, 0));
        for (int y = 0; y < 4; y++)
            for (int z = -2; z <= 2; z++)
                h.getLevel().setBlockAndUpdate(w.offset(0, y, z), Blocks.STONE.defaultBlockState());
        StingerHook[] hook = new StingerHook[1];
        // (a few ticks first, so the freshly loaded chunks here are running)
        h.runAfterDelay(10, () -> {
            face(p, Vec3.atCenterOf(w.above()));
            hook[0] = StingerItem.throwIt(p, new ItemStack(ModItems.STINGER), 1f, true);
        });
        // a tick after it sticks in the wall, the pull is on (when it sticks depends on the throw's own ticks)
        int[] seen = {0};
        for (int t = 11; t < 60; t++)
            h.runAfterDelay(t, () -> {
                if (hook[0] == null || !hook[0].isAlive() || hook[0].state() != StingerHook.REEL_ME || ++seen[0] != 2)
                    return;
                h.assertTrue(p.getDeltaMovement().x > 0.3, "you're not pulled to the wall: " + p.getDeltaMovement());
                h.assertTrue(p.fallDistance == 0f, "being reeled counts as falling");
                hook[0].discard();
                for (int y = 0; y < 4; y++)
                    for (int z = -2; z <= 2; z++)
                        h.getLevel().setBlockAndUpdate(w.offset(0, y, z), Blocks.AIR.defaultBlockState());
                drop(p);
                letGo(h, at);
                h.succeed();
            });
        h.runAfterDelay(
                62,
                () -> h.fail(
                        "the stinger never stuck in the wall (alive " + (hook[0] != null && hook[0].isAlive()) + ")"));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "stinger_him")
    public void theStingerSticksInHimAndPullsYou(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.CALM, 314);
        StingerHook[] hook = new StingerHook[1];
        ServerPlayer[] pl = new ServerPlayer[1];
        h.runAfterDelay(20, () -> {
            // from outside him (at 0.2 he is about 40 across), level with one of his glowing spots
            Vec3 spot = e.spotWorld(0);
            Vec3 flat = new Vec3(spot.x - e.getX(), 0, spot.z - e.getZ());
            flat = flat.lengthSqr() < 1 ? new Vec3(1, 0, 0) : flat.normalize();
            Vec3 from = new Vec3(e.getX(), spot.y, e.getZ()).add(flat.scale(34));
            pl[0] = player(h, from.subtract(0, 1.62, 0));
            pl[0].setNoGravity(true);
            face(pl[0], spot);
            hook[0] = StingerItem.throwIt(pl[0], new ItemStack(ModItems.STINGER), 1f, true);
        });
        int[] seen = {0};
        for (int t = 22; t < 50; t++)
            h.runAfterDelay(t, () -> {
                // a tick after it sticks, the pull is on
                if (hook[0] != null && hook[0].isAlive() && hook[0].state() == StingerHook.REEL_ME && ++seen[0] == 2) {
                    Vec3 v = pl[0].getDeltaMovement();
                    Vec3 toHim = hook[0].position().subtract(pl[0].position());
                    h.assertTrue(v.dot(toHim.normalize()) > 0.3, "stuck in him but you're not pulled to him: " + v);
                    hook[0].discard();
                    drop(pl[0]);
                    release(h, e);
                    h.succeed();
                }
            });
        h.runAfterDelay(
                52,
                () -> h.fail("the stinger never stuck in him (alive " + (hook[0] != null && hook[0].isAlive()) + ")"));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "armour_glide")
    public void inTheAirTheArmourGlides(GameTestHelper h) {
        Powers.clear();
        ServerPlayer p = player(h, onGround(h, 1, 1).add(0, 20, 0));
        wearBellGlass(p);
        p.setOnGround(false);
        p.setYRot(-90); // facing +x
        p.setXRot(0);
        h.runAfterDelay(2, () -> {
            p.setOnGround(false);
            var k = Powers.use(p);
            h.assertTrue(k == Powers.Kind.GLIDE, "in the air the key should glide, did " + k);
        });
        h.runAfterDelay(14, () -> {
            p.setOnGround(false);
            Vec3 v = p.getDeltaMovement();
            h.assertTrue(v.x > 0.4 && v.y > -0.2 && v.y < 0.05, "not gliding along: " + v);
            Powers.clear();
            drop(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "armour_last_ring")
    public void hurtLowTheGlassRingsOnce(GameTestHelper h) {
        BellArmorItem.forgetRings();
        Vec3 at = onGround(h, 1, 1);
        ServerPlayer p = player(h, at);
        wearBellGlass(p);
        var z = EntityType.ZOMBIE.create(h.getLevel());
        z.moveTo(at.x + 2, at.y, at.z, 0, 0);
        z.setNoAi(true);
        h.getLevel().addFreshEntity(z);
        p.setHealth(p.getMaxHealth());
        h.assertTrue(!BellArmorItem.lastRing(p), "rang at full health");
        p.setHealth(4f);
        h.assertTrue(BellArmorItem.lastRing(p), "didn't ring when hurt low");
        h.assertTrue(p.hasEffect(MobEffects.ABSORPTION), "no cover after the ring");
        h.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the ring didn't reach the zombie");
        h.assertTrue(!BellArmorItem.lastRing(p), "rang twice in a row");
        // and his toll's dizziness is shaken off
        p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200));
        BellArmorItem.abilities(p);
        h.assertTrue(!p.hasEffect(MobEffects.CONFUSION), "the set kept the dizziness");
        z.discard();
        drop(p);
        h.succeed();
    }

    static void wearTheSet(ServerPlayer p) {
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.BELL_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.BELL_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.BELL_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.BELL_BOOTS));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "adv_gear")
    public void theGearAdvancementsAreGiven(GameTestHelper h) {
        Vec3 at = h.absoluteVec(new Vec3(1, 2, 1));
        ServerPlayer p = HollowbellGameTests.player(h, at);
        h.assertTrue(
                !done(p, "bell_glass_set") && !done(p, "bell_toll") && !done(p, "stinger_throw"),
                "a new player has them already");
        wearTheSet(p);
        h.runAfterDelay(25, () -> {
            h.assertTrue(done(p, "bell_glass_set"), "wearing the set didn't give it");
            p.setOnGround(true);
            Powers.use(p);
            h.assertTrue(done(p, "bell_toll"), "the armour power didn't give it");
            var st = new ItemStack(ModItems.STINGER);
            p.setItemInHand(InteractionHand.MAIN_HAND, st);
            StingerItem.throwIt(p, st, 1f, false);
            h.assertTrue(done(p, "stinger_throw"), "throwing the Stinger didn't give it");
            // his loot cache
            BlockPos c = BlockPos.containing(at).offset(2, 0, 0);
            h.getLevel().setBlockAndUpdate(c, ModBlocks.LOOT_CACHE.defaultBlockState());
            var hit = new BlockHitResult(Vec3.atCenterOf(c), Direction.UP, c, false);
            h.getLevel().getBlockState(c).useWithoutItem(h.getLevel(), p, hit);
            h.assertTrue(done(p, "loot_cache"), "opening the loot cache didn't give it");
            p.closeContainer();
            h.getLevel().setBlockAndUpdate(c, Blocks.AIR.defaultBlockState());
            for (var hk : h.getLevel()
                    .getEntitiesOfClass(StingerHook.class, p.getBoundingBox().inflate(40))) hk.discard();
            drop(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "adv_own_glass")
    public void killingHimInHisOwnGlassGivesIt(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.05f, HollowbellEntity.CALM, 331);
        h.runAfterDelay(20, () -> {
            ServerPlayer p = HollowbellGameTests.player(h, under(e).add(4, 0, 0));
            wearTheSet(p);
            e.hurt(e.damageSources().playerAttack(p), 1e9f);
            h.assertTrue(e.isDeadOrDying(), "he didn't die");
            h.assertTrue(done(p, "own_glass"), "killing him in his own glass didn't give it");
            drop(p);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "adv_watch")
    public void beingNearWhenGiantsFightGivesIt(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 332);
        h.runAfterDelay(20, () -> {
            ServerPlayer near = HollowbellGameTests.player(h, under(e).add(30, 0, 0));
            near.setGameMode(GameType.CREATIVE);
            var z = giantOfKind(h, under(e).add(12, 0, 0), "fire_ice_cerberus");
            h.assertTrue(e.startMeeting(z), "no meeting");
            h.assertTrue(done(near, "watch_giants"), "watching two giants fight didn't give it");
            z.discard();
            drop(near);
            release(h, e);
            h.succeed();
        });
    }
}
