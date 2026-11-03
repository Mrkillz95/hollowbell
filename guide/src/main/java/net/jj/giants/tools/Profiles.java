package net.jj.giants.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 6, ADMIN_BRIDGE.md §22.11) Per-world profiles: config/jj_giants_admin/worlds/<world folder>.txt holds one
 * settings string (GA1-... or its JSON) for the world with that folder name. When that world starts and the file has
 * changed since it was last put in, it becomes this world's own settings (as a preset: a snapshot first, locked ones
 * left alone; Home then reads "Profile <world>"). So one game or server keeps a different set for each of its worlds
 * in plain files. The book (or /giants admin profile save) writes the world's settings into its file.
 */
public final class Profiles {
    private Profiles() {}

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("jj_giants_admin").resolve("worlds");
    }

    /** the world's folder name, as a file name */
    public static String worldName(MinecraftServer server) {
        Path p = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String n = p.getFileName() == null ? "world" : p.getFileName().toString();
        n = n.replaceAll("[^A-Za-z0-9 _.()-]", "_").strip();
        return n.isEmpty() ? "world" : n;
    }

    public static Path file(MinecraftServer server) {
        return dir().resolve(worldName(server) + ".txt");
    }

    /** where the file is, as people say it */
    public static String where(MinecraftServer server) {
        return "config/jj_giants_admin/worlds/" + worldName(server) + ".txt";
    }

    private static @Nullable String read(MinecraftServer server) {
        try {
            Path f = file(server);
            return Files.isRegularFile(f) ? Files.readString(f, StandardCharsets.UTF_8).trim() : null;
        } catch (IOException e) {
            GiantsGuideMod.LOG.warn("Giants profile: couldn't read {}: {}", file(server), e.toString());
            return null;
        }
    }

    static String hash(String text) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    /** at server start: the profile goes in if it changed since it last did */
    public static void onStart(MinecraftServer server) {
        String text = read(server);
        if (text == null || text.isEmpty()) return;
        ToolsData d = ToolsData.get(server);
        if (hash(text).equals(d.s.profileHash)) return;
        AdminServer.Result r = apply(server, "Profile", text);
        for (String l : r.lines()) GiantsGuideMod.LOG.info("Giants profile: {}", l);
    }

    /** /giants admin profile apply: the file's settings into this world now */
    public static AdminServer.Result applyNow(MinecraftServer server, String who) {
        String text = read(server);
        if (text == null || text.isEmpty())
            return AdminServer.Result.fail("This world has no profile file (" + where(server) + ").");
        return apply(server, who, text);
    }

    private static AdminServer.Result apply(MinecraftServer server, String who, String text) {
        String no = PackMode.refusal();
        if (no != null) return AdminServer.Result.fail(no);
        AdminTransfer.Bundle b = AdminTransfer.decode(text);
        if (b == null)
            return AdminServer.Result.fail("The profile file isn't a settings string (GA1-... from Export, or its"
                    + " JSON). Nothing changed.");
        String name = "Profile " + worldName(server);
        AdminTransfer.Bundle as = new AdminTransfer.Bundle("profile", name,
                "This world's own settings from " + where(server) + ".", b.replace(), b.values(), b.keep());
        AdminTransfer.Plan plan = AdminTransfer.plan(server, as);
        int n = AdminTransfer.apply(server, who, plan);
        ToolsData d = ToolsData.get(server);
        d.s.profileHash = hash(text);
        d.s.profileAt = System.currentTimeMillis();
        d.setDirty();
        ToolsAdmin.changed();
        List<String> lines = new ArrayList<>(List.of(name + ": " + n + (n == 1 ? " setting" : " settings")
                + " changed for this world."));
        if (!plan.locked().isEmpty()) lines.add("Locked, so left as they are: " + String.join(", ", plan.locked())
                + ".");
        return new AdminServer.Result(true, lines, n == 0 ? List.of() : List.of(who + " put in this world's profile ("
                + n + " changes)."));
    }

    /** /giants admin profile save: this world's settings into its file (it isn't put in again for that) */
    public static AdminServer.Result save(MinecraftServer server, String who) {
        String text = AdminTransfer.encode(AdminTransfer.current(server));
        try {
            Files.createDirectories(dir());
            Files.writeString(file(server), text + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return AdminServer.Result.fail("Couldn't write " + where(server) + ": " + e.getMessage());
        }
        ToolsData d = ToolsData.get(server);
        d.s.profileHash = hash(text);
        d.s.profileAt = System.currentTimeMillis();
        d.setDirty();
        Areas.log(server, who, "profile", "Profile saved to " + where(server));
        return new AdminServer.Result(true, List.of("Saved this world's settings to " + where(server) + ". When this"
                + " world starts and that file has been changed, the world takes it."), List.of(who
                + " saved this world's profile."));
    }

    public static AdminServer.Result delete(MinecraftServer server, String who) {
        try {
            if (!Files.deleteIfExists(file(server)))
                return AdminServer.Result.fail("This world has no profile file (" + where(server) + ").");
        } catch (IOException e) {
            return AdminServer.Result.fail("Couldn't delete " + where(server) + ": " + e.getMessage());
        }
        ToolsData d = ToolsData.get(server);
        d.s.profileHash = "";
        d.setDirty();
        Areas.log(server, who, "profile", "Profile deleted: " + where(server));
        return new AdminServer.Result(true, List.of("Deleted " + where(server) + ". The world keeps the settings it"
                + " has."), List.of(who + " deleted this world's profile file."));
    }

    /** the page's and /giants admin profile's words */
    public static com.google.gson.JsonObject json(MinecraftServer server) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        String text = read(server);
        ToolsData d = ToolsData.get(server);
        o.addProperty("file", where(server));
        o.addProperty("exists", text != null && !text.isEmpty());
        o.addProperty("applied", d.s.profileAt);
        o.addProperty("changed", text != null && !text.isEmpty() && !hash(text).equals(d.s.profileHash));
        return o;
    }

    public static AdminServer.Result status(MinecraftServer server) {
        com.google.gson.JsonObject o = json(server);
        List<String> lines = new ArrayList<>();
        if (!o.get("exists").getAsBoolean())
            lines.add("No profile for this world. /giants admin profile save writes its settings to " + where(server)
                    + "; edit or copy that file and the world takes it when it next starts.");
        else {
            lines.add("This world's profile: " + where(server) + ".");
            lines.add(o.get("changed").getAsBoolean()
                    ? "It has changed since it was last put in: it goes in when the world next starts (or /giants admin"
                            + " profile apply)."
                    : "It is what the world last took (or saved).");
        }
        return AdminServer.Result.said(lines);
    }
}
