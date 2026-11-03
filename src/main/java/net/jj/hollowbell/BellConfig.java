package net.jj.hollowbell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

/** config/hollowbell.json - written with defaults the first time the game starts. */
public final class BellConfig {
    public static final class Values {
        /** Which version of these settings this file was written by (older files are brought up to date). */
        public int configVersion = 0;
        /**
         * How big the spawn eggs make him. 1.0 = full size, the same as the KillzAI build (about 200 wide and 200
         * tall).
         */
        public float spawnEggScale = 1.0f;
        /** How big the small spawn egg makes him. */
        public float smallEggScale = 0.2f;
        /** His health at full size. Smaller ones get less. */
        public float health = 6000f;
        /** his health times this (/giants health): set for all the giants at once, 0.1 to 10 */
        public float healthMultiplier = 1.0f;
        /**
         * He heals on his own (on or off): while he sleeps, and once he has been out of any fight for healPauseMinutes
         * (nothing has hurt him, he has hurt nothing and he is hunting nothing).
         */
        public boolean passiveHeal = true;
        /** How much of his most health he gets back a minute over his own ground, the Bell Hollows, in percent. */
        public float healHomePerMinute = 2.0f;
        /** How much of his most health he gets back a minute anywhere else, in percent. */
        public float healAwayPerMinute = 1.0f;
        /** How many minutes out of any fight before he starts to heal (0 to 60). */
        public float healPauseMinutes = 5.0f;
        /** Multiplies every hit he lands. */
        public float damageMultiplier = 1.0f;
        /** His hits on anything that isn't a player are multiplied by this. */
        public float mobDamage = 2.5f;
        /** How much of a blow from another giant boss he takes. */
        public float giantArmor = 1.38f;
        /** How much of a blast (TNT, a creeper, anything that isn't another giant) he takes. */
        public float explosionArmor = 0.35f;
        /** He picks fights with the other giant bosses ("/hollowbell giants on"). Off = he leaves them alone. */
        public boolean fightGiants = true;
        /**
         * Over half health, no heavy move until this many seconds into a fight (0 = at once, up to 600). Under half,
         * only the usual wait after the last one ("/hollowbell heavydelay").
         */
        public int heavyDelaySeconds = 60;
        /** When he wanders near another giant he fights it or keeps away from it. */
        public boolean meetings = true;
        /** How far off he notices another giant, in blocks at full size (times his size, kept between 48 and 320). */
        public int meetRange = 160;
        /** A giant fight ends when one drops under this share of its health: it backs down. 0 = to the death. */
        public float yieldAt = 0.25f;
        /** Backs down when he's losing a wild giants' fight, one he started on his own (/giants flee). A fight a
         *  player started (the book, /giants meet, riding him) he never backs down from. */
        public boolean fleeInWildFights = true;
        /** Minutes before the same two giants can meet again. */
        public int meetCooldown = 10;
        /** (This computer.) His fight music plays when a fight with him is on near you. */
        public boolean fightMusic = true;
        /**
         * Lets him pull trees up, flatten plants with his strands and crack the ground with his arm (also needs the
         * mobGriefing gamerule).
         */
        public boolean griefing = true;
        /** He pulls cows, villagers and trees up into his dome. */
        public boolean harvest = true;
        /** Things he pulls all the way up end up inside his dome. Off = he squeezes them and lets go instead. */
        public boolean insideDome = true;
        /** How long a popped pod takes to start growing back, in seconds (it then takes about 20 more to grow). */
        public int podRegrowSeconds = 150;
        /** How much of his health a popped pod takes with it (0.01 = 1%). */
        public float podPopShare = 0.010f;
        /** How many of his pods (as a share, 0.34 = a third) have to be popped before he loses his lift and sinks. */
        public float podsToSink = 0.34f;
        /** Once he sinks, he stays down at least this long, in seconds. */
        public int sunkSeconds = 30;
        /** How many egg clumps he sheds at a time, and whether he sheds at all (0 = never). */
        public int shedCount = 4;
        /**
         * Keeps the chunk at his middle moving and the ground under him loaded while a player can see him, so he never
         * freezes in the air.
         */
        public boolean chunkLoading = true;
        /** How many of him the world holds at once. Summon another past this and the oldest one goes. 0 = no limit. */
        public int maxInWorld = 1;
        /** maxInWorld under its old name: an old file's number is moved over, then this is dropped. */
        public Integer maxHollowbells = null;
        /** The world keeps one of him out there, rising on his own ("/hollowbell natural on|off"). */
        public boolean oneInTheWorld = true;
        /** How big the world's own one is. */
        public float worldScale = 1.0f;
        /** Days after he dies before the next one rises. */
        public int worldRespawnDays = 3;
        /** Days instead when the one killed was a player's (somebody held his book). */
        public int ownedRespawnDays = 15;
        /** A player's Hollowbell killed: their book burns, and the book passes to the next player carrying a copy. */
        public boolean burnOwnersBook = true;
        /** The most blocks from where the last one died that the next one rises. */
        public int respawnBlocks = 10000;
        /**
         * How far his own ground, the Bell Hollows, reaches from its middle, in blocks (200 to 2000). A bigger number
         * only grows a ground, it never shrinks one.
         */
        public int homeRadius = 900;
        /**
         * How close to the world's spawn his ground may begin (its nearest edge), in blocks, when his first ground and
         * any extra copies are chosen. At the shipped 2100 to 14100 he chooses exactly as he always has.
         */
        public int groundNearest = 2100;
        /** How far from the world's spawn his ground may begin at most (its nearest edge), in blocks. */
        public int groundFarthest = 14100;
        /**
         * Blocks kept between his ground and every other giant's, edge to edge, whenever one of his grounds is chosen.
         * 0: no rule while the world is new (a ground chosen after he falls still keeps 200 off).
         */
        public int groundGap = 0;
        /** How many grounds of his own the world has (1 = one, as always). */
        public int groundCopies = 1;
        /**
         * How far off you can see him coming, in blocks, even past where the game would stop drawing him (0 = off, up
         * to 4096).
         */
        public int farSightBlocks = 1024;
        /** How far his crown, woken against him, holds him off, in blocks ("/hollowbell ward blocks"). */
        public int wardBlocks = 700;
        /** How long the ward lasts once it is started, in seconds ("/hollowbell ward minutes"). */
        public int wardSeconds = 1200;
        /** How long the crown sits dark after a ward, in seconds ("/hollowbell ward rest"). */
        public int wardRestSeconds = 1200;
        /** He gets more health the more players are fighting him. */
        public boolean scaleToPlayers = true;
        /** How loud he is, 0 = silent, 1 = normal. */
        public float soundVolume = 1.0f;
        /** His low hum and the wet sound of him drifting, going on all the time near him. */
        public boolean ambientSounds = true;
        /** The screen shakes when his bell or arm slams down. */
        public boolean screenShake = true;
        /**
         * How much flies about in his big moves (dust and chunks of ground, splashes and waves, flashes, clouds): 0 =
         * none, 1 = normal, 2 = more.
         */
        public float bigEffects = 1.0f;
        /** Shows his health and pods bars. */
        public boolean bossBar = true;
        /** How far away (in blocks) he is still drawn. */
        public int renderDistance = 720;
        /**
         * Far away he is drawn with bigger, merged blocks so he runs faster ("/hollowbell detail on"). Off = always
         * drawn in full ("/hollowbell detail off").
         */
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
        /** How far away (in blocks) his boss bars still show. 0 = worked out from his size (420 at full size). */
        public int bossBarRange = 0;
        /**
         * With nobody near him he steps out of the world and keeps going as a sum, and comes back when somebody gets
         * near.
         */
        public boolean offscreenTravel = true;
        /**
         * How far the nearest player has to be before he steps out. 0 = worked out from his size and the game's own
         * settings.
         */
        public int awayBlocks = 0;
        // Part 4 (ADMIN_BRIDGE.md 19): behaviour settings every giant shares. As shipped they change nothing.
        /** the world's own one (and his companions) come at worldScale times a random number from here... */
        public float spawnSizeMin = 1.0f;
        /** ...to here (1 and 1: always worldScale) */
        public float spawnSizeMax = 1.0f;
        /** how many of him the world keeps: the world's own one and companions beside him (1 = only him) */
        public int worldCount = 1;
        /** off: once the world's own one falls, no other comes in this world */
        public boolean respawn = true;
        /** peaceful (never starts a fight with a player), normal (as his mood says), hunter (hunts every player) */
        public String temper = "normal";
        /** home (wanders as always), wander (travels the whole world), stay (stays where he is) */
        public String roam = "home";
        /** he travels to the nearest other giant and fights it */
        public boolean seekGiants = false;
        /** (Part 2C) meeting another kind: decide (the table, as always), fight, run_away, ignore */
        public String meetGiants = "decide";
        /** he heads for villages and players and attacks */
        public boolean raidVillages = false;
        /** usual, day (sleeps all day, hunts all night), never */
        public String sleepSchedule = "usual";
        /** at night his hits times this */
        public float nightStrength = 1.0f;
        /** what he drops when he falls, times this */
        public float lootMultiplier = 1.0f;
    }

