package net.jj.giants.systems;

import java.util.List;
import java.util.Map;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.tools.Watch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (Part 9, ADMIN_BRIDGE.md 25.3) Village reactions, every second: villagers near a giant flee from him, hide (the
 * raid bell's hiding: they go indoors and come out a little after he has gone) or worship him (gather round a calm
 * one at a respectful distance, look up at him, and leave him bread once a game day); iron golems go for a wild giant
 * who hunts or fights near them, or walks into their village. Only villagers and golems already loaded near a giant
 * standing in loaded land are touched; nothing is loaded and nothing is kept once he has gone.
 */
public final class Villages {
    private Villages() {}

    /** what the villagers near one giant do (for the tests) */
    public enum Took { NONE, FLED, HID, WORSHIPPED }

    public static void tick(MinecraftServer server) {
        String mode = Systems.option(server, "villagers");
        boolean golems = Systems.on(server, "golems");
        if ((mode.isEmpty() || mode.equals("off")) && !golems) return;
        if (server.getPlayerList().getPlayers().isEmpty() && !forced) return;
        for (Map.Entry<String, List<Watch.Row>> e : Watch.rows(server).entrySet())
            for (Watch.Row r : e.getValue()) {
                if (!"here".equals(r.state())) continue;
                LivingEntity giant = FunGiants.find(server, r.id());
                if (giant == null || !(giant.level() instanceof ServerLevel sl)) continue;
                double size = AdminJson.num(r.r(), "size", FunGiants.sizeOf(giant));
                double reach = FunGiants.reach(e.getKey(), size);
                boolean wild = AdminJson.str(r.r(), "ownerId", 40).isEmpty();
                boolean asleep = Look.flag(r.r(), "asleep"), fighting = r.fighting();
                boolean hunting = "hunting".equals(AdminJson.str(r.r(), "mood", 16));
                if (!mode.isEmpty() && !mode.equals("off")) villagers(server, sl, giant, mode, reach, wild, asleep,
                        fighting, hunting);
                if (golems && wild && !asleep) golems(sl, giant, reach, fighting || hunting);
            }
    }

    /** how far villagers notice him */
    static double noticed(double reach) {
        return Math.min(96, 20 + reach * 0.8);
    }

