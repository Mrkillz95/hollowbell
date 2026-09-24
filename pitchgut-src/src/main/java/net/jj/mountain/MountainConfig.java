package net.jj.mountain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** config/mountain_breathes.json - written with defaults the first time the game starts. */
public final class MountainConfig {
    public static final class Values {
        /** Size the spawn eggs make him. 1.0 = full size, the same as the KillzAI build (255 long, 185 tall, 161 wide). */
        public float spawnEggScale = 1.0f;
        /** His health at full size. Smaller mountains get proportionally less. */
        public float health = 6000f;
        /** Multiplies every hit he lands. */
        public float damageMultiplier = 1.0f;
        /** How hard he hits, as a plain number (10 = normal). /mountain damage */
        public float attackDamage = 10f;
        /** His hits on anything that isn't a player are multiplied by this. /mountain damage mobs */
        public float mobDamage = 2.0f;
        /** Far away he is drawn with bigger, merged blocks so he runs faster. /mountain detail */
        public boolean simpleFarAway = true;
        /** How far away (in blocks, at full size) the simpler drawing starts. */
        public int simpleFarAwayAt = 150;
        /** Lets his goo eat trenches into the ground and his feet flatten plants (also needs the mobGriefing gamerule). */
        public boolean griefing = true;
        /** Black goo pours from his mouth and trails behind him. Off = no goo blocks at all (the goo is still drawn). */
        public boolean gooTrail = true;
        /** Swallowed players drop inside him to fight his heart. Off = he chews and spits them out instead. */
        public boolean fightInside = true;
        /** Keeps the chunks under him loaded while players are near him, so he never freezes half-loaded. */
        public boolean chunkLoading = true;
        /** Keeps one of him alive in the world at all times, wherever you are. */
        public boolean oneInTheWorld = true;
        /** How big the one the world keeps is. */
        public float worldScale = 1.0f;
        /** How many Minecraft days after one is killed before the next gets up. */
        public int worldRespawnDays = 10;
        /** How far, in blocks, the next one gets up from where the last one fell. */
        public int respawnBlocks = 7000;
        /** How many of him the world will hold at once. Summon another past this and the oldest one goes.
         *  0 = as many as you like. /mountain limit */
        public int maxMountains = 1;
        /** Legs and arms that have been broken and mended once never come back the same. /mountain scars */
        public boolean scars = true;
        /** How far, in blocks, his own heart holds him off while it is beating. 0 = the heart holds him off nowhere. */
        public int wardBlocks = 700;
        /** How long the heart holds him off for, in seconds, once it is woken. /mountain ward minutes */
        public int wardSeconds = 1200;
        /** How long the heart is dark afterwards before it can be woken again, in seconds. /mountain ward rest */
        public int wardRestSeconds = 1200;
        /** What share of his legs have to be broken before he comes down and does not get up again. */
        public float crippleAt = 0.70f;
        /** Nearly dead, lumps of his flesh fall off him and come after you. */
        public boolean shedsLumps = true;
        /** He can be sent under the ground with the book. He never goes under of his own accord. */
        public boolean canBurrow = true;
        /** Take a hit while you are in him and you are thrown out for this many ticks (72000 = an hour). */
        public int shutOutTicks = 72000;
        /** He gets more health the more players are fighting him. */
        public boolean scaleToPlayers = true;
        /** He beds down in daylight and walks at night. */
        public boolean sleepsByDay = true;
        /** How long his body lies there after he falls, in seconds, before it goes into the ground. */
        public int carcassSeconds = 30;
        /** How loud he is, 0 = silent, 1 = normal. /mountain volume */
        public float soundVolume = 1.0f;
        /** The ground shakes when his feet come down. /mountain shake */
        public boolean screenShake = true;
        /** Shows his health bar and his eye bar at the top of the screen. /mountain bossbar */
        public boolean bossBar = true;
        /** How far away (in blocks) he is still drawn. */
        public int renderDistance = 720;
        /** Minecraft days between one order to go under and the next. /mountain digcooldown */
        public int burrowGapDays = 2;
        /** With nobody near him he is written down in full, taken out of the world and run as a sum instead. */
        public boolean offscreenTravel = true;
        /** How far the nearest player has to be, in blocks, before he is taken out of the world.
         *  0 = the moment the game would stop running him anyway (the server's simulation distance). */
        public int awayBlocks = 0;
        /** The book tires him: every order costs him wind, and he has to get it back. /mountain wind */
        public boolean bookCosts = true;
        /** How long he takes to get all his wind back from nothing, in seconds. */
        public int windSeconds = 90;
        /** How many times somebody his book protects can hit him before he stops listening to it. 0 = never. */
        public int freeHits = 5;
        /** How hard he resents being pushed: 1 = normal, 0 = he never sours on you, 2 = twice as fast. */
        public float grudgeRate = 1.0f;
        /** Lets the book ask a dying Mountain for the last thing he does. /mountain unmake */
        public boolean unmake = true;
        /** How wide that hole is, in blocks out from him, at full size. */
        public int unmakeRadius = 1000;
        /** How far the wave that follows it carries, in blocks. */
        public int unmakeWave = 1000;
    }

    public static Values V = new Values();

    /** writes the current settings back to the file (the /mountain goo and /mountain breakblocks commands) */
    public static void save() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("mountain_breathes.json");
        try { Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(V)); }
        catch (Exception e) { MountainMod.LOG.warn("Could not write {}: {}", p, e.toString()); }
    }

    public static void load() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("mountain_breathes.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(p)) {
                Values v = gson.fromJson(Files.readString(p), Values.class);
                if (v != null) V = v;
            }
            Files.writeString(p, gson.toJson(V));
        } catch (Exception e) {
            MountainMod.LOG.warn("Could not read {}, using defaults: {}", p, e.toString());
        }
    }
}
