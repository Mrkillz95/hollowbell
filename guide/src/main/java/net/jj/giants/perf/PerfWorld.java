package net.jj.giants.perf;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** The world's Features (operators only), saved with the world: setting id to value. Ids not here are at default. */
public final class PerfWorld extends SavedData {
    private final Map<String, Double> values = new LinkedHashMap<>();

    private static final Factory<PerfWorld> FACTORY = new Factory<>(PerfWorld::new, PerfWorld::read, null);

    public static PerfWorld get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "jj_giants_performance");
    }

    private static PerfWorld read(CompoundTag tag, HolderLookup.Provider p) {
        PerfWorld w = new PerfWorld();
        CompoundTag v = tag.getCompound("Values");
        for (String k : v.getAllKeys()) {
            double d = v.getDouble(k);
            if (Double.isFinite(d)) w.values.put(k, d);
        }
        return w;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        CompoundTag v = new CompoundTag();
        for (var e : values.entrySet()) v.putDouble(e.getKey(), e.getValue());
        tag.put("Values", v);
        return tag;
    }

    public Map<String, Double> values() {
        return values;
    }

    /** a value kept (one at its default is just forgotten: that's the same, and keeps the file small) */
    public void put(PerfSetting s, double v) {
        v = s.clamp(v);
        if (Math.abs(v - s.def()) < 1e-9) values.remove(s.id());
        else values.put(s.id(), v);
        setDirty();
    }
}