    private static void villagers(MinecraftServer server, ServerLevel sl, LivingEntity giant, String mode,
            double reach, boolean wild, boolean asleep, boolean fighting, boolean hunting) {
        double range = noticed(reach);
        boolean threat = fighting || wild && !asleep;
        String does = mode;
        // worshipped only while he's at peace; a hunting or fighting one makes them hide
        if (mode.equals("worship") && (fighting || hunting)) does = "hide";
        if (!does.equals("worship") && !threat) return;
        List<Villager> near = sl.getEntitiesOfClass(Villager.class, giant.getBoundingBox().inflate(range, 32, range),
                v -> v.isAlive() && !v.isSleeping() && v.distanceToSqr(giant) <= range * range * 1.2);
        if (near.isEmpty()) return;
        long now = sl.getGameTime();
        int worshippers = 0;
        double ring = Math.max(6, Math.min(range - 4, reach * 0.7 + 4));
        // (worship: a ring round him, evenly spaced, starting from where the first of them stands)
        near.sort(java.util.Comparator.comparing(Villager::getUUID));
        double base = Math.atan2(near.get(0).getZ() - giant.getZ(), near.get(0).getX() - giant.getX());
        double tall = FunGiants.height(FunGiants.kindOf(giant) == null ? "" : FunGiants.kindOf(giant),
                FunGiants.sizeOf(giant));
        BlockPos head = BlockPos.containing(giant.getX(), giant.getY() + Math.max(2, tall * 0.85), giant.getZ());
        List<Vec3> feet = new java.util.ArrayList<>();
        int index = 0;
        for (Villager v : near) {
            Vec3 away = new Vec3(v.getX() - giant.getX(), 0, v.getZ() - giant.getZ());
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            away = away.normalize();
            switch (does) {
                case "flee" -> {
                    BlockPos to = BlockPos.containing(v.position().add(away.scale(16)));
                    v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, 0.75f, 1));
                    v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(giant, true));
                    remember(v, Took.FLED);
                }
                case "hide" -> {
                    // (the bell: they run for their homes and stay in until a little after he has gone)
                    v.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
                    remember(v, Took.HID);
                }
                default -> {
                    // his place in the ring, facing him: he walks there, then stays, looking up at his head
                    double a = base + index++ * Math.PI * 2 / near.size();
                    Vec3 spot = giant.position().add(Math.cos(a) * ring, 0, Math.sin(a) * ring);
                    double off = Math.hypot(v.getX() - spot.x, v.getZ() - spot.z);
                    v.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                            new WalkTarget(BlockPos.containing(spot.x, v.getY(), spot.z), 0.5f, 0));
                    if (off <= 2) {
                        worshippers++;
                        feet.add(new Vec3(spot.x + (giant.getX() - spot.x) / ring * 1.5, v.getY(),
                                spot.z + (giant.getZ() - spot.z) / ring * 1.5));
                    }
                    v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
                            new net.minecraft.world.entity.ai.behavior.BlockPosTracker(head));
                    v.getLookControl().setLookAt(head.getX() + 0.5, head.getY(), head.getZ() + 0.5);
                    if (off <= 2)
                        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getEyeY() + 0.5, v.getZ(), 2, 0.25,
                                0.15, 0.25, 0);
                    remember(v, Took.WORSHIPPED);
                }
            }
        }
        // (the offering once most of the ring stands in place, so it is laid all round him)
        if (worshippers > 0 && worshippers * 5 >= near.size() * 3) offering(server, sl, giant, feet);
    }

    /**
     * once a game day the villagers round a giant they worship each lay bread down in front of them, toward him (with
     * Hunger he eats it)
     */
    private static void offering(MinecraftServer server, ServerLevel sl, LivingEntity giant, List<Vec3> feet) {
        SystemsData d = SystemsData.get(server);
        long day = server.overworld().getDayTime() / 24000L;
        Long last = d.s.offered.get(giant.getStringUUID());
        if (last != null && last == day) return;
        d.s.offered.put(giant.getStringUUID(), day);
        if (d.s.offered.size() > 256) d.s.offered.remove(d.s.offered.keySet().iterator().next());
        d.setDirty();
        for (Vec3 at : feet.subList(0, Math.min(8, feet.size()))) {
            ItemEntity it = new ItemEntity(sl, at.x, at.y + 0.3, at.z, new ItemStack(Items.BREAD, 1), 0, 0.05, 0);
            // (left lying a while: the villagers themselves would pick bread straight back up)
            it.setPickUpDelay(2400);
            sl.addFreshEntity(it);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.5, at.z, 6, 0.3, 0.3, 0.3, 0);
        }
        offerings++;
    }

    private static void golems(ServerLevel sl, LivingEntity giant, double reach, boolean angry) {
        double far = angry ? 48 + reach * 0.5 : 16 + reach * 0.5;
        for (IronGolem g : sl.getEntitiesOfClass(IronGolem.class, giant.getBoundingBox().inflate(far, 24, far),
                x -> x.isAlive() && FunGiants.kindOf(x) == null)) {
            if (g.distanceToSqr(giant) > far * far * 1.5) continue;
            LivingEntity t = g.getTarget();
            if (t == giant || t != null && t.isAlive() && FunGiants.kindOf(t) != null) continue;
            if (t != null && t.isAlive() && t.distanceToSqr(g) < 16 * 16) continue; // (busy close by)
            g.setTarget(giant);
            golemsSent++;
        }
    }

    // (the tests)

    private static boolean forced;
    public static int offerings, golemsSent;
    private static final String TOOK = "jj_giants.sys.took=";

    private static void remember(Villager v, Took t) {
        if (!forced) return;
        for (String x : List.copyOf(v.getTags())) if (x.startsWith(TOOK)) v.removeTag(x);
        v.addTag(TOOK + t.name());
    }

    /** what the systems last had this villager do (only noted while the tests look) */
    public static Took tookForTests(Villager v) {
        for (String x : v.getTags()) if (x.startsWith(TOOK)) return Took.valueOf(x.substring(TOOK.length()));
        return Took.NONE;
    }

    public static void lookForTests(MinecraftServer server) {
        forced = true;
        try {
            Watch.forget();
            tick(server);
        } finally {
            forced = false;
        }
    }
}
