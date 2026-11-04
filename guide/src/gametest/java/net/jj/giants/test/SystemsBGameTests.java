package net.jj.giants.test;

import static net.jj.giants.test.SystemsGameTests.done;
import static net.jj.giants.test.SystemsGameTests.fresh;
import static net.jj.giants.test.SystemsGameTests.golem;
import static net.jj.giants.test.SystemsGameTests.set;
import static net.jj.giants.test.SystemsGameTests.sweep;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.fakesys.GiantsBridge;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminInsight;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminUndo;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.systems.Chaos;
import net.jj.giants.systems.Followers;
import net.jj.giants.systems.Mirror;
import net.jj.giants.systems.Sacred;
import net.jj.giants.systems.Systems;
import net.jj.giants.systems.SystemsAdmin;
import net.jj.giants.systems.SystemsCommand;
import net.jj.giants.systems.SystemsData;
import net.jj.giants.systems.Underworld;
import net.jj.giants.systems.Villages;
import net.jj.giants.systems.Weather;
import net.jj.giants.tools.Watch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Part 9, "New systems B" (ADMIN_BRIDGE.md 25), the Guide's side with the stand-in giant (net.jj.fakesys: iron golems
 * tagged "fakesys"): the settings as ordinary ones and the five presets, villagers fleeing, hiding and worshipping and
 * golems going for a wild one, followers gathering, keeping near and crumbling away, the weather round a giant,
 * the Underworld (the Overworld held, one of each in the Nether sized to fit, coming back after his days), sacred
 * ground refusing a survival player's breaking and building, Mirror learning a tactic and telling the giant, and Random
 * chaos's shuffle (sizes, moods, resting moves on what he is sent only). Each test has its own batch and spot and turns
 * the systems back off. Left out with the real giants loaded (SystemsBGiantsGameTests).
 */
public class SystemsBGameTests implements FabricGameTest {

    static final List<String> IDS = List.of("all.villagers", "all.golems", "all.followers", "all.followerCount",
            "all.followerEvery", "all.weather", "all.weatherArea", "all.weatherStrength", "all.underworld",
            "all.sacred", "all.mirror", "all.mirrorMost", "all.chaos", "all.chaosDays");

    static Villager villager(ServerLevel l, BlockPos at, double dx, double dz) {
        Villager v = EntityType.VILLAGER.create(l);
        v.moveTo(at.getX() + 0.5 + dx, at.getY(), at.getZ() + 0.5 + dz, 0, 0);
        l.addFreshEntity(v);
        return v;
    }

    static IronGolem plainGolem(ServerLevel l, BlockPos at, double dx, double dz) {
        IronGolem g = EntityType.IRON_GOLEM.create(l);
        g.moveTo(at.getX() + 0.5 + dx, at.getY(), at.getZ() + 0.5 + dz, 0, 0);
        l.addFreshEntity(g);
        return g;
    }

    // the settings and the presets

