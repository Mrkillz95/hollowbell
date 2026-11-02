package net.jj.giants.admin;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Presets an admin saved from a world's settings (Part 2B, the Presets page): kept for the whole server in
 * config/jj_giants_admin/presets.json, so every world on it can use them. Each is a bundle (ADMIN_BRIDGE.md,
 * "Bundles") that puts exactly the saved values back, with who saved it; only they (or the server console) delete it.
 */
public final class AdminPresets {
    private AdminPresets() {}

    /** most presets kept on one server */
    public static final int MOST = 50;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 '_\\-]{0,31}");

    /**
     * One saved preset.
     *
     * @param by   who saved it (a player's name, or "Server")
     * @param byId their UUID ("" for the console)
     */
    public record Saved(String name, String by, String byId, long time, AdminTransfer.Bundle bundle) {}

    /** (the tests) where the file is; null: the game's config folder */
    public static @Nullable Path dirOverride;

    private static @Nullable List<Saved> cache;

    public static Path dir() {
        return dirOverride != null ? dirOverride : FabricLoader.getInstance().getConfigDir().resolve("jj_giants_admin");
    }

    private static Path file() {
        return dir().resolve("presets.json");
    }

    /** read again from the file next time (the tests, and a server starting) */
    public static void forget() {
        cache = null;
    }

    /** every saved preset, oldest first */
    public static List<Saved> list() {
        if (cache == null) cache = load();
        return List.copyOf(cache);
    }

    private static List<Saved> load() {
        List<Saved> out = new ArrayList<>();
        Path f = file();
        if (!Files.isRegularFile(f)) return out;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : o.getAsJsonArray("presets")) {
                try {
                    JsonObject p = e.getAsJsonObject();
                    AdminTransfer.Bundle b = AdminTransfer.read(p.getAsJsonObject("bundle").toString());
                    String name = p.get("name").getAsString();
                    if (b == null || !NAME.matcher(name).matches()) continue;
                    out.add(new Saved(name, p.get("by").getAsString(), p.has("byId") ? p.get("byId").getAsString() : "",
                            p.has("time") ? p.get("time").getAsLong() : 0, b));
                } catch (RuntimeException bad) {
                    // (one that can't be read is left out)
                }
            }
        } catch (IOException | RuntimeException e) {
            GiantsGuideMod.LOG.warn("Giants admin: couldn't read {}: {}", f, e.toString());
        }
        return out;
    }

    private static void write(List<Saved> list) throws IOException {
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray();
        for (Saved s : list) {
            JsonObject p = new JsonObject();
            p.addProperty("name", s.name());
            p.addProperty("by", s.by());
            p.addProperty("byId", s.byId());
            p.addProperty("time", s.time());
            p.add("bundle", JsonParser.parseString(AdminTransfer.write(s.bundle())));
            a.add(p);
        }
        o.add("presets", a);
        Files.createDirectories(dir());
        Path tmp = dir().resolve("presets.json.tmp");
        Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
        Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        cache = new ArrayList<>(list);
    }

    /** the saved preset with this name (any case), or null */
    public static @Nullable Saved find(String name) {
        String n = name.trim();
        for (Saved s : list()) if (s.name().equalsIgnoreCase(n)) return s;
        return null;
    }

    /** a saved preset's id in the change log ("saved-my-weekend") */
    static String idOf(String name) {
        return "saved-" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    /** /giants admin savepreset, the book's "Save as preset": this world's settings now, under a name */
    public static AdminServer.Result save(MinecraftServer server, String who, String whoId, String name) {
        return save(AdminWorld.get(server), who, whoId, name);
    }

    /** the same from any world's values (the Create World screen's draft too) */
    public static AdminServer.Result save(AdminWorld w, String who, String whoId, String name) {
        String n = name == null ? "" : name.trim();
        if (!NAME.matcher(n).matches())
            return AdminServer.Result.fail("A preset's name is 1 to 32 letters, numbers, spaces, ' - or _.");
        for (String b : AdminTransfer.presets())
            if (b.equalsIgnoreCase(n) || AdminTransfer.preset(b) != null
                    && AdminTransfer.preset(b).name().equalsIgnoreCase(n))
                return AdminServer.Result.fail(n + " is a built-in preset. Pick another name.");
        List<Saved> all = new ArrayList<>(list());
        Saved old = find(n);
        if (old != null && !old.byId().equals(whoId))
            return AdminServer.Result.fail("There's already a preset called " + old.name() + " (saved by " + old.by()
                    + ").");
        if (old == null && all.size() >= MOST)
            return AdminServer.Result.fail("This server already keeps " + MOST + " presets. Delete one first.");
        Map<String, Map<String, JsonElement>> v = new LinkedHashMap<>();

        int count = 0;
        for (String g : w.giantsWithValues()) {
            v.put(g, w.values(g));
            count += w.values(g).size();
        }
        String when = new java.text.SimpleDateFormat("MMM d", Locale.ROOT).format(new java.util.Date());
        AdminTransfer.Bundle b = new AdminTransfer.Bundle(idOf(n), n, "Saved by " + who + " on " + when + ".", true, v);
        Saved s = new Saved(n, who, whoId, System.currentTimeMillis(), b);
        if (old != null) all.set(all.indexOf(old), s);
        else all.add(s);
        try {
            write(all);
        } catch (IOException e) {
            return AdminServer.Result.fail("Couldn't write the presets file: " + e.getMessage());
        }
        AdminBook.changed("presets");
        return new AdminServer.Result(true,
                List.of((old != null ? "Saved over your preset " : "Saved this world's settings as the preset ") + n
                        + " (" + count + (count == 1 ? " setting" : " settings") + "). Every world on this server can"
                        + " use it."),
                List.of(who + " saved the preset " + n + "."));
    }

    /** /giants admin deletepreset: only whoever saved it (or the server console) */
    public static AdminServer.Result delete(String who, String whoId, String name) {
        Saved s = find(name);
        if (s == null) return AdminServer.Result.fail("No saved preset called " + name.trim() + ".");
        if (!whoId.isEmpty() && !s.byId().equals(whoId))
            return AdminServer.Result.fail(s.name() + " was saved by " + s.by() + ": only they can delete it.");
        List<Saved> all = new ArrayList<>(list());
        all.remove(s);
        try {
            write(all);
        } catch (IOException e) {
            return AdminServer.Result.fail("Couldn't write the presets file: " + e.getMessage());
        }
        AdminBook.changed("presets");
        return new AdminServer.Result(true, List.of("Deleted the preset " + s.name() + "."),
                List.of(who + " deleted the preset " + s.name() + "."));
    }
}
