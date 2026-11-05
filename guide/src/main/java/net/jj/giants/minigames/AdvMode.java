package net.jj.giants.minigames;

import java.util.List;
import net.jj.giants.adventure.Adventure;
import net.jj.giants.adventure.AdvMaps;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 11, ADMIN_BRIDGE.md §28.5) An adventure map played as a minigame round: the map built into the round's own
 * world, its giants summoned with their scripts, tags and moods, its paths, areas, scripts and hooks live in that world
 * only. The players work together (team lives, as in Escort); the map's hooks end it (/giants adventure win|lose),
 * or its win area, or everyone falling, or the time running out (the map says whether that's a win or a loss).
 */
public final class AdvMode extends Coop {
    final String map;
    private final String title;
    private String goal = "";

    public AdvMode(String map, String title, String goal) {
        this.map = map;
        this.title = title;
        this.goal = goal;
    }

    @Override
    public String id() {
        return "adventure";
    }

    @Override
    public String name() {
        return title;
    }

    @Override
    public String about() {
        return "An adventure map: " + goal;
    }

    @Override
    public String goal() {
        return goal;
    }

    @Override
    public @Nullable String cannot(MinecraftServer server) {
        if (Minigames.kinds().isEmpty()) return "No giants are installed.";
        Object m = AdvMaps.load(server, map, 1, 0.15);
        return m instanceof String why ? why : null;
    }

    @Override
    public @Nullable String challenge() {
        return null;
    }

    /** the map as last loaded for this game (the game's data is cleared each round, so it's kept here) */
    private AdvMaps.Loaded loaded;

    AdvMaps.Loaded loaded(MgGame g) {
        return loaded;
    }

    @Override
    public MgMap map(MgGame g) {
        Object o = AdvMaps.load(g.server, map, g.sizeIndex, g.giantSize);
        AdvMaps.Loaded m = o instanceof AdvMaps.Loaded l ? l : new AdvMaps.Loaded();
        loaded = m;
        if (!m.goal.isEmpty()) goal = m.goal;
        MgMap mm = new MgMap();
        for (Object[] p : m.pieces) mm.add((net.jj.giants.fun.MgShapes.Shape) p[0], (int) p[1], (int) p[2]);
        mm.half = m.half;
        mm.starts.addAll(m.starts);
        mm.watch = m.watch;
        return mm;
    }

    @Override
    public void begin(MgGame g) {
        for (MgGame.P p : g.alive()) {
            ServerPlayer sp = g.online(p);
            if (sp != null) MgKits.fighter(sp);
        }
        giveLives(g);
        g.data.remove("end");
        AdvMaps.Loaded m = loaded(g);
        if (m == null || g.level == null) return;
        Adventure.beginRound(g.server, g.level, m, (kind, at, mood, size, then) -> g.giants.summon(kind, at, mood, size,
                1, 1, "adventure", -1, k -> {
                    var e = g.giants.entity(k);
                    if (e != null) then.accept(e);
                }));
    }

    @Override
    public void tick(MgGame g) {
        if (g.roundTicks % 20 != 0 || g.level == null) return;
        AdvMaps.Loaded m = loaded(g);
        if (m == null || m.winArea.isEmpty()) return;
        if (Adventure.playerInRoundArea(g.level, m.winArea)) g.data.put("end", "win:Someone reached the goal!");
    }

    /** the map's hooks: /giants adventure win|lose [words] */
    public static net.jj.giants.admin.AdminServer.Result end(boolean win, String words) {
        MgGame g = Minigames.current();
        if (g == null || !(g.mode instanceof AdvMode) || g.phase() != MgGame.Phase.PLAY)
            return net.jj.giants.admin.AdminServer.Result.fail("No adventure map is being played right now.");
        if (g.data.containsKey("end")) return net.jj.giants.admin.AdminServer.Result.said(List.of("It's ending already."));
        String line = words == null || words.isBlank() ? (win ? "The players won!" : "The players lost.") : words.strip();
        g.data.put("end", (win ? "win:" : "lose:") + line);
        return net.jj.giants.admin.AdminServer.Result.said(List.of((win ? "Win: " : "Loss: ") + line));
    }

    @Override
    public boolean giantMayBeHurt(MgGame g, MgGiants.G giant, DamageSource src, MgGame.P by) {
        if (by == null) return true;
        var e = g.giants.entity(giant);
        return e == null || !e.getTags().contains(AdvMaps.PROTECT);
    }

    @Override
    public MgGame.End check(MgGame g) {
        Object end = g.data.get("end");
        if (end instanceof String s) {
            boolean win = s.startsWith("win:");
            String line = s.substring(s.indexOf(':') + 1);
            return new MgGame.End(win ? everyone(g) : List.of(), line + (win ? mvpLine(g) : ""));
        }
        return wiped(g, "Everyone fell: " + title + " was lost.");
    }

    @Override
    public MgGame.End timeUp(MgGame g) {
        AdvMaps.Loaded m = loaded(g);
        boolean win = m != null && m.timeUp.equals("win");
        return new MgGame.End(win ? everyone(g) : List.of(), win ? "Time's up: the players held out!"
                : "Time's up: " + title + " wasn't finished in time.");
    }

    @Override
    public String bar(MgGame g) {
        return title + " · " + lives(g) + " lives · " + g.clock();
    }

    @Override
    public void go(MgGame g) {
        g.tellRound(Component.literal(goal).withStyle(ChatFormatting.GOLD));
    }

    /** where a map's spot is in the round (for tests) */
    public static Vec3 spot(double x, double z) {
        return new Vec3(x, 0, z);
    }
}
