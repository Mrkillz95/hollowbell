package net.jj.hollowbell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
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

    // (1.16) his minions wear his look

    private static final Map<Entity, Object[]> MINIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, Map<String, String>> ANY = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile int anyVersion = -1;

    /**
     * The look one of his minions wears (robots, Bellings, Mudlings, Furrowlings): the look of the nearest one of him
     * within 96 blocks (his own, else his kind's), else his kind's; or null: drawn as always. Looked up again once a
     * second.
     */
    public static @Nullable String ofMinion(Entity minion, Class<? extends Entity> giant) {
        State s = state;
        if (s.maps.isEmpty()) return null;
        if (s.giants.isEmpty()) return s.kind;
        Object[] c = MINIONS.get(minion);
        if (c != null && (int) c[1] == version && Math.abs(minion.tickCount - (int) c[2]) < 20) return (String) c[0];
        String look = s.kind;
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity g : minion.level().getEntitiesOfClass(giant, minion.getBoundingBox().inflate(96))) {
            double d = g.distanceToSqr(minion);
            if (d < bd) {
                bd = d;
                best = g;
            }
        }
        if (best != null) {
            String k = s.giants.get(best.getUUID());
            if (k != null) look = k;
        }
        MINIONS.put(minion, new Object[] {look, version, minion.tickCount});
        return look;
    }

    /**
     * A block of a minion's own model in this look: as his own model has it in the look, else the look's block for the
     * same block of his, else the look's block for his own block nearest in colour (glowing to glowing).
     */
    public static String blockAny(@Nullable String look, String entry) {
        if (look == null) return entry;
        Map<String, String> m = state.maps.get(look);
        if (m == null) return entry;
        String to = block(look, entry);
        if (!to.equals(entry) || m.containsKey(entry) || m.containsKey("minecraft:" + entry)) return to;
        if (anyVersion != version) {
            ANY.clear();
            anyVersion = version;
        }
        Map<String, String> per = ANY.computeIfAbsent(look, k -> new java.util.concurrent.ConcurrentHashMap<>());
        String kept = per.get(entry);
        if (kept != null) return kept;
        String out = entry;
        BlockState st = parse(entry);
        if (st != null) {
            String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
            String props = entry.indexOf('[') < 0 ? "" : entry.substring(entry.indexOf('['));
            String same = null, near = null;
            int col = st.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
            boolean glow = st.getLightEmission() > 0;
            double nd = Double.MAX_VALUE;
            for (var e : m.entrySet()) {
                BlockState from = parse(e.getKey());
                if (from == null) continue;
                if (BuiltInRegistries.BLOCK.getKey(from.getBlock()).toString().equals(id)) {
                    same = e.getValue();
                    break;
                }
                if ((from.getLightEmission() > 0) != glow) continue;
                int c = from.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
                double d = sq((c >> 16 & 255) - (col >> 16 & 255)) + sq((c >> 8 & 255) - (col >> 8 & 255))
                        + sq((c & 255) - (col & 255));
                if (d < nd) {
                    nd = d;
                    near = e.getValue();
                }
            }
            String pick = same != null ? same : near;
            if (pick != null) {
                // (his own shape kept when the look's block takes it: a slab stays a slab)
                String base = pick.indexOf('[') < 0 ? pick : pick.substring(0, pick.indexOf('['));
                out = !props.isEmpty() && parse(base + props) != null ? base + props
                        : parse(pick) != null ? pick : entry;
            }
        }
        per.put(entry, out);
        return out;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static @Nullable BlockState parse(String text) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
        } catch (Exception e) {
            return null;
        }
    }
}
