package net.jj.hollowbell;

import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * What /giants asks of this mod. The same shape in all five of JJ's boss mods, so whichever of them runs the
 * command can call every one that is loaded, by name, without knowing any of them.
 */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static final int API = 1;
    private static final String WHO = "Hollowbell: ";

    /**
     * action: natural|limit|fight|away|volume|shake|bossbar|griefing|where|list|kill|remove|status|goto ("x z")|tp (player UUID)|paint ("radius full|biome playerUUID").
     * arg: the value as text, or "". Returns lines to show the caller (prefixed with the boss's name, plain
     * words); empty list = nothing; null = unknown action.
     */
    public static List<String> giants(MinecraftServer server, String action, String arg) {
        var V = HollowbellConfig.V;
        List<String> out = new ArrayList<>();
        String a = arg == null ? "" : arg.trim().toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "natural" -> {
                Boolean on = onOff(a);
                if (on != null) { V.oneInTheWorld = on; HollowbellConfig.save(); }
                out.add(WHO + (V.oneInTheWorld ? "he rises on his own." : "he only comes when summoned."));
            }
            case "limit" -> {
                if (!a.isEmpty()) {
                    try { V.maxInWorld = Mth.clamp(Integer.parseInt(a), 0, 20); HollowbellConfig.save(); }
                    catch (NumberFormatException e) { out.add(WHO + "the limit has to be a number from 0 to 20."); return out; }
                    if (V.maxInWorld > 0) WorldOne.limitNow(null, server.overworld());
                }
                out.add(WHO + (V.maxInWorld <= 0 ? "no limit." : "at most " + V.maxInWorld + "."));
            }
            case "fight" -> {
                Boolean on = onOff(a);
                if (on != null) { V.fightGiants = on; HollowbellConfig.save(); }
                out.add(WHO + (V.fightGiants ? "he fights the other bosses." : "he leaves the other bosses alone."));
            }
            case "away" -> {
                Boolean on = onOff(a);
                if (on != null) { V.offscreenTravel = on; HollowbellConfig.save(); }
                out.add(WHO + (V.offscreenTravel ? "he steps out of the world when nobody is near." : "he stays in the world."));
            }
            case "volume" -> {
                if (!a.isEmpty()) {
                    try { V.soundVolume = Mth.clamp(Float.parseFloat(a), 0f, 2f); HollowbellConfig.save(); }
                    catch (NumberFormatException e) { out.add(WHO + "the volume has to be a number from 0 to 2."); return out; }
                }
                out.add(WHO + "volume " + V.soundVolume + ".");
            }
            case "shake" -> {
                Boolean on = onOff(a);
                if (on != null) { V.screenShake = on; HollowbellConfig.save(); }
                out.add(WHO + "screen shake " + (V.screenShake ? "on." : "off."));
            }
            case "bossbar" -> {
                if (!a.isEmpty()) {
                    try { V.bossBarRange = Mth.clamp(Integer.parseInt(a), 0, 100000); HollowbellConfig.save(); }
                    catch (NumberFormatException e) { out.add(WHO + "the boss bar range has to be a number of blocks."); return out; }
                }
                out.add(WHO + "boss bars show " + (V.bossBarRange > 0 ? V.bossBarRange + " blocks off." : "as far as his size says."));
            }
            case "griefing" -> {
                Boolean on = onOff(a);
                if (on != null) { V.griefing = on; HollowbellConfig.save(); }
                out.add(WHO + (V.griefing ? "he breaks blocks." : "he doesn't break blocks."));
            }
            case "where", "list" -> {
                for (ServerLevel l : server.getAllLevels())
                    for (HollowbellEntity h : l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying()))
                        out.add(WHO + "at " + h.getBlockX() + " " + h.getBlockY() + " " + h.getBlockZ() + " ("
                                + l.dimension().location() + "), health " + (int) h.healthNow() + "/" + (int) h.healthMax()
                                + (action.equals("list") ? ", size " + String.format("%.2f", h.bellScale()) : "") + ".");
                long now = server.overworld().getGameTime();
                for (Away.Rec r : Away.get(server).all()) {
                    Vec3 s = r.spot(now);
                    out.add(WHO + "out of the world near " + (int) s.x + " " + (int) s.z + " (" + r.dim + ").");
                }
                if (out.isEmpty()) {
                    WorldOne w = WorldOne.get(server);
                    int days = w.daysLeft(server.overworld());
                    if (V.oneInTheWorld && w.where() != null && days > 0)
                        out.add(WHO + "none standing. The next comes down near " + w.where().getX() + " " + w.where().getZ() + " in about " + days + " days.");
                    else out.add(WHO + "none standing.");
                }
            }
            case "kill" -> {
                int n = net.jj.hollowbell.world.FarOrders.killAll(server);
                out.add(WHO + (n == 0 ? "none to kill." : "killed " + n + "."));
            }
            case "remove" -> {
                int n = Away.get(server).count();
                WorldOne w = WorldOne.get(server);
                for (Away.Rec r : Away.get(server).all()) w.removed(server.overworld(), r.id);
                Away.get(server).forgetAll();
                for (ServerLevel l : server.getAllLevels())
                    for (HollowbellEntity h : new ArrayList<>(l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved()))) {
                        h.discard();
                        w.removed(server.overworld(), h.getUUID());
                        n++;
                    }
                out.add(WHO + (n == 0 ? "none to remove." : "removed " + n + "."));
            }
            case "goto" -> {
                String[] xz = a.split("\\s+");
                double x, z;
                try { x = Double.parseDouble(xz[0]); z = Double.parseDouble(xz[1]); }
                catch (Exception e) { out.add(WHO + "goto needs an x and a z."); return out; }
                int n = 0;
                for (ServerLevel l : server.getAllLevels())
                    for (var t : net.jj.hollowbell.world.FarOrders.all(l))
                        if (net.jj.hollowbell.world.FarOrders.order(l, t, new Vec3(x, 0, z), null, 0f) != null) n++;
                out.add(WHO + (n == 0 ? "none to send." : n + " on the way to " + Mth.floor(x) + ", " + Mth.floor(z) + "."));
            }
            case "paint" -> {
                // "radius mode playerUUID"
                String[] w = arg == null ? new String[0] : arg.trim().split("\\s+");
                net.minecraft.server.level.ServerPlayer p = null;
                int radius = 64;
                boolean full = true;
                for (String x : w) {
                    if (x.equalsIgnoreCase("biome")) full = false;
                    else if (x.equalsIgnoreCase("full")) full = true;
                    else if (x.matches("\\d+")) radius = Integer.parseInt(x);
                    else try { p = server.getPlayerList().getPlayer(java.util.UUID.fromString(x)); } catch (Exception ignored) {}
                }
                if (p == null) { out.add(WHO + "nobody to paint round."); return out; }
                int n = net.jj.hollowbell.world.Painter.start(p, radius, full);
                out.add(WHO + "painting the Bell Hollows " + Mth.clamp(radius, 16, 512) + " blocks round " + p.getGameProfile().getName()
                        + (full ? " (land and biome)" : " (biome only)") + ", " + n + " chunks. It can't be undone.");
            }
            case "tp" -> {
                net.minecraft.server.level.ServerPlayer p = null;
                try { p = server.getPlayerList().getPlayer(java.util.UUID.fromString(arg.trim())); } catch (Exception ignored) {}
                if (p == null) { out.add(WHO + "nobody to take there."); return out; }
                out.add(WHO + net.jj.hollowbell.world.TakeMe.tp(p, 0).getString());
            }
            case "status" -> {
                int standing = Away.get(server).count();
                for (ServerLevel l : server.getAllLevels()) standing += l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying()).size();
                out.add(WHO + standing + " standing, " + (V.maxInWorld <= 0 ? "no limit" : "at most " + V.maxInWorld)
                        + ", " + (V.oneInTheWorld ? "rises on his own" : "only when summoned")
                        + ", " + (V.fightGiants ? "fights the others" : "leaves the others alone")
                        + ", volume " + V.soundVolume + ".");
            }
            default -> { return null; }
        }
        return out;
    }

    private static Boolean onOff(String a) {
        return switch (a) {
            case "on", "true", "yes" -> Boolean.TRUE;
            case "off", "false", "no" -> Boolean.FALSE;
            default -> null;
        };
    }
}
