package net.jj.hollowbell.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the world remembers for him: everybody's own safe list (the players and the kinds of creature each player
 * has told him to leave alone), and the names that go with them. Only the list of whoever is holding the book
 * counts at any one time, the same as the Mountain's.
 */
public final class BellWorld extends SavedData {
    private final Map<UUID, Set<UUID>> friends = new HashMap<>();
    private final Map<UUID, Set<String>> kinds = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    private static final SavedData.Factory<BellWorld> FACTORY = new SavedData.Factory<>(BellWorld::new, BellWorld::load, null);

    public static BellWorld get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "hollowbell");
    }

    public boolean onList(UUID owner, UUID who) { Set<UUID> s = friends.get(owner); return s != null && s.contains(who); }

    public boolean kindOnList(UUID owner, EntityType<?> type) {
        Set<String> s = kinds.get(owner);
        return s != null && s.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    public boolean toggleFriend(UUID owner, UUID who) {
        Set<UUID> s = friends.computeIfAbsent(owner, k -> new HashSet<>());
        boolean on = s.add(who);
        if (!on) s.remove(who);
        setDirty();
        return on;
    }

    public void addFriend(UUID owner, UUID who) { friends.computeIfAbsent(owner, k -> new HashSet<>()).add(who); setDirty(); }

    public void dropFriend(UUID owner, UUID who) { Set<UUID> s = friends.get(owner); if (s != null && s.remove(who)) setDirty(); }

    public boolean toggleKind(UUID owner, EntityType<?> type) {
        Set<String> s = kinds.computeIfAbsent(owner, k -> new HashSet<>());
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        boolean on = s.add(id);
        if (!on) s.remove(id);
        setDirty();
        return on;
    }

    public void dropKind(UUID owner, String id) { Set<String> s = kinds.get(owner); if (s != null && s.remove(id)) setDirty(); }

    public List<UUID> listOf(UUID owner) {
        Set<UUID> s = friends.get(owner);
        List<UUID> out = s == null ? new ArrayList<>() : new ArrayList<>(s);
        out.sort(Comparator.comparing(u -> nameOf(u).toLowerCase(Locale.ROOT)));
        return out;
    }

    public List<String> kindsOf(UUID owner) {
        Set<String> s = kinds.get(owner);
        List<String> out = s == null ? new ArrayList<>() : new ArrayList<>(s);
        out.sort(String::compareTo);
        return out;
    }

    public String nameOf(UUID who) { return names.getOrDefault(who, who.toString().substring(0, 8)); }
    public void rememberName(UUID who, String name) { if (!name.equals(names.get(who))) { names.put(who, name); setDirty(); } }

    /** the everyday name of a kind, for the book's page */
    public static String kindName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return id;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(rl).map(t -> t.getDescription().getString()).orElse(id);
    }

    private static BellWorld load(CompoundTag tag, HolderLookup.Provider p) {
        BellWorld w = new BellWorld();
        ListTag l = tag.getList("Safe", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            UUID owner = NbtUtils.loadUUID(c.get("Owner"));
            Set<UUID> set = new HashSet<>();
            ListTag who = c.getList("Who", Tag.TAG_INT_ARRAY);
            for (Tag t : who) set.add(NbtUtils.loadUUID(t));
            if (!set.isEmpty()) w.friends.put(owner, set);
        }
        l = tag.getList("SafeKinds", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            UUID owner = NbtUtils.loadUUID(c.get("Owner"));
            Set<String> set = new HashSet<>();
            ListTag k = c.getList("Kinds", Tag.TAG_STRING);
            for (int j = 0; j < k.size(); j++) set.add(k.getString(j));
            if (!set.isEmpty()) w.kinds.put(owner, set);
        }
        l = tag.getList("Names", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            w.names.put(NbtUtils.loadUUID(c.get("Id")), c.getString("Name"));
        }
        return w;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        ListTag l = new ListTag();
        for (var e : friends.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            CompoundTag c = new CompoundTag();
            c.put("Owner", NbtUtils.createUUID(e.getKey()));
            ListTag who = new ListTag();
            for (UUID u : e.getValue()) who.add(NbtUtils.createUUID(u));
            c.put("Who", who);
            l.add(c);
        }
        tag.put("Safe", l);
        l = new ListTag();
        for (var e : kinds.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            CompoundTag c = new CompoundTag();
            c.put("Owner", NbtUtils.createUUID(e.getKey()));
            ListTag k = new ListTag();
            for (String s : e.getValue()) k.add(StringTag.valueOf(s));
            c.put("Kinds", k);
            l.add(c);
        }
        tag.put("SafeKinds", l);
        l = new ListTag();
        for (var e : names.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.put("Id", NbtUtils.createUUID(e.getKey()));
            c.putString("Name", e.getValue());
            l.add(c);
        }
        tag.put("Names", l);
        return tag;
    }
}
