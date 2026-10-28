package net.jj.hollowbell.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.FightWith;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Who he's in a fight with, for his fight music (FightWith): it starts when he targets you or a hit lands either way,
 * lasts the whole fight through any pause while he still holds a grudge, and ends when he forgives, dies, backs down,
 * or you die or go out of range. A hit alone only starts one for 20 seconds; a calm one nearby is no fight.
 */
public class FightWithTests implements FabricGameTest {
    private static ServerPlayer player(GameTestHelper h, Vec3 at) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "fight-with"), false);
        ServerPlayer p = new ServerPlayer(
                h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0f, 0f);
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static void drop(ServerPlayer p) {
        p.getServer().getPlayerList().remove(p);
    }

    private static HollowbellEntity giant(GameTestHelper h) {
        HollowbellEntity g = h.spawn(ModEntities.HOLLOWBELL, new BlockPos(1, 2, 1));
        g.setBellScale(0.1f);
        return g;
    }

    private static void target(HollowbellEntity g, LivingEntity t) {
        g.setTarget(t);
    }

    /** he lets it all go: no target, no grudge */
    private static void forgive(HollowbellEntity g) {
        g.forgiveAll();
        target(g, null);
    }

    private static boolean grudge(HollowbellEntity g, ServerPlayer p) {
        return g.holdsGrudge(p);
    }

    private static long now(GameTestHelper h) {
        return h.getLevel().getGameTime();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "fight_with_calm")
    public void aCalmOneNearbyIsNoFight(GameTestHelper h) {
        HollowbellEntity g = giant(h);
        ServerPlayer p = player(h, h.absoluteVec(new Vec3(1.5, 2, 14.5)));
        try {
            forgive(g);
            h.assertFalse(FightWith.update(g, p, now(h)), "a calm one nearby, nobody hurt: no fight");
            h.assertFalse(FightWith.engaged(g, p), "remembered a fight that never was");
        } finally {
            drop(p);
            g.discard();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "fight_with_target")
    public void hisTargetStartsItAndLettingGoEndsIt(GameTestHelper h) {
        HollowbellEntity g = giant(h);
        ServerPlayer p = player(h, h.absoluteVec(new Vec3(1.5, 2, 14.5)));
        // (once he has settled in: a fresh one isn't awake for a tick or two)
        h.runAfterDelay(20, () -> {
            try {
                forgive(g);
                target(g, p);
                h.assertTrue(FightWith.targetOf(g) == p, "the target didn't take");
                h.assertTrue(FightWith.update(g, p, now(h)), "his target is you: a fight");
                h.assertTrue(FightWith.update(g, p, now(h) + 1200), "still his target a minute on: still a fight");
                forgive(g);
                h.assertFalse(FightWith.update(g, p, now(h) + 1210), "no target and no grudge: over");
            } finally {
                drop(p);
                g.discard();
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "fight_with_his_hit")
    public void hisHitStartsItForTwentySeconds(GameTestHelper h) {
        HollowbellEntity g = giant(h);
        ServerPlayer p = player(h, h.absoluteVec(new Vec3(1.5, 2, 14.5)));
        // (a player who just joined can't be hurt for three seconds)
        h.runAfterDelay(80, () -> {
            try {
                forgive(g);
                // (he may have had a go at you while you waited: start clean)
                FightWith.endAll(p.getUUID());
                long t = now(h);
                h.assertFalse(FightWith.starts(g, p, t), "a fight before any hit");
                p.hurt(g.damageSources().mobAttack(g), 1f);
                h.assertTrue(FightWith.recent(g, p, t), "his hit on you wasn't noted");
                forgive(g);
                h.assertTrue(FightWith.starts(g, p, t + 10), "his hit didn't start a fight");
                h.assertTrue(FightWith.starts(g, p, t + FightWith.HOLD_TICKS), "a hit counts for 20 seconds");
                h.assertFalse(FightWith.starts(g, p, t + FightWith.HOLD_TICKS + 1), "a hit counted past 20 seconds");
            } finally {
                drop(p);
                g.discard();
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "fight_with_grudge")
    public void yourHitStartsItAndHisGrudgeKeepsItThroughAPause(GameTestHelper h) {
        HollowbellEntity g = giant(h);
        ServerPlayer p = player(h, h.absoluteVec(new Vec3(1.5, 2, 14.5)));
        h.runAfterDelay(20, () -> {
            try {
                forgive(g);
                long t = now(h);
                g.hurt(g.damageSources().playerAttack(p), 2f);
                h.assertTrue(FightWith.recent(g, p, t), "your hit on him wasn't noted");
                h.assertTrue(FightWith.update(g, p, t), "your hit didn't start a fight");
                h.assertTrue(grudge(g, p), "he holds no grudge after you hit him");
                target(g, null);
                // a minute with no hits either way: his grudge holds, so the fight (and the music) goes on
                h.assertTrue(FightWith.update(g, p, t + 1200), "a 60 s pause ended the fight while he holds a grudge");
                forgive(g);
                h.assertFalse(FightWith.update(g, p, t + 1210), "he forgave you, but the fight went on");
                h.assertFalse(FightWith.update(g, p, t + 1220), "the old hit started it again");
            } finally {
                drop(p);
                g.discard();
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "fight_with_ends")
    public void itEndsWhenHeDiesBacksDownOrYouGoOrDie(GameTestHelper h) {
        HollowbellEntity g = giant(h);
        ServerPlayer p = player(h, h.absoluteVec(new Vec3(1.5, 2, 14.5)));
        h.runAfterDelay(20, () -> {
            try {
                long t = now(h);
                // backing down
                target(g, p);
                h.assertTrue(FightWith.update(g, p, t), "no fight with you as his target");
                g.addTag("jj_giant_yield");
                h.assertFalse(FightWith.update(g, p, t + 10), "he backed down, but the fight went on");
                g.removeTag("jj_giant_yield");
                // out of his range
                h.assertTrue(FightWith.update(g, p, t + 20), "no fight once he stopped backing down");
                Vec3 was = p.position();
                p.teleportTo(was.x + FightWith.range(g) + 50, was.y, was.z);
                h.assertFalse(FightWith.update(g, p, t + 30), "out of his range, but the fight went on");
                p.teleportTo(was.x, was.y, was.z);
                // you die
                h.assertTrue(FightWith.update(g, p, t + 40), "no fight back in range with you as his target");
                p.setHealth(0f);
                h.assertFalse(FightWith.update(g, p, t + 50), "you died, but the fight went on");
                p.setHealth(20f);
                FightWith.endAll(p.getUUID());
                forgive(g);
                h.assertFalse(FightWith.update(g, p, t + 60), "a fight after you respawned, nothing new");
                // he dies
                target(g, p);
                h.assertTrue(FightWith.update(g, p, t + 70), "no fight after he took you as his target again");
                g.hurt(g.damageSources().genericKill(), Float.MAX_VALUE);
                h.assertFalse(FightWith.update(g, p, t + 80), "he died, but the fight went on");
            } finally {
                drop(p);
                g.discard();
            }
            h.succeed();
        });
    }
}
