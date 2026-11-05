package net.jj.giants.minigames;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.jj.giants.fun.MgShapes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.8) Escort: a calm giant walks an old road across a valley, west to east, and the
 * players keep him alive: bands of creatures come down the banks at him, and twice on the way another giant comes
 * hunting him. He reaches the gatehouse: everyone wins; he falls (or the time runs out): the giants won. The
 * players can't hurt him.
 */
public final class Escort extends Coop {
    static final int[] ROAD = {80, 110, 140};
    static final int WIDTH = 7;
    static final String FOE = "jj_giants.mg_foe";

    @Override
    public String id() {
        return "escort";
    }

    @Override
    public String name() {
        return "Escort";
    }

    @Override
    public String about() {
        return "A calm giant walks the old road across the valley: keep him alive until he reaches the gate.";
    }

    @Override
    public String goal() {
        return "Keep the giant alive until he reaches the gate!";
    }

    @Override
    public String cannot(net.minecraft.server.MinecraftServer server) {
        return Minigames.kinds().isEmpty() ? "No giants are installed." : null;
    }

    static int road(MgGame g) {
        return ROAD[g.sizeIndex];
    }

    static double roadZ(MgGame g, double x) {
        return MgShapes.roadZ(road(g), x);
    }

    @Override
    public MgMap map(MgGame g) {
        MgMap m = new MgMap();
        int half = road(g);
        m.add(MgShapes.road(half, WIDTH), 0, 0);
        m.half = half + 14;
        double sx = -half + 8;
        m.spots.put("start", new Vec3(sx, 0, roadZ(g, sx)));
        m.spots.put("end", new Vec3(half - 8, 0, roadZ(g, half - 8)));
        int n = Math.max(8, g.players().size());
        for (int i = 0; i < n; i++) {
            double x = sx + 10 + (i / 3) * 3;
            m.starts.add(new Vec3(x, 0, roadZ(g, x) + (i % 3 - 1) * 2.5));
        }
        m.watch = new Vec3(sx + 30, 30, roadZ(g, sx + 30));
        return m;
    }

    @Override
    public void begin(MgGame g) {
        for (MgGame.P p : g.alive()) {
            ServerPlayer sp = g.online(p);
            if (sp != null) MgKits.fighter(sp);
        }
        giveLives(g);
        List<String> kinds = shuffled(g);
        String kind = kinds.get(0);
        g.data.put("kind", kind);
        g.data.put("raiders", new ArrayList<>(kinds.subList(1, kinds.size())));
        g.data.put("raids", 0);
        g.data.put("lastX", Double.NaN);
        g.data.put("stuck", 0);
        g.giants.summonHp(kind, g.map.spots.get("start"), "calm", g.giantSize, 260 + 80 * n(g), 0.3, "escort", -1,
                k -> g.data.put("escort", k.id));
    }

    static MgGiants.G escort(MgGame g) {
        Object id = g.data.get("escort");
        return id instanceof UUID u ? g.giants.of(u) : null;
    }

    /** how far along the road he is, 0 to 1 */
    static double progress(MgGame g, LivingEntity e) {
        double sx = g.map.spots.get("start").x, ex = g.map.spots.get("end").x;
        return Math.max(0, Math.min(1, (e.getX() - sx) / (ex - sx)));
    }

    @Override
    public void tick(MgGame g) {
        if (g.roundTicks % 20 != 0 || g.level == null) return;
        MgGiants.G k = escort(g);
        LivingEntity e = k == null ? null : g.giants.entity(k);
        if (e == null) return;
        ServerLevel l = g.level;
        // on along the road: his own wander spot a little ahead of him each second
        double ahead = Math.min(g.map.spots.get("end").x, e.getX() + 28);
        MgGiants.goToward(g, k, ahead, roadZ(g, ahead));
        // stuck for ten seconds (a fight, the banks): a nudge along the road
        if (g.roundTicks % 200 == 0) {
            double last = (double) g.data.getOrDefault("lastX", Double.NaN);
            if (!Double.isNaN(last) && e.getX() - last < 3) {
                double to = Math.min(g.map.spots.get("end").x, e.getX() + 6);
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("dim", l.dimension().location().toString());
                o.addProperty("x", to);
                o.addProperty("y", MgGiants.ground(l, to, roadZ(g, to)));
                o.addProperty("z", roadZ(g, to));
                o.addProperty("where", "land");
                o.addProperty("stay", false);
                net.jj.giants.Giant gi = net.jj.giants.admin.AdminServer.giant(k.kind);
                if (gi != null) net.jj.giants.fun.FunGiants.act(g.server, gi, k.id.toString(), "move", o);
            }
            g.data.put("lastX", e.getX());
        }
        double prog = progress(g, e);
        // creatures down the banks at him, every fifteen seconds
        if (g.roundTicks % 300 == 100) band(g, l, e);
        if (g.roundTicks % 100 == 0) for (Mob m : foes(l)) if (m.getTarget() == null || !m.getTarget().isAlive())
            m.setTarget(e);
        // twice on the way, a giant comes hunting him
        int raids = (int) g.data.getOrDefault("raids", 0);
        if (raids < 2 && prog >= (raids + 1) / 3.0) raid(g, l, k, e, raids);
    }

