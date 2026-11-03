package net.jj.giants.systems;

import java.util.List;
import java.util.Map;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.tools.Watch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * (Part 8, ADMIN_BRIDGE.md §24.3) Fear and awe, every two seconds: players near a wild giant on the hunt slow down
 * (and, strong, the dark closes in); players beside a giant they own stand taller (strength and resistance). Short
 * effects, refreshed while it lasts, so they wear off a few seconds after.
 */
public final class Fear {
    private Fear() {}

    public static void tick(MinecraftServer server) {
        String fear = Systems.option(server, "fear");
        boolean awe = Systems.on(server, "awe");
        if ((fear.equals("off") || fear.isEmpty()) && !awe) return;
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        Map<String, List<Watch.Row>> rows = Watch.rows(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || p.isCreative()) continue;
            String dim = p.level().dimension().location().toString();
            boolean afraid = false, proud = false;
            for (var e : rows.entrySet())
                for (Watch.Row r : e.getValue()) {
                    if (!"here".equals(r.state()) || !dim.equals(r.dim())) continue;
                    double size = AdminJson.num(r.r(), "size", 1);
                    double h = FunGiants.height(e.getKey(), size);
                    double dist = Math.hypot(p.getX() - r.x(), p.getZ() - r.z());
                    String owner = AdminJson.str(r.r(), "ownerId", 40);
                    boolean mine = owner.equals(p.getStringUUID());
                    if (awe && mine && dist <= 32 + h * 0.5) proud = true;
                    if (!fear.equals("off") && owner.isEmpty() && !mine
                            && (r.fighting() || "hunting".equals(AdminJson.str(r.r(), "mood", 16)))
                            && !Look.flag(r.r(), "asleep") && dist <= Math.min(160, 16 + h * 0.6))
                        afraid = true;
                }
            if (afraid) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true, false, true));
                if (fear.equals("strong"))
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false, true));
            }
            if (proud) {
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, true, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, false, true));
            }
        }
    }
}
