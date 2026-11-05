package net.jj.giants.test;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.minigames.MgData;
import net.jj.giants.minigames.MgGame;
import net.jj.giants.minigames.MgGiants;
import net.jj.giants.minigames.Minigames;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Part 10A: each minigame mode's win (and loss) with stand-in giants (net.jj.fakemg, net.jj.fakemg2): Boss Rush,
 * Siege, Escort, Legends, Possession, Rival Tamers, Bounty Board, Kaiju War, One Life, Speedrun (Giant Royale:
 * MinigameGameTests). Each has its own batch.
 */
public class MinigameModeGameTests implements FabricGameTest {
    static final Giant FAKE2 = new Giant("fakemg2", "fakemg2", "fakemg2", "net.jj.fakemg2.GiantsBridge", List.of(),
            Set.of());

    static MinecraftServer fresh(GameTestHelper h, boolean two) {
        MinecraftServer s = MinigameGameTests.fresh(h);
        MinigameGameTests.set(h, s, "mgMinutes", "2");
        if (two) {
            AdminServer.useGiants(List.of(MinigameGameTests.FAKE, FAKE2));
            FunGiants.TEST_KINDS.put(net.jj.fakemg2.GiantsBridge.TAG, "fakemg2");
            net.jj.fakemg2.GiantsBridge.reset();
        }
        return s;
    }

    static void done(GameTestHelper h, ServerPlayer... ps) {
        for (var gm : net.jj.fakemg2.GiantsBridge.golems(h.getLevel().getServer())) gm.discard();
        FunGiants.TEST_KINDS.remove(net.jj.fakemg2.GiantsBridge.TAG);
        MinigameGameTests.done(h, ps);
    }

    static MgGame game() {
        return MinigameGameTests.game();
    }

