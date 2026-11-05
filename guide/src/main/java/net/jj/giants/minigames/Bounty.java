package net.jj.giants.minigames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.8) Bounty Board: three calm giants wander the arena, and contracts are posted on the
 * board during the round (up to three open at once): land so many blows on a certain giant, deal so much damage to
 * one, stay close beside one for a while, or knock out the creatures that came. The first to finish a contract takes
 * its points; most points when the time runs out wins. Hit a giant and he may not stay calm.
 */
public final class Bounty extends MgMode {
    static final int[] ARENA = {48, 64, 80};
    static final String FOE = "jj_giants.mg_bounty";

    /** one contract */
    static final class Job {
        String kind, giant, line;
        int need, points;
        /** where each player stood when it was posted (blows, damage) or how far they've got (time, creatures) */
        final Map<UUID, Double> base = new HashMap<>(), got = new HashMap<>();
        @Nullable UUID done;
    }

    @Override
    public String id() {
        return "bounty";
    }

    @Override
    public String name() {
        return "Bounty Board";
    }

    @Override
    public String about() {
        return "Contracts are posted as you play: hit this giant so often, stand beside that one... first to finish"
                + " takes the points.";
    }

    @Override
    public String goal() {
        return "Finish the contracts first: the most points wins!";
    }

    @Override
    public MgMap map(MgGame g) {
        return Coop.arenaMap(g, ARENA);
    }

    @Override
    public void begin(MgGame g) {
        for (MgGame.P p : g.alive()) {
            ServerPlayer sp = g.online(p);
            if (sp != null) MgKits.light(sp);
        }
        g.data.put("jobs", new ArrayList<Job>());
        g.data.put("kinds", new ArrayList<>(Coop.shuffled(g)));
        for (int i = 0; i < Math.min(3, Minigames.kinds().size()); i++) giant(g, i);
    }

    @SuppressWarnings("unchecked")
    private void giant(MgGame g, int i) {
        List<String> kinds = (List<String>) g.data.get("kinds");
        if (kinds.isEmpty()) kinds.addAll(Coop.shuffled(g));
        if (kinds.isEmpty()) return;
        String kind = kinds.remove(0);
        g.giants.summonHp(kind, Coop.gate(g, i).scale(0.4), "calm", g.giantSize, 160 + 60 * Coop.n(g), 0.45, "quarry",
                -1, x -> {});
    }

    @SuppressWarnings("unchecked")
    static List<Job> jobs(MgGame g) {
        return (List<Job>) g.data.get("jobs");
    }

