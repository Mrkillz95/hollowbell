package net.jj.hollowbell.test.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** sets the seed a mob's death loot is rolled with */
@Mixin(Mob.class)
public interface MobAccess {
    @Accessor("lootTableSeed")
    void hollowbell$setLootSeed(long seed);
}
