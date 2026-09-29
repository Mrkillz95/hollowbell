package net.jj.hollowbell.item;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jj.hollowbell.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Bell glass armor. As tough as diamond and light, and the full set carries his own gifts: poison and his toll's
 * dizziness never touch you, falling fast wakes slow falling (the way he lets things down), and hurt low, the glass
 * rings once on its own. The "Armour power" key does the Bell toll or a glide (see BellPower).
 */
public class BellArmorItem extends ArmorItem {
    public BellArmorItem(Holder<ArmorMaterial> material, Type type, Properties p) { super(material, type, p); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.hollowbell.bell_glass.tip").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("item.hollowbell.bell_glass.tip2").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("item.hollowbell.bell_glass.tip3").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("item.hollowbell.bell_glass.power", Component.keybind("key.hollowbell.armour_power")).withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, ctx, tip, flag);
    }

    /** wearing all four pieces */
    public static boolean fullSet(LivingEntity p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.BELL_HELMET)
                && p.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.BELL_CHESTPLATE)
                && p.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.BELL_LEGGINGS)
                && p.getItemBySlot(EquipmentSlot.FEET).is(ModItems.BELL_BOOTS);
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BellArmorItem::tick);
        ServerTickEvents.END_SERVER_TICK.register(BellPower::tick);
        // hurt low in the full set: the glass rings once, throwing back what's close and giving you a moment's cover
        ServerLivingEntityEvents.AFTER_DAMAGE.register((e, src, base, taken, blocked) -> {
            if (e instanceof ServerPlayer p && p.isAlive() && taken > 0) lastRing(p);
        });
        // poison's own bite (one point of magic a tick while poisoned) never reaches somebody in the full set;
        // other magic, like a potion of harming, still does
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amount) -> {
            if (e instanceof ServerPlayer p && src.is(DamageTypes.MAGIC) && amount <= 1.0f && p.hasEffect(MobEffects.POISON) && fullSet(p)) {
                p.removeEffect(MobEffects.POISON);
                return false;
            }
            return true;
        });
    }

    private static void tick(MinecraftServer server) {
        boolean slow = server.getTickCount() % 10 == 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // the fall is checked every tick (a fall is quick); the rest every half second
            if (p.fallDistance > 3f || slow) abilities(p);
        }
    }

    /** when the glass last rang on its own, per player (game time) */
    private static final java.util.Map<java.util.UUID, Long> RANG = new java.util.HashMap<>();
    public static final int LAST_RING_WAIT = 600;

    /** below a third of your health in the full set: a ring goes out and you get two hearts of cover (every 30 s at most) */
    public static boolean lastRing(ServerPlayer p) {
        if (!fullSet(p) || p.getHealth() > p.getMaxHealth() / 3f) return false;
        long now = p.serverLevel().getGameTime();
        Long was = RANG.get(p.getUUID());
        if (was != null && now - was < LAST_RING_WAIT && now >= was) return false;
        RANG.put(p.getUUID(), now);
        BellPower.ring(p, 0.5f);
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0, true, true, true));
        return true;
    }
    public static void forgetRings() { RANG.clear(); }

    /** one pass of the set's gifts (the tests call this straight) */
    public static void abilities(ServerPlayer p) {
        if (p.isSpectator() || !fullSet(p)) return;
        net.jj.hollowbell.HollowbellMod.award(p, "bell_glass_set");
        if (p.hasEffect(MobEffects.POISON)) p.removeEffect(MobEffects.POISON);
        // his toll's ringing in your head (nausea) doesn't get through the glass either
        if (p.hasEffect(MobEffects.CONFUSION)) p.removeEffect(MobEffects.CONFUSION);
        // falling fast (more than three blocks, not gliding): he lets you down the way he lets things down
        if (p.fallDistance > 3f && !p.isFallFlying() && !p.getAbilities().flying && !p.isInWater()) {
            MobEffectInstance now = p.getEffect(MobEffects.SLOW_FALLING);
            if (now == null || now.getDuration() < 40)
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 70, 0, true, false, true));
            p.resetFallDistance();
        }
    }
}
