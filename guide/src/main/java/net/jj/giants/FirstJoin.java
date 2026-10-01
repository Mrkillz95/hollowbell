package net.jj.giants;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Who has had their free Giants Guide in this world (saved with the world, so each player gets one once). */
public final class FirstJoin extends SavedData {
    private final Set<UUID> given = new HashSet<>();

    private static final Factory<FirstJoin> FACTORY = new Factory<>(FirstJoin::new, FirstJoin::read, null);

    public static FirstJoin get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "jj_giants_guide");
    }

    private static FirstJoin read(CompoundTag tag, HolderLookup.Provider p) {
        FirstJoin f = new FirstJoin();
        for (Tag t : tag.getList("Given", Tag.TAG_INT_ARRAY)) f.given.add(NbtUtils.loadUUID(t));
        return f;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        ListTag l = new ListTag();
        for (UUID u : given) l.add(NbtUtils.createUUID(u));
        tag.put("Given", l);
        return tag;
    }

    public boolean had(UUID id) { return given.contains(id); }

    /** a player joined: the first time ever in this world (and with the setting on), they get one guide */
    public static boolean onJoin(ServerPlayer player) {
        if (!GuideConfig.V.giveOnFirstJoin) return false;
        FirstJoin f = get(player.server);
        if (!f.given.add(player.getUUID())) return false;
        f.setDirty();
        GiantsGuideMod.give(player);
        return true;
    }
}
