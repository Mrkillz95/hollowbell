package net.jj.hollowbell.entity;

import net.jj.hollowbell.BellConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** What he writes into his save and reads back. */
final class BellSaving {
    private final HollowbellEntity hb;

    BellSaving(HollowbellEntity hb) {
        this.hb = hb;
    }

    void writeSave(CompoundTag tag) {
        tag.putFloat("BellScale", hb.bellScale());
        tag.putBoolean("Asleep", hb.asleep);
        tag.putBoolean("Frozen", hb.frozen);
        tag.putFloat("SpeedMul", hb.speedMul);
        if (hb.comeTo != null) tag.putUUID("ComeTo", hb.comeTo);
        if (hb.comeLostAt != 0) tag.putLong("ComeLostAt", hb.comeLostAt);
        tag.putBoolean("HoldThere", hb.holdThere);
        // where he's going (an order, a /goto trip), so a restart doesn't lose it
        if (hb.goal != null) {
            tag.putDouble("GoalX", hb.goal.x);
            tag.putDouble("GoalY", hb.goal.y);
            tag.putDouble("GoalZ", hb.goal.z);
        }
        tag.putFloat("SleepK", hb.sleepiness());
        tag.putInt("BellVariant", hb.variant());
        tag.putFloat("BellHp", hb.healthNow());
        tag.putFloat("BellHpMax", hb.healthMax());
        tag.putFloat("BellBaseMax", hb.baseMax);
        int mask = 0;
        for (int i = 0; i < hb.rig.pods.length && i < 31; i++) if (hb.state.podPopped[i]) mask |= 1 << i;
        tag.putInt("Pods", mask);
        ListTag ph = new ListTag();
        for (float v : hb.podHp) ph.add(FloatTag.valueOf(v));
        tag.put("PodHp", ph);
        byte[] e = new byte[hb.rig.eggs.length];
        for (int i = 0; i < e.length; i++) e[i] = (byte) (hb.state.eggGone[i] ? 1 : 0);
        tag.putByteArray("Eggs", e);
        ListTag pg = new ListTag();
        long now = hb.level().getGameTime();
        for (int i = 0; i < hb.rig.pods.length; i++) {
            CompoundTag o = new CompoundTag();
            o.putFloat("G", hb.state.podGrowth[i]);
            o.putLong("Wait", Math.max(0, hb.podRegrowAt[i] - now));
            pg.add(o);
        }
        tag.put("PodGrowth", pg);
        tag.putFloat("Sunk", hb.getEntityData().get(HollowbellEntity.DATA_SUNK));
        tag.putLong("SunkFor", Math.max(0, hb.sunkUntil - now));
        if (hb.home != null) {
            tag.putDouble("HomeX", hb.home.x);
            tag.putDouble("HomeY", hb.home.y);
            tag.putDouble("HomeZ", hb.home.z);
        }
        tag.putBoolean("Stay", hb.stay);
        if (hb.flight.follow != null) tag.putUUID("Follow", hb.flight.follow);
        tag.putBoolean("Settled", hb.settled);
        if (hb.hunted != null) tag.putUUID("Hunted", hb.hunted);
        if (hb.huntOrdered) tag.putBoolean("HuntOrdered", true);
        if (hb.huntSeen != null) {
            tag.putDouble("HuntX", hb.huntSeen.x);
            tag.putDouble("HuntY", hb.huntSeen.y);
            tag.putDouble("HuntZ", hb.huntSeen.z);
        }
        if (hb.bornAt >= 0) tag.putLong("BornAt", hb.bornAt);
        if (hb.worldOne) tag.putBoolean("WorldOne", true);
        if (hb.deathBy != null) tag.putUUID("DeathBy", hb.deathBy);
        // (his waits as time left: the game clock of another world, or after a long time away, means nothing)
        tag.putLong("HeavyIn", hb.moves.heavyIn());
        tag.putLong("FightFor", hb.moves.fightFor());
        ListTag met = new ListTag();
        for (var m : hb.metLately.entrySet()) {
            if (m.getValue() <= now) continue;
            CompoundTag o = new CompoundTag();
            o.putUUID("Id", m.getKey());
            o.putLong("Left", m.getValue() - now);
            met.add(o);
        }
        if (!met.isEmpty()) tag.put("MetLately", met);
        if (hb.meetFoe != null) tag.putUUID("MeetFoe", hb.meetFoe);
        long gt = hb.level().getGameTime();
        if (hb.yieldUntil > gt) tag.putLong("YieldFor", hb.yieldUntil - gt);
        if (hb.fleeFrom != null && hb.fleeUntil > gt) {
            tag.putLong("FleeFor", hb.fleeUntil - gt);
            tag.putDouble("FleeX", hb.fleeFrom.x);
            tag.putDouble("FleeZ", hb.fleeFrom.z);
        }
        if (hb.bound()) {
            tag.putDouble("BoundX", hb.boundX);
            tag.putDouble("BoundZ", hb.boundZ);
            tag.putInt("BoundR", hb.boundR);
        }
        hb.mood.save(tag);
    }

