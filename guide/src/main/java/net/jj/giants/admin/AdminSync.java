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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Settings sync for server owners (Part 2B): config/jj_giants_admin/sync.txt holds one settings string (GA1-..., from
 * /giants admin export or the book's Export) or the bundle's JSON. When the server starts and that file has changed
 * since it was last applied, every giant setting it names becomes the server default (as /giants admin set ...
 * allworlds does: each giant's config file), and the server log says so. Settings it doesn't name keep their config
 * file values. The all-giants multipliers and the per-move switches are only ever kept for each world, so the sync
 * leaves them out (and says so). /giants admin sync now applies it again; sync status tells how things stand.
 */
public final class AdminSync {
    private AdminSync() {}

    public static Path file() {
        return AdminPresets.dir().resolve("sync.txt");
    }

    private static Path applied() {
        return AdminPresets.dir().resolve("sync.applied.json");
    }

    /**
     * What one sync did.
     *
     * @param applied   settings that became server defaults
     * @param skipped   what it left out, in plain words ("Wreckback move.claw_snip.on (only kept for each world)")
     */
    public record Outcome(boolean ran, List<String> lines, int applied, List<String> skipped) {}

    /** at server start: applies the file if it changed since last time */
    public static Outcome onStart(MinecraftServer server) {
        return run(server, false);
    }

    /** the file's text, or null if there's none */
    private static @Nullable String read() {
        try {
            return Files.isRegularFile(file()) ? Files.readString(file(), StandardCharsets.UTF_8).trim() : null;
        } catch (IOException e) {
            GiantsGuideMod.LOG.warn("Giants settings sync: couldn't read {}: {}", file(), e.toString());
            return null;
        }
    }

    static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private static @Nullable JsonObject last() {
        try {
            return Files.isRegularFile(applied())
                    ? JsonParser.parseString(Files.readString(applied(), StandardCharsets.UTF_8)).getAsJsonObject()
                    : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** applies the sync file: always (force, sync now) or only if it changed since it was last applied */
    public static Outcome run(MinecraftServer server, boolean force) {
        String text = read();
        if (text == null || text.isEmpty())
            return new Outcome(false, List.of("No settings sync file (" + where() + ")."), 0, List.of());
        String h = hash(text);
        JsonObject last = last();
        if (!force && last != null && h.equals(str(last, "hash")))
            return new Outcome(false, List.of("The settings sync file hasn't changed since it was applied."), 0,
                    List.of());
        AdminTransfer.Bundle b = AdminTransfer.decode(text);
        if (b == null) {
            String line = "The settings sync file isn't a settings string (GA1-... from /giants admin export, or its"
                    + " JSON). Nothing changed.";
            GiantsGuideMod.LOG.warn("Giants settings sync: {}", line);
            return new Outcome(false, List.of(line), 0, List.of());
        }
        int n = 0;
        List<String> skipped = new ArrayList<>();
        for (Map.Entry<String, Map<String, JsonElement>> g : b.values().entrySet()) {
            if (g.getKey().equals(AdminCatalog.ALL)) {
                for (String id : g.getValue().keySet()) skipped.add("All giants " + id + " (only kept for each world)");
                continue;
            }
            Giant giant = AdminServer.giant(g.getKey());
            AdminJson.Report r = giant == null ? null : AdminServer.report(server, giant);
            if (r == null) {
                skipped.add(g.getKey() + " (not here)");
                continue;
            }
            for (var e : g.getValue().entrySet()) {
                AdminSetting s = r.setting(e.getKey());
                if (s == null) {
                    skipped.add(r.name() + " " + e.getKey() + " (not known)");
                    continue;
                }
                if (s.id().startsWith("move.")) {
                    skipped.add(r.name() + " " + s.name() + " (only kept for each world)");
                    continue;
                }
                JsonElement v;
                try {
                    v = s.parse(AdminSetting.text(e.getValue()));
                } catch (AdminSetting.BadValue bad) {
                    skipped.add(r.name() + " " + s.name() + " (" + bad.getMessage() + ")");
                    continue;
                }
                if (AdminSetting.same(v, s.def())) continue; // (already the server default)
                String why = AdminServer.writeDefault(server, giant, r.name(), s, v);
                if (why != null) skipped.add(r.name() + " " + s.name() + " (" + why + ")");
                else n++;
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add("Settings sync: " + n + (n == 1 ? " server default" : " server defaults") + " set from "
                + where() + ".");
        if (!skipped.isEmpty()) lines.add("Left out: " + String.join("; ", skipped) + ".");
        for (String l : lines) GiantsGuideMod.LOG.info("Giants {}", l);
        if (n > 0 && server.overworld() != null) {
            AdminWorld w = AdminWorld.get(server);
            w.log(new AdminWorld.Change(System.currentTimeMillis(), "Server", "everything", "sync",
                    "settings sync (server defaults)", "", n + (n == 1 ? " change" : " changes"), ""));
        }
        JsonObject done = new JsonObject();
        done.addProperty("hash", h);
        done.addProperty("time", System.currentTimeMillis());
        done.addProperty("applied", n);
        JsonArray sk = new JsonArray();
        for (String s : skipped) sk.add(s);
        done.add("skipped", sk);
        try {
            Files.createDirectories(AdminPresets.dir());
            Files.writeString(applied(), new GsonBuilder().setPrettyPrinting().create().toJson(done),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            GiantsGuideMod.LOG.warn("Giants settings sync: couldn't note it was applied: {}", e.toString());
        }
        return new Outcome(true, lines, n, skipped);
    }

    /** /giants admin sync status */
    public static List<String> status() {
        String text = read();
        JsonObject last = last();
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) out.add("No settings sync file. Put a settings string in " + where()
                + " and it becomes the server defaults when the server starts.");
        else out.add("Settings sync file: " + where() + " (" + text.length() + " characters).");
        if (last == null) out.add("It has never been applied on this server.");
        else {
            String when = new java.text.SimpleDateFormat("MMM d HH:mm", Locale.ROOT)
                    .format(new java.util.Date(last.get("time").getAsLong()));
            out.add("Last applied " + when + ": " + last.get("applied").getAsInt() + " server defaults set"
                    + (last.getAsJsonArray("skipped").isEmpty() ? "." : ", " + last.getAsJsonArray("skipped").size()
                            + " left out."));
            if (text != null && !text.isEmpty())
                out.add(hash(text).equals(str(last, "hash")) ? "The file hasn't changed since."
                        : "The file has changed since: it is applied at the next start (or /giants admin sync now).");
        }
        return out;
    }

    private static String where() {
        return "config/jj_giants_admin/sync.txt";
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) ? o.get(k).getAsString() : "";
    }
}
