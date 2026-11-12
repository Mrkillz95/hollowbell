package net.jj.giants.minigames;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.8) Speedrun: one giant waits in the middle of a small arena; bring him down as fast
 * as you can together (the clock starts at Go). Everyone wins when he falls, and each player's best time is kept (the
 * book and the Guide show it).
 */
public final class Speedrun extends Coop {
    static final int[] ARENA_R = {32, 40, 56};

    @Override
    public String id() {
        return "speedrun";
    }

    @Override
    public String name() {
        return "Speedrun";
    }

    @Override
    public String about() {
        return "One giant, a stopwatch: bring him down as fast as you can together. Best times are kept.";
    }

    @Override
    public String goal() {
        return "Bring him down as fast as you can!";
    }

    @Override
    int lives(int players) {
        return players;
    }

    @Override
    public MgMap map(MgGame g) {
        return arenaMap(g, ARENA_R);
    }

    @Override
    public void begin(MgGame g) {
        for (MgGame.P p : g.alive()) {
            ServerPlayer sp = g.online(p);
            if (sp != null) MgKits.champion(sp);
        }
        giveLives(g);
        String kind = shuffled(g).get(0);
        g.data.put("kind", kind);
        g.giants.summonHp(kind, Vec3.ZERO, "hunting", g.giantSize, 120 + 60 * n(g), 0.45, "target", -1, x -> {});
    }

    static String time(long ticks) {
        long ms = ticks * 50;
        return ms / 60000 + ":" + String.format("%02d.%d", (ms / 1000) % 60, (ms / 100) % 10);
    }

    @Override
    public void giantFell(MgGame g, MgGiants.G giant) {
        // (a fall is found before the round's clock ticks on in the same tick: this tick counts. A giant brought
        // down right at Go would otherwise take 0:00.0, which is no time at all and never kept as a best)
        int time = g.roundTicks + 1;
        g.data.put("time", time);
        MgData d = MgData.get(g.server);
        for (MgGame.P p : g.players()) {
            MgData.Player rec = d.player(p.id.toString(), p.name);
            if (rec.best <= 0 || time < rec.best) {
                rec.best = time;
                ServerPlayer sp = g.online(p);
                if (sp != null) sp.sendSystemMessage(Component.literal("Your best time yet: " + time(time) + "!")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        d.setDirty();
    }

    @Override
    public MgGame.End check(MgGame g) {
        if (g.data.get("time") instanceof Integer t)
            return new MgGame.End(everyone(g), "The " + Minigames.name((String) g.data.get("kind")) + " fell in "
                    + time(t) + "!" + mvpLine(g));
        return wiped(g, "The " + Minigames.name((String) g.data.getOrDefault("kind", "")) + " won: no lives left.");
    }

    @Override
    public MgGame.End timeUp(MgGame g) {
        return new MgGame.End(List.of(), "Time's up: the " + Minigames.name((String) g.data.getOrDefault("kind", ""))
                + " is still standing.");
    }

    @Override
    public String bar(MgGame g) {
        return name() + " · " + time(g.roundTicks) + " · " + lives(g) + " lives";
    }

    @Override
    public float barProgress(MgGame g) {
        MgGiants.G k = g.giants.standing().isEmpty() ? null : g.giants.standing().get(0);
        var e = k == null ? null : g.giants.entity(k);
        return e == null ? 0 : e.getHealth() / Math.max(1, e.getMaxHealth());
    }
}
