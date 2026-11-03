package net.jj.giants.systems;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.tools.Watch;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 9, ADMIN_BRIDGE.md 25.7) Sacred ground: nobody may build or break blocks in a giant's ground (his biome: any
 * biome of his mod, painted ones too) while one of him lives in the world. Breaking, placing blocks, emptying buckets
 * and lighting fires there are refused with one line on the action bar; players in creative mode still may (admins
 * build that way). Opening doors and chests, fighting and everything else is as always. Which kinds live is looked at
 * every five seconds (their live lists); nothing is loaded.
 */
public final class Sacred {
    private Sacred() {}

    private static volatile boolean on;
    /** the kinds standing in the world now (one of him alive: here, out of the world or in unloaded land) */
    private static volatile Set<String> living = Set.of();
    private static final Map<UUID, Long> told = new HashMap<>();

    /** (the tests) a biome id that counts as a giant's ground: biome -> giant key */
    public static final Map<String, String> TEST_BIOMES = new java.util.concurrent.ConcurrentHashMap<>();

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !refused(player, level, pos));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() || !on) return InteractionResult.PASS;
            ItemStack st = player.getItemInHand(hand);
            if (!builds(st)) return InteractionResult.PASS;
            BlockPos at = hit.getBlockPos();
            if (refused(player, level, at.relative(hit.getDirection())) || refused(player, level, at))
                return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack st = player.getItemInHand(hand);
            if (level.isClientSide() || !on || !(st.getItem() instanceof BucketItem))
                return InteractionResultHolder.pass(st);
            HitResult hit = player.pick(player.blockInteractionRange(), 0, true);
            if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK
                    && (refused(player, level, b.getBlockPos()) || refused(player, level,
                            b.getBlockPos().relative(b.getDirection()))))
                return InteractionResultHolder.fail(st);
            return InteractionResultHolder.pass(st);
        });
    }

    static void forget() {
        on = false;
        living = Set.of();
        told.clear();
    }

    /** every five seconds: on or off, and which kinds live */
    public static void tick(MinecraftServer server) {
        on = Systems.on(server, "sacred");
        if (!on) {
            living = Set.of();
            return;
        }
        java.util.Set<String> l = new java.util.HashSet<>();
        for (var e : Watch.rows(server).entrySet()) if (!e.getValue().isEmpty()) l.add(e.getKey());
        living = Set.copyOf(l);
    }

    private static boolean builds(ItemStack st) {
        return st.getItem() instanceof BlockItem || st.getItem() instanceof BucketItem
                || st.getItem() instanceof FlintAndSteelItem || st.getItem() instanceof FireChargeItem;
    }

    /** whose ground this is (his key), or null */
    public static @Nullable String groundOf(Level level, BlockPos pos) {
        var key = level.getBiome(pos).unwrapKey().orElse(null);
        if (key == null) return null;
        String id = key.location().toString();
        String test = TEST_BIOMES.get(id);
        if (test != null) return test;
        String space = key.location().getNamespace();
        for (Giant g : Giants.ALL) if (g.modId().equals(space)) return g.key();
        return null;
    }

    /** may this player not build or break here (told why, once a second at most) */
    public static boolean refused(Player player, Level level, BlockPos pos) {
        if (!on || level.isClientSide() || player.isCreative() || player.isSpectator()) return false;
        String kind = groundOf(level, pos);
        if (kind == null || !living.contains(kind)) return false;
        if (player instanceof ServerPlayer sp) {
            long now = sp.serverLevel().getGameTime();
            Long last = told.get(sp.getUUID());
            if (last == null || now - last >= 20 || now < last) {
                told.put(sp.getUUID(), now);
                Giant g = AdminServer.giant(kind);
                String name = g == null ? kind : AdminServer.nameOf(g);
                sp.displayClientMessage(Component.literal("This is the " + name + "'s sacred ground: nothing may be"
                        + " built or broken here while he lives."), true);
            }
        }
        refusals++;
        return true;
    }

    public static int refusals;

    /** (the tests) these kinds live now, and the system on */
    public static void setForTests(boolean isOn, List<String> kinds) {
        on = isOn;
        living = Set.copyOf(kinds);
    }

    static Set<String> living() {
        return living;
    }
}
