package net.jj.giants.minigames;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.minigamesmixin.MgServerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.3) What a player had in the main world, kept while they play or watch a minigame and
 * put back exactly when they come back: where they stood (world, place, look), their game mode, everything they
 * carried (every slot, the one in hand), their levels, health, food, air, fire and effects, and whether they were
 * flying.
 *
 * <p>It is written to its own file ({@code data/jj_giants_minigames/<uuid>.dat}) before anything of theirs changes, and
 * deleted only after they are back and their own player file has been saved, so it survives leaving the game, the
 * server stopping and a crash: whoever logs in with a file waiting is put back from it at once.
 */
public final class MgSnap {
    private MgSnap() {}

    public static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("jj_giants_minigames");
    }

    public static Path file(MinecraftServer server, UUID id) {
        return folder(server).resolve(id + ".dat");
    }

    /** what they have now (call before anything of theirs changes) */
    public static CompoundTag take(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.putInt("Version", 1);
        t.putString("Name", p.getGameProfile().getName());
        t.putString("Dim", p.level().dimension().location().toString());
        t.putDouble("X", p.getX());
        t.putDouble("Y", p.getY());
        t.putDouble("Z", p.getZ());
        t.putFloat("Yaw", p.getYRot());
        t.putFloat("Pitch", p.getXRot());
        t.putString("Mode", p.gameMode.getGameModeForPlayer().getName());
        t.put("Inventory", p.getInventory().save(new ListTag()));
        t.putInt("Selected", p.getInventory().selected);
        t.putInt("XpLevel", p.experienceLevel);
        t.putFloat("XpP", p.experienceProgress);
        t.putInt("XpTotal", p.totalExperience);
        t.putInt("Score", p.getScore());
        t.putFloat("Health", p.getHealth());
        t.putFloat("Absorption", p.getAbsorptionAmount());
        CompoundTag food = new CompoundTag();
        p.getFoodData().addAdditionalSaveData(food);
        t.put("Food", food);
        t.putInt("Air", p.getAirSupply());
        t.putInt("Fire", p.getRemainingFireTicks());
        ListTag fx = new ListTag();
        for (MobEffectInstance e : p.getActiveEffects()) fx.add(e.save());
        t.put("Effects", fx);
        t.putBoolean("Flying", p.getAbilities().flying);
        t.putLong("Taken", System.currentTimeMillis());
        return t;
    }

    /** written safely (a new file moved into place): false if it couldn't be, and then nothing of theirs may change */
    public static boolean write(MinecraftServer server, UUID id, CompoundTag t) {
        try {
            Path f = file(server, id);
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(id + ".tmp");
            NbtIo.writeCompressed(t, tmp);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            GiantsGuideMod.LOG.error("Giants minigames: couldn't keep {}'s things safe: {}", id, e.toString());
            return false;
        }
    }

    public static @Nullable CompoundTag read(MinecraftServer server, UUID id) {
        Path f = file(server, id);
        if (!Files.exists(f)) return null;
        try {
            return NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            GiantsGuideMod.LOG.error("Giants minigames: couldn't read {}'s kept things: {}", id, e.toString());
            return null;
        }
    }

    public static boolean waiting(MinecraftServer server, UUID id) {
        return Files.exists(file(server, id));
    }

    public static void delete(MinecraftServer server, UUID id) {
        try {
            Files.deleteIfExists(file(server, id));
        } catch (IOException e) {
            GiantsGuideMod.LOG.warn("Giants minigames: couldn't delete {}'s kept things: {}", id, e.toString());
        }
    }

    /** everything of theirs off them (going into a round: the snapshot is on disk already) */
    public static void strip(ServerPlayer p) {
        p.stopRiding();
        p.closeContainer();
        p.getInventory().clearContent();
        p.removeAllEffects();
        p.clearFire();
        p.setHealth(p.getMaxHealth());
        p.setAbsorptionAmount(0);
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5);
        p.setAirSupply(p.getMaxAirSupply());
        p.experienceLevel = 0;
        p.experienceProgress = 0;
        p.totalExperience = 0;
        p.fallDistance = 0;
        p.inventoryMenu.broadcastChanges();
    }

    /** put back exactly as kept: the place, the game mode, then everything else */
    public static void apply(ServerPlayer p, CompoundTag t) {
        MinecraftServer server = p.server;
        p.stopRiding();
        p.closeContainer();
        ServerLevel to = null;
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("Dim"));
        if (dim != null && !MgLevels.temporary(dim))
            to = server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        double x = t.getDouble("X"), y = t.getDouble("Y"), z = t.getDouble("Z");
        if (to == null) {
            // (their world is gone: a mod taken out) spawn, on the ground
            to = server.overworld();
            BlockPos sp = to.getSharedSpawnPos();
            x = sp.getX() + 0.5;
            z = sp.getZ() + 0.5;
            y = to.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
        }
        GameType mode = GameType.byName(t.getString("Mode"), GameType.SURVIVAL);
        // (the game mode first: a spectator can't be taken between worlds the usual way)
        p.setGameMode(mode);
        p.fallDistance = 0;
        p.teleportTo(to, x, y, z, t.getFloat("Yaw"), t.getFloat("Pitch"));
        p.getInventory().clearContent();
        p.getInventory().load(t.getList("Inventory", Tag.TAG_COMPOUND));
        p.getInventory().selected = Math.max(0, Math.min(8, t.getInt("Selected")));
        p.experienceLevel = t.getInt("XpLevel");
        p.experienceProgress = t.getFloat("XpP");
        p.totalExperience = t.getInt("XpTotal");
        p.setScore(t.getInt("Score"));
        p.removeAllEffects();
        for (Tag e : t.getList("Effects", Tag.TAG_COMPOUND)) {
            MobEffectInstance m = MobEffectInstance.load((CompoundTag) e);
            if (m != null) p.addEffect(m);
        }
        p.getFoodData().readAdditionalSaveData(t.getCompound("Food"));
        p.setHealth(Math.max(0.5f, t.getFloat("Health")));
        p.setAbsorptionAmount(t.getFloat("Absorption"));
        p.setAirSupply(t.getInt("Air"));
        p.setRemainingFireTicks(t.getInt("Fire"));
        p.fallDistance = 0;
        if (t.getBoolean("Flying") && p.getAbilities().mayfly) {
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        }
        p.inventoryMenu.broadcastChanges();
        p.containerMenu.broadcastChanges();
        // (levels are sent when they differ from what the game last told them)
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(p.experienceProgress,
                p.totalExperience, p.experienceLevel));
    }

    /** their own player file written now (so the kept file can go: it isn't needed any more) */
    public static void savePlayer(ServerPlayer p) {
        try {
            ((MgServerAccess) p.server).jjGiants$playerData().save(p);
        } catch (RuntimeException e) {
            GiantsGuideMod.LOG.warn("Giants minigames: couldn't save {}: {}", p.getGameProfile().getName(),
                    e.toString());
        }
    }
}
