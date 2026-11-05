package net.jj.giants.minigamesmixin;

import java.util.Map;
import java.util.concurrent.Executor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * (Part 10A) What the minigames need of the server to make a world for one round and throw it away after: its map of
 * worlds, the threads new worlds load land on, the world's folder, and where players' files are kept.
 */
@Mixin(MinecraftServer.class)
public interface MgServerAccess {
    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> jjGiants$levels();

    @Accessor("executor")
    Executor jjGiants$executor();

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess jjGiants$storage();

    @Accessor("playerDataStorage")
    PlayerDataStorage jjGiants$playerData();
}
