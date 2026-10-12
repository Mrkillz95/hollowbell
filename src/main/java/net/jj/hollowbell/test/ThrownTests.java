package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

/** What is shot or thrown at him: it hurts him the way a blow does, and what it was is never lost. */
public class ThrownTests implements FabricGameTest {
    private static final float S = 0.3f;

    /** sends it straight down at his crown from a few blocks over it */
    private static void shootAtCrown(GameTestHelper h, HollowbellEntity e, Projectile p) {
        e.ensurePose();
        Vec3 from = e.crownWorld().add(0, 3, 0);
        p.setPos(from.x, from.y, from.z);
        p.setNoGravity(true);
        p.setDeltaMovement(0, -0.8, 0);
        h.getLevel().addFreshEntity(p);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "thrown_keep")
    public void aTridentOrTippedArrowShotAtHimIsNeverLost(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 605);
        ServerPlayer[] pl = new ServerPlayer[1];
        ThrownTrident[] tr = new ThrownTrident[1];
        Vec3[] near = new Vec3[1];
        float[] hp = new float[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            pl[0] = player(h, e.position().add(100 * S + 20, 0, 0));
            hp[0] = e.healthNow();
            tr[0] = new ThrownTrident(h.getLevel(), 0, 0, 0, new ItemStack(Items.TRIDENT));
            tr[0].setOwner(pl[0]);
            tr[0].pickup = AbstractArrow.Pickup.ALLOWED;
            shootAtCrown(h, e, tr[0]);
            Arrow a = new Arrow(h.getLevel(), 0, 0, 0, PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.POISON), null);
            a.setOwner(pl[0]);
            a.pickup = AbstractArrow.Pickup.ALLOWED;
            shootAtCrown(h, e, a);
            near[0] = e.crownWorld();
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(e.healthNow() < hp[0], "nothing he was shot with hurt him");
            h.assertTrue(!tr[0].isRemoved(), "the trident thrown at him is gone");
            var dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(near[0], near[0]).inflate(12));
            boolean tipped = dropped.stream().anyMatch(i -> i.getItem().is(Items.TIPPED_ARROW));
            boolean plain = dropped.stream().anyMatch(i -> i.getItem().is(Items.ARROW));
            h.assertTrue(tipped && !plain, "the tipped arrow came back as " + dropped.stream().map(i -> i.getItem().toString()).toList());
            for (ItemEntity i : dropped) i.discard();
            tr[0].discard();
            drop(pl[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "thrown_invuln")
    public void invulnerableArrowsDoNotHurtHim(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 606);
        ServerPlayer[] pl = new ServerPlayer[1];
        float[] hp = new float[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            e.setInvulnerable(true);
            pl[0] = player(h, e.position().add(100 * S + 20, 0, 0));
            hp[0] = e.healthNow();
            for (int i = 0; i < 3; i++) {
                Arrow a = new Arrow(h.getLevel(), 0, 0, 0, new ItemStack(Items.ARROW), null);
                a.setOwner(pl[0]);
                a.setBaseDamage(10);
                shootAtCrown(h, e, a);
            }
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(e.healthNow() >= hp[0], "invulnerable, arrows still took him from " + hp[0] + " to " + e.healthNow());
            e.setInvulnerable(false);
            drop(pl[0]);
            release(h, e);
            h.succeed();
        });
    }
}
