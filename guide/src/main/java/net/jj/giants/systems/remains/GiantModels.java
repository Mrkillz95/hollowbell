package net.jj.giants.systems.remains;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.jj.giants.GiantsGuideMod;

/**
 * (remains) Each giant's model and rig, read from his own mod's files (only when he is loaded), kept while memory
 * allows. Null when his mod isn't here or his files can't be read (the plain plan is used then).
 */
public final class GiantModels {
    private GiantModels() {}

    private static final Map<String, SoftReference<GiantModel>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> FAILED = new ConcurrentHashMap<>();

    public static GiantModel get(String kind) {
        Design d = Designs.of(kind);
        if (d == null || FAILED.containsKey(kind)) return null;
        SoftReference<GiantModel> ref = CACHE.get(kind);
        GiantModel m = ref == null ? null : ref.get();
        if (m != null) return m;
        try (InputStream mi = open(d.mod, d.modelPath); InputStream ri = open(d.mod, d.rigPath)) {
            if (mi == null || ri == null) {
                FAILED.put(kind, true);
                return null;
            }
            m = GiantModel.read(kind, mi, ri);
            CACHE.put(kind, new SoftReference<>(m));
            return m;
        } catch (Exception | LinkageError e) {
            GiantsGuideMod.LOG.warn("Giants remains: couldn't read the {} model, his remains use the plain plan", kind, e);
            FAILED.put(kind, true);
            return null;
        }
    }

    /** a file of his mod (null when his mod isn't loaded or hasn't it) */
    static InputStream open(String mod, String path) throws java.io.IOException {
        Optional<ModContainer> c = FabricLoader.getInstance().getModContainer(mod);
        if (c.isEmpty()) return null;
        Optional<Path> p = c.get().findPath(path.startsWith("/") ? path.substring(1) : path);
        if (p.isPresent()) return Files.newInputStream(p.get());
        return GiantModels.class.getResourceAsStream(path);
    }
}