    static List<Mob> foes(ServerLevel l) {
        List<Mob> out = new ArrayList<>();
        for (var x : l.getAllEntities()) if (x instanceof Mob m && m.isAlive() && m.getTags().contains(FOE)) out.add(m);
        return out;
    }

    private void band(MgGame g, ServerLevel l, LivingEntity e) {
        if (foes(l).size() >= 24) return;
        int n = 2 + g.players().size() / 2;
        EntityType<?>[] kinds = {EntityType.HUSK, EntityType.VINDICATOR, EntityType.SPIDER, EntityType.PILLAGER};
        for (int i = 0; i < n; i++) {
            double x = Math.min(g.map.spots.get("end").x, e.getX() + 26 + l.random.nextInt(22));
            double side = l.random.nextBoolean() ? 1 : -1;
            double z = roadZ(g, x) + side * (WIDTH / 2.0 + 6 + l.random.nextInt(8));
            if (!(kinds[l.random.nextInt(kinds.length)].create(l) instanceof Mob m)) continue;
            m.moveTo(x, MgGiants.ground(l, x, z), z, l.random.nextFloat() * 360, 0);
            m.finalizeSpawn(l, l.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            m.addTag(FOE);
            m.setPersistenceRequired();
            l.addFreshEntity(m);
            m.setTarget(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void raid(MgGame g, ServerLevel l, MgGiants.G k, LivingEntity e, int raids) {
        g.data.put("raids", raids + 1);
        List<String> left = (List<String>) g.data.get("raiders");
        String kind = left.isEmpty() ? k.kind : left.remove(0);
        double x = Math.min(g.map.spots.get("end").x - 10, e.getX() + 45);
        double side = raids % 2 == 0 ? 1 : -1;
        Vec3 at = new Vec3(x, 0, roadZ(g, x) + side * 26);
        g.giants.summonHp(kind, at, "hunting", g.giantSize, 90 + 40 * n(g), 0.45, "raider", -1,
                r -> MgGiants.meet(g, r, k));
        g.tellRound(Component.literal("A " + Minigames.name(kind) + " comes down the bank for him!")
                .withStyle(ChatFormatting.RED));
    }

    @Override
    public boolean giantMayBeHurt(MgGame g, MgGiants.G giant, DamageSource src, MgGame.P by) {
        // the players' blows never land on the one they protect
        return !(giant.role.equals("escort") && by != null);
    }

    @Override
    public void giantFell(MgGame g, MgGiants.G giant) {
        if (!giant.role.equals("raider")) return;
        MgGame.P by = giant.lastBy == null ? null : g.of(giant.lastBy);
        if (by != null) by.score++;
        g.tellRound(Component.literal("The " + Minigames.name(giant.kind) + " that came for him falls"
                + (by != null ? " to " + by.name : "") + "!").withStyle(ChatFormatting.GREEN));
        reward(g, new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.ARROW, 12));
    }

    @Override
    public MgGame.End check(MgGame g) {
        MgGiants.G k = escort(g);
        if (k == null) return null;
        String name = "the " + Minigames.name(k.kind);
        if (k.fell || k.gone) return new MgGame.End(List.of(), cap(name) + " fell on the road.");
        LivingEntity e = g.giants.entity(k);
        if (e != null && e.getX() >= g.map.spots.get("end").x - 6)
            return new MgGame.End(everyone(g), cap(name) + " reached the gate safely!" + mvpLine(g));
        return wiped(g, "Everyone fell: " + name + " was left alone on the road.");
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public MgGame.End timeUp(MgGame g) {
        MgGiants.G k = escort(g);
        return new MgGame.End(List.of(), "Time's up: the " + (k == null ? "giant" : Minigames.name(k.kind))
                + " didn't reach the gate in time.");
    }

    @Override
    public String bar(MgGame g) {
        MgGiants.G k = escort(g);
        LivingEntity e = k == null ? null : g.giants.entity(k);
        if (e == null) return name() + " · " + g.clock();
        return name() + " · the " + Minigames.name(k.kind) + " " + Math.round(progress(g, e) * 100) + "% of the way"
                + " · his health " + Math.round(e.getHealth() / Math.max(1, e.getMaxHealth()) * 100) + "% · "
                + lives(g) + " lives · " + g.clock();
    }

    @Override
    public float barProgress(MgGame g) {
        MgGiants.G k = escort(g);
        LivingEntity e = k == null ? null : g.giants.entity(k);
        return e == null ? 0 : (float) progress(g, e);
    }
}
