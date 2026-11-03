package net.jj.giants.fun;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** (the tests) what the game's own events would call */
public final class FunTestAccess {
    private FunTestAccess() {}

    public static void left(ServerPlayer p) {
        Watch.left(p);
        PlayAs.left(p);
    }

    public static void joined(MinecraftServer s, ServerPlayer p) {
        Watch.joined(s, p);
    }

    public static void loaded(MinecraftServer s, LivingEntity e) {
        Marks.loaded(s, e);
    }

    public static void buildTick(MinecraftServer s) {
        Builds.tick(s);
    }
}
