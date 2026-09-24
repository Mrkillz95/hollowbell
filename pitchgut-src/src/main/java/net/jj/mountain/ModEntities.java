package net.jj.mountain;

import net.jj.mountain.entity.GooGlob;
import net.jj.mountain.entity.GripSeat;
import net.jj.mountain.entity.HeartEntity;
import net.jj.mountain.entity.MountainEntity;
import net.jj.mountain.entity.MountainPart;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<MountainEntity> MOUNTAIN = register("mountain",
            EntityType.Builder.of(MountainEntity::new, MobCategory.MONSTER).sized(3.0f, 3.0f).fireImmune()
                    .clientTrackingRange(32).updateInterval(1));
    public static final EntityType<MountainPart> MOUNTAIN_PART = register("mountain_part",
            EntityType.Builder.<MountainPart>of(MountainPart::new, MobCategory.MISC).sized(1.0f, 1.0f).fireImmune().noSave().noSummon()
                    .clientTrackingRange(32).updateInterval(20));
    public static final EntityType<GripSeat> GRIP = register("grip",
            EntityType.Builder.<GripSeat>of(GripSeat::new, MobCategory.MISC).sized(0.6f, 0.6f).fireImmune().noSave().noSummon()
                    .clientTrackingRange(24).updateInterval(1));
    public static final EntityType<HeartEntity> HEART = register("heart",
            EntityType.Builder.of(HeartEntity::new, MobCategory.MISC).sized(12.0f, 17.0f).fireImmune().noSummon()
                    .clientTrackingRange(8).updateInterval(2));

    public static final EntityType<GooGlob> GOO_GLOB = register("goo_glob",
            EntityType.Builder.<GooGlob>of(GooGlob::new, MobCategory.MISC).sized(1.2f, 1.2f).fireImmune().noSummon()
                    .clientTrackingRange(16).updateInterval(1));

    /** what stands up out of the goo when he looks at you: your own shape, your own health, your own weapon */
    public static final EntityType<net.jj.mountain.entity.ShadowOfYou> SHADOW = register("shadow_of_you",
            EntityType.Builder.of(net.jj.mountain.entity.ShadowOfYou::new, MobCategory.MONSTER).sized(0.6f, 1.95f)
                    .eyeHeight(1.7f).fireImmune().clientTrackingRange(10).updateInterval(2));

    public static final EntityType<net.jj.mountain.entity.inside.GutTentacle> GUT_TENTACLE = register("gut_tentacle",
            EntityType.Builder.of(net.jj.mountain.entity.inside.GutTentacle::new, MobCategory.MONSTER).sized(1.4f, 8.0f).fireImmune()
                    .clientTrackingRange(10).updateInterval(2));
    public static final EntityType<net.jj.mountain.entity.inside.GutLeech> GUT_LEECH = register("gut_leech",
            EntityType.Builder.of(net.jj.mountain.entity.inside.GutLeech::new, MobCategory.MONSTER).sized(0.9f, 0.5f).fireImmune()
                    .clientTrackingRange(10).updateInterval(2));
    public static final EntityType<net.jj.mountain.entity.inside.WatcherEye> WATCHER = register("watcher_eye",
            EntityType.Builder.of(net.jj.mountain.entity.inside.WatcherEye::new, MobCategory.MONSTER).sized(1.4f, 2.3f).eyeHeight(1.75f).fireImmune()
                    .clientTrackingRange(10).updateInterval(2));

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> b) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, name);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, b.build(id.toString()));
    }

    public static void init() {}
}