    void readSave(CompoundTag tag) {
        if (tag.contains("BellScale") && Float.isFinite(tag.getFloat("BellScale")))
            hb.getEntityData()
                    .set(
                            HollowbellEntity.DATA_SCALE,
                            Mth.clamp(
                                    tag.getFloat("BellScale"), HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE));
        hb.asleep = tag.getBoolean("Asleep");
        hb.frozen = tag.getBoolean("Frozen");
        hb.speedMul = tag.contains("SpeedMul") ? Mth.clamp(tag.getFloat("SpeedMul"), 0.1f, 5f) : 1f;
        hb.comeTo = tag.hasUUID("ComeTo") ? tag.getUUID("ComeTo") : null;
        hb.comeLostAt = tag.getLong("ComeLostAt");
        hb.holdThere = tag.getBoolean("HoldThere");
        if (tag.contains("SleepK"))
            hb.getEntityData().set(HollowbellEntity.DATA_SLEEP, Mth.clamp(tag.getFloat("SleepK"), 0f, 1f));
        if (tag.contains("BellVariant"))
            hb.getEntityData().set(HollowbellEntity.DATA_VARIANT, Mth.clamp(tag.getInt("BellVariant"), 0, 2));
        // a spawn egg's own tag: 0 calm, 1 hunting, 2 guardian, 3 small
        if (tag.contains("HollowbellEgg")) {
            int v = tag.getInt("HollowbellEgg");
            hb.getEntityData().set(HollowbellEntity.DATA_VARIANT, v == 3 ? HollowbellEntity.HUNTER : v);
            float sc = v == 3 ? BellConfig.V.smallEggScale : BellConfig.V.spawnEggScale;
            hb.getEntityData()
                    .set(
                            HollowbellEntity.DATA_SCALE,
                            Mth.clamp(sc, HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE));
        }
        // an egg's tag also carries the fresh body's health (6000, base -1): his first tick works it out for his size
        if (tag.contains("HollowbellEgg")) hb.hp = -1f;
        else if (tag.contains("BellHpMax")) {
            hb.baseMax = tag.contains("BellBaseMax") ? tag.getFloat("BellBaseMax") : tag.getFloat("BellHpMax");
            hb.getEntityData().set(HollowbellEntity.DATA_HP_MAX, tag.getFloat("BellHpMax"));
            hb.hp = tag.getFloat("BellHp");
            hb.getEntityData().set(HollowbellEntity.DATA_HP, hb.hp);
        } else hb.hp = -1f;
        if (tag.contains("Pods")) {
            int mask = tag.getInt("Pods");
            for (int i = 0; i < hb.rig.pods.length && i < 31; i++) hb.state.podPopped[i] = (mask & (1 << i)) != 0;
        }
        if (tag.contains("PodHp", Tag.TAG_LIST)) {
            ListTag ph = tag.getList("PodHp", Tag.TAG_FLOAT);
            for (int i = 0; i < Math.min(ph.size(), hb.podHp.length); i++) hb.podHp[i] = ph.getFloat(i);
        }
        if (tag.contains("Eggs")) {
            byte[] e = tag.getByteArray("Eggs");
            for (int i = 0; i < Math.min(e.length, hb.rig.eggs.length); i++) {
                hb.state.eggGone[i] = e[i] != 0;
                if (hb.state.eggGone[i]) hb.eggBack[i] = 20 * 60 * 5;
            }
        }
        if (tag.contains("PodGrowth", Tag.TAG_LIST)) {
            ListTag pg = tag.getList("PodGrowth", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(pg.size(), hb.rig.pods.length); i++) {
                CompoundTag o = pg.getCompound(i);
                hb.state.podGrowth[i] = o.getFloat("G");
                hb.podRegrowAt[i] = o.getLong("Wait"); // made relative to now on the first tick
            }
        } else for (int i = 0; i < hb.rig.pods.length; i++) hb.state.podGrowth[i] = hb.state.podPopped[i] ? 0f : 1f;
        hb.getEntityData().set(HollowbellEntity.DATA_SUNK, tag.getFloat("Sunk"));
        hb.sunkUntil = tag.getLong("SunkFor");
        hb.pendingWaits = true;
        if (tag.contains("HomeX"))
            hb.home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        hb.setStay(tag.getBoolean("Stay"));
        hb.flight.follow = tag.hasUUID("Follow") ? tag.getUUID("Follow") : null;
        hb.setFlag(HollowbellEntity.F_FOLLOW, hb.flight.follow != null);
        hb.goal = tag.contains("GoalX")
                ? new Vec3(tag.getDouble("GoalX"), tag.getDouble("GoalY"), tag.getDouble("GoalZ"))
                : null;
        // one from an older save, or from a spawn egg's tag, is where it's meant to be (an egg's comes down)
        hb.settled = tag.getBoolean("Settled") || (!tag.contains("HollowbellEgg") && tag.contains("BellHpMax"));
        // a saved body carries his own health tag; a fresh one (an egg, a summon) never does
        // (a spawn egg merges a fresh save into its own tag, so health tags alone don't tell: BornAt is only written
        // once he has lived a tick, and older saves without it never carry the egg's tag)
        hb.loadedFromSave = tag.contains("BornAt") || (tag.contains("BellHpMax") && !tag.contains("HollowbellEgg"));
        hb.hunted = tag.hasUUID("Hunted") ? tag.getUUID("Hunted") : null;
        hb.huntOrdered = hb.hunted != null && tag.getBoolean("HuntOrdered");
        hb.huntSeen = tag.contains("HuntX")
                ? new Vec3(tag.getDouble("HuntX"), tag.getDouble("HuntY"), tag.getDouble("HuntZ"))
                : null;
        hb.huntLostAt = -1;
        // one from before ages were kept counts as the oldest there is
        hb.bornAt = tag.contains("BornAt") ? tag.getLong("BornAt") : hb.loadedFromSave ? 0 : -1;
        hb.worldOne = tag.getBoolean("WorldOne");
        hb.deathBy = tag.hasUUID("DeathBy") ? tag.getUUID("DeathBy") : null;
        hb.heavyIn = tag.contains("HeavyIn") ? tag.getLong("HeavyIn") : -1;
        hb.fightFor = tag.contains("FightFor") ? tag.getLong("FightFor") : -1;
        hb.metLeft.clear();
        for (Tag t : tag.getList("MetLately", Tag.TAG_COMPOUND)) {
            CompoundTag o = (CompoundTag) t;
            if (o.hasUUID("Id")) hb.metLeft.put(o.getUUID("Id"), o.getLong("Left"));
        }
        hb.meetFoe = tag.hasUUID("MeetFoe") ? tag.getUUID("MeetFoe") : null;
        hb.yieldFor = tag.getLong("YieldFor");
        hb.fleeFor = tag.getLong("FleeFor");
        hb.fleeFrom = tag.contains("FleeX") ? new Vec3(tag.getDouble("FleeX"), 0, tag.getDouble("FleeZ")) : null;
        // an old save from before the meetings: he's a giant of this kind too
        hb.addTag(Meeting.KIND + Meeting.ME);
        if (tag.contains("BoundR")) {
            hb.boundX = tag.getDouble("BoundX");
            hb.boundZ = tag.getDouble("BoundZ");
            hb.boundR = tag.getInt("BoundR");
        } else hb.boundR = -1;
        hb.mood.load(tag);
        // one saved with no base health (from an egg): his most health for his size, keeping how hurt he was
        if (hb.hp >= 0f && hb.baseMax <= 0f) {
            float ratio = hb.healthMax() > 0 ? hb.hp / hb.healthMax() : 1f;
            hb.baseMax = Math.max(40f, BellConfig.V.health * hb.bellScale());
            hb.setMax(hb.baseMax, Mth.clamp(ratio, 0f, 1f));
        }
        hb.partsDirty = true;
        hb.refreshDimensions();
    }
}