    private void post(MgGame g) {
        List<MgGiants.G> st = g.giants.standing();
        ServerLevel l = g.level;
        if (l == null) return;
        Job j = new Job();
        int roll = l.random.nextInt(st.isEmpty() ? 1 : 4);
        MgGiants.G k = st.isEmpty() ? null : st.get(l.random.nextInt(st.size()));
        int n = Coop.n(g);
        if (k == null || roll == 0) {
            j.kind = "foes";
            j.need = 3;
            j.points = 2;
            j.line = "Knock out 3 of the husks that just came in";
            for (int i = 0; i < 2 + n; i++) {
                if (!(EntityType.HUSK.create(l) instanceof Mob m)) continue;
                Vec3 at = Coop.gate(g, l.random.nextInt(4));
                m.moveTo(at.x, MgGiants.ground(l, at.x, at.z), at.z, 0, 0);
                m.finalizeSpawn(l, l.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
                m.addTag(FOE);
                m.setPersistenceRequired();
                l.addFreshEntity(m);
            }
        } else {
            j.giant = k.id.toString();
            String name = "the " + Minigames.name(k.kind);
            switch (roll) {
                case 1 -> {
                    j.kind = "hits";
                    j.need = 8 + 2 * Math.min(4, n);
                    j.points = 3;
                    j.line = "Land " + j.need + " blows on " + name;
                    for (var e : k.hits.entrySet()) j.base.put(e.getKey(), (double) e.getValue());
                }
                case 2 -> {
                    j.kind = "damage";
                    j.need = 40 + 10 * Math.min(6, n);
                    j.points = 3;
                    j.line = "Deal " + j.need + " damage to " + name;
                    j.base.putAll(k.dealt);
                }
                default -> {
                    j.kind = "near";
                    j.need = 15;
                    j.points = 2;
                    j.line = "Stay within 10 blocks of " + name + " for 15 seconds";
                }
            }
        }
        jobs(g).add(j);
        g.tellRound(Component.literal("New bounty (" + j.points + " points): " + j.line + ".")
                .withStyle(ChatFormatting.AQUA));
    }

    private double progress(MgGame g, Job j, MgGame.P p) {
        MgGiants.G k = j.giant == null ? null : g.giants.of(UUID.fromString(j.giant));
        return switch (j.kind) {
            case "hits" -> k == null ? 0 : k.hits.getOrDefault(p.id, 0) - j.base.getOrDefault(p.id, 0.0);
            case "damage" -> k == null ? 0 : k.dealt.getOrDefault(p.id, 0.0) - j.base.getOrDefault(p.id, 0.0);
            default -> j.got.getOrDefault(p.id, 0.0);
        };
    }

    @Override
    public void tick(MgGame g) {
        if (g.roundTicks % 20 != 0) return;
        List<Job> js = jobs(g);
        long open = js.stream().filter(j -> j.done == null).count();
        if (g.roundTicks == 20 || (open < 3 && g.roundTicks % 600 == 0)) post(g);
        // standing beside one: a second for each second within 10 blocks (not while down)
        for (Job j : js) {
            if (j.done != null) continue;
            MgGiants.G k = j.giant == null ? null : g.giants.of(UUID.fromString(j.giant));
            if (j.giant != null && (k == null || k.fell || k.gone)) {
                j.done = new UUID(0, 0);
                g.tellRound(Component.literal("Bounty gone (the giant fell): " + j.line + ".").withStyle(
                        ChatFormatting.GRAY));
                continue;
            }
            if (j.kind.equals("near")) {
                LivingEntity e = k == null ? null : g.giants.entity(k);
                for (MgGame.P p : g.alive()) {
                    ServerPlayer sp = g.online(p);
                    if (e == null || sp == null || p.down) continue;
                    double d = Math.max(0, sp.distanceTo(e) - net.jj.giants.fun.FunGiants.reach(k.kind,
                            net.jj.giants.fun.FunGiants.sizeOf(e)) * 0.5);
                    if (d <= 10) j.got.merge(p.id, 1.0, Double::sum);
                }
            }
            for (MgGame.P p : g.alive())
                if (progress(g, j, p) >= j.need) {
                    finish(g, j, p);
                    break;
                }
        }
        // the quarry: three calm giants, a new one when one falls
        if (g.giants.standing().size() + (g.giants.waiting() ? 1 : 0) < Math.min(3, Minigames.kinds().size())
                && g.roundTicks % 200 == 0) giant(g, g.roundTicks / 200);
    }

    private void finish(MgGame g, Job j, MgGame.P p) {
        j.done = p.id;
        p.score += j.points;
        g.tellRound(Component.literal(p.name + " claims the bounty (+" + j.points + "): " + j.line + ".")
                .withStyle(ChatFormatting.GOLD));
        ServerPlayer sp = g.online(p);
        if (sp != null) sp.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.MASTER, 0.8f, 1.2f);
        Minigames.changed();
    }

    @Override
    public void creatureDied(MgGame g, LivingEntity e, DamageSource src) {
        if (!e.getTags().contains(FOE) || !(src.getEntity() instanceof ServerPlayer by)) return;
        MgGame.P p = g.of(by.getUUID());
        if (p == null) return;
        for (Job j : jobs(g)) if (j.done == null && j.kind.equals("foes")) j.got.merge(p.id, 1.0, Double::sum);
    }

    @Override
    public void knockedOut(MgGame g, MgGame.P p, ServerPlayer sp, DamageSource src) {
        if (p.score > 0) p.score--;
        g.down(p, sp, 5, "Knocked out! (-1 point)");
    }

    @Override
    public MgGame.End timeUp(MgGame g) {
        return Possession.mostPoints(g, "points");
    }

    @Override
    public String bar(MgGame g) {
        long open = jobs(g) == null ? 0 : jobs(g).stream().filter(j -> j.done == null).count();
        return name() + " · " + open + " bounties open · " + g.clock();
    }

    @Override
    public Component status(MgGame g, MgGame.P p) {
        Job best = null;
        double bp = -1;
        if (jobs(g) != null)
            for (Job j : jobs(g)) {
                if (j.done != null) continue;
                double f = progress(g, j, p) / Math.max(1, j.need);
                if (f > bp) {
                    bp = f;
                    best = j;
                }
            }
        String now = best == null ? "no bounty open" : best.line + ": " + (int) Math.min(best.need, progress(g, best, p))
                + "/" + best.need;
        return Component.literal(p.score + " points · " + now).withStyle(ChatFormatting.AQUA);
    }
}
