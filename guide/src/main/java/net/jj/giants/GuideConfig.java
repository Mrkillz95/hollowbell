package net.jj.giants;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** config/jj_giants.json: the guide's own settings. */
public final class GuideConfig {
    public static final class Values {
        /** every player gets one Giants Guide the first time they join a world */
        public boolean giveOnFirstJoin = true;
    }

    public static Values V = new Values();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GuideConfig() {}

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("jj_giants.json"); }

    public static void load() {
        Path f = file();
        try {
            if (Files.exists(f)) {
                Values v = GSON.fromJson(Files.readString(f), Values.class);
                if (v != null) V = v;
            }
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(V));
        } catch (Exception e) {
            GiantsGuideMod.LOG.warn("Giants Guide: could not read {}, using the defaults", f, e);
        }
    }
}
