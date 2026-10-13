package net.jj.hollowbell;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.jj.hollowbell.command.OpsCommand;
import net.jj.hollowbell.entity.Giants;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.FarOrders;
import net.jj.hollowbell.world.Painter;
import net.jj.hollowbell.world.TakeMe;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * What /giants asks of this mod. The same shape in every giant mod, so whichever one runs the command can call
 * every one that is loaded, by name, without knowing any of them.
 */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static final int API = 1;
    private static final String WHO = "Hollowbell: ";

    /**
     * action: natural|limit|fight|meetings|meet (the other giant's
     * UUID)|away|volume|shake|bossbar|griefing|heavydelay|where|list|kill|remove|status|goto ("x z")|tp (player
     * UUID)|paint ("radius full|biome playerUUID")|paintstop. arg: the value as text, or "". Returns lines to show the
     * caller (prefixed with the boss's name, plain words); empty list = nothing; null = unknown action.
     */
    public static List<String> giants(MinecraftServer server, String action, String arg) {
        List<String> out = new ArrayList<>();
        String a = arg == null ? "" : arg.trim().toLowerCase(Locale.ROOT);
        switch (action) {
            case "natural" -> natural(a, out);
            case "limit" -> limit(server, a, out);
            case "fight" -> fight(a, out);
            case "meetings" -> meetings(a, out);
            case "meet" -> meet(server, arg, out);
            case "away" -> away(a, out);
            case "volume" -> volume(a, out);
            case "shake" -> shake(a, out);
            case "bossbar" -> bossbar(a, out);
            case "heavydelay" -> heavyDelay(a, out);
            case "griefing" -> griefing(a, out);
            case "where", "list" -> where(server, action, out);
            case "kill" -> kill(server, out);
            case "remove" -> remove(server, out);
            case "goto" -> goTo(server, a, out);
            case "config" -> config(arg, out);
            case "paint" -> paint(server, a, arg, out);
            case "paintstop" -> paintStop(out);
            case "tp" -> tp(server, arg, out);
            case "status" -> status(server, out);
            default -> {
                return null;
            }
        }
        return out;
    }

    private static void natural(String a, List<String> out) {
        var V = BellConfig.V;
        Boolean on = onOff(a);
        if (on != null) {
            V.oneInTheWorld = on;
            BellConfig.save();
        }
        out.add(WHO + (V.oneInTheWorld ? "he rises on his own." : "he only comes when summoned."));
    }

    private static void limit(MinecraftServer server, String a, List<String> out) {
        var V = BellConfig.V;
        if (!a.isEmpty()) {
            try {
                V.maxInWorld = Mth.clamp(Integer.parseInt(a), 0, 20);
                BellConfig.save();
            } catch (NumberFormatException e) {
                out.add(WHO + "the limit has to be a number from 0 to 20.");
                return;
            }
            if (V.maxInWorld > 0) WorldOne.limitNow(null, server.overworld());
        }
        out.add(WHO + (V.maxInWorld <= 0 ? "no limit." : "at most " + V.maxInWorld + "."));
    }

    private static void fight(String a, List<String> out) {
        var V = BellConfig.V;
        Boolean on = onOff(a);
        if (on != null) {
            V.fightGiants = on;
            BellConfig.save();
        }
        out.add(WHO + (V.fightGiants ? "he fights the other bosses." : "he leaves the other bosses alone."));
    }

    private static void meetings(String a, List<String> out) {
        var V = BellConfig.V;
        Boolean on = onOff(a);
        if (on != null) {
            V.meetings = on;
            BellConfig.save();
        }
        out.add(WHO
                + (V.meetings
                        ? "he fights or keeps away from the giants he meets."
                        : "he takes no notice of the giants he meets."));
    }

    private static void meet(MinecraftServer server, String arg, List<String> out) {
        // arg: "otherUUID [selfUUID]": that Hollowbell (else the one nearest the other giant) fights it now
        String[] ids = arg == null ? new String[0] : arg.trim().split("\\s+");
        UUID other, mine = null;
        try {
            other = UUID.fromString(ids[0]);
            if (ids.length > 1) mine = UUID.fromString(ids[1]);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            out.add(WHO + "meet needs the other giant's id.");
            return;
        }
        for (ServerLevel l : server.getAllLevels()) {
            Entity o = l.getEntity(other);
            if (o == null) continue;
            LivingEntity og = Giants.ownerOf(o);
            if (og == null) continue;
            HollowbellEntity best = null;
            double bd = Double.MAX_VALUE;
            for (HollowbellEntity h :
                    l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying() && e != og)) {
                if (mine != null && !mine.equals(h.getUUID())) continue;
                double d = h.distanceToSqr(og);
                if (d < bd) {
                    bd = d;
                    best = h;
                }
            }
            if (best != null && best.startMeeting(og)) {
                out.add(WHO + "he goes for it.");
                return;
            }
        }
        return;
    }

    private static void away(String a, List<String> out) {
        var V = BellConfig.V;
        Boolean on = onOff(a);
        if (on != null) {
            V.offscreenTravel = on;
            BellConfig.save();
        }
        out.add(WHO
                + (V.offscreenTravel ? "he steps out of the world when nobody is near." : "he stays in the world."));
    }

    private static void volume(String a, List<String> out) {
        var V = BellConfig.V;
        if (!a.isEmpty()) {
            try {
                V.soundVolume = Mth.clamp(Float.parseFloat(a), 0f, 2f);
                BellConfig.save();
            } catch (NumberFormatException e) {
                out.add(WHO + "the volume has to be a number from 0 to 2.");
                return;
            }
        }
        out.add(WHO + "volume " + V.soundVolume + ".");
    }

    private static void shake(String a, List<String> out) {
        var V = BellConfig.V;
        Boolean on = onOff(a);
        if (on != null) {
            V.screenShake = on;
            BellConfig.save();
        }
        out.add(WHO + "screen shake " + (V.screenShake ? "on." : "off."));
    }

    private static void bossbar(String a, List<String> out) {
        var V = BellConfig.V;
        if (!a.isEmpty()) {
            try {
                V.bossBarRange = Mth.clamp(Integer.parseInt(a), 0, 100000);
                BellConfig.save();
            } catch (NumberFormatException e) {
                out.add(WHO + "the boss bar range has to be a number of blocks.");
                return;
            }
        }
        out.add(WHO + "boss bars show "
                + (V.bossBarRange > 0 ? V.bossBarRange + " blocks off." : "as far as his size says."));
    }

    /** /giants paint stop: his paint jobs, if any are running, are dropped */
    private static void paintStop(List<String> out) {
        if (!Painter.busy()) return;
        Painter.forget();
        out.add(WHO + "stopped painting.");
    }

    private static void heavyDelay(String a, List<String> out) {
        var V = BellConfig.V;
        if (!a.isEmpty()) {
            try {
                V.heavyDelaySeconds = Mth.clamp(Integer.parseInt(a), 0, 600);
                BellConfig.save();
            } catch (NumberFormatException e) {
                out.add(WHO + "the wait has to be a number of seconds from 0 to 600.");
                return;
            }
        }
        out.add(WHO + "heavy moves wait " + V.heavyDelaySeconds + " seconds into a fight.");
    }

    private static void griefing(String a, List<String> out) {
        var V = BellConfig.V;
        Boolean on = onOff(a);
        if (on != null) {
            V.griefing = on;
            BellConfig.save();
        }
        out.add(WHO + (V.griefing ? "he breaks blocks." : "he doesn't break blocks."));
    }

    private static void where(MinecraftServer server, String action, List<String> out) {
        var V = BellConfig.V;
        for (ServerLevel l : server.getAllLevels())
            for (HollowbellEntity h : l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying()))
                out.add(WHO + "at " + h.getBlockX() + " " + h.getBlockY() + " " + h.getBlockZ() + " ("
                        + l.dimension().location() + "), health " + (int) h.healthNow() + "/"
                        + (int) h.healthMax()
                        + (action.equals("list") ? ", size " + String.format("%.2f", h.bellScale()) : "")
                        + ".");
        long now = server.overworld().getGameTime();
        for (Away.Rec r : Away.get(server).all()) {
            Vec3 s = r.spot(now);
            out.add(WHO + "out of the world near " + (int) s.x + " " + (int) s.z + " (" + r.dim + ").");
        }
        // put away with his chunk where nobody was near (not loaded, not out of the world)
        for (var e : Away.get(server).parked().entrySet()) {
            var key = ResourceLocation.tryParse(e.getValue().dim());
            ServerLevel pl = key == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
            if (pl != null && pl.getEntity(e.getKey()) != null) continue;
            out.add(WHO + "lying still near "
                    + (int) Math.floor(e.getValue().x()) + " "
                    + (int) Math.floor(e.getValue().z()) + " ("
                    + e.getValue().dim() + "), not loaded.");
        }
        if (out.isEmpty()) {
            WorldOne w = WorldOne.get(server);
            int days = w.daysLeft(server.overworld());
            if (w.aliveNow() && w.where() != null)
                out.add(WHO + "lying still near " + w.where().getX() + " "
                        + w.where().getZ() + " (minecraft:overworld), not loaded.");
            else if (V.oneInTheWorld && w.where() != null && days > 0)
                out.add(WHO + "none standing. The next comes down near "
                        + w.where().getX() + " " + w.where().getZ() + " in about " + days + " days.");
            else out.add(WHO + "none standing.");
        }
    }

    private static void kill(MinecraftServer server, List<String> out) {
        int n = FarOrders.killAll(server);
        out.add(WHO + (n == 0 ? "none to kill." : "killed " + n + "."));
    }

    private static void remove(MinecraftServer server, List<String> out) {
        int n = Away.get(server).count() + FarOrders.removeLying(server);
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

    private static void goTo(MinecraftServer server, String a, List<String> out) {
        String[] xz = a.split("\\s+");
        double x, z;
        try {
            x = Double.parseDouble(xz[0]);
            z = Double.parseDouble(xz[1]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            out.add(WHO + "goto needs an x and a z.");
            return;
        }
        int n = 0;
        for (ServerLevel l : server.getAllLevels())
            for (var t : FarOrders.all(l)) if (FarOrders.order(l, t, new Vec3(x, 0, z), null, 0f) != null) n++;
        out.add(WHO + (n == 0 ? "none to send." : n + " on the way to " + Mth.floor(x) + ", " + Mth.floor(z) + "."));
    }

    private static void config(String arg, List<String> out) {
        // "key" or "key value": only answers when this mod has that setting
        String[] kv = arg == null ? new String[0] : arg.trim().split("\\s+", 2);
        if (kv.length == 0 || kv[0].isEmpty()) {
            out.add(WHO + String.join(", ", OpsCommand.keys()));
            return;
        }
        if (!OpsCommand.has(kv[0])) return;
        String bad = kv.length > 1 ? OpsCommand.set(kv[0], kv[1]) : null;
        out.add(WHO + kv[0] + (bad != null ? " can't be set to " + kv[1] + "." : " = " + OpsCommand.get(kv[0])));
    }

    private static void paint(MinecraftServer server, String a, String arg, List<String> out) {
        // "radius mode playerUUID depth=N" (an older /giants sends no depth: 5 then; a bare number after the
        // player is taken as the depth too)
        String[] w = arg == null ? new String[0] : arg.trim().split("\\s+");
        ServerPlayer p = null;
        int radius = 64, depth = Painter.DEPTH;
        boolean full = true, sawPlayer = false;
        for (String x : w) {
            if (x.equalsIgnoreCase("biome")) full = false;
            else if (x.equalsIgnoreCase("full")) full = true;
            else if (x.startsWith("depth=")) {
                try {
                    depth = Integer.parseInt(x.substring(6));
                } catch (NumberFormatException ignored) {
                }
            } else if (x.matches("\\d+")) {
                if (sawPlayer) depth = Integer.parseInt(x);
                else radius = Integer.parseInt(x);
            } else
                try {
                    p = server.getPlayerList().getPlayer(UUID.fromString(x));
                    sawPlayer = true;
                } catch (IllegalArgumentException notAPlayer) {
                    // a word that is neither a setting nor a player: left out
                }
        }
        if (p == null) {
            out.add(WHO + "nobody to paint round.");
            return;
        }
        depth = Mth.clamp(depth, Painter.MIN_DEPTH, Painter.MAX_DEPTH);
        int n = Painter.start(p, radius, full, depth);
        out.add(WHO + "painting the Bell Hollows " + Mth.clamp(radius, 16, 512) + " blocks round "
                + p.getGameProfile().getName()
                + (full ? " (land and biome, " + depth + " blocks deep)" : " (biome only)") + ", " + n
                + " chunks. It can't be undone.");
    }

    private static void tp(MinecraftServer server, String arg, List<String> out) {
        ServerPlayer p = null;
        try {
            p = server.getPlayerList().getPlayer(UUID.fromString(arg.trim()));
        } catch (IllegalArgumentException notAPlayer) {
            // answered below: nobody to take there
        }
        if (p == null) {
            out.add(WHO + "nobody to take there.");
            return;
        }
        out.add(WHO + TakeMe.tp(p, 0).getString());
    }

    private static void status(MinecraftServer server, List<String> out) {
        var V = BellConfig.V;
        int standing = Away.get(server).count();
        for (ServerLevel l : server.getAllLevels())
            standing += l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying())
                    .size();
        out.add(WHO + standing + " standing, " + (V.maxInWorld <= 0 ? "no limit" : "at most " + V.maxInWorld)
                + ", " + (V.oneInTheWorld ? "rises on his own" : "only when summoned")
                + ", " + (V.fightGiants ? "fights the others" : "leaves the others alone")
                + ", volume " + V.soundVolume + ".");
    }

    private static Boolean onOff(String a) {
        return switch (a) {
            case "on", "true", "yes" -> Boolean.TRUE;
            case "off", "false", "no" -> Boolean.FALSE;
            default -> null;
        };
    }
}