    /**
     * The settings in use: the config file's (the server's defaults) with this world's admin settings on top
     * (GiantsAdmin, set from the Giants Guide). With no admin settings it is just the file.
     */
    public static Values V = new Values();
    /** what the config file says: the server's defaults. Only this is ever written to the file */
    private static Values FILE = new Values();
    /** V as it was last put together: a field of V that differs from it was changed since (by a command) */
    private static Values APPLIED = new Values();

    private static final Gson COPIER = new Gson();

    private static Values copy(Values v) {
        return COPIER.fromJson(COPIER.toJson(v), Values.class);
    }

    private static java.util.List<java.lang.reflect.Field> fields() {
        java.util.List<java.lang.reflect.Field> out = new java.util.ArrayList<>();
        for (var f : Values.class.getFields())
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) out.add(f);
        return out;
    }

    /** what the config file says (the server's defaults): read it, don't change it (change V and save) */
    public static Values fileValues() {
        return FILE;
    }

    /** the config file's value of one field (the server's default) */
    public static Object fileValue(java.lang.reflect.Field f) {
        try {
            return f.get(FILE);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Whatever was changed in V since it was last put together (a command, the book, /hollowbell config) is a change
     * to the server's defaults: it goes into FILE. A field the world keeps its own value for is put back to that
     * value by the next compose, so the change only reaches the file.
     */
    private static synchronized void takeChanges() {
        for (var f : fields()) {
            try {
                Object now = f.get(V);
                if (!java.util.Objects.equals(now, f.get(APPLIED))) f.set(FILE, now);
            } catch (IllegalAccessException e) {
                // (public fields only)
            }
        }
    }

    /** V put together again: the file's values, then the world's admin settings on top. V stays the same object */
    public static synchronized void compose() {
        takeChanges();
        composeFresh();
    }

    private static synchronized void composeFresh() {
        Values next = copy(FILE);
        GiantsAdmin.applyTo(next);
        for (var f : fields()) {
            try {
                f.set(V, f.get(next));
            } catch (IllegalAccessException e) {
                // (public fields only)
            }
        }
        APPLIED = copy(V);
    }

    /** which version of the settings this build writes; save() and load() both stamp it */
    private static final int VERSION = 12;
    /**
     * the file on disk couldn't be read: it's kept as it is, and nothing is written over it until /hollowbell reload
     * reads it cleanly
     */
    private static boolean locked;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("hollowbell.json");
    }

    /**
     * Writes the server's defaults: V as it is now, except where the world keeps its own value (that stays out of the
     * file). Without admin settings this is exactly V, as it always was.
     */
    public static void save() {
        takeChanges();
        // an unreadable file is never written over (the settings still change for this session)
        if (locked) {
            HollowbellMod.LOG.warn(
                    "Not writing {}: it couldn't be read, so it is left as it is until it reads cleanly", file());
            composeFresh();
            return;
        }
        FILE.configVersion = Math.max(FILE.configVersion, VERSION);
        try {
            write(new GsonBuilder().setPrettyPrinting().create().toJson(FILE));
        } catch (IOException e) {
            HollowbellMod.LOG.warn("Could not write {}: {}", file(), e.toString());
        }
        composeFresh();
    }

    /**
     * Server defaults from a command with "allworlds" (ADMIN_BRIDGE.md, Command scope): each field's value goes
     * straight into the file's values, whatever this world keeps for itself (even the same number), then the file is
     * written (unless it couldn't be read) and V put together again.
     */
    public static synchronized void setDefaults(java.util.Map<java.lang.reflect.Field, Object> values) {
        takeChanges();
        for (var e : values.entrySet()) {
            try {
                e.getKey().set(FILE, e.getValue());
            } catch (IllegalAccessException | IllegalArgumentException ex) {
                HollowbellMod.LOG.warn("Not a value for {}: {}", e.getKey().getName(), e.getValue());
            }
        }
        migrate(FILE); // (the same limits as reading the file)
        save();
    }

    /** written beside it first, then put in its place, so a crash halfway never leaves half a file */
    private static void write(String json) throws IOException {
        Path tmp = file().resolveSibling("hollowbell.json.tmp");
        Files.writeString(tmp, json);
        try {
            Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** reads the file (at start, and on /hollowbell reload); true when it read cleanly (or there was none yet) */
    public static boolean load() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(file())) {
                Values v = gson.fromJson(Files.readString(file()), Values.class);
                if (v == null) throw new IllegalStateException("the file is empty");
                migrate(v);
                FILE = v;
            } else FILE = new Values();
            composeFresh();
            locked = false;
            save();
            return true;
        } catch (Exception e) {
            // a file that won't read is never written over: it's kept as it was, and a copy goes beside it as .bad
            HollowbellMod.LOG.warn(
                    "Could not read {}, using defaults (your file is kept, and copied to hollowbell.json.bad): {}",
                    file(),
                    e.toString());
            try {
                Files.copy(file(), file().resolveSibling("hollowbell.json.bad"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException copyFailed) {
                HollowbellMod.LOG.warn("Could not copy {} to hollowbell.json.bad: {}", file(), copyFailed.toString());
            }
            FILE = new Values();
            composeFresh();
            locked = true;
            return false;
        }
    }

    public static boolean locked() {
        return locked;
    }

    /** brings settings saved by an older version up to date; only values still at an old default are changed */
    public static void migrate(Values v) {
        // (2: he hits creatures much harder)
        if (v.configVersion < 2) v.mobDamage = Math.max(v.mobDamage, 2.5f);
        // (3: pods take longer to grow back, so they can't be farmed)
        if (v.configVersion < 3 && v.podRegrowSeconds == 90) v.podRegrowSeconds = 150;
        // (4: the bosses take a bit more from each other, so their fights don't drag on)
        if (v.configVersion < 4 && Math.abs(v.giantArmor - 0.55f) < 1e-4f) v.giantArmor = 0.7f;
        // (5: the world holds one of him by default; nobody already out there is removed for it, the limit only acts
        // when a new one is made, or on /hollowbell limit)
        if (v.configVersion < 5 && v.maxHollowbells != null && v.maxHollowbells == 0) v.maxHollowbells = 1;
        // (the old name)
        if (v.maxHollowbells != null) {
            v.maxInWorld = v.maxHollowbells;
            v.maxHollowbells = null;
        }
        // (6: his ground is much bigger, and he can be seen from much further off)
        if (v.configVersion < 6 && (v.homeRadius == 0 || v.homeRadius == 320)) v.homeRadius = 900;
        if (v.configVersion < 6 && v.farSightBlocks == 0) v.farSightBlocks = 1024;
        // (8: the next one rises sooner and nearer: 3 days, within 10000 blocks of where the last one died)
        if (v.configVersion < 8 && v.worldRespawnDays == 10) v.worldRespawnDays = 3;
        if (v.configVersion < 8 && v.respawnBlocks == 7000) v.respawnBlocks = 10000;
        // (9: he flies, so the giants' blows that do reach him land a bit harder)
        if (v.configVersion < 9 && Math.abs(v.giantArmor - 0.7f) < 1e-4f) v.giantArmor = 0.78f;
        // (10: he can fly off from any giant's fight, so he takes more from the giants' blows; players see no change)
        if (v.configVersion < 10 && Math.abs(v.giantArmor - 0.78f) < 1e-4f) v.giantArmor = 1.13f;
        // (11: re-balanced after a full duel test between the giants; players see no change)
        if (v.configVersion < 11 && Math.abs(v.giantArmor - 1.13f) < 1e-4f) v.giantArmor = 1.38f;
        v.worldRespawnDays = Math.max(0, v.worldRespawnDays);
        v.ownedRespawnDays = Math.max(0, v.ownedRespawnDays);
        v.respawnBlocks = Math.max(0, v.respawnBlocks);
        v.homeRadius = Math.max(200, Math.min(2000, v.homeRadius));
        v.groundNearest = Math.max(0, Math.min(100000, v.groundNearest));
        v.groundFarthest = Math.max(0, Math.min(100000, v.groundFarthest));
        v.groundGap = Math.max(0, Math.min(20000, v.groundGap));
        v.groundCopies = Math.max(1, Math.min(5, v.groundCopies));
        v.farSightBlocks = Math.max(0, Math.min(4096, v.farSightBlocks));
        v.soundVolume = Math.max(0f, Math.min(2f, v.soundVolume));
        v.bigEffects = Math.max(0f, Math.min(2f, v.bigEffects));
        v.maxInWorld = Math.max(0, Math.min(20, v.maxInWorld));
        v.meetRange = Math.max(16, Math.min(1000, v.meetRange));
        v.yieldAt = Math.max(0f, Math.min(0.9f, v.yieldAt));
        v.meetCooldown = Math.max(0, Math.min(1440, v.meetCooldown));
        v.heavyDelaySeconds = Math.max(0, Math.min(600, v.heavyDelaySeconds));
        v.health = clamp(v.health, 10f, 1_000_000f, 6000f);
        v.healthMultiplier = clamp(v.healthMultiplier, 0.1f, 10f, 1f);
        v.damageMultiplier = clamp(v.damageMultiplier, 0f, 100f, 1f);
        v.mobDamage = clamp(v.mobDamage, 0f, 100f, 2.5f);
        v.giantArmor = clamp(v.giantArmor, 0f, 10f, 1.38f);
        v.explosionArmor = clamp(v.explosionArmor, 0f, 10f, 0.35f);
        v.spawnEggScale = clamp(v.spawnEggScale, 0.01f, 2f, 1f);
        v.smallEggScale = clamp(v.smallEggScale, 0.01f, 2f, 0.2f);
        v.worldScale = clamp(v.worldScale, 0.01f, 2f, 1f);
        v.healHomePerMinute = clamp(v.healHomePerMinute, 0f, 100f, 2f);
        v.healAwayPerMinute = clamp(v.healAwayPerMinute, 0f, 100f, 1f);
        v.healPauseMinutes = clamp(v.healPauseMinutes, 0f, 60f, 5f);
        v.podPopShare = clamp(v.podPopShare, 0f, 1f, 0.01f);
        v.podsToSink = clamp(v.podsToSink, 0f, 1f, 0.34f);
        v.grudgeRate = clamp(v.grudgeRate, 0f, 100f, 1f);
        v.podRegrowSeconds = Math.max(0, v.podRegrowSeconds);
        v.sunkSeconds = Math.max(0, v.sunkSeconds);
        v.shedCount = Math.max(0, v.shedCount);
        GiantsBehave.clamp(v);
    }

    /** kept within lo..hi; a value that isn't a number at all goes back to the default */
    private static float clamp(float x, float lo, float hi, float def) {
        return Float.isNaN(x) ? def : Math.max(lo, Math.min(hi, x));
    }
}