    static GameTestSequence start(GameTestHelper h, MinecraftServer s, String mode, ServerPlayer... ps) {
        AdminServer.Result r = Minigames.start(s, "Tester", mode, false, 1);
        h.assertTrue(r.ok(), mode + " opened: " + r.lines());
        for (ServerPlayer p : ps) h.assertTrue(Minigames.join(p, false).ok(), "joined");
        Minigames.now(s, "Tester");
        return h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(game().phase() == MgGame.Phase.PLAY, "playing: " + game().phase()))
                .thenExecute(() -> MinigameGameTests.arrived(ps));
    }

    static boolean over() {
        MgGame g = Minigames.current();
        return g == null || g.phase() == MgGame.Phase.ROUND_END || g.phase() == MgGame.Phase.RESULTS;
    }

    static void killStanding() {
        MgGame g = Minigames.current();
        if (g == null) return;
        for (MgGiants.G k : g.giants.standing()) {
            LivingEntity e = g.giants.entity(k);
            if (e != null && !e.isDeadOrDying()) e.kill();
        }
    }

    /** the last result: its line, and whether this player won it */
    static void result(GameTestHelper h, MinecraftServer s, ServerPlayer p, boolean won, String has) {
        var res = MgData.get(s).s.results;
        h.assertTrue(!res.isEmpty(), "a result kept");
        var r = res.get(res.size() - 1);
        h.assertTrue(r.line.contains(has), "the line: " + r.line);
        h.assertTrue(r.winners.contains(p.getGameProfile().getName()) == won, "won " + won + ": " + r.winners);
    }

    static MinigameGameTests.Joined join(GameTestHelper h, String name) {
        return MinigameGameTests.join(h, name, false);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 14000, batch = "mgm_bossrush")
    public void bossRushIsWonByBringingDownTheTarget(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h, false);
        ServerPlayer a = join(h, "mgbr").p();
        start(h, s, "bossrush", a)
                .thenExecute(() -> h.assertTrue((int) game().data.get("target") == 3, "target for one player"))
                .thenWaitUntil(() -> {
                    killStanding();
                    h.assertTrue(over(), "over");
                })
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "finished"))
                .thenExecute(() -> {
                    result(h, s, a, true, "brought down together");
                    h.assertTrue(net.jj.giants.players.Challenges.done(a, "mg_bossrush"), "challenge");
                    done(h, a);
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 14000, batch = "mgm_siege")
    public void siegeIsWonByHoldingThroughEveryWave(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h, false);
        ServerPlayer a = join(h, "mgsg").p();
        start(h, s, "siege", a)
                .thenExecute(() -> h.assertTrue(game().level.getDayTime() == 13200, "dusk"))
                .thenWaitUntil(() -> {
                    killStanding();
                    h.assertTrue(over(), "over, wave " + game().data.get("wave"));
                })
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "finished"))
                .thenExecute(() -> {
                    result(h, s, a, true, "held through all 4 waves");
                    done(h, a);
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 14000, batch = "mgm_escort")
    public void escortIsWonAtTheGateAndThePlayersCantHurtHim(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h, false);
        ServerPlayer a = join(h, "mges").p();
        LivingEntity[] e = {null};
        start(h, s, "escort", a)
                .thenWaitUntil(() -> {
                    MgGiants.G k = game().giants.standing().isEmpty() ? null : game().giants.standing().get(0);
                    e[0] = k == null ? null : game().giants.entity(k);
                    h.assertTrue(e[0] != null && k.role.equals("escort"), "the escorted one");
                })
                .thenExecute(() -> {
                    float hp = e[0].getHealth();
                    e[0].hurt(e[0].damageSources().playerAttack(a), 5);
                    h.assertTrue(e[0].getHealth() == hp, "a player's blow doesn't land on him");
                })
                .thenWaitUntil(() -> h.assertTrue(net.jj.fakemg.GiantsBridge.calls.stream().anyMatch(c ->
                        c.startsWith("sysact") && c.contains("\"go\"")), "sent along the road"))
                .thenExecute(() -> {
                    Vec3 end = game().map.spots.get("end");
                    e[0].teleportTo(end.x, e[0].getY(), end.z);
                })
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "finished"))
                .thenExecute(() -> {
                    result(h, s, a, true, "reached the gate");
                    done(h, a);
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 14000, batch = "mgm_escort_lost")
    public void escortIsLostWhenHeFalls(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h, false);
        ServerPlayer a = join(h, "mgesl").p();
        start(h, s, "escort", a)
                .thenWaitUntil(() -> {
                    h.assertTrue(!game().giants.standing().isEmpty(), "the escorted one");
                    killStanding();
                })
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "finished"))
                .thenExecute(() -> {
                    result(h, s, a, false, "fell on the road");
                    done(h, a);
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 14000, batch = "mgm_legends")
    public void legendsDownedPlayersStayDownUntilTheLegendFalls(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h, false);
        ServerPlayer a = join(h, "mglga").p(), b = join(h, "mglgb").p();
        start(h, s, "legends", a, b)
                .thenWaitUntil(() -> h.assertTrue(!game().giants.standing().isEmpty(), "the first legend"))
                .thenExecute(() -> {
                    b.hurt(b.damageSources().genericKill(), Float.MAX_VALUE);
                    h.assertTrue(game().of(b.getUUID()).down && b.isSpectator(), "B down");
                })
                .thenIdle(200)
                .thenExecute(() -> {
                    h.assertTrue(game().of(b.getUUID()).down, "still down while the legend stands");
                    killStanding();
                })
                .thenWaitUntil(() -> h.assertTrue(!game().of(b.getUUID()).down && !b.isSpectator(), "B back up"))
                .thenWaitUntil(() -> {
                    killStanding();
                    h.assertTrue(over(), "over");
                })
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "finished"))
                .thenExecute(() -> {
                    result(h, s, a, true, "Every legend has fallen");
                    done(h, a, b);
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 14000, batch = "mgm_possession")
    public void possessionHandsTheControlsOutAndScoresKnockOuts(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h, false);
        ServerPlayer a = join(h, "mgpoa").p(), b = join(h, "mgpob").p();
        ServerPlayer[] held = {null};
        start(h, s, "possession", a, b)
                .thenWaitUntil(() -> {
                    held[0] = a.isPassenger() ? a : b.isPassenger() ? b : null;
                    h.assertTrue(held[0] != null, "someone at the giant's controls");
                })
                .thenExecute(() -> {
                    ServerPlayer foot = held[0] == a ? b : a;
                    boolean pvp = s.isPvpAllowed();
                    s.setPvpAllowed(true);
                    // a knock-out on foot scores 1 (past the grace at Go)
                    held[0].removeAllEffects();
                    held[0].hurt(held[0].damageSources().playerAttack(foot), Float.MAX_VALUE);
                    s.setPvpAllowed(pvp);
                    h.assertTrue(game().of(foot.getUUID()).score == 1, "1 for a knock-out on foot");
                    h.assertTrue(game().of(held[0].getUUID()).down, "down for a moment");
                    Minigames.stop(s, "Tester");
                    h.assertTrue(!a.isPassenger() && !b.isPassenger(), "nobody left at the controls");
                    done(h, a, b);
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 14000, batch = "mgm_rivals")
    public void rivalTamersTheLastTeamGiantStandingWins(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h, true);
        ServerPlayer a = join(h, "mgrva").p(), b = join(h, "mgrvb").p();
        start(h, s, "rivals", a, b)
                .thenWaitUntil(() -> h.assertTrue(game().giants.standing().size() == 2, "a giant for each team"))
                .thenExecute(() -> {
                    MgGame.P pa = game().of(a.getUUID()), pb = game().of(b.getUUID());
                    h.assertTrue(pa.team != pb.team, "two teams");
                    MgGiants.G mine = null, theirs = null;
                    for (MgGiants.G k : game().giants.standing())
                        if (k.team == pa.team) mine = k;
                        else theirs = k;
                    LivingEntity em = game().giants.entity(mine);
                    float hp = em.getHealth();
                    em.hurt(em.damageSources().playerAttack(a), 5);
                    h.assertTrue(em.getHealth() == hp, "your own team's giant is safe from you");
                    game().giants.entity(theirs).kill();
                })
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "finished"))
                .thenExecute(() -> {
                    result(h, s, a, true, "last standing");
                    result(h, s, b, false, "last standing");
                    done(h, a, b);
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 14000, batch = "mgm_bounty")
    public void bountyBoardPostsContractsAndTheMostPointsWin(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h, false);
        ServerPlayer a = join(h, "mgbta").p(), b = join(h, "mgbtb").p();
        start(h, s, "bounty", a, b)
                .thenWaitUntil(() -> h.assertTrue(!((List<?>) game().data.get("jobs")).isEmpty(), "a bounty posted"))
                .thenExecute(() -> game().of(a.getUUID()).score = 5)
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "time up, finished"))
                .thenExecute(() -> {
                    result(h, s, a, true, "points");
                    result(h, s, b, false, "points");
                    done(h, a, b);
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 14000, batch = "mgm_kaiju")
    public void kaijuWarTheGiantsFightAndSurvivorsWin(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h, true);
        ServerPlayer a = join(h, "mgkja").p(), b = join(h, "mgkjb").p();
        start(h, s, "kaiju", a, b)
                .thenWaitUntil(() -> h.assertTrue(net.jj.fakemg.GiantsBridge.calls.stream().anyMatch(c ->
                        c.startsWith("meet")), "the giants go for each other"))
                .thenExecute(() -> b.hurt(b.damageSources().genericKill(), Float.MAX_VALUE))
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "time up, finished"))
                .thenExecute(() -> {
                    result(h, s, a, true, "survived");
                    result(h, s, b, false, "survived");
                    done(h, a, b);
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 14000, batch = "mgm_onelife")
    public void oneLifeEveryGiantOnceSurvivorsWin(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h, true);
        ServerPlayer a = join(h, "mgola").p(), b = join(h, "mgolb").p();
        start(h, s, "onelife", a, b)
                .thenExecute(() -> b.hurt(b.damageSources().genericKill(), Float.MAX_VALUE))
                .thenExecute(() -> h.assertTrue(!game().of(b.getUUID()).alive, "one life: B is out"))
                .thenWaitUntil(() -> {
                    killStanding();
                    h.assertTrue(over(), "over");
                })
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "finished"))
                .thenExecute(() -> {
                    result(h, s, a, true, "Every giant has fallen");
                    result(h, s, b, false, "Every giant has fallen");
                    done(h, a, b);
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 14000, batch = "mgm_speedrun")
    public void speedrunKeepsTheBestTime(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h, false);
        ServerPlayer a = join(h, "mgsr").p();
        start(h, s, "speedrun", a)
                .thenWaitUntil(() -> {
                    killStanding();
                    h.assertTrue(over(), "over");
                })
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "finished"))
                .thenExecute(() -> {
                    result(h, s, a, true, "fell in");
                    MgData.Player rec = MgData.get(s).s.players.get(a.getStringUUID());
                    h.assertTrue(rec != null && rec.best > 0, "best time kept");
                    done(h, a);
                });
    }
}
