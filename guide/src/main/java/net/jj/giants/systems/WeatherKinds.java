package net.jj.giants.systems;

import java.util.Map;

/**
 * (Part 9, ADMIN_BRIDGE.md 25.5) Weather Gods: each giant's own weather, shared by the server (rain, thunder,
 * lightning) and the player's game (the fog, its colour, the sky and what falls). Each fits the giant: Pitchgut's ash,
 * Furrowmaw's dust, the Cerberus's sleet and embers, Hollowbell's storm, the Willow's mist, the Wreckback's squall,
 * Swarmforge's smog, the Gloamwick's gloaming.
 *
 * @param rain      rain at full strength (0-1; snow where it's cold)
 * @param thunder   thunder (darkens the sky with the rain)
 * @param lightning bolts for show round him (no fire, no harm)
 * @param fog       the fog's colour
 * @param fogEnd    how far you see at full strength, in blocks
 */
public record WeatherKinds(String id, float rain, float thunder, boolean lightning, int fog, float fogEnd) {

    public static final WeatherKinds ASH = new WeatherKinds("ash", 0, 0, false, 0x4f4339, 36);
    public static final WeatherKinds DUST = new WeatherKinds("dust", 0, 0, false, 0x9a7b52, 34);
    public static final WeatherKinds SLEET = new WeatherKinds("sleet", 0, 0, false, 0x6e6476, 56);
    public static final WeatherKinds STORM = new WeatherKinds("storm", 1, 1, true, 0x4c5466, 88);
    public static final WeatherKinds MIST = new WeatherKinds("mist", 0.35f, 0, false, 0x8a9a86, 22);
    public static final WeatherKinds SQUALL = new WeatherKinds("squall", 1, 0.6f, true, 0x46566a, 64);
    public static final WeatherKinds SMOG = new WeatherKinds("smog", 0, 0, false, 0x7d6448, 40);
    public static final WeatherKinds GLOAMING = new WeatherKinds("gloaming", 0, 0, false, 0x17152b, 30);
    public static final WeatherKinds RAIN = new WeatherKinds("rain", 0.8f, 0, false, 0x6e7886, 80);

    static final Map<String, WeatherKinds> BY_GIANT = Map.of("pitchgut", ASH, "furrowmaw", DUST, "cerberus", SLEET,
            "hollowbell", STORM, "lanternwillow", MIST, "wreckback", SQUALL, "swarmforge", SMOG, "gloamwick", GLOAMING);
    static final Map<String, WeatherKinds> BY_ID = Map.of("ash", ASH, "dust", DUST, "sleet", SLEET, "storm", STORM,
            "mist", MIST, "squall", SQUALL, "smog", SMOG, "gloaming", GLOAMING, "rain", RAIN);

    /** this giant's weather (another kind: plain rain) */
    public static WeatherKinds of(String giant) {
        return BY_GIANT.getOrDefault(giant, RAIN);
    }

    /** a weather by its id, or null */
    public static WeatherKinds byId(String id) {
        return BY_ID.get(id);
    }
}
