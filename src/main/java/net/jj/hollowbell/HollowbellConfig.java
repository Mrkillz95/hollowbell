package net.jj.hollowbell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** config/hollowbell.json - written with defaults the first time the game starts. */
public final class HollowbellConfig {
    public static final class Values {
        /** Which version of these settings this file was written by (older files are brought up to date). */
        public int configVersion = 0;
        /** How big the spawn eggs make him. 1.0 = full size, the same as the KillzAI build (about 200 wide and 200 tall). */
        public float spawnEggScale = 1.0f;
        /** How big the small spawn egg makes him. */
        public float smallEggScale = 0.2f;
        /** His health at full size. Smaller ones get less. */
        public float health = 6000f;
        /** Multiplies every hit he lands. */
        public float damageMultiplier = 1.0f;
        /** His hits on anything that isn't a player are multiplied by this. */
        public float mobDamage = 2.5f;
        /** Lets him pull trees up, flatten plants with his strands and crack the ground with his arm (also needs the mobGriefing gamerule). */
        public boolean griefing = true;
        /** He pulls cows, villagers and trees up into his dome. */
        public boolean harvest = true;
        /** Things he pulls all the way up end up inside his dome. Off = he squeezes them and lets go instead. */
        public boolean insideDome = true;
        /** How long a popped pod takes to start growing back, in seconds (it then takes about 20 more to grow). */
        public int podRegrowSeconds = 90;
        /** How many of his pods (as a share, 0.34 = a third) have to be popped before he loses his lift and sinks. */
        public float podsToSink = 0.34f;
        /** Once he sinks, he stays down at least this long, in seconds. */
        public int sunkSeconds = 30;
        /** How many egg clumps he sheds at a time, and whether he sheds at all (0 = never). */
        public int shedCount = 4;
        /** Keeps the chunk at his middle moving and the ground under him loaded while a player can see him, so he never freezes in the air. */
        public boolean chunkLoading = true;
        /** How many of him the world holds at once. Summon another past this and the oldest one goes. 0 = no limit. */
        public int maxHollowbells = 0;
        /** He gets more health the more players are fighting him. */
        public boolean scaleToPlayers = true;
        /** How loud he is, 0 = silent, 1 = normal. */
        public float soundVolume = 1.0f;
        /** His low hum and the wet sound of him drifting, going on all the time near him. */
        public boolean ambientSounds = true;
        /** The screen shakes when his bell or arm slams down. */
        public boolean screenShake = true;
        /** Shows his health and pods bars. */
        public boolean bossBar = true;
        /** How far away (in blocks) he is still drawn. */
        public int renderDistance = 720;
        /** Far away he is drawn with bigger, merged blocks so he runs faster ("/hollowbell detail on"). Off = always drawn in full ("/hollowbell detail off"). */
        public boolean simpleFarAway = true;
        /** How far away (in blocks, at full size) the simpler drawing starts. */
        public int simpleFarAwayAt = 160;
        /** The book tires him: every order costs him wind, and he has to get it back. */
        public boolean bookCosts = true;
        /** How long he takes to get all his wind back from nothing, in seconds. */
        public int windSeconds = 90;
        /** How many times somebody his book protects can hit him before he stops listening to it. 0 = never. */
        public int freeHits = 5;
        /** How hard he resents being pushed: 1 = normal, 0 = never, 2 = twice as fast. */
        public float grudgeRate = 1.0f;
        /** How far the book reaches him, in blocks. */
        public int bookRange = 600;
    }

    public static Values V = new Values();

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("hollowbell.json"); }

    public static void save() {
        V.configVersion = 2;
        try { Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(V)); }
        catch (Exception e) { HollowbellMod.LOG.warn("Could not write {}: {}", file(), e.toString()); }
    }

    public static void load() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(file())) {
                Values v = gson.fromJson(Files.readString(file()), Values.class);
                if (v != null) {
                    // 1.1: he hits creatures much harder (1.0 barely killed them)
                    if (v.configVersion < 2) v.mobDamage = Math.max(v.mobDamage, 2.5f);
                    V = v;
                }
            }
            V.configVersion = 2;
            Files.writeString(file(), gson.toJson(V));
        } catch (Exception e) {
            HollowbellMod.LOG.warn("Could not read {}, using defaults: {}", file(), e.toString());
        }
    }
}
