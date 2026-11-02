package net.jj.giants.admin;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * A new world's settings from before it existed (ADMIN_BRIDGE.md §18.7-18.8): the Create World screen's choice (armed
 * when Create New World is pressed), or on a dedicated server config/jj_giants_admin/new_world.txt. Put into the
 * world in the Guide's early step of the overworld's loading, before any giant chooses his ground and before any land
 * is made, and only for a world that is brand new.
 */
public final class AdminCreation {
    private AdminCreation() {}

    /** the log entry's id */
    public static final String LOG_ID = "creation";
    public static final String LOG_NAME = "Set at world creation";

    /**
     * The Create World screen's choice, waiting for its world.
     *
     * @param folder the new world's folder name (the screen's target folder)
     * @param bundle every value the creator chose (a replace bundle)
     * @param label  the preset as Home will show it ("Far Frontier", "Far Frontier, changed", "Custom")
     * @param who    the creator's name
     */
    public record Pending(String folder, AdminTransfer.Bundle bundle, String label, String who) {}

    private static volatile @Nullable Pending pending;

    /** Create New World was pressed with a choice made (the client, in the same game as its server) */
    public static void arm(@Nullable Pending p) {
        pending = p;
        if (p != null)
            GiantsGuideMod.LOG.info("Giants admin: the new world {} will start with {}", p.folder(), p.label());
    }

    public static @Nullable Pending pending() {
        return pending;
    }

    /** (the tests) a dedicated server's file is read even though this isn't one; null: only on a dedicated server */
    public static @Nullable Boolean testDedicated;
    /** (the tests) where new_world.txt is looked for; null: the game's config folder */
    public static @Nullable Path dirOverride;

    public static Path file() {
        return (dirOverride != null ? dirOverride : AdminPresets.dir()).resolve("new_world.txt");
    }

    /** the world being loaded is brand new: its spawn hasn't been looked for yet (nothing has run in it) */
    public static boolean brandNew(MinecraftServer server) {
        return !server.getWorldData().overworldData().isInitialized();
    }

    /** the world's folder name */
    public static String folder(MinecraftServer server) {
        Path p = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        return p.getFileName() == null ? "" : p.getFileName().toString();
    }

    /**
     * The Guide's early step of the overworld's loading: a brand-new world gets the Create World choice armed for it,
     * or (a dedicated server, nothing armed) new_world.txt. Returns how many settings went in.
     */
    public static int atLoad(MinecraftServer server, ServerLevel over) {
        Pending p = pending;
        pending = null;
        if (!brandNew(server)) return 0;
        return atLoad(server, over, p, folder(server));
    }

    /** the same with the world's folder and newness given (the tests) */
    public static int atLoad(MinecraftServer server, ServerLevel over, @Nullable Pending p, String folder) {
        if (p != null && p.folder().equals(folder)) return put(server, over, p.bundle(), p.label(), p.who());
        if (p != null)
            GiantsGuideMod.LOG.info("Giants admin: the Create World choice was for {}, not {}; left out", p.folder(),
                    folder);
        boolean dedicated = testDedicated != null ? testDedicated : server.isDedicatedServer();
        if (!dedicated) return 0;
        return fromFile(server, over);
    }

    /** new_world.txt, if there is one: a preset's id or name, a saved preset's name, or a settings string */
    static int fromFile(MinecraftServer server, ServerLevel over) {
        Path f = file();
        if (!Files.isRegularFile(f)) return 0;
        String line = null;
        try {
            for (String l : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String t = l.strip();
                if (!t.isEmpty() && !t.startsWith("#")) {
                    line = t;
                    break;
                }
            }
        } catch (IOException | RuntimeException e) {
            GiantsGuideMod.LOG.warn("Giants admin: couldn't read {}: {}", f, e.toString());
            return 0;
        }
        if (line == null) return 0;
        AdminTransfer.Bundle b = AdminTransfer.preset(line);
        String label;
        if (b != null) label = b.name().isEmpty() ? line : b.name();
        else {
            b = AdminTransfer.decode(line);
            label = "Imported settings";
        }
        if (b == null) {
            GiantsGuideMod.LOG.warn("Giants admin: new_world.txt says \"{}\", which is no preset here and no settings"
                    + " string (GA1-...): the new world starts at the server defaults", cut(line));
            return 0;
        }
        int n = put(server, over, b, label, "Server");
        GiantsGuideMod.LOG.info("Giants admin: the new world starts with {} from new_world.txt ({} settings)", label,
                n);
        return n;
    }

    private static String cut(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }

    /**
     * A bundle into the new world: planned against it as /giants admin would (each giant's own checks), every value
     * kept, and one log entry "Set at world creation". The giants are sent their values right after (the usual push).
     */
    static int put(MinecraftServer server, ServerLevel over, AdminTransfer.Bundle b, String label, String who) {
        AdminView view = AdminView.of(server, over);
        AdminWorld w = view.world();
        AdminTransfer.Plan plan = AdminTransfer.plan(view, b);
        Map<String, Map<String, JsonElement>> before = new LinkedHashMap<>();
        for (AdminTransfer.Step s : plan.changes()) {
            before.computeIfAbsent(s.giant(), k -> new HashMap<>()).put(s.id(), s.old());
            if (s.now() == null) w.remove(s.giant(), s.id());
            else w.put(s.giant(), s.id(), s.now());
        }
        int n = plan.changes().size();
        if (n == 0 && label.equals("Classic")) return 0;
        w.log(new AdminWorld.Change(System.currentTimeMillis(), who, "everything", LOG_ID, LOG_NAME, label,
                n + (n == 1 ? " change" : " changes"), AdminWorld.Change.undo(before)));
        if (!plan.skipped().isEmpty())
            GiantsGuideMod.LOG.info("Giants admin: at world creation, left out (not here or not known): {}",
                    String.join(", ", plan.skipped()));
        return n;
    }

    /** (the tests) every value the world was given at creation, as text, for one giant */
    public static List<String> ids(AdminWorld w, String giant) {
        return List.copyOf(w.values(giant).keySet());
    }
}
