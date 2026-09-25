package net.jj.hollowbell;

import net.jj.hollowbell.item.CodexItem;
import net.jj.hollowbell.item.StingerItem;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.Map;

public final class ModItems {
    public static final Item CALM_EGG = egg("calm_hollowbell_spawn_egg", 0, 0x52A284, 0xE5E1CF);
    public static final Item HUNTING_EGG = egg("hunting_hollowbell_spawn_egg", 1, 0x52A284, 0x70B919);
    public static final Item GUARDIAN_EGG = egg("guardian_hollowbell_spawn_egg", 2, 0x52A284, 0xF8C527);
    public static final Item SMALL_EGG = egg("small_hollowbell_spawn_egg", 3, 0x6C996E, 0xE5E1CF);
    public static final Item BELLING_EGG = reg("belling_spawn_egg", new SpawnEggItem(ModEntities.BELLING, 0x3E4447, 0xE5941D, new Item.Properties()));

    public static final Item POD = reg("hollowbell_pod", new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final Item BELL_GLASS = reg("bell_glass", new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final Item STINGER = reg("stinger", new StingerItem(Tiers.NETHERITE,
            new Item.Properties().rarity(Rarity.EPIC).fireResistant().attributes(SwordItem.createAttributes(Tiers.NETHERITE, 4, -2.2f))));
    public static final Item CODEX = reg("hollowbell_codex", new CodexItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final Item CROWN = reg("hollowbell_crown", new BlockItem(ModBlocks.CROWN, new Item.Properties().rarity(Rarity.EPIC)));

    /** bell glass armor: as tough as diamond, light, and it doesn't care for poison (see HollowbellMod) */
    public static final Holder<ArmorMaterial> BELL_GLASS_ARMOR = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
            ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "bell_glass"),
            new ArmorMaterial(Map.of(ArmorItem.Type.BOOTS, 3, ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.CHESTPLATE, 8,
                    ArmorItem.Type.HELMET, 3, ArmorItem.Type.BODY, 11),
                    18, SoundEvents.ARMOR_EQUIP_DIAMOND, () -> Ingredient.of(BELL_GLASS),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "bell_glass"))),
                    2.0f, 0.05f));
    public static final Item BELL_HELMET = armour("bell_glass_helmet", ArmorItem.Type.HELMET);
    public static final Item BELL_CHESTPLATE = armour("bell_glass_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final Item BELL_LEGGINGS = armour("bell_glass_leggings", ArmorItem.Type.LEGGINGS);
    public static final Item BELL_BOOTS = armour("bell_glass_boots", ArmorItem.Type.BOOTS);

    private static Item armour(String name, ArmorItem.Type type) {
        return reg(name, new ArmorItem(BELL_GLASS_ARMOR, type, new Item.Properties().durability(type.getDurability(35))));
    }

    private static Item reg(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, name), item);
    }

    private static Item egg(String name, int variant, int c1, int c2) {
        CompoundTag t = new CompoundTag();
        t.putString("id", HollowbellMod.MOD_ID + ":hollowbell");
        t.putInt("HollowbellEgg", variant);
        return reg(name, new SpawnEggItem(ModEntities.HOLLOWBELL, c1, c2, new Item.Properties().component(DataComponents.ENTITY_DATA, CustomData.of(t))));
    }

    public static void init() {}
}
