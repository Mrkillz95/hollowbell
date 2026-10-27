package net.jj.hollowbell.test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.command.GiantsCommand;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The /giants commands that change or find the giants (health, damage, preset, heal, calm, sleep, wake, freeze,
 * summon, info, home, where) are for operators only, and each one reaches him through his bridge.
 */
public class GiantsCommandTests implements FabricGameTest {

    private static final String[] OPS_ONLY = {
        "giants home hollowbell",
        "giants health",
        "giants health 1.2",
        "giants damage 1.2 hollowbell",
        "giants preset hard",
        "giants heal",
        "giants calm hollowbell",
        "giants sleep",
        "giants wake",
        "giants freeze",
        "giants unfreeze hollowbell",
        "giants info",
        "giants summon hollowbell calm 0.2",
        "giants where"
    };

    private static CommandSourceStack op(GameTestHelper h) {
        return h.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withLevel(h.getLevel())
                .withPermission(4)
                .withSuppressedOutput();
    }

    /** runs a command: the count it returned, or -1 if it didn't parse or threw */
    private static int run(GameTestHelper h, CommandSourceStack src, String cmd) {
        try {
            return h.getLevel().getServer().getCommands().getDispatcher().execute(cmd, src);
        } catch (Exception e) {
            return -1;
        }
    }

