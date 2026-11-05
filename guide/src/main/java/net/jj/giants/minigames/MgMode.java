package net.jj.giants.minigames;

import net.jj.giants.admin.AdminServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.7) One minigame mode: its map, what happens when the players are in, each tick, when
 * a player is knocked out, and who won. The framework (MgGame) does the rest: the lobby, taking players in and home,
 * the countdown, the rounds, the boss bar, the results.
 */
public abstract class MgMode {
    public abstract String id();

    public abstract String name();

    /** one line: what it is (the book, the Guide, the chat when it opens) */
    public abstract String about();

    /** the goal, shown when the round starts */
    public abstract String goal();

    /** the fewest players a round needs (the world's own Fewest players may ask for more) */
    public int fewest() {
        return 1;
    }

    /** players may hurt each other (when the world's Players hitting players lets them) */
    public boolean pvp() {
        return false;
    }

    /** the time of day in its world (6000 noon, 13000 dusk, 18000 midnight) */
    public long dayTime() {
        return 6000;
    }

    /** why a round can't be played now (null: it can) */
    public @Nullable String cannot(MinecraftServer server) {
        return AdminServer.giants().isEmpty() ? "No giants are installed." : null;
    }

    /** the round's map */
    public abstract MgMap map(MgGame g);

    /** everyone is in (the countdown runs now): kits, giants */
    public void begin(MgGame g) {}

    /** the countdown is over */
    public void go(MgGame g) {}

    /** every tick while the round is played */
    public void tick(MgGame g) {}

    /** a player took a blow that would have killed them: they live, and the mode says what happens */
    public void knockedOut(MgGame g, MgGame.P p, ServerPlayer sp, DamageSource src) {
        g.out(p, sp, "You're out!");
    }

    /** a player left the round (logged out, /giants leave) */
    public void left(MgGame g, MgGame.P p) {}

    /** one of the round's giants fell (his last blow's player is giant.lastBy, if any) */
    public void giantFell(MgGame g, MgGiants.G giant) {}

    /** one of the round's giants left the round some other way (taken away) */
    public void giantGone(MgGame g, MgGiants.G giant) {}

    /** may this blow land on one of the round's giants (false: it doesn't) */
    public boolean giantMayBeHurt(MgGame g, MgGiants.G giant, net.minecraft.world.damagesource.DamageSource src,
            @org.jetbrains.annotations.Nullable MgGame.P by) {
        return true;
    }

    /** may this blow land on a player in the round (false: it doesn't) */
    public boolean playerMayBeHurt(MgGame g, MgGame.P p, net.minecraft.world.damagesource.DamageSource src) {
        return true;
    }

    /** a creature of the round died (not a player, not a giant) */
    public void creatureDied(MgGame g, net.minecraft.world.entity.LivingEntity e,
            net.minecraft.world.damagesource.DamageSource src) {}

    /** whether the round is over now (null: it goes on) */
    public @Nullable MgGame.End check(MgGame g) {
        return null;
    }

    /** the round's time is up: who won */
    public abstract MgGame.End timeUp(MgGame g);

    /** the boss bar's words while the round is played */
    public String bar(MgGame g) {
        return name() + " · " + g.clock();
    }

    public float barProgress(MgGame g) {
        return g.timeLeftShare();
    }

    /** a line on one player's action bar, once a second (null: none) */
    public @Nullable Component status(MgGame g, MgGame.P p) {
        return null;
    }

    /** the challenge (advancement) a game's winners get, or null */
    public @Nullable String challenge() {
        return "mg_" + id();
    }

    /** teams instead of each for themselves (the results name the team) */
    public boolean teams() {
        return false;
    }
}
