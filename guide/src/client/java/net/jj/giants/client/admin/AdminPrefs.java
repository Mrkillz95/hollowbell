package net.jj.giants.client.admin;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * What the admin book remembers for each player on this game (config/jj_giants_adminbook.json): Simple or Advanced,
 * and whether they have seen the new-admin walkthrough. Kept by the player's id, so two people sharing a game each
 * keep their own. Any trouble reading or writing it is ignored: the book then just starts in Simple mode with the
 * walkthrough.
 */
public final class AdminPrefs {
    private AdminPrefs() {}

    private static JsonObject all;
    /** (the picture-taking) where to keep it instead, or null */
    static Path override;

    private static Path file() {
        if (override != null) return override;
        return FabricLoader.getInstance().getConfigDir().resolve("jj_giants_adminbook.json");
    }

    private static String who() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) return mc.player.getUUID().toString();
            return mc.getUser().getProfileId().toString();
        } catch (RuntimeException e) {
            return "anyone";
        }
    }

    private static JsonObject mine() {
        if (all == null) {
            all = new JsonObject();
            try {
                Path f = file();
                if (Files.exists(f)) {
                    JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
                    if (e.isJsonObject()) all = e.getAsJsonObject();
                }
            } catch (Exception | Error e) {
                all = new JsonObject();
            }
        }
        JsonObject players;
        try {
            if (!(all.get("players") instanceof JsonObject p)) {
                players = new JsonObject();
                all.add("players", players);
            } else players = p;
            if (!(players.get(who()) instanceof JsonObject m)) {
                JsonObject n = new JsonObject();
                players.add(who(), n);
                return n;
            } else return m;
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static boolean flag(String k, boolean or) {
        try {
            JsonObject m = mine();
            return m.has(k) ? m.get(k).getAsBoolean() : or;
        } catch (RuntimeException e) {
            return or;
        }
    }

    private static void put(String k, boolean v) {
        try {
            mine().addProperty(k, v);
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, new GsonBuilder().setPrettyPrinting().create().toJson(all), StandardCharsets.UTF_8);
        } catch (Exception | Error e) {
            // (kept for this time the game runs, at least)
        }
    }

    public static boolean advanced() {
        return flag("advanced", false);
    }

    public static void advanced(boolean on) {
        put("advanced", on);
    }

    public static boolean seenIntro() {
        return flag("seenIntro", false);
    }

    public static void seenIntro(boolean seen) {
        put("seenIntro", seen);
    }
}
