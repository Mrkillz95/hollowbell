package net.jj.hollowbell.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.fx.BigFx;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.world.NoWait;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The heavy moves: the whirlpool, the sky dive, the deep toll, the stinger storm, the sun lances, the undertow, and the
 * egg rain, pod burst and spores.
 */
final class BellHeavyMoves {
    private final BellMoves mv;
    private final HollowbellEntity h;
    private final BellRig rig;

    BellHeavyMoves(BellMoves mv) {
        this.mv = mv;
        this.h = mv.h;
        this.rig = mv.rig;
    }

    /** the spore cloud: the egg clumps burst into drifting clouds of poison that follow what he's after */
    void spores() {
        float s = mv.s();
        List<Vec3> at = new ArrayList<>();
        for (int i = 0; i < rig.eggs.length && at.size() < 4; i++)
            if (!h.state.eggGone[(i * 7) % rig.eggs.length]) at.add(h.eggWorld((i * 7) % rig.eggs.length));
        if (mv.target != null) at.add(mv.target.position());
        if (at.isEmpty()) at.add(h.position());
        for (Vec3 p : at) {
            double gy = h.groundAt(p.x, p.z);
            AreaEffectCloud c = new AreaEffectCloud(mv.level(), p.x, Math.max(gy, Math.min(p.y, gy + 4 * s + 1)), p.z);
            c.setRadius(3f + 5f * s);
            c.setDuration(260);
            c.setWaitTime(0);
            c.setRadiusPerTick(0f);
            c.setPotionContents(new PotionContents(Potions.POISON));
            c.setOwner(h);
            mv.level().addFreshEntity(c);
            mv.clouds.add(c);
            mv.fx(BigFx.CLOUD, new Vec3(p.x, gy, p.z), mv.px(6, 4), Vec3.ZERO, 0x7FBF3F);
            h.particles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                    p,
                    30,
                    2 * s + 0.5,
                    0.2);
        }
        h.sound(h.position().add(0, 50 * s, 0), ModSounds.SPORES, 3f, 0.7f);
        h.sound(h.position().add(0, 50 * s, 0), ModSounds.EGG_BURST, 3f, 0.6f);
    }

    /** the clouds drift after what he's hunting, and whatever is in one is hurt as well as poisoned */
    void cloudsTick() {
        if (mv.clouds.isEmpty()) return;
        float s = mv.s();
        LivingEntity tg = h.getTarget();
        for (Iterator<AreaEffectCloud> it = mv.clouds.iterator(); it.hasNext(); ) {
            AreaEffectCloud c = it.next();
            if (c.isRemoved()) {
                it.remove();
                continue;
            }
            if (tg != null && tg.isAlive()) {
                Vec3 d = tg.position().subtract(c.position()).multiply(1, 0, 1);
                if (d.length() > 1) {
                    Vec3 step = d.normalize().scale(0.05 + 0.06 * s);
                    double gy = h.groundAt(c.getX() + step.x, c.getZ() + step.z);
                    c.setPos(c.getX() + step.x, Math.max(gy, c.getY() + (gy + 1 - c.getY()) * 0.1), c.getZ() + step.z);
                }
            }
            if (c.tickCount % 10 == 0) {
                double r = c.getRadius();
                for (LivingEntity e : mv.level()
                        .getEntitiesOfClass(
                                LivingEntity.class,
                                new AABB(c.position(), c.position()).inflate(r, 3 + 3 * s, r),
                                x -> h.fairGame(x) && h.fightingGiant(x))) {
                    if (Math.hypot(e.getX() - c.getX(), e.getZ() - c.getZ()) > r) continue;
                    e.invulnerableTime = 0;
                    e.hurt(h.damageSources().indirectMagic(c, h), h.dmg(5f, e));
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
                }
            }
        }
    }

    /** the pod burst: every pod left swells and sprays poison goo straight down */
    void podBurst() {
        float s = mv.s();
        if (mv.t < Moves.POD_AT || mv.t > Moves.POD_AT + 16 || mv.t % 2 != 0) return;
        if (mv.t == Moves.POD_AT) {
            h.sound(h.position().add(0, 40 * s, 0), ModSounds.GOO, 4f, 0.6f);
            for (int i = 0; i < rig.pods.length; i++) {
                if (h.isPodPopped(i)) continue;
                Vec3 pod = h.podWorld(i);
                mv.fx(BigFx.SPARKS, pod, mv.px(4, 2), new Vec3(0, -1, 0), 0x9CFF6A);
                mv.fx(BigFx.CLOUD, new Vec3(pod.x, h.groundAt(pod.x, pod.z), pod.z), 5 * s + 3, Vec3.ZERO, 0x9CFF6A);
            }
        }
        double r = 5 * s + 3;
        for (int i = 0; i < rig.pods.length; i++) {
            if (h.isPodPopped(i)) continue;
            Vec3 c = h.podWorld(i);
            for (int k = 0; k < 6; k++) {
                double ox = (h.getRandom().nextDouble() - 0.5) * r,
                        oz = (h.getRandom().nextDouble() - 0.5) * r;
                mv.level()
                        .sendParticles(
                                ParticleTypes.ITEM_SLIME,
                                c.x + ox * 0.3,
                                c.y,
                                c.z + oz * 0.3,
                                0,
                                ox * 0.06,
                                -1.0,
                                oz * 0.06,
                                1.0);
            }
            mv.level().sendParticles(ParticleTypes.SNEEZE, c.x, c.y - 2 * s, c.z, 4, r * 0.3, 1, r * 0.3, 0.02);
            double gy = h.groundAt(c.x, c.z);
            for (LivingEntity e : mv.near(new AABB(c.x - r, gy - 1, c.z - r, c.x + r, c.y, c.z + r))) {
                if (mv.struckThisMove.contains(e.getUUID()) || Math.hypot(mv.at(e).x - c.x, mv.at(e).z - c.z) > r)
                    continue;
                mv.struckThisMove.add(e.getUUID());
                mv.blow(e, 18f, c, 0, 0);
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
            }
        }
    }

    /**
     * Egg rain. The wind-up (to EGG_AT): he tips his mouth toward you, his glow builds and the clumps rattle, with a
     * rising shiver of glass and a toll just before. Then he flings his strands and the clumps come off one after
     * another, each with a ring on the ground where it will land.
     */
    void eggRain() {
        float s = mv.s();
        if (mv.t < Moves.EGG_AT && mv.t % 5 == 0 && mv.level() instanceof ServerLevel sl) {
            // the clumps glow as it builds, seen from well off
            int n = 0;
            for (int i = 0; i < rig.eggs.length && n < 10; i++) {
                if (h.state.eggGone[i]) continue;
                n++;
                Vec3 c = h.eggWorld(i);
                for (ServerPlayer p : sl.players())
                    if (p.distanceToSqr(c) < 160 * 160)
                        sl.sendParticles(
                                p,
                                ParticleTypes.GLOW,
                                true,
                                c.x,
                                c.y,
                                c.z,
                                2 + mv.t / 10,
                                2 * s + 0.4,
                                2 * s + 0.4,
                                2 * s + 0.4,
                                0.02);
            }
        }
        if (mv.t == Moves.EGG_AT - 8) h.sound(h.position().add(0, (rig.rimY + 20) * s, 0), ModSounds.TOLL, 3f, 1.35f);
        if (mv.t == Moves.EGG_AT)
            mv.fx(BigFx.FLASH, h.position().add(0, rig.rimY * s, 0), mv.px(20, 6), Vec3.ZERO, BellMoves.GLOW);
        if (mv.t >= Moves.EGG_AT && mv.t <= Moves.EGG_AT + 24 && (mv.t - Moves.EGG_AT) % 4 == 0) dropEgg();
    }

    /** egg rain: an egg clump comes off and drops toward what he's after, splatting where it lands */
    void dropEgg() {
        float s = mv.s();
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < rig.eggs.length; i++) if (!h.state.eggGone[i]) left.add(i);
        if (left.isEmpty()) return;
        int i = left.get(h.getRandom().nextInt(left.size()));
        Vec3 from = h.eggWorld(i);
        h.eggGone(i);
        Vec3 to = mv.target != null && mv.target.isAlive()
                ? mv.target.position()
                : mv.forcedSpot != null ? mv.forcedSpot : mv.eggSpot != null ? mv.eggSpot : h.position();
        double spread = 6 * s + 2;
        to = to.add(
                (h.getRandom().nextDouble() - 0.5) * 2 * spread,
                0,
                (h.getRandom().nextDouble() - 0.5) * 2 * spread);
        double gy = h.groundAt(to.x, to.z);
        // the way it really falls (a step, then the air slows it, then it drops faster): how many ticks to the
        // ground, and how far across it gets for each block a tick it starts with, so it lands on its marker
        double y = from.y, vy = 0, across = 0, k = 1;
        int n = 0;
        while (y > gy && n < 400) {
            y += vy;
            across += k;
            k *= 0.99;
            vy = vy * 0.99 - 0.05;
            n++;
        }
        across = Math.max(1, across);
        float r = 2.5f + 3f * s;
        Shot sh = new Shot(mv.level(), h, Shot.EGG, 14f, r);
        sh.setPos(from.x, from.y, from.z);
        sh.setDeltaMovement((to.x - from.x) / across, 0, (to.z - from.z) / across);
        sh.setEgg(i, s, new Vec3(to.x, gy, to.z));
        mv.level().addFreshEntity(sh);
        h.sound(from, ModSounds.EGG_BURST, 2f, 1.1f);
        mv.lastEggs.add(sh);
    }

    /** the whirlpool: he spins, and his strands drag everything in a wide ring in toward the middle under him */
    void whirlpool() {
        float s = mv.s();
        double bell = h.bellRadius();
        if (mv.t == Moves.WHIRL_UP) h.sound(h.position(), ModSounds.CHURN, 4f, 0.6f);
        if (mv.t == Moves.WHIRL_UP)
            mv.fx(
                    BigFx.WHIRL,
                    mv.under(),
                    h.bellRadius() * 1.9 + mv.px(0, 6),
                    Vec3.ZERO,
                    Moves.WHIRL_CRUSH - Moves.WHIRL_UP);
        if (mv.t > Moves.WHIRL_UP && mv.t < Moves.WHIRL_CRUSH) {
            if (mv.t % 20 == 0) h.sound(h.position(), ModSounds.CHURN, 3f, 0.7f);
            double R = bell * 1.9 + 6;
            Vec3 c = h.position();
            for (LivingEntity e :
                    mv.near(h.bodyBox().inflate(R - bell + 4, 0, R - bell + 4).setMaxY(h.getY() + rig.rimY * s))) {
                Vec3 in = new Vec3(c.x - mv.at(e).x, 0, c.z - mv.at(e).z);
                double d = in.length();
                if (d > R || d < 0.5) continue;
                Vec3 n = in.normalize();
                Vec3 round = new Vec3(-n.z, 0, n.x);
                double pull = (0.10 + 0.06 * s) * (0.5 + 0.5 * d / R);
                e.setDeltaMovement(e.getDeltaMovement()
                        .scale(0.85)
                        .add(n.scale(pull))
                        .add(round.scale(0.12 + 0.05 * s))
                        .add(0, 0.02, 0));
                e.hurtMarked = true;
                if (d < bell && mv.every(e, 20)) {
                    mv.blow(e, 6f, c, 0, 0);
                    mv.sting(e);
                }
            }
            if (mv.t % 3 == 0) {
                double a = mv.t * 0.4;
                for (int k = 0; k < 16; k++) {
                    double aa = a + k * Math.PI / 8, rr = R * (0.3 + 0.7 * ((k * 37 + mv.t) % 16) / 16.0);
                    mv.level()
                            .sendParticles(
                                    ParticleTypes.BUBBLE_POP,
                                    c.x + Math.cos(aa) * rr,
                                    mv.groundUnder() + 0.5,
                                    c.z + Math.sin(aa) * rr,
                                    2,
                                    0.5,
                                    0.2,
                                    0.5,
                                    0.05);
                }
            }
        }
        if (mv.t == Moves.WHIRL_CRUSH) {
            Vec3 c = h.position();
            double r = bell * 0.75 + 4;
            for (LivingEntity e : mv.near(h.bodyBox().setMaxY(h.getY() + rig.rimY * s))) {
                if (h.horiz(mv.at(e)) > r) continue;
                mv.blow(e, 55f, c, 1.2, 1.6);
            }
            h.sound(c, ModSounds.SLAM, 4f, 0.5f);
            h.sound(c, ModSounds.SPLASH, 4f, 0.5f);
            mv.fx(BellMoves.heavy(r), mv.under(), r);
        }
        bellTone(Moves.WHIRL_CRUSH);
    }

    /** the sky dive: up very high, over he turns, bell first down he comes, and a huge shock wave where he lands */
    void skyDive() {
        float s = mv.s();
        int turn = Moves.DIVE_CLIMB, fall = Moves.DIVE_CLIMB + Moves.DIVE_TURN, back = fall + Moves.DIVE_FALL;
        // follow it while he climbs
        if (mv.t < fall && mv.target != null && mv.target.isAlive()) h.setAim(h.toModel(mv.aimAt(mv.target)));
        if (mv.t == turn - 10) h.sound(h.position().add(0, rig.rimY * s, 0), ModSounds.WARN_DIVE, 5f, 0.9f);
        if (mv.t >= turn && mv.t < fall) mv.diveOver = Moves.smooth((mv.t - turn) / (float) Moves.DIVE_TURN);
        if (mv.t == fall) h.sound(h.position(), ModSounds.SWOOP, 5f, 0.6f);
        if (mv.t == turn) mv.fx(BigFx.EMBERS, h.crownWorld(), mv.px(10, 3), Vec3.ZERO, 20);
        if (mv.t >= fall && mv.t < back && !mv.landed) {
            // down, bell first: he's landed when the lowest of him reaches the ground
            Vec3 crown = h.crownWorld();
            double gy = h.groundAt(crown.x, crown.z);
            // (the air he pushes ahead flattens the ground first)
            if (!mv.diveWarned && crown.y - gy < mv.px(40, 10)) {
                mv.diveWarned = true;
                mv.fx(BigFx.DUST_RING, new Vec3(crown.x, gy, crown.z), mv.px(60, 12));
            }
            if (crown.y <= gy + 1.5 * s + 0.5 || mv.t == back - 1) {
                mv.landed = true;
                double R = 75 * s + 24;
                Vec3 c = new Vec3(crown.x, gy, crown.z);
                mv.diveAt = c;
                h.sound(c, ModSounds.SHOCK, 5f, 0.5f);
                h.sound(c, ModSounds.SLAM, 5f, 0.4f);
                h.sound(c, ModSounds.SPLASH, 4f, 0.5f);
                h.particles(ParticleTypes.EXPLOSION_EMITTER, c.add(0, 1, 0), 3, 6 * s, 0);
                h.particles(ParticleTypes.CLOUD, c.add(0, 1, 0), 250, R * 0.5, 0.25);
                mv.fx(BellMoves.heavy(mv.px(45, 12)), c, mv.px(45, 12));
                mv.fx(BigFx.SHOCKWAVE, c, R, Vec3.ZERO, BellMoves.DIVE_RING);
                mv.fx(BigFx.FLASH, c.add(0, 4 * s, 0), mv.px(30, 8), Vec3.ZERO, BellMoves.GLOW);
                // crown first: his own glass and calcite fly
                mv.fx(
                        BigFx.DEBRIS,
                        c,
                        mv.px(25, 6),
                        new Vec3(0, 1, 0),
                        Block.getId(Blocks.CALCITE.defaultBlockState()));
                mv.fx(BigFx.SPARKS, c, mv.px(30, 6), new Vec3(0, 1, 0), BellMoves.GLASS);
                if (mv.griefing()) mv.crack(BlockPos.containing(c), (int) Math.ceil(R * 0.6));
                // on to the turn back up
                h.rewindMove(back);
                mv.t = back;
            }
        }
        // the blow runs out from where he landed as a ring
        if (mv.landed && mv.diveAt != null && mv.t >= back && mv.t <= back + BellMoves.DIVE_RING) {
            double R = 75 * s + 24, r = R * (mv.t - back) / BellMoves.DIVE_RING;
            Vec3 c = mv.diveAt;
            for (LivingEntity e : mv.near(new AABB(c, c).inflate(R, 30 * s + 12, R))) {
                if (mv.struckThisMove.contains(e.getUUID())) continue;
                double d = Math.hypot(mv.at(e).x - c.x, mv.at(e).z - c.z);
                if (d > r) continue;
                mv.struckThisMove.add(e.getUUID());
                mv.blow(e, 70f * (float) (1 - 0.5 * d / R), c, 3.0, 1.4);
                mv.knockDown(e);
            }
        }
        if (mv.landed) bellTone(back);
        if (mv.t >= back) mv.diveOver = 1f - Moves.smooth((mv.t - back - 8) / (float) (Moves.DIVE_BACK - 8));
        h.setLift(mv.diveOver);
    }

    /**
     * His glass bell hitting the ground at tick at: a low note of the bell itself, then the glass ringing on.
     */
    void bellTone(int at) {
        Vec3 c = h.position().add(0, rig.rimY * mv.s(), 0);
        if (mv.t == at) h.sound(c, ModSounds.TOLL, 4f, 0.5f);
        if (mv.t == at + 6) h.sound(c, SoundEvents.AMETHYST_BLOCK_RESONATE, 4f, 0.5f);
    }

    /** the deep toll: three tolls, louder each time, then a massive shock ring that throws everything far */
    void deepToll() {
        float s = mv.s();
        Vec3 mid = h.position().add(0, (rig.rimY + 20) * s, 0);
        if (mv.t == 1 || mv.t == 37 || mv.t == 73) {
            int k = mv.t / 36;
            h.pulse(1.2f + 0.2f * k);
            h.sound(mid, ModSounds.TOLL, 3f + 1.5f * k, 0.8f);
            for (LivingEntity e : mv.near(h.bodyBox().inflate(40 * s + 20)))
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80 + 40 * k, 0));
            mv.fx(BigFx.DUST_RING, mv.under(), h.bellRadius() * (1.2 + 0.3 * k));
            mv.fx(BigFx.SHAKE, h.position(), mv.px(20 + 10 * k, 8));
            h.sound(mid, SoundEvents.AMETHYST_BLOCK_CHIME, 3f, 0.6f);
        }
        if (mv.t == 74) mv.fx(BigFx.EMBERS, h.crownWorld(), mv.px(10, 3), Vec3.ZERO, 36);
        int big = Moves.TOLL_BIG;
        if (mv.t == big) {
            h.pulse(1.8f);
            h.sound(mid, ModSounds.TOLL_BIG, 6f, 1f);
            h.sound(mid, ModSounds.SHOCK, 5f, 0.4f);
            // (the big toll: a flash of his light and the ring the players see, running out with the real one)
            mv.fx(BigFx.FLASH, mid, mv.px(60, 16), Vec3.ZERO, BellMoves.GLOW);
            mv.fx(BigFx.SHOCKWAVE, mv.under(), mv.px(120, 40), Vec3.ZERO, 25);
            mv.fx(BigFx.SPARKS, mid, mv.px(30, 6), new Vec3(0, 1, 0), BellMoves.GLASS);
            // (under the big note, the bell's own low one, and the glass ringing on after)
            h.sound(mid, ModSounds.TOLL, 4f, 0.5f);
        }
        if (mv.t == big + 4 || mv.t == big + 10) h.sound(mid, SoundEvents.AMETHYST_BLOCK_RESONATE, 4f, 0.5f);
        if (mv.t >= big && mv.t <= big + 25) {
            // (from right under him out, at the pace of the ring the players see)
            double R = 120 * s + 40, r0 = h.bellRadius();
            double r = (mv.t - big) / 25.0 * R, rIn = (mv.t - big - 1) / 25.0 * R;
            Vec3 c = h.position();
            for (LivingEntity e : mv.near(h.bodyBox().inflate(R - r0 + 4, 100 * s + 40, R - r0 + 4))) {
                if (mv.struckThisMove.contains(e.getUUID())) continue;
                double d = h.horiz(mv.at(e));
                if (d > r) continue;
                mv.struckThisMove.add(e.getUUID());
                mv.blow(e, 50f * (float) (1 - 0.4 * d / R), c, 3.4, 1.1);
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
                mv.knockDown(e);
            }
            if (mv.griefing()) shatter(c, Math.max(0, rIn), r, 40 * s + 12);
        }
    }

    /** the big toll's ring breaks glass and leaves as it passes (a limited number of blocks each tick) */
    void shatter(Vec3 c, double r0, double r1, double height) {
        ServerLevel l = mv.level();
        int broken = 0;
        double mid = (r0 + r1) * 0.5;
        int n = (int) Math.min(1200, Math.max(16, 2 * Math.PI * mid));
        for (int i = 0; i < n && broken < 300; i++) {
            double a = i * 2 * Math.PI / n;
            for (double r = Math.max(0, r0); r <= r1 && broken < 300; r += 1) {
                int x = Mth.floor(c.x + Math.cos(a) * r), z = Mth.floor(c.z + Math.sin(a) * r);
                if (!NoWait.loaded(l, new BlockPos(x, 0, z))) continue;
                int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int y0 = Math.max(l.getMinBuildHeight(), top - 12), y1 = Math.min(top + 2, (int) (c.y + height));
                for (int y = y1; y >= y0 && broken < 300; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState st = l.getBlockState(p);
                    if (st.is(BlockTags.LEAVES)
                            || st.is(BlockTags.IMPERMEABLE)
                            || st.getBlock() instanceof IronBarsBlock && st.getSoundType() == SoundType.GLASS) {
                        l.destroyBlock(p, false);
                        broken++;
                    }
                }
            }
        }
    }

    /** the stinger storm: every strand flicks stingers up and out, and they rain down over a wide area */
    void stingerRain() {
        float s = mv.s();
        double R = 90 * s + 24;
        Vec3 c = h.position();
        List<LivingEntity> near =
                mv.near(h.bodyBox().inflate(R - h.bellRadius() + 4, 40 * s + 16, R - h.bellRadius() + 4));
        int n = 3 + (int) (3 * s);
        for (int k = 0; k < n; k++) {
            int strand = h.getRandom().nextInt(rig.strands.length);
            if (rig.strands[strand].hidden()) continue;
            Vec3 from = h.strandTipWorld(strand).add(0, 2 + 3 * s, 0);
            Vec3 to;
            if (!near.isEmpty() && h.getRandom().nextInt(3) > 0) {
                LivingEntity e = near.get(h.getRandom().nextInt(near.size()));
                double sp = 2 * s + 1.5;
                // (at another giant: at the bit of it that's in reach)
                to = mv.at(e)
                        .add(
                                (h.getRandom().nextDouble() - 0.5) * sp,
                                mv.giantAt.containsKey(e) ? 0 : e.getBbHeight() * 0.5,
                                (h.getRandom().nextDouble() - 0.5) * sp);
            } else {
                double a = h.getRandom().nextDouble() * Math.PI * 2,
                        d = Math.sqrt(h.getRandom().nextDouble()) * R;
                double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
                to = new Vec3(x, h.groundAt(x, z), z);
            }
            // up first and over: a lob
            Vec3 d = to.subtract(from);
            double horiz = Math.hypot(d.x, d.z);
            Shot sh = new Shot(mv.level(), h, Shot.STINGER, 10f, 1f);
            sh.setPos(from.x, from.y, from.z);
            double speed = 1.3 + 0.7 * s;
            double time = Math.max(8, horiz / speed);
            sh.setDeltaMovement(d.x / time, d.y / time + 0.5 * 0.02 * time, d.z / time);
            mv.level().addFreshEntity(sh);
            if (k == 0 && mv.t % 10 == 0) mv.fx(BigFx.SPARKS, to, mv.px(3, 1), Vec3.ZERO, 0xD8FFE0);
        }
        if (mv.t % 6 == 0)
            h.sound(
                    c.add(0, 20 * s, 0),
                    ModSounds.VOLLEY,
                    3f,
                    0.8f + h.getRandom().nextFloat() * 0.3f);
    }

    /** the sun lances: a beam of light from each glowing spot to the ground, sweeping out round him and burning */
    void sunLances() {
        float s = mv.s();
        double bell = h.bellRadius();
        int from = Moves.LANCE_FROM, to = Moves.LANCE_TO;
        if (mv.t < from || mv.t > to) {
            if (mv.t == from - 12)
                for (int i = 0; i < 5; i++) h.particles(ParticleTypes.END_ROD, h.spotWorld(i), 30, 3 * s + 0.5, 0.05);
            return;
        }
        float frac = (mv.t - from) / (float) (to - from);
        if (mv.t == from)
            for (int i = 0; i < 5; i++) mv.fx(BigFx.FLASH, h.spotWorld(i), mv.px(10, 3), Vec3.ZERO, 0xFFF2B0);
        if (mv.t % 20 == 0)
            h.sound(h.position().add(0, (rig.rimY + 20) * s, 0), ModSounds.BEAM, 3f, 0.7f + 0.3f * frac);
        double beamR = 2.5 + 2.5 * s;
        for (int i = 0; i < 5; i++) {
            Vec3 top = h.spotWorld(i);
            Vec3 ground;
            if (i < 4) {
                // spiralling out from under him, each from its own side
                double a0 = Math.atan2(rig.spots[i].centre().z, rig.spots[i].centre().x) - h.getYRot() * Mth.DEG_TO_RAD;
                double a = a0 + frac * 2.4;
                double r = bell * (0.35 + 1.35 * frac);
                double x = h.getX() + Math.cos(a) * r, z = h.getZ() + Math.sin(a) * r;
                ground = new Vec3(x, h.groundAt(x, z), z);
            } else {
                // the vase's beam hunts what he's after
                Vec3 want = mv.target != null && mv.target.isAlive() ? mv.target.position() : h.position();
                Vec3 was = mv.lance[4] == null ? new Vec3(h.getX(), mv.groundUnder(), h.getZ()) : mv.lance[4];
                Vec3 d = want.subtract(was).multiply(1, 0, 1);
                double step = 0.25 + 0.35 * s;
                Vec3 nx = d.length() > step ? was.add(d.normalize().scale(step)) : was.add(d);
                ground = new Vec3(nx.x, h.groundAt(nx.x, nx.z), nx.z);
            }
            mv.lance[i] = ground;
            if (mv.t == from) mv.fx(BigFx.SPARKS, ground, mv.px(3, 1.5), Vec3.ZERO, 0);
            if (mv.t % 2 == 0) {
                Vec3 line = ground.subtract(top);
                for (int k = 0; k <= 12; k++) {
                    Vec3 p = top.add(line.scale(k / 12.0));
                    mv.level().sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.3 * s, 0.3 * s, 0.3 * s, 0);
                }
                mv.level()
                        .sendParticles(
                                ParticleTypes.FLAME,
                                ground.x,
                                ground.y + 0.2,
                                ground.z,
                                8,
                                beamR * 0.4,
                                0.1,
                                beamR * 0.4,
                                0.02);
                mv.level()
                        .sendParticles(
                                ParticleTypes.LAVA,
                                ground.x,
                                ground.y + 0.2,
                                ground.z,
                                1,
                                beamR * 0.3,
                                0.1,
                                beamR * 0.3,
                                0);
            }
            if (mv.t % 10 == i * 2) {
                mv.fx(BigFx.EMBERS, ground, beamR * 1.6 + 1, Vec3.ZERO, 14);
                mv.fx(BigFx.SMOKE, ground, beamR * 1.4 + 1);
            }
            for (LivingEntity e : mv.near(new AABB(
                    ground.x - beamR, ground.y - 2, ground.z - beamR, ground.x + beamR, top.y, ground.z + beamR))) {
                if (Math.hypot(mv.at(e).x - ground.x, mv.at(e).z - ground.z) > beamR || !mv.every(e, 10)) continue;
                mv.blow(e, 16f, ground, 0.3, 0.2);
                e.igniteForSeconds(4);
            }
            if (mv.griefing() && h.getRandom().nextInt(5) == 0) {
                BlockPos p = BlockPos.containing(
                        ground.x + (h.getRandom().nextDouble() - 0.5) * beamR,
                        ground.y,
                        ground.z + (h.getRandom().nextDouble() - 0.5) * beamR);
                if (mv.level().getBlockState(p).isAir()
                        && mv.level().getBlockState(p.below()).isFaceSturdy(mv.level(), p.below(), Direction.UP))
                    mv.level().setBlockAndUpdate(p, Blocks.FIRE.defaultBlockState());
            }
        }
    }

    /** the undertow: every strand spreads wide and drags everything round him in, then slams it down */
    void undertow() {
        float s = mv.s();
        double bell = h.bellRadius();
        Vec3 c = h.position();
        if (mv.t >= Moves.UNDERTOW_PULL && mv.t < Moves.UNDERTOW_SLAM) {
            double R = bell * 2.2 + 8;
            if (mv.t == Moves.UNDERTOW_PULL) h.sound(c, ModSounds.CHURN, 4f, 0.5f);
            if (mv.t == Moves.UNDERTOW_PULL)
                mv.fx(BigFx.WHIRL, mv.under(), R, Vec3.ZERO, Moves.UNDERTOW_SLAM - Moves.UNDERTOW_PULL);
            for (LivingEntity e : mv.near(h.bodyBox().inflate(R - bell + 4, 20 * s + 8, R - bell + 4))) {
                Vec3 in = new Vec3(c.x - mv.at(e).x, 0, c.z - mv.at(e).z);
                double d = in.length();
                if (d > R || d < 0.5) continue;
                e.setDeltaMovement(e.getDeltaMovement()
                        .scale(0.8)
                        .add(in.normalize().scale(0.16 + 0.1 * s))
                        .add(0, 0.05, 0));
                e.hurtMarked = true;
                if (mv.every(e, 20)) mv.sting(e);
            }
            // dust dragged in by the strand ends round his edge
            if (mv.t % 4 == 2 && mv.t < Moves.UNDERTOW_SLAM - 2) {
                for (int k = 0; k < 8; k++) {
                    Vec3 tip = h.strandTipWorld(Math.floorMod(k * 10 + 3, rig.strands.length));
                    Vec3 to = c.subtract(tip).multiply(1, 0, 1);
                    mv.fx(
                            BigFx.TRAIL,
                            new Vec3(tip.x, h.groundAt(tip.x, tip.z), tip.z),
                            mv.px(5, 2),
                            to.lengthSqr() > 1e-6 ? to.normalize() : Vec3.ZERO,
                            0);
                }
            }
            if (mv.t % 2 == 0) {
                for (int k = 0; k < 12; k++) {
                    double a = h.getRandom().nextDouble() * Math.PI * 2,
                            r = R * (0.5 + 0.5 * h.getRandom().nextDouble());
                    mv.level()
                            .sendParticles(
                                    ParticleTypes.BUBBLE_COLUMN_UP,
                                    c.x + Math.cos(a) * r,
                                    mv.groundUnder() + 1,
                                    c.z + Math.sin(a) * r,
                                    0,
                                    -Math.cos(a),
                                    0.2,
                                    -Math.sin(a),
                                    0.6);
                }
            }
        }
        if (mv.t == Moves.UNDERTOW_SLAM) {
            double r = bell * 0.95 + 5;
            for (LivingEntity e : mv.near(h.bodyBox().setMaxY(h.getY() + rig.rimY * s))) {
                if (h.horiz(mv.at(e)) > r) continue;
                mv.blow(e, 52f, c, 0.8, 0);
                e.setDeltaMovement(e.getDeltaMovement().x, -1.5, e.getDeltaMovement().z);
                mv.knockDown(e);
            }
            h.sound(c, ModSounds.SLAM, 4f, 0.5f);
            h.sound(c, ModSounds.SPLASH, 4f, 0.6f);
            h.particles(ParticleTypes.CLOUD, new Vec3(c.x, mv.groundUnder() + 1, c.z), 150, r * 0.5, 0.1);
            mv.fx(BellMoves.heavy(mv.px(40, 6)), mv.under(), mv.px(40, 6));
            mv.fx(BigFx.DUST_RING, mv.under(), bell * 0.95 + mv.px(0, 5));
        }
        bellTone(Moves.UNDERTOW_SLAM);
    }
}
