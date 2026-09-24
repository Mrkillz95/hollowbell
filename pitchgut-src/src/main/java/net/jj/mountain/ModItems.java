package net.jj.mountain;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;

public final class ModItems {
    public static final Item CALM_EGG = egg("calm_mountain_spawn_egg", 0, 0xE9C9BC, 0x8C6A7C);
    public static final Item HUNTER_EGG = egg("hunting_mountain_spawn_egg", 1, 0xE3A698, 0x1A0710);
    public static final Item GUARDIAN_EGG = egg("guardian_mountain_spawn_egg", 2, 0xE6D8C4, 0x3F7A3A);
    public static final Item GOO = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "goo"),
            new BlockItem(ModBlocks.GOO, new Item.Properties()));
    /** his heart, set down somewhere. There is one of these per Mountain and no other way to get one. */
    public static final Item HEART = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "mountain_heart"),
            new BlockItem(ModBlocks.HEART, new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC).fireResistant()) {
                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack st, TooltipContext ctx,
                                            java.util.List<net.minecraft.network.chat.Component> lines,
                                            net.minecraft.world.item.TooltipFlag flag) {
                    lines.add(net.minecraft.network.chat.Component.translatable("item.mountain_breathes.mountain_heart.tip1")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                    lines.add(net.minecraft.network.chat.Component.translatable("item.mountain_breathes.mountain_heart.tip2",
                            (int) net.jj.mountain.world.MountainWorld.wardRange(),
                            Math.max(1, net.jj.mountain.MountainConfig.V.wardSeconds / 60)).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
                    lines.add(net.minecraft.network.chat.Component.translatable("item.mountain_breathes.mountain_heart.tip3",
                            Math.max(1, net.jj.mountain.MountainConfig.V.wardRestSeconds / 60)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                }
            });

    // ------------------------------------------------------------------ what you cut out of him
    public static final Item FLESH = plain("mountain_flesh", new Item.Properties().food(
            new net.minecraft.world.food.FoodProperties.Builder().nutrition(4).saturationModifier(0.1f).alwaysEdible()
                    .effect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HUNGER, 300, 1), 0.8f)
                    .effect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 400, 0), 1.0f)
                    .build()));
    public static final Item GOO_BUCKET = reg("goo_bucket", new net.jj.mountain.item.GooBucketItem(new Item.Properties().stacksTo(1)));
    public static final Item EYE = reg("mountain_eye", new net.jj.mountain.item.MountainEyeItem(new Item.Properties().durability(48).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final Item HORN = reg("mountain_horn", new net.jj.mountain.item.MountainHornItem(new Item.Properties().durability(64).rarity(net.minecraft.world.item.Rarity.RARE)));

    /** His hide, cut up and worn. Heavy, and it does not care much what hits it. */
    public static final net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> HIDE =
            Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "hide"),
                    new net.minecraft.world.item.ArmorMaterial(
                            java.util.Map.of(net.minecraft.world.item.ArmorItem.Type.BOOTS, 3,
                                    net.minecraft.world.item.ArmorItem.Type.LEGGINGS, 6,
                                    net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, 8,
                                    net.minecraft.world.item.ArmorItem.Type.HELMET, 3,
                                    net.minecraft.world.item.ArmorItem.Type.BODY, 11),
                            14, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER,
                            () -> net.minecraft.world.item.crafting.Ingredient.of(FLESH),
                            java.util.List.of(new net.minecraft.world.item.ArmorMaterial.Layer(
                                    ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "hide"))),
                            2.5f, 0.12f));

    public static final Item COMPASS = reg("mountain_compass", new net.jj.mountain.item.MountainCompassItem(
            new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final Item CODEX = reg("mountain_codex", new net.jj.mountain.item.MountainCodexItem(
            new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC).fireResistant()));

    public static final Item HIDE_HELMET = armour("hide_helmet", net.minecraft.world.item.ArmorItem.Type.HELMET);
    public static final Item HIDE_CHESTPLATE = armour("hide_chestplate", net.minecraft.world.item.ArmorItem.Type.CHESTPLATE);
    public static final Item HIDE_LEGGINGS = armour("hide_leggings", net.minecraft.world.item.ArmorItem.Type.LEGGINGS);
    public static final Item HIDE_BOOTS = armour("hide_boots", net.minecraft.world.item.ArmorItem.Type.BOOTS);

    private static Item armour(String name, net.minecraft.world.item.ArmorItem.Type type) {
        return reg(name, new net.minecraft.world.item.ArmorItem(HIDE, type, new Item.Properties().durability(type.getDurability(37))));
    }

    private static Item plain(String name, Item.Properties props) { return reg(name, new Item(props)); }

    private static Item reg(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, name), item);
    }

    private static Item egg(String name, int variant, int c1, int c2) {
        CompoundTag t = new CompoundTag();
        t.putString("id", MountainMod.MODID + ":mountain");
        t.putInt("MountainVariant", variant);
        return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, name),
                new SpawnEggItem(ModEntities.MOUNTAIN, c1, c2, new Item.Properties().component(DataComponents.ENTITY_DATA, CustomData.of(t))));
    }

    public static void init() {}
}
