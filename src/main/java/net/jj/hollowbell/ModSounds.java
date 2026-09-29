package net.jj.hollowbell;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * His own sounds. Each is set out in assets/hollowbell/sounds.json (made by tools/sounds.py): some are made for him
 * from scratch, the rest are the game's own sounds pitched right down and layered. Nothing from other games or mods.
 */
public final class ModSounds {
    private ModSounds() {}

    // his body
    public static final SoundEvent HUM = reg("hum"), DRIFT = reg("drift"), PULSE = reg("pulse"), PULSE_WATER = reg("pulse_water"),
            RIPPLE = reg("ripple"), HURT = reg("hurt"), HURT_HEAVY = reg("hurt_heavy"), DEATH = reg("death"), DEATH_FALL = reg("death_fall"),
            TIRED = reg("tired");
    // his strands and parts
    public static final SoundEvent STRAND = reg("strand"), GRAB = reg("grab"), STING = reg("sting"), POD_POP = reg("pod_pop"),
            EGG_BURST = reg("egg_burst"), GOO = reg("goo"), SPORES = reg("spores");
    // the egg rain: the wind-up, an egg landing, a Belling hatching
    public static final SoundEvent EGG_WIND = reg("egg_wind"), EGG_SPLAT = reg("egg_splat"), EGG_HATCH = reg("egg_hatch");
    // the Bellings
    public static final SoundEvent BELLING = reg("belling"), BELLING_HURT = reg("belling_hurt"), BELLING_DEATH = reg("belling_death");
    // inside the dome
    public static final SoundEvent HEARTBEAT = reg("heartbeat"), ECHO = reg("echo");
    // the moves
    public static final SoundEvent TOLL = reg("toll"), TOLL_BIG = reg("toll_big"), SLAM = reg("slam"), SHOCK = reg("shock"),
            FLASH = reg("flash"), WHIP = reg("whip"), VOLLEY = reg("volley"), BEAM = reg("beam"), SPLASH = reg("splash"),
            CHURN = reg("churn"), SWOOP = reg("swoop"), CLICK = reg("click");
    // his fight music, and his ground's music and sounds
    public static final SoundEvent MUSIC_FIGHT = reg("music.fight"), MUSIC_GROUND = reg("music.ground"), AMBIENT_GROUND = reg("ambient.ground"),
            AMBIENT_MOOD = reg("ambient.mood"), AMBIENT_ADDITIONS = reg("ambient.additions");
    // the warning before each heavy move
    public static final SoundEvent WARN_DROP = reg("warn_drop"), WARN_WHIRLPOOL = reg("warn_whirlpool"), WARN_DIVE = reg("warn_dive"),
            WARN_TOLL = reg("warn_toll"), WARN_ARMS = reg("warn_arms"), WARN_STINGERS = reg("warn_stingers"), WARN_LANCES = reg("warn_lances"),
            WARN_UNDERTOW = reg("warn_undertow");

    /** every one of them, for the test that checks they're all there */
    public static final SoundEvent[] ALL = {HUM, DRIFT, PULSE, PULSE_WATER, RIPPLE, HURT, HURT_HEAVY, DEATH, DEATH_FALL, TIRED,
            STRAND, GRAB, STING, POD_POP, EGG_BURST, GOO, SPORES, EGG_WIND, EGG_SPLAT, EGG_HATCH, BELLING, BELLING_HURT, BELLING_DEATH, HEARTBEAT, ECHO,
            TOLL, TOLL_BIG, SLAM, SHOCK, FLASH, WHIP, VOLLEY, BEAM, SPLASH, CHURN, SWOOP, CLICK,
            WARN_DROP, WARN_WHIRLPOOL, WARN_DIVE, WARN_TOLL, WARN_ARMS, WARN_STINGERS, WARN_LANCES, WARN_UNDERTOW,
            MUSIC_FIGHT, MUSIC_GROUND, AMBIENT_GROUND, AMBIENT_MOOD, AMBIENT_ADDITIONS};

    private static SoundEvent reg(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {}
}
