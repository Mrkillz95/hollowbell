package net.jj.giants.systems;

import java.util.List;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * (Part 8, ADMIN_BRIDGE.md §24.3) Difficulty scaling, the full moon's frenzy and the seasons: factors on the
 * all-giants multipliers, put on what the giants are sent (never on what the world keeps). Worked out every ten
 * seconds; the giants are sent their values again only when a factor changes.
 */
public final class Scaling {
    private Scaling() {}

    /** the factors on health, damage, speed and healing now, and why (for the book) */
    public record Factors(double health, double damage, double speed, double healing, double scale, int players,
            long days, double armour, boolean fullMoon, int season) {
        public boolean none() {
            return health == 1 && damage == 1 && speed == 1 && healing == 1;
        }
    }

    public static final List<String> SEASONS = List.of("spring", "summer", "autumn", "winter");
    private static final Factors NONE = new Factors(1, 1, 1, 1, 1, 0, 0, 0, false, -1);

    /** the factors the giants were last sent */
    private static Factors sent = NONE;

    static void forget() {
        sent = NONE;
    }

    static double r(double v) {
        return Math.round(v * 20) / 20.0;
    }

    public static Factors factors(MinecraftServer server) {
        boolean scale = Systems.num(server, "scalePlayers") > 0 || Systems.num(server, "scaleDays") > 0
                || Systems.num(server, "scaleArmour") > 0;
        double moon = Systems.num(server, "moonFrenzy");
        boolean seasons = Systems.on(server, "seasons");
        if (!scale && moon <= 1 && !seasons) return NONE;
        ServerLevel o = server.overworld();
        int players = server.getPlayerList().getPlayerCount();
        long days = o.getGameTime() / 24000L;
        double armour = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || p.isCreative()) continue;
            double a = p.getArmorValue() + p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            armour = Math.max(armour, Math.min(1, a / 32.0));
        }
        double d = 1;
        if (scale) {
            d = 1 + Systems.num(server, "scalePlayers") * Math.max(0, players - 1)
                    + Systems.num(server, "scaleDays") * (days / 10.0)
                    + Systems.num(server, "scaleArmour") * armour;
            d = r(Math.min(Math.max(1, Systems.num(server, "scaleMost")), d));
        }
        long time = o.getDayTime() % 24000L;
        boolean night = time >= 13000 && time < 23000;
        boolean full = moon > 1 && o.dimensionType().hasSkyLight() && o.getMoonPhase() == 0 && night;
        int season = seasons ? (int) ((o.getDayTime() / 24000L / Math.max(1, (long) Systems.num(server, "seasonDays")))
                % 4) : -1;
        double health = d, damage = d, speed = 1, healing = 1;
        if (full) {
            damage *= moon;
            speed *= 1 + (moon - 1) / 2;
        }
        switch (season) {
            case 0 -> healing *= 1.5;
            case 1 -> {
                damage *= 1.15;
                speed *= 1.1;
            }
            case 3 -> {
                speed *= 0.85;
                health *= 1.2;
            }
            default -> {}
        }
        return new Factors(r(health), r(damage), r(speed), r(healing), d, players, days, armour, full, season);
    }

    /** every ten seconds: the giants get their values again when a factor changed; the season told when it turns */
    public static void tick(MinecraftServer server) {
        Factors f = factors(server);
        boolean moonUp = f.fullMoon() && !sent.fullMoon();
        if (f.health() != sent.health() || f.damage() != sent.damage() || f.speed() != sent.speed()
                || f.healing() != sent.healing() || f.fullMoon() != sent.fullMoon()) {
            sent = f;
            AdminServer.push(server, AdminWorld.get(server));
            SystemsAdmin.changed();
        }
        if (moonUp)
            net.jj.giants.tools.Tools.tellEveryone(server, Component.literal(
                    "The full moon is up: the giants are in a frenzy tonight.").withStyle(ChatFormatting.GOLD));
        SystemsData d = SystemsData.get(server);
        if (f.season() >= 0 && f.season() != d.s.season) {
            d.s.season = f.season();
            d.setDirty();
            tellSeason(server, f.season());
        } else if (f.season() < 0 && d.s.season >= 0) {
            d.s.season = -1;
            d.setDirty();
        }
    }

    private static void tellSeason(MinecraftServer server, int season) {
        String line = switch (season) {
            case 0 -> "Spring has come: the giants heal faster.";
            case 1 -> "Summer has come: the giants hit harder and move faster.";
            case 2 -> "Autumn has come: the giants are as they always are.";
            default -> "Winter has come: the giants move slower, with a tougher hide.";
        };
        net.jj.giants.tools.Tools.tellEveryone(server, Component.literal(line).withStyle(ChatFormatting.AQUA));
    }

    /** (the tests) the factors the giants were last sent, as if never */
    public static void resendForTests() {
        sent = NONE;
    }
}
