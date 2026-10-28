package net.jj.hollowbell.solid;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Your own setting for sitting on him, kept in config/hollowbell-client.json (only on your computer): whether the
 * triple crouch sits you down (tripleCrouchSit, on to start with). The "Sit on giant" key works either way. The Giants
 * Guide's Controls tab reads and sets it through the bridge (controlget / controlset).
 */
public final class SitSettings {
    private SitSettings() {}

    public static final String TRIPLE_CROUCH = "tripleCrouchSit";
    static final String FILE = "hollowbell-client.json";

    private static boolean loaded;
    private static boolean tripleCrouch = true;

    /** where it's kept (tests point it elsewhere) */
    public static Path file = null;

    private static Path file() {
        return file != null ? file : FabricLoader.getInstance().getConfigDir().resolve(FILE);
    }

    public static boolean tripleCrouchSit() {
        load();
        return tripleCrouch;
    }

    public static void setTripleCrouchSit(boolean on) {
        load();
        tripleCrouch = on;
        save();
    }

    /** read it again next time (tests) */
    public static void reload() {
        loaded = false;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        tripleCrouch = true;
        try {
            if (Files.exists(file())) {
                JsonObject o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (o.has(TRIPLE_CROUCH)) tripleCrouch = o.get(TRIPLE_CROUCH).getAsBoolean();
            }
        } catch (Exception e) {
            // (a broken file: the setting as it starts)
        }
    }

    /** written into the file with whatever else is kept there */
    private static void save() {
        try {
            JsonObject o = new JsonObject();
            if (Files.exists(file())) {
                try {
                    o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
                } catch (Exception e) {
                    o = new JsonObject();
                }
            }
            o.addProperty(TRIPLE_CROUCH, tripleCrouch);
            Files.createDirectories(file().toAbsolutePath().getParent());
            Path tmp = file().resolveSibling(file().getFileName() + ".tmp");
            Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // (can't write it: kept for this session)
        }
    }

    /** the bridge's controlget: ["true"] / ["false"], or null for a setting that isn't mine */
    public static @Nullable List<String> controlGet(@Nullable String arg) {
        if (arg == null || !arg.trim().equals(TRIPLE_CROUCH)) return null;
        return List.of(Boolean.toString(tripleCrouchSit()));
    }

    /** the bridge's controlset: {"controls":1,"config":"tripleCrouchSit","value":false} */
    public static @Nullable List<String> controlSet(@Nullable String arg) {
        if (arg == null) return null;
        try {
            JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
            JsonElement c = o.get("config"), v = o.get("value");
            if (c == null || !c.getAsString().equals(TRIPLE_CROUCH) || v == null) return null;
            boolean on = v.getAsJsonPrimitive().isBoolean()
                    ? v.getAsBoolean()
                    : Boolean.parseBoolean(v.getAsString().trim());
            setTripleCrouchSit(on);
            return List.of("ok");
        } catch (Exception e) {
            return null;
        }
    }
}
