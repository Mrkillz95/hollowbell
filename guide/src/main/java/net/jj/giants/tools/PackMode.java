package net.jj.giants.tools;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminDraft;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 6, ADMIN_BRIDGE.md §22.12) Pack mode, for modpacks: config/jj_giants_admin/pack.json names the pack and the
 * preset it plays with, {"name":"Giant Isles","preset":"titans","lock":false}. New worlds start with that preset
 * (the Create World picker opens on it, a dedicated server's new world takes it); a world with nothing set takes it
 * when it first starts. With "lock": true the preset stays: no other preset, import, snapshot, profile, schedule or
 * vote can be applied, and "every setting back" is refused (single settings can still be changed, and changed back).
 */
public final class PackMode {
    private PackMode() {}

    /** the pack, as its file says (null: no pack file, or one that can't be read) */
    public record Pack(String name, String preset, boolean lock) {
        public String presetName() {
            AdminTransfer.Bundle b = AdminTransfer.preset(preset);
            return b == null ? preset : AdminTransfer.title(b);
        }
    }

    private static @Nullable Pack pack;
    private static boolean loaded;

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("jj_giants_admin").resolve("pack.json");
    }

    /** reads the pack file (at every server start, and the first time the Create World screen asks) */
    public static void load() {
        loaded = true;
        pack = null;
        Path f = file();
        if (!Files.isRegularFile(f)) return;
        try {
            JsonObject o = AdminJson.object(Files.readString(f, StandardCharsets.UTF_8));
            if (o == null) throw new IOException("not JSON");
            String preset = AdminJson.str(o, "preset", 64);
            if (preset.isEmpty() || AdminTransfer.preset(preset) == null)
                throw new IOException("no preset called \"" + preset + "\"");
            String name = AdminJson.str(o, "name", 48);
            boolean lock = o.has("lock") && o.get("lock").getAsBoolean();
            pack = new Pack(name.isEmpty() ? "This modpack" : name, preset, lock);
        } catch (IOException | RuntimeException e) {
            GiantsGuideMod.LOG.warn("Giants pack mode: {} can't be used ({}); no pack.", f, e.getMessage());
        }
    }

    public static @Nullable Pack pack() {
        if (!loaded) load();
        return pack;
    }

    /** (the tests) a pack as if its file said so, or none */
    public static void packForTests(@Nullable Pack p) {
        loaded = true;
        pack = p;
    }

    /** why a preset, import or the like can't be applied: the pack keeps its preset (null: it can) */
    public static @Nullable String refusal() {
        Pack p = pack();
        if (p == null || !p.lock()) return null;
        return p.name() + " keeps the " + p.presetName() + " preset: other presets, imports, snapshots, schedules and"
                + " votes can't be applied (config/jj_giants_admin/pack.json).";
    }

    /** the same for one bundle: the pack's own preset always may */
    public static @Nullable String refusal(AdminTransfer.Bundle b) {
        Pack p = pack();
        if (p == null || !p.lock()) return null;
        String id = AdminTransfer.builtInId(p.preset());
        if (b.id().equals(id != null ? id : p.preset()) || b.name().equalsIgnoreCase(p.presetName())) return null;
        return refusal();
    }

    /** a world starting: a pack's preset goes in once (or whenever the pack changes, when it locks its preset) */
    public static void onStart(MinecraftServer server) {
        Pack p = pack();
        if (p == null) return;
        ToolsData d = ToolsData.get(server);
        String h = Profiles.hash(p.name() + "/" + p.preset() + "/" + p.lock());
        if (h.equals(d.s.packHash)) return;
        AdminWorld w = AdminWorld.get(server);
        if (p.lock() || w.giantsWithValues().isEmpty()) {
            AdminTransfer.Bundle b = AdminTransfer.preset(p.preset());
            if (b != null) {
                int n = AdminTransfer.apply(server, "Modpack", AdminTransfer.plan(server, b));
                GiantsGuideMod.LOG.info("Giants pack mode: {} put in the {} preset ({} settings)", p.name(),
                        p.presetName(), n);
            }
        }
        d.s.packHash = h;
        d.s.packAt = System.currentTimeMillis();
        d.setDirty();
    }

    /** the Create World picker's draft: the pack's preset to start with */
    public static AdminDraft newDraft() {
        AdminDraft d = new AdminDraft();
        Pack p = pack();
        if (p != null) d.pick(p.preset());
        return d;
    }

    public static JsonObject json() {
        JsonObject o = new JsonObject();
        Pack p = pack();
        o.addProperty("on", p != null);
        if (p != null) {
            o.addProperty("name", p.name());
            o.addProperty("preset", p.preset());
            o.addProperty("presetName", p.presetName());
            o.addProperty("lock", p.lock());
        }
        o.addProperty("file", "config/jj_giants_admin/pack.json");
        return o;
    }

    public static AdminServer.Result status() {
        Pack p = pack();
        if (p == null)
            return AdminServer.Result.said(List.of("No modpack settings. A pack puts {\"name\":\"...\",\"preset\":"
                    + "\"<preset>\",\"lock\":false} in config/jj_giants_admin/pack.json: new worlds start with that"
                    + " preset, and with lock true it stays."));
        return AdminServer.Result.said(List.of(p.name() + " plays with the " + p.presetName() + " preset"
                + (p.lock() ? " and keeps it: other presets, imports, snapshots, schedules and votes can't be applied."
                        : ": new worlds start with it; admins may change it.")));
    }
}