    @GameTest(template = EMPTY_STRUCTURE, batch = "p9_settings", timeoutTicks = 100)
    public void partBSettingsAreOrdinaryAndThePresetsNameThem(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        for (String id : IDS) {
            AdminSetting x = AdminCatalog.shared(id);
            h.assertTrue(x != null && AdminCatalog.systems(x) && x.when().equals("live")
                    && AdminSetting.same(x.def(), x.jar()), id + ": a systems setting, live, as the jar has it");
        }
        h.assertTrue(Systems.option(s, "villagers").equals("off") && Systems.option(s, "underworld").equals("off")
                && !Systems.on(s, "weather") && !Systems.on(s, "chaos"), "every one off");
        // off: nothing extra sent, his own values as they are
        Map<String, JsonElement> all = AdminWorld.get(s).values("all");
        h.assertTrue(Systems.forGiants(s, all) == all, "off: the very same map");
        Map<String, JsonElement> mine = Map.of("move.bite.on", new JsonPrimitive(true));
        h.assertTrue(Systems.forGiant(s, SystemsGameTests.FAKE, mine) == mine, "no chaos: his own values untouched");
        // logged and undone like any other
        set(h, s, "weather", "on");
        AdminWorld w = AdminWorld.get(s);
        AdminWorld.Change c = w.log().get(w.log().size() - 1);
        h.assertTrue(c.id().equals("all.weather") && c.now().equals("on"), "logged: " + c);
        AdminServer.Result u = AdminUndo.undo(s, "Tester", w.log().size() - 1, c.time());
        h.assertTrue(u.ok() && !Systems.on(s, "weather"), "undone: " + u.lines());
        h.assertTrue(!AdminServer.set(s, "Tester", "all", "underworld", "heaven").ok(), "not an option");
        h.assertTrue(!AdminServer.set(s, "Tester", "all", "followerCount", "99").ok(), "past the most");
        // the Underworld: the giants are told the Overworld holds none
        set(h, s, "underworld", "both");
        h.assertTrue("nogiants".equals(GiantsBridge.all.get("all.dimOverworld").getAsString()), "held: "
                + GiantsBridge.all);
        set(h, s, "underworld", "off");
        h.assertTrue(!GiantsBridge.all.has("all.dimOverworld"), "not held: " + GiantsBridge.all);
        // Mirror reaches the giants
        set(h, s, "mirror", "on");
        h.assertTrue(GiantsBridge.all.get("all.mirror").getAsBoolean(), "mirror sent");
        w.remove("all", "all.mirror");
        // the five presets each turn theirs on; Classic puts them back
        String[][] presets = {{"random_chaos", "chaos", "true"}, {"underworld", "underworld", "both"},
            {"weather_gods", "weather", "true"}, {"sacred_ground", "sacred", "true"}, {"mirror", "mirror", "true"}};
        for (String[] p : presets) {
            AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset(p[0])));
            h.assertTrue(Systems.value(s, p[1]).getAsString().equals(p[2]), p[0] + " turns " + p[1] + " on");
        }
        h.assertTrue(Systems.num(s, "scaleArmour") == 1 && !Systems.on(s, "weather"), "Mirror: stronger against"
                + " armour, and Weather Gods back off");
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("classic")));
        h.assertTrue(!Systems.on(s, "mirror") && Systems.num(s, "scaleArmour") == 0, "Classic: back off");
        // the clash, and the danger
        h.assertTrue(AdminInsight.warnings(List.of(), Map.of("all.underworld", 1.0, "all.dimNether", 1.0)).stream()
                .anyMatch(x -> x.rule().equals("underworld_shut")), "Underworld into a Nether with no giants");
        h.assertTrue(AdminInsight.warnings(List.of(), Map.of("all.underworld", 2.0, "all.dimNether", 1.0)).stream()
                .noneMatch(x -> x.rule().equals("underworld_shut")), "the End only: no clash");
        AdminInsight.View v = SystemsGameTests.view(Map.of());
        double base = AdminInsight.danger(List.of(v), Map.of()).score();
        h.assertTrue(AdminInsight.danger(List.of(v), Map.of("all.followers", 1.0, "all.followerCount", 12.0)).score()
                > base && AdminInsight.danger(List.of(v), Map.of("all.mirror", 1.0)).score() > base
                && AdminInsight.danger(List.of(v), Map.of("all.chaos", 1.0)).score() > base, "harder");
        done(h);
    }

    // village reactions

    @GameTest(template = EMPTY_STRUCTURE, batch = "p9_villages", timeoutTicks = 200)
    public void villagersFleeHideAndWorshipAndGolemsGoForWildOnes(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerLevel l = h.getLevel();
        BlockPos at = FunGameTests.spot(h, 30);
        IronGolem g = golem(h, at, 0, 0);
        GiantsBridge.put(g, "size", "0.1");
        h.runAfterDelay(3, () -> {
            sweep(h);
            Villager a = villager(l, at, 7.5, 0);
            set(h, s, "villagers", "flee");
            Villages.lookForTests(s);
            h.assertTrue(Villages.tookForTests(a) == Villages.Took.FLED, "fled: " + Villages.tookForTests(a));
            WalkTarget t = a.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            h.assertTrue(t != null && t.getTarget().currentBlockPosition().distSqr(g.blockPosition())
                    > a.blockPosition().distSqr(g.blockPosition()), "running away from him");
            set(h, s, "villagers", "hide");
            Villages.lookForTests(s);
            h.assertTrue(Villages.tookForTests(a) == Villages.Took.HID
                    && a.getBrain().getMemory(MemoryModuleType.HEARD_BELL_TIME).isPresent(), "hiding, as for a raid");
            // worship: round a calm one at his feet's distance, bread once a day
            set(h, s, "villagers", "worship");
            int offered = Villages.offerings;
            Villages.lookForTests(s);
            h.assertTrue(Villages.tookForTests(a) == Villages.Took.WORSHIPPED, "worshipping");
            h.assertTrue(Villages.offerings == offered + 1 && !l.getEntitiesOfClass(ItemEntity.class,
                    g.getBoundingBox().inflate(10), it -> it.getItem().is(Items.BREAD)).isEmpty(), "bread laid before him");
            Villages.lookForTests(s);
            h.assertTrue(Villages.offerings == offered + 1, "once a day");
            // from a hunting one they hide
            GiantsBridge.put(g, "mood", "hunting");
            Villages.lookForTests(s);
            h.assertTrue(Villages.tookForTests(a) == Villages.Took.HID, "a hunting one: they hide");
            // an owned calm one doesn't frighten them
            GiantsBridge.put(g, "mood", "calm");
            GiantsBridge.put(g, "owner", java.util.UUID.randomUUID().toString());
            set(h, s, "villagers", "flee");
            Villager b = villager(l, at, -7.5, 0);
            Villages.lookForTests(s);
            h.assertTrue(Villages.tookForTests(b) == Villages.Took.NONE, "owned and calm: nothing");
            GiantsBridge.put(g, "owner", null);
            // golems: one in his way goes for him; one further off only while he hunts
            set(h, s, "villagers", "off");
            set(h, s, "golems", "on");
            IronGolem near = plainGolem(l, at, 0, 12), far = plainGolem(l, at, 0, -40);
            Villages.lookForTests(s);
            h.assertTrue(near.getTarget() == g && far.getTarget() == null, "near: him; far: nothing yet");
            GiantsBridge.put(g, "mood", "hunting");
            Villages.lookForTests(s);
            h.assertTrue(far.getTarget() == g, "he hunts: the far one comes too");
            near.setTarget(null);
            GiantsBridge.put(g, "owner", java.util.UUID.randomUUID().toString());
            Villages.lookForTests(s);
            h.assertTrue(near.getTarget() == null, "owned: left alone");
            for (var e : List.of(a, b, near, far)) e.discard();
            for (ItemEntity it : l.getEntitiesOfClass(ItemEntity.class, g.getBoundingBox().inflate(10))) it.discard();
            done(h);
        });
    }

    // mob followers

    @GameTest(template = EMPTY_STRUCTURE, batch = "p9_followers", timeoutTicks = 200)
    public void followersGatherKeepNearAndCrumbleAway(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerLevel l = h.getLevel();
        Difficulty was = l.getDifficulty();
        BlockPos at = FunGameTests.spot(h, 31);
        IronGolem g = golem(h, at, 0, 0);
        GiantsBridge.put(g, "size", "0.1");
        ServerPlayer p = PlayersGameTests.join(h, "follow9", false);
        h.runAfterDelay(3, () -> {
            sweep(h);
            s.setDifficulty(Difficulty.NORMAL, true);
            p.teleportTo(l, at.getX() + 0.5, at.getY(), at.getZ() + 30.5, 0, 0);
            set(h, s, "followers", "on");
            set(h, s, "followerCount", "2");
            AABB box = new AABB(at).inflate(120, 40, 120);
            String of = Followers.OF + g.getStringUUID();
            java.util.function.Supplier<List<Mob>> band = () -> l.getEntitiesOfClass(Mob.class, box,
                    m -> m.isAlive() && m.getTags().contains(of));
            Followers.hurryForTests();
            Followers.lookForTests(s);
            h.assertTrue(band.get().size() == 1, "one rose beside him: " + band.get().size());
            for (int i = 0; i < 3; i++) {
                Followers.hurryForTests();
                Followers.lookForTests(s);
            }
            List<Mob> b = band.get();
            h.assertTrue(b.size() == 2, "as many as he may: " + b.size());
            Mob f = b.get(0);
            h.assertTrue(f instanceof Zombie && f.isPersistenceRequired() && f.distanceTo(g) < 40,
                    "his kind (another kind's: zombies), kept, near him");
            try {
                java.lang.reflect.Method burn = Mob.class.getDeclaredMethod("isSunBurnTick");
                burn.setAccessible(true);
                h.assertTrue(!(Boolean) burn.invoke(f), "the sun doesn't burn him");
            } catch (ReflectiveOperationException x) {
                throw new RuntimeException(x);
            }
            // his blows and theirs don't cross
            h.assertTrue(!Followers.allowDamage(f, g.damageSources().mobAttack(g), 5f), "he doesn't hurt his own");
            h.assertTrue(!Followers.allowDamage(g, f.damageSources().mobAttack(f), 5f), "they don't hurt him");
            h.assertTrue(!Followers.allowDamage(f, b.get(1).damageSources().mobAttack(b.get(1)), 5f), "nor each other");
            h.assertTrue(Followers.allowDamage(f, p.damageSources().playerAttack(p), 5f), "a player hurts them");
            // left far behind: brought back beside him
            f.teleportTo(at.getX() + 46.5, at.getY(), at.getZ() + 0.5);
            Followers.lookForTests(s);
            h.assertTrue(f.isAlive() && f.distanceTo(g) < 20, "back beside him: " + f.distanceTo(g) + " " + f.isAlive());
            // owned: the band crumbles away
            GiantsBridge.put(g, "owner", p.getStringUUID());
            Followers.lookForTests(s);
            h.assertTrue(band.get().isEmpty(), "gone when he's owned");
            GiantsBridge.put(g, "owner", null);
            // none in peaceful
            s.setDifficulty(Difficulty.PEACEFUL, true);
            Followers.hurryForTests();
            Followers.lookForTests(s);
            h.assertTrue(band.get().isEmpty(), "peaceful: none");
            s.setDifficulty(Difficulty.NORMAL, true);
            Followers.hurryForTests();
            Followers.lookForTests(s);
            h.assertTrue(band.get().size() == 1, "one again");
            // they die with nothing to show for it: no loot, no experience, and a slime of his doesn't split
            Mob one = band.get().get(0);
            Vec3 where = one.position();
            one.hurt(p.damageSources().playerAttack(p), 1000f);
            h.assertTrue(one.isDeadOrDying(), "it died");
            AABB round = new AABB(where, where).inflate(4);
            h.assertTrue(l.getEntitiesOfClass(ItemEntity.class, round).isEmpty()
                    && l.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, round).isEmpty(),
                    "no loot and no experience");
            net.minecraft.world.entity.monster.Slime slime = EntityType.SLIME.create(l);
            slime.setSize(2, true);
            slime.moveTo(at.getX() + 8.5, at.getY(), at.getZ() + 8.5, 0, 0);
            slime.addTag(Followers.MARK);
            l.addFreshEntity(slime);
            slime.hurt(p.damageSources().playerAttack(p), 1000f);
            slime.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
            h.assertTrue(l.getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,
                    new AABB(at).inflate(20), x -> x.isAlive()).isEmpty(), "his slime doesn't split");
            // off: they crumble away
            set(h, s, "followers", "off");
            Followers.tick(s);
            h.assertTrue(band.get().isEmpty(), "off: gone");
            s.setDifficulty(was, true);
            done(h, p);
        });
    }

    // Weather Gods

    @GameTest(template = EMPTY_STRUCTURE, batch = "p9_weather", timeoutTicks = 200)
    public void weatherCoversHisAreaAndOnlyThePlayersInIt(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerLevel l = h.getLevel();
        BlockPos at = FunGameTests.spot(h, 32);
        IronGolem g = golem(h, at, 0, 0);
        GiantsBridge.put(g, "size", "0.1");
        ServerPlayer p = PlayersGameTests.join(h, "weather9", false), q = PlayersGameTests.join(h, "dry9", false);
        h.runAfterDelay(3, () -> {
            sweep(h);
            // his area: (48 + his height 10) blocks, all of it inside seven tenths
            h.assertTrue(Weather.radius("fakesys", 0.1, 1) == 58, "area " + Weather.radius("fakesys", 0.1, 1));
            p.teleportTo(l, at.getX() + 20.5, at.getY(), at.getZ() + 0.5, 0, 0);
            q.teleportTo(l, at.getX() + 400.5, at.getY(), at.getZ() + 0.5, 0, 0);
            set(h, s, "weather", "on");
            float world = l.getRainLevel(1f);
            Weather.lookForTests(s);
            Weather.In in = Weather.of(p);
            h.assertTrue(in != null && in.kind().equals("rain") && in.strength() == 1f, "in his weather: " + in);
            float want = Math.abs(0.8f - world) <= 0.2f ? 0.8f : world + Math.signum(0.8f - world) * 0.2f;
            h.assertTrue(Math.abs(in.rain() - want) < 1e-4, "the rain eases in: " + in.rain() + " want " + want);
            h.assertTrue(Weather.of(q) == null, "far off: the world's own weather");
            // near the edge: thinner
            p.teleportTo(l, at.getX() + 50.5, at.getY(), at.getZ() + 0.5, 0, 0);
            Weather.lookForTests(s);
            in = Weather.of(p);
            h.assertTrue(in.strength() > 0.4f && in.strength() < 0.5f, "fading at the edge: " + in.strength());
            // strength and area settings
            set(h, s, "weatherStrength", "0.5");
            p.teleportTo(l, at.getX() + 20.5, at.getY(), at.getZ() + 0.5, 0, 0);
            Weather.lookForTests(s);
            h.assertTrue(Weather.of(p).strength() == 0.5f, "half as thick: " + Weather.of(p));
            set(h, s, "weatherArea", "2");
            p.teleportTo(l, at.getX() + 100.5, at.getY(), at.getZ() + 0.5, 0, 0);
            Weather.lookForTests(s);
            h.assertTrue(Weather.of(p) != null && !Weather.of(p).kind().isEmpty(), "a bigger area reaches him");
            // walking out: eased back to the world's own
            p.teleportTo(l, at.getX() + 500.5, at.getY(), at.getZ() + 0.5, 0, 0);
            for (int i = 0; i < 6; i++) Weather.lookForTests(s);
            h.assertTrue(Weather.of(p) == null, "out: the world's own weather again");
            // off: nothing kept
            p.teleportTo(l, at.getX() + 20.5, at.getY(), at.getZ() + 0.5, 0, 0);
            Weather.lookForTests(s);
            set(h, s, "weather", "off");
            Weather.lookForTests(s);
            h.assertTrue(Weather.of(p) == null && Weather.players() == 0, "off: none");
            done(h, p, q);
        });
    }

    // the Underworld

    /** a room under the Nether's roof: solid to y 39, 30 blocks of air, a roof from y 70 (81 blocks across) */
    static BlockPos netherRoom(MinecraftServer s, int x, int z) {
        return netherRoom(s, x, z, 40);
    }

    static BlockPos netherRoom(MinecraftServer s, int x, int z, int r) {
        ServerLevel n = s.getLevel(Level.NETHER);
        int c = (r >> 4) + 1;
        for (int cx = (x >> 4) - c; cx <= (x >> 4) + c; cx++)
            for (int cz = (z >> 4) - c; cz <= (z >> 4) + c; cz++) {
                n.setChunkForced(cx, cz, true);
                n.getChunk(cx, cz);
            }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int y = 1; y <= 104; y++)
                    n.setBlock(m.set(x + dx, y, z + dz), y >= 40 && y < 70 ? Blocks.AIR.defaultBlockState()
                            : Blocks.NETHERRACK.defaultBlockState(), 2);
        return new BlockPos(x, 40, z);
    }

    /** a room under the Nether's roof (netherRoom), or in a world with no roof a floor (y 50-59) with air over it */
    static BlockPos room(MinecraftServer s, ServerLevel l, int x, int z) {
        if (l.dimensionType().hasCeiling()) return netherRoom(s, x, z);
        for (int cx = (x >> 4) - 3; cx <= (x >> 4) + 3; cx++)
            for (int cz = (z >> 4) - 3; cz <= (z >> 4) + 3; cz++) {
                l.setChunkForced(cx, cz, true);
                l.getChunk(cx, cz);
            }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -40; dx <= 40; dx++)
            for (int dz = -40; dz <= 40; dz++)
                for (int y = 50; y <= 120; y++)
                    l.setBlock(m.set(x + dx, y, z + dz), y < 60 ? Blocks.END_STONE.defaultBlockState()
                            : Blocks.AIR.defaultBlockState(), 2);
        return new BlockPos(x, 60, z);
    }

    static void unroom(MinecraftServer s, ServerLevel l, BlockPos at) {
        for (int cx = (at.getX() >> 4) - 5; cx <= (at.getX() >> 4) + 5; cx++)
            for (int cz = (at.getZ() >> 4) - 5; cz <= (at.getZ() >> 4) + 5; cz++) l.setChunkForced(cx, cz, false);
    }

    static void unroom(MinecraftServer s, BlockPos at) {
        ServerLevel n = s.getLevel(Level.NETHER);
        for (int cx = (at.getX() >> 4) - 5; cx <= (at.getX() >> 4) + 5; cx++)
            for (int cz = (at.getZ() >> 4) - 5; cz <= (at.getZ() >> 4) + 5; cz++) n.setChunkForced(cx, cz, false);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p9_under", timeoutTicks = 400)
    public void theUnderworldHoldsTheOverworldAndKeepsOneBelow(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerLevel l = h.getLevel(), nether = s.getLevel(Level.NETHER);
        BlockPos at = FunGameTests.spot(h, 33);
        IronGolem own = golem(h, at, 0, 0);
        own.addTag(GiantsBridge.TAG + ".own");
        BlockPos room = netherRoom(s, 56000, 56000, 64);
        ServerPlayer p = PlayersGameTests.join(h, "under9", false);
        h.runAfterDelay(3, () -> {
            sweep(h);
            p.teleportTo(nether, room.getX() + 0.5, room.getY(), room.getZ() + 0.5, 0, 0);
            Underworld.nearMin = 4;
            Underworld.nearMax = 12;
            String key = "fakesys|" + Underworld.NETHER + "|0";
            try {
                set(h, s, "underworld", "nether");
                Underworld.lookForTests(s);
                h.assertTrue(own.isRemoved(), "the Overworld's own one goes below");
                h.assertTrue(GiantsBridge.called("summon minecraft:the_nether"), "his own summon in the Nether: "
                        + GiantsBridge.calls);
                Underworld.lookForTests(s);
                IronGolem below = null;
                for (IronGolem x : GiantsBridge.golems(s))
                    if (x.level() == nether && x.getTags().contains("fakesys.summoned")) below = x;
                h.assertTrue(below != null && below.getTags().contains(Underworld.MARK), "one of him below, marked");
                double size = Double.parseDouble(GiantsBridge.get(below, "size", "9"));
                h.assertTrue(size * 100 <= 30 && size >= 0.1, "sized to fit the room: " + size);
                h.assertTrue("calm".equals(GiantsBridge.get(below, "mood", "")), "calm");
                SystemsData.Under u = SystemsData.get(s).s.under.get(key);
                h.assertTrue(u != null && u.id.equals(below.getStringUUID()), "kept: " + (u == null ? null : u.id));
                // only one of him there
                GiantsBridge.calls.clear();
                Underworld.lookForTests(s);
                h.assertTrue(!GiantsBridge.called("summon"), "no second one");
                // his own How many come: a second, on a spot of his own well away from the first
                h.assertTrue(AdminServer.set(s, "Tester", "fakesys", "worldCount", "2").ok(), "count 2");
                h.assertTrue(AdminServer.set(s, "Tester", "fakesys", "worldScale", "0.1").ok(), "smaller");
                Underworld.nearMax = 52;
                for (int i = 0; i < 12 && SystemsData.get(s).s.under.getOrDefault(key.replace("|0", "|1"),
                        new SystemsData.Under()).id.isEmpty(); i++) Underworld.lookForTests(s);
                SystemsData.Under second = SystemsData.get(s).s.under.get(key.replace("|0", "|1"));
                h.assertTrue(second != null && !second.id.isEmpty() && !second.id.equals(u.id), "a second one kept");
                IronGolem two = null;
                for (IronGolem x : GiantsBridge.golems(s)) if (x.getStringUUID().equals(second.id)) two = x;
                h.assertTrue(two != null && two.distanceTo(below) >= 30, "apart: " + (two == null ? -1
                        : two.distanceTo(below)));
                two.discard();
                SystemsData.get(s).s.under.remove(key.replace("|0", "|1"));
                AdminWorld.get(s).remove("fakesys", "worldCount");
                AdminWorld.get(s).remove("fakesys", "worldScale");
                AdminServer.push(s, AdminWorld.get(s));
                Underworld.nearMax = 12;
                Systems.forget(); // (his count, looked at every ten seconds, read again)
                GiantsBridge.calls.clear();
                // he falls: the next after his own days (a day)
                below.discard();
                Underworld.lookForTests(s);
                h.assertTrue(u.id.isEmpty() && u.fell > 0 && u.due - u.fell == 24000 && !GiantsBridge.called("summon"),
                        "waits a day: " + u.due);
                h.assertTrue(SystemsAdmin.json(s).getAsJsonObject("now").getAsJsonArray("underworld").get(0)
                        .getAsJsonObject().get("state").getAsString().equals("waits"), "the book says he waits");
                AdminServer.Result r = AdminServer.set(s, "Tester", "fakesys", "worldRespawnDays", "0");
                h.assertTrue(r.ok(), "" + r.lines());
                Underworld.lookForTests(s);
                h.assertTrue(u.due <= nether.getServer().overworld().getGameTime() + 1, "no wait now");
                Underworld.lookForTests(s);
                h.assertTrue(GiantsBridge.called("summon minecraft:the_nether"), "comes again");
                Underworld.lookForTests(s);
                // never, with "Comes back after he falls" off
                AdminServer.set(s, "Tester", "fakesys", "respawn", "off");
                for (IronGolem x : GiantsBridge.golems(s)) if (x.level() == nether) x.discard();
                GiantsBridge.calls.clear();
                Underworld.lookForTests(s);
                Underworld.lookForTests(s);
                h.assertTrue(u.never && !GiantsBridge.called("summon"), "never again");
                // off: the Overworld isn't held
                set(h, s, "underworld", "off");
                h.assertTrue(!GiantsBridge.all.has("all.dimOverworld"), "not held");
            } finally {
                Underworld.nearMin = 48;
                Underworld.nearMax = 160;
                AdminWorld.get(s).clear("fakesys");
                for (IronGolem x : GiantsBridge.golems(s)) if (x.level() == nether) x.discard();
                SystemsData.get(s).s.under.clear();
                p.teleportTo(l, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                unroom(s, room);
            }
            done(h, p);
        });
    }

    // sacred ground

    @GameTest(template = EMPTY_STRUCTURE, batch = "p9_sacred", timeoutTicks = 200)
    public void sacredGroundRefusesBuildingAndBreakingWhileHeLives(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerLevel l = h.getLevel();
        BlockPos at = FunGameTests.spot(h, 34);
        IronGolem g = golem(h, at, 0, 0);
        ServerPlayer p = PlayersGameTests.join(h, "sacred9", false);
        BlockPos b = at.offset(4, 0, 0);
        String biome = l.getBiome(b).unwrapKey().orElseThrow().location().toString();
        h.runAfterDelay(3, () -> {
            sweep(h);
            Sacred.TEST_BIOMES.put(biome, "fakesys");
            try {
                p.setGameMode(GameType.SURVIVAL);
                p.teleportTo(l, b.getX() + 0.5, b.getY() + 1, b.getZ() + 2.5, 0, 0);
                l.setBlock(b, Blocks.STONE.defaultBlockState(), 3);
                h.assertTrue(p.gameMode.destroyBlock(b) && l.getBlockState(b).isAir(), "off: as always");
                l.setBlock(b, Blocks.STONE.defaultBlockState(), 3);
                set(h, s, "sacred", "on");
                Watch.forget();
                Sacred.tick(s);
                h.assertTrue(Sacred.groundOf(l, b).equals("fakesys"), "his ground");
                h.assertTrue(!p.gameMode.destroyBlock(b) && l.getBlockState(b).is(Blocks.STONE), "no breaking");
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(b).add(0, 0.5, 0), Direction.UP, b, false);
                InteractionResult r = p.gameMode.useItemOn(p, l, p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                h.assertTrue(r == InteractionResult.FAIL && l.getBlockState(b.above()).isAir(), "no building: " + r);
                // creative may
                p.setGameMode(GameType.CREATIVE);
                h.assertTrue(p.gameMode.destroyBlock(b) && l.getBlockState(b).isAir(), "creative may");
                l.setBlock(b, Blocks.STONE.defaultBlockState(), 3);
                // he's gone: as always
                p.setGameMode(GameType.SURVIVAL);
                g.discard();
                Watch.forget();
                Sacred.tick(s);
                h.assertTrue(p.gameMode.destroyBlock(b) && l.getBlockState(b).isAir(), "nobody lives: as always");
            } finally {
                Sacred.TEST_BIOMES.clear();
                l.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
                l.setBlock(b.above(), Blocks.AIR.defaultBlockState(), 3);
            }
            set(h, s, "sacred", "off");
            Sacred.tick(s);
            done(h, p);
        });
    }

    // Mirror

    @GameTest(template = EMPTY_STRUCTURE, batch = "p9_mirror", timeoutTicks = 100)
    public void mirrorLearnsTheTacticPlayersLeanOnAndTellsTheGiant(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 35);
        IronGolem g = golem(h, at, 0, 0);
        h.runAfterDelay(3, () -> {
            sweep(h);
            // the sums
            double[] even = Mirror.factors(new double[] {10, 10, 10, 10}, 0.5);
            double[] arrows = Mirror.factors(new double[] {0, 90, 10, 0}, 0.5);
            double[] few = Mirror.factors(new double[] {0, 30, 0, 0}, 0.5);
            h.assertTrue(even[0] == 1 && even[1] == 1 && arrows[1] == 0.5 && arrows[2] == 1 && few[1] == 1,
                    "even: nothing; mostly arrows: half; too little seen: nothing");
            String hit = "{\"sys\":1,\"giant\":\"fakesys\",\"id\":\"" + g.getStringUUID() + "\",\"by\":\"-\","
                    + "\"amount\":10,\"weak\":false,\"tactic\":\"arrows\",\"player\":";
            // off: nothing kept
            Systems.hurt(s, hit + "true}");
            h.assertTrue(SystemsData.get(s).s.mirror.isEmpty(), "off: not learned");
            set(h, s, "mirror", "on");
            for (int i = 0; i < 5; i++) Systems.hurt(s, hit + "true}");
            Systems.hurt(s, hit + "false}");
            SystemsData.Learned m = SystemsData.get(s).s.mirror.get("fakesys");
            h.assertTrue(m != null && m.score[1] == 50 && m.score[0] == 0, "players' arrows only: "
                    + (m == null ? null : java.util.Arrays.toString(m.score)));
            Mirror.tick(s);
            h.assertTrue(GiantsBridge.called("counter") && GiantsBridge.counter.get("arrows").getAsDouble() < 0.6
                    && GiantsBridge.counter.get("melee").getAsDouble() == 1, "told: " + GiantsBridge.counter);
            h.assertTrue(m.told[1], "players near were told");
            JsonObject now = SystemsAdmin.json(s).getAsJsonObject("now");
            h.assertTrue(now.getAsJsonObject("mirror").getAsJsonObject("fakesys").get("arrows").getAsDouble() < 0.6,
                    "the book shows it");
            // off: all of it again
            set(h, s, "mirror", "off");
            Mirror.tick(s);
            h.assertTrue(GiantsBridge.counter.get("arrows").getAsDouble() == 1 && SystemsData.get(s).s.mirror
                    .isEmpty(), "forgotten: " + GiantsBridge.counter);
            done(h);
        });
    }

    // Random chaos

    @GameTest(template = EMPTY_STRUCTURE, batch = "p9_chaos", timeoutTicks = 100)
    public void chaosShufflesSizesMoodsAndTheDaysMoves(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 36);
        IronGolem wild = golem(h, at, 0, 0), owned = golem(h, at, 20, 0);
        GiantsBridge.put(owned, "owner", java.util.UUID.randomUUID().toString());
        h.runAfterDelay(3, () -> {
            sweep(h);
            set(h, s, "chaos", "on");
            List<String> rest = null;
            for (long seed = 1; seed < 40 && (rest == null || rest.isEmpty()); seed++) {
                Chaos.seedForTests(seed);
                GiantsBridge.calls.clear();
                Chaos.shuffle(s, s.overworld().getDayTime() / 24000L);
                rest = SystemsData.get(s).s.rests.get("fakesys");
            }
            h.assertTrue(rest != null && !rest.isEmpty() && rest.size() <= 4, "some of his six moves rest: " + rest);
            for (String m : rest)
                h.assertTrue(GiantsBridge.values.has("move." + m + ".on") && !GiantsBridge.values.get("move." + m
                        + ".on").getAsBoolean(), "sent off: " + m + " " + GiantsBridge.values);
            h.assertTrue(AdminWorld.get(s).values("fakesys").isEmpty(), "the world's own values untouched");
            double size = Double.parseDouble(GiantsBridge.get(wild, "size", "1"));
            h.assertTrue(size >= 0.4 - 1e-6 && size <= 1.8 + 1e-6, "a new size: " + size);
            h.assertTrue(GiantsBridge.called("mood") && GiantsBridge.calls.stream().filter(c -> c.startsWith("mood"))
                    .noneMatch(c -> c.contains(owned.getStringUUID())), "a wild one's mood; the owned one's kept");
            h.assertTrue(SystemsCommand.nowLines(s).stream().anyMatch(x -> x.contains("Random chaos")),
                    "the command says so");
            // the same day: no other shuffle
            int n = Chaos.shuffles;
            Chaos.tick(s);
            h.assertTrue(Chaos.shuffles == n || s.getPlayerList().getPlayers().isEmpty(), "once a day");
            // off: every move back at once
            set(h, s, "chaos", "off");
            Chaos.tick(s);
            h.assertTrue(SystemsData.get(s).s.rests.isEmpty() && !GiantsBridge.values.has("move." + rest.get(0)
                    + ".on"), "back: " + GiantsBridge.values);
            done(h);
        });
    }

    // the book and the rules page

    @GameTest(template = EMPTY_STRUCTURE, batch = "p9_page", timeoutTicks = 100)
    public void theBookTheCommandAndTheRulesPageShowThem(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        h.runAfterDelay(3, () -> {
            set(h, s, "weather", "on");
            set(h, s, "sacred", "on");
            set(h, s, "underworld", "end");
            Sacred.tick(s);
            JsonObject now = SystemsAdmin.json(s).getAsJsonObject("now");
            h.assertTrue(now.has("weatherPlayers") && now.has("followers") && now.has("sacred")
                    && now.getAsJsonArray("underworld") != null, "right now: " + now);
            List<String> lines = SystemsCommand.nowLines(s);
            h.assertTrue(lines.get(0).contains("Weather Gods") && lines.get(0).contains("Underworld")
                    && lines.stream().anyMatch(x -> x.startsWith("  Weather Gods:"))
                    && lines.stream().anyMatch(x -> x.startsWith("  Sacred ground:")), "the command: " + lines);
            JsonObject rules = net.jj.giants.players.RulesPage.json(s);
            String all = String.valueOf(rules.get("rules"));
            h.assertTrue(all.contains("Weather Gods: on.") && all.contains("Underworld: end."), "the rules page: "
                    + all);
            done(h);
        });
    }
}
