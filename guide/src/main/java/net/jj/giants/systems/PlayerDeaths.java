package net.jj.giants.systems;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 8, ADMIN_BRIDGE.md §24.3) A player dying near a giant: killed by anything of his, or dying within his reach
 * while he fights. "keep": nothing drops and their things and levels come back with them; "swallow": their things go
 * into him and drop where he falls. "normal" (as shipped): the game's own death.
 */
public final class PlayerDeaths {
    private PlayerDeaths() {}

    /** players whose things stay with them through this death (until they come back) */
    private static final Set<UUID> keeping = ConcurrentHashMap.newKeySet();
    public static final int MOST_SWALLOWED = 256;

    static void forget() {
        keeping.clear();
        dyingFrom.clear();
        dyingAt.clear();
    }

    /** the mixin, as a player's things would drop: true when they don't (kept, or swallowed) */
    public static boolean dropping(Player p) {
        if (!(p instanceof ServerPlayer sp) || !(p.level() instanceof ServerLevel sl)) return false;
        String rule = Systems.option(sl.getServer(), "playerDeath");
        if (rule.isEmpty() || rule.equals("normal")) return false;
        LivingEntity giant = giantFor(sp, sl);
        if (giant == null) return false;
        String kind = FunGiants.kindOf(giant);
        String name = AdminServer.giant(kind) == null ? "giant" : AdminServer.nameOf(AdminServer.giant(kind));
        if (rule.equals("keep")) {
            keeping.add(p.getUUID());
            sp.sendSystemMessage(Component.literal("You fell to the " + name + ", but you keep your things.")
                    .withStyle(ChatFormatting.GRAY));
            return true;
        }
        // swallow
        SystemsData d = SystemsData.get(sl.getServer());
        SystemsData.G g = d.giant(giant.getStringUUID(), kind);
        if (g.born == 0) {
            g.born = sl.getGameTime();
            g.start = FunGiants.sizeOf(giant);
            g.seen = g.born;
        }
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            inv.setItem(i, ItemStack.EMPTY);
            if (EnchantmentHelper.has(s, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) continue;
            if (g.belly.size() >= MOST_SWALLOWED) {
                // (his belly is full: this one drops as usual)
                p.drop(s, true, false);
                continue;
            }
            g.belly.add(s.save(sl.registryAccess()).toString());
            n++;
        }
        d.setDirty();
        if (n > 0)
            sp.sendSystemMessage(Component.literal("The " + name
                    + " swallowed your things. Bring him down to get them back.").withStyle(ChatFormatting.GOLD));
        Systems.log("a {} swallowed {} stacks of {}'s", name, n, sp.getGameProfile().getName());
        SystemsAdmin.changed();
        return true;
    }

    /** the mixin: no experience dropped for a player who keeps their things */
    public static boolean keeps(Player p) {
        return keeping.contains(p.getUUID());
    }

    /** a player came back after dying: what they kept goes with them */
    public static void copy(ServerPlayer old, ServerPlayer now, boolean alive) {
        if (alive || !keeping.remove(old.getUUID())) return;
        now.getInventory().replaceWith(old.getInventory());
        now.experienceLevel = old.experienceLevel;
        now.totalExperience = old.totalExperience;
        now.experienceProgress = old.experienceProgress;
    }

    /** the blow that is killing each player now (the game notes its own only after the death) */
    private static final java.util.Map<UUID, DamageSource> dyingFrom = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Long> dyingAt = new ConcurrentHashMap<>();

    /** (ALLOW_DEATH) a player is about to die of this */
    static boolean dying(net.minecraft.world.entity.LivingEntity e, DamageSource src, float amount) {
        if (e instanceof ServerPlayer p) {
            dyingFrom.put(p.getUUID(), src);
            dyingAt.put(p.getUUID(), p.level().getGameTime());
        }
        return true;
    }

    /** the giant this player fell to: whose was the blow, else one fighting close by */
    static @Nullable LivingEntity giantFor(ServerPlayer p, ServerLevel sl) {
        Long at = dyingAt.remove(p.getUUID());
        DamageSource now = dyingFrom.remove(p.getUUID());
        for (DamageSource src : new DamageSource[] {at != null && at == sl.getGameTime() ? now : null,
                p.getLastDamageSource()}) {
            if (src == null) continue;
            LivingEntity g = Systems.giantBehind(sl, src);
            if (g != null) return g;
        }
        if (p.getLastHurtByMob() != null && FunGiants.kindOf(p.getLastHurtByMob()) != null) return p.getLastHurtByMob();
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(256),
                x -> FunGiants.kindOf(x) != null && x.isAlive())) {
            if (!(e instanceof Mob m) || m.getTarget() == null) continue;
            String kind = FunGiants.kindOf(e);
            double reach = Math.max(16, FunGiants.height(kind, FunGiants.sizeOf(e)));
            double dd = e.distanceTo(p);
            if (dd <= reach && dd < bd) {
                bd = dd;
                best = e;
            }
        }
        return best;
    }

    /** he fell: what he swallowed drops where he fell */
    static void dropBelly(ServerLevel sl, LivingEntity e, SystemsData.G g) {
        if (g.belly.isEmpty()) return;
        List<ItemStack> drop = new ArrayList<>();
        for (String s : g.belly)
            try {
                CompoundTag t = TagParser.parseTag(s);
                ItemStack.parse(sl.registryAccess(), t).ifPresent(drop::add);
            } catch (Exception bad) {
                Systems.log("a swallowed stack couldn't be read: {}", s);
            }
        for (ItemStack s : drop) {
            ItemEntity it = new ItemEntity(sl, e.getX(), e.getY() + 1, e.getZ(), s);
            it.setDeltaMovement((sl.random.nextDouble() - 0.5) * 0.4, 0.35, (sl.random.nextDouble() - 0.5) * 0.4);
            it.setDefaultPickUpDelay();
            sl.addFreshEntity(it);
        }
        Systems.log("a fallen giant gave back {} swallowed stacks at {}", drop.size(),
                e.blockPosition().toShortString());
        g.belly.clear();
    }
}
