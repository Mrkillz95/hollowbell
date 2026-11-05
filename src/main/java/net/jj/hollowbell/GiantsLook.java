package net.jj.hollowbell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * (Admin book Part 12, ADMIN_BRIDGE.md §29) The Giants Guide's giant editor on this game: the looks (material sets)
 * the server gave this kind of giant, or one of him, each a map from his model's palette entries to other blocks. Only
 * how he is drawn changes (his meshes are built once more with these blocks and kept); his body, hits and solid blocks
 * stay his own. Nothing sent: every one of him is drawn as always. Same file in every giant (only the package differs).
 */
public final class GiantsLook {
    private GiantsLook() {}

    public static final int VERSION = 1;

    private record State(Map<String, Map<String, String>> maps, @Nullable String kind, Map<UUID, String> giants) {}

    private static volatile State state = new State(Map.of(), null, Map.of());
    /** goes up with every change (the meshes then free the looks no one wears any more) */
    public static volatile int version;

    /** the server: does this giant's version take looks ("look", answered by the giant's jar) */
    public static List<String> describe() {
        return List.of("{\"look\":" + VERSION + "}");
    }

    /**
     * The Guide on this game ("lookclient"): {"look":1,"maps":{"key":{"palette entry":"block",...}},"kind":"key",
     * "giants":{"uuid":"key"}}; {} = none (leaving a world). Null when it can't be read or is newer.
     */
    public static @Nullable List<String> receive(String json) {
        Map<String, Map<String, String>> maps = new HashMap<>();
        Map<UUID, String> giants = new HashMap<>();
        String kind = null;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("look") && o.get("look").getAsInt() > VERSION) return null;
            if (o.get("maps") instanceof JsonObject m)
                for (var e : m.entrySet()) {
                    if (!(e.getValue() instanceof JsonObject v)) continue;
                    Map<String, String> one = new HashMap<>();
                    for (var b : v.entrySet())
                        if (b.getValue().isJsonPrimitive()) one.put(b.getKey(), b.getValue().getAsString());
                    maps.put(e.getKey(), Map.copyOf(one));
                }
            if (o.get("kind") instanceof JsonElement k && k.isJsonPrimitive() && maps.containsKey(k.getAsString()))
                kind = k.getAsString();
            if (o.get("giants") instanceof JsonObject g)
                for (var e : g.entrySet())
                    if (e.getValue().isJsonPrimitive() && maps.containsKey(e.getValue().getAsString()))
                        try {
                            giants.put(UUID.fromString(e.getKey()), e.getValue().getAsString());
                        } catch (IllegalArgumentException bad) {
                            // (not a UUID: left out)
                        }
        } catch (RuntimeException e) {
            return null;
        }
        state = new State(Map.copyOf(maps), kind, Map.copyOf(giants));
        version++;
        return List.of("ok");
    }

    /** the look this one wears (his own, else his kind's), or null: drawn as always */
    public static @Nullable String of(Entity e) {
        State s = state;
        if (s.maps.isEmpty()) return null;
        String k = s.giants.get(e.getUUID());
        return k != null ? k : s.kind;
    }

    /** the look his kind wears (for one drawn without his entity at hand: far off), or null */
    public static @Nullable String ofKind() {
        State s = state;
        return s.maps.isEmpty() ? null : s.kind;
    }

    /** is this look still worn by anyone (else its meshes are freed) */
    public static boolean used(String look) {
        return state.maps.containsKey(look);
    }

    /** a palette entry of his model as this look draws it (a block that can't be read: his own) */
    public static String block(@Nullable String look, String entry) {
        if (look == null) return entry;
        Map<String, String> m = state.maps.get(look);
        String to = m == null ? null : m.get(entry);
        // (the Guide names every block in full: "minecraft:basalt" for his "basalt")
        if (to == null && m != null && entry.indexOf(':') < 0) to = m.get("minecraft:" + entry);
        if (to == null || to.equals(entry)) return entry;
        try {
            BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), to, false);
            return to;
        } catch (Exception e) {
            return entry;
        }
    }
}
