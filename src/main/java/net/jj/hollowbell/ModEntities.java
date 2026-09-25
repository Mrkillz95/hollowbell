package net.jj.hollowbell;

import net.jj.hollowbell.entity.Belling;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Seat;
import net.jj.hollowbell.entity.Shot;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<HollowbellEntity> HOLLOWBELL = register("hollowbell",
            EntityType.Builder.of(HollowbellEntity::new, MobCategory.MONSTER).sized(8f, 8f).fireImmune()
                    .clientTrackingRange(32).updateInterval(1));
    public static final EntityType<Seat> SEAT = register("seat",
            EntityType.Builder.<Seat>of(Seat::new, MobCategory.MISC).sized(0.5f, 0.5f).fireImmune().noSave().noSummon()
                    .clientTrackingRange(24).updateInterval(1));
    public static final EntityType<Shot> SHOT = register("shot",
            EntityType.Builder.<Shot>of(Shot::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(1));
    public static final EntityType<Belling> BELLING = register("belling",
            EntityType.Builder.of(Belling::new, MobCategory.MONSTER).sized(1.2f, 1.6f).fireImmune()
                    .clientTrackingRange(10).updateInterval(2));

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> b) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, name);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, b.build(id.toString()));
    }

    public static void init() {}
}