    private static HollowbellEntity spawn(GameTestHelper h) {
        HollowbellEntity g = h.spawn(ModEntities.HOLLOWBELL, new BlockPos(1, 2, 1));
        g.setBellScale(0.1f);
        return g;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "giants_cmd_ops")
    public void newGiantsCommandsNeedCheats(GameTestHelper h) {
        var d = h.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack op = op(h);
        CommandSourceStack anyone = op.withPermission(0);
        for (String c : OPS_ONLY) {
            h.assertTrue(d.parse(c, anyone).getReader().canRead(), "/" + c + " works without cheats");
            var r = d.parse(c, op);
            h.assertTrue(!r.getReader().canRead() && r.getExceptions().isEmpty(), "/" + c + " doesn't parse for an op");
        }
        h.assertTrue(d.parse("giants find hollowbell", op).getReader().canRead(), "/giants find is still there");
        h.assertTrue(d.parse("giants detail", op).getReader().canRead(), "/giants detail is there");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "giants_cmd_health")
    public void healthMultiplierKeepsHisShareOfHealth(GameTestHelper h) {
        float was = BellConfig.V.healthMultiplier;
        try {
            BellConfig.V.healthMultiplier = 1.0f;
            HollowbellEntity g = spawn(h);
            float max = g.healthMax();
            g.setHealthTo(max * 0.5f);
            h.assertTrue(run(h, op(h), "giants health 1.2") > 0, "/giants health 1.2 failed");
            h.assertTrue(Math.abs(BellConfig.V.healthMultiplier - 1.2f) < 1.0E-4f, "the multiplier wasn't saved");
            float now = g.healthMax();
            h.assertTrue(Math.abs(now - max * 1.2f) < 1.0f, "most health " + now + ", not 1.2x " + max);
            h.assertTrue(
                    Math.abs(g.healthNow() / g.healthMax() - 0.5f) < 0.01f,
                    "his share of health went from half to " + g.healthNow() / g.healthMax());
            List<String> said = GiantsCommand.ask(h.getLevel().getServer(), "healthmult", "");
            h.assertTrue(said.contains("Hollowbell: health x1.2."), "bare health says " + said);
            List<String> bad = GiantsCommand.ask(h.getLevel().getServer(), "healthmult", "50");
            h.assertTrue(bad.get(0).contains("0.1 to 10"), "a multiplier of 50 was taken: " + bad);
            g.discard();
        } finally {
            BellConfig.V.healthMultiplier = was;
            BellConfig.save();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "giants_cmd_preset")
    public void presetSetsHealthAndDamageTogether(GameTestHelper h) {
        float health = BellConfig.V.healthMultiplier, damage = BellConfig.V.damageMultiplier;
        try {
            CommandSourceStack op = op(h);
            h.assertTrue(run(h, op, "giants preset hard") > 0, "/giants preset hard failed");
            h.assertTrue(
                    BellConfig.V.healthMultiplier == 1.5f && BellConfig.V.damageMultiplier == 1.35f,
                    "hard set " + BellConfig.V.healthMultiplier + "/" + BellConfig.V.damageMultiplier);
            h.assertTrue(run(h, op, "giants preset easy hollowbell") > 0, "/giants preset easy failed");
            h.assertTrue(
                    BellConfig.V.healthMultiplier == 0.75f && BellConfig.V.damageMultiplier == 0.75f,
                    "easy set " + BellConfig.V.healthMultiplier + "/" + BellConfig.V.damageMultiplier);
            h.assertTrue(run(h, op, "giants damage 2") > 0, "/giants damage 2 failed");
            h.assertTrue(BellConfig.V.damageMultiplier == 2.0f, "/giants damage didn't reach his own multiplier");
            h.assertTrue(run(h, op, "giants preset normal") > 0, "/giants preset normal failed");
            h.assertTrue(
                    BellConfig.V.healthMultiplier == 1.0f && BellConfig.V.damageMultiplier == 1.0f,
                    "normal set " + BellConfig.V.healthMultiplier + "/" + BellConfig.V.damageMultiplier);
        } finally {
            BellConfig.V.healthMultiplier = health;
            BellConfig.V.damageMultiplier = damage;
            BellConfig.save();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "giants_cmd_forward")
    public void giantsCommandsReachHim(GameTestHelper h) {
        HollowbellEntity g = spawn(h);
        CommandSourceStack op = op(h);
        g.setHealthTo(g.healthMax() * 0.3f);
        h.assertTrue(run(h, op, "giants heal hollowbell") > 0, "/giants heal hollowbell failed");
        h.assertTrue(g.healthNow() >= g.healthMax() - 0.5f, "/giants heal didn't heal him");
        h.assertTrue(run(h, op, "giants freeze") > 0, "/giants freeze failed");
        h.assertTrue(g.frozen(), "/giants freeze didn't freeze him");
        h.assertTrue(run(h, op, "giants unfreeze hollowbell") > 0, "/giants unfreeze failed");
        h.assertTrue(!g.frozen(), "/giants unfreeze didn't let him go");
        g.setVariant(HollowbellEntity.HUNTER);
        h.assertTrue(run(h, op, "giants calm") > 0, "/giants calm failed");
        h.assertTrue(g.variant() == HollowbellEntity.CALM, "/giants calm didn't calm him");
        List<String> info = GiantsCommand.ask(h.getLevel().getServer(), "info", "");
        h.assertTrue(
                info.size() == 1
                        && info.get(0).startsWith("Hollowbell: ")
                        && info.get(0).contains(" alive, health ")
                        && info.get(0).contains("damage x"),
                "/giants info said " + info);
        h.assertTrue(run(h, op, "giants wake hollowbell") > 0, "/giants wake failed");
        g.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "giants_cmd_summon")
    public void giantsSummonUsesHisOwnSummon(GameTestHelper h) {
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
        CommandSourceStack op = op(h).withPosition(at.getCenter());
        Set<UUID> before = new HashSet<>();
        for (HollowbellEntity e : h.getLevel().getEntities(ModEntities.HOLLOWBELL, e -> true)) {
            before.add(e.getUUID());
        }
        h.assertTrue(run(h, op, "giants summon hollowbell calm 0.1") > 0, "/giants summon failed");
        HollowbellEntity made = null;
        for (HollowbellEntity e : h.getLevel().getEntities(ModEntities.HOLLOWBELL, e -> true)) {
            if (!before.contains(e.getUUID())) made = e;
        }
        h.assertTrue(made != null, "no Hollowbell came");
        HollowbellEntity g = made;
        h.assertTrue(g.variant() == HollowbellEntity.CALM, "he didn't come calm");
        h.assertTrue(Math.abs(g.bellScale() - 0.1f) < 0.01f, "he came at size " + g.bellScale());
        made.discard();
        String bad = GiantsCommand.ask(h.getLevel().getServer(), "summon", "minecraft:overworld 0 80 0 - grumpy", null)
                .get(0);
        h.assertTrue(bad.startsWith("Hollowbell: "), "a bad mood said " + bad);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "giants_cmd_ground")
    public void groundSpotIsOneMachineLine(GameTestHelper h) {
        for (String s : GiantsCommand.ask(h.getLevel().getServer(), "groundspot", "")) {
            h.assertTrue(s.matches("groundspot hollowbell -?\\d+ -?\\d+ \\d+"), "groundspot said " + s);
        }
        h.succeed();
    }
}
