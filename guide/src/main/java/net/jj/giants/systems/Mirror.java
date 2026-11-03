package net.jj.giants.systems;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

/**
 * (Part 9, ADMIN_BRIDGE.md 25.8) Mirror: the giants learn how players fight them. Each hit a player lands is told by
 * the giant ("syshurt", with its tactic: melee, arrows, magic or blasts); the Guide keeps, for each kind, what each
 * tactic has done to him lately (fading: about a fifth lost every ten minutes). Once he has taken enough (40 points of
 * damage), a tactic that has done more than 40% of it is cut, down to "Most they learn" at 90% and over; it is sent to every one of that kind ("sysact" "counter") and he applies it in his own hurt path, to
 * players' hits only. Players near him are told when he learns a tactic. Off: everything back to x1.
 */
public final class Mirror {
    private Mirror() {}

    public static final List<String> TACTICS = List.of("melee", "arrows", "magic", "blasts");
    static final List<String> WORDS = List.of("blades and fists", "arrows", "magic", "blasts");
    /** what a kind must have taken before he learns anything */
    static final double ENOUGH = 40;

    /** what each kind was last sent this run */
    private static final Map<String, double[]> pushed = new HashMap<>();

    static void forget() {
        pushed.clear();
    }

    /** a player's hit on a giant, by its tactic */
    static void hurt(MinecraftServer server, String id, String tactic, boolean player, double amount) {
        if (!player || !Systems.on(server, "mirror")) return;
        int i = TACTICS.indexOf(tactic);
        if (i < 0 || !(amount > 0)) return;
        LivingEntity e = FunGiants.find(server, id);
        String kind = e == null ? null : FunGiants.kindOf(e);
        if (kind == null) return;
        SystemsData d = SystemsData.get(server);
        SystemsData.Learned m = d.s.mirror.computeIfAbsent(kind, k -> new SystemsData.Learned());
        m.score[i] += Math.min(40, amount);
        d.setDirty();
    }

    /** every ten seconds: the learning fades a little, what each kind counters is worked out and sent */
    public static void tick(MinecraftServer server) {
        SystemsData d = SystemsData.get(server);
        if (!Systems.on(server, "mirror")) {
            if (!d.s.mirror.isEmpty() || !pushed.isEmpty()) {
                d.s.mirror.clear();
                d.setDirty();
                for (Giant g : AdminServer.giants())
                    if (pushed.containsKey(g.key())) push(server, g, new double[] {1, 1, 1, 1});
                pushed.clear();
            }
            return;
        }
        double most = Systems.num(server, "mirrorMost");
        for (Giant g : AdminServer.giants()) {
            SystemsData.Learned m = d.s.mirror.get(g.key());
            if (m == null) continue;
            double total = 0;
            for (int i = 0; i < 4; i++) {
                m.score[i] *= 0.996;
                total += m.score[i];
            }
            double[] f = factors(m.score, most);
            boolean changed = false;
            for (int i = 0; i < 4; i++) if (Math.abs(f[i] - m.factor[i]) > 1e-9) changed = true;
            m.factor = f;
            double[] was = pushed.get(g.key());
            if (was == null || changed || !java.util.Arrays.equals(was, f)) {
                if (push(server, g, f)) pushed.put(g.key(), f.clone());
            }
            for (int i = 0; i < 4; i++) {
                if (f[i] <= 0.8 && !m.told[i]) {
                    m.told[i] = true;
                    tell(server, g, Component.literal("The " + AdminServer.nameOf(g) + " is learning your "
                            + WORDS.get(i) + ": they hurt him less now.").withStyle(ChatFormatting.GOLD));
                } else if (f[i] >= 1) m.told[i] = false;
            }
            d.setDirty();
            if (total < 0.5) d.s.mirror.remove(g.key());
        }
        SystemsAdmin.changed();
    }

    /** what each tactic does to him from these scores: all of it until it has done more than 40% (rounded to 0.05) */
    public static double[] factors(double[] score, double most) {
        double total = 0;
        for (double s : score) total += s;
        double[] f = {1, 1, 1, 1};
        if (total < ENOUGH) return f;
        for (int i = 0; i < 4; i++) {
            double share = score[i] / total;
            double cut = most * Math.max(0, Math.min(1, (share - 0.4) / 0.5));
            f[i] = Math.round((1 - cut) * 20) / 20.0;
        }
        return f;
    }

    /** his own counters: every one of him (true when his mod took them) */
    static boolean push(MinecraftServer server, Giant g, double[] f) {
        if (!Look.sys(server, g).contains("counter")) return false;
        JsonObject more = new JsonObject();
        JsonObject c = new JsonObject();
        for (int i = 0; i < 4; i++) c.addProperty(TACTICS.get(i), f[i]);
        more.add("counter", c);
        List<String> r = Systems.act(server, g, "-", "counter", more);
        return r != null && !r.isEmpty() && r.get(0).equals("ok");
    }

    private static void tell(MinecraftServer server, Giant g, Component line) {
        for (LivingEntity e : FunGiants.standing(server, g.key())) Look.tellNear(e, line, 160);
    }

    /** (the book) what each kind counters now: kind -> {tactic: factor} for the ones under x1 */
    static JsonObject json(MinecraftServer server) {
        JsonObject o = new JsonObject();
        if (!Systems.on(server, "mirror")) return o;
        for (var e : SystemsData.get(server).s.mirror.entrySet()) {
            JsonObject k = new JsonObject();
            double total = 0;
            for (double s : e.getValue().score) total += s;
            for (int i = 0; i < 4; i++) {
                k.addProperty(TACTICS.get(i), e.getValue().factor[i]);
                k.addProperty(TACTICS.get(i) + "Share", total <= 0 ? 0 : Math.round(e.getValue().score[i] / total
                        * 100) / 100.0);
            }
            k.addProperty("seen", Math.round(total));
            o.add(e.getKey(), k);
        }
        return o;
    }
}
