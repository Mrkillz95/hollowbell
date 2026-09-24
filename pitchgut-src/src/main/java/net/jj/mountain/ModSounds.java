package net.jj.mountain;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** His own voice. Everything he makes a noise with goes through here so /mountain volume can turn him down. */
public final class ModSounds {
    public static final SoundEvent STEP = reg("step");
    public static final SoundEvent BREATH_IN = reg("breath_in");
    public static final SoundEvent BREATH_OUT = reg("breath_out");
    public static final SoundEvent ROAR = reg("roar");
    public static final SoundEvent SCREAM = reg("scream");
    public static final SoundEvent HEART = reg("heart");
    public static final SoundEvent HURT = reg("hurt");
    public static final SoundEvent DEATH = reg("death");
    public static final SoundEvent AMBIENT = reg("ambient");
    public static final SoundEvent GOO = reg("goo");
    public static final SoundEvent RAGE = reg("rage");
    public static final SoundEvent WEAK = reg("weak");

    private static SoundEvent reg(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, name);
        // he is heard a long way off, so the sounds carry further than a normal one
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** every sound he makes is scaled by the /mountain volume setting */
    public static float vol(float v) { return v * MountainConfig.V.soundVolume; }
    public static boolean silent() { return MountainConfig.V.soundVolume <= 0f; }

    public static void init() {}
}
