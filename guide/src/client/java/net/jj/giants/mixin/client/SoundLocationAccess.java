package net.jj.giants.mixin.client;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (Part 12) a giant's fight track pointed at another giant's, before it starts (the same sound, so his own code still stops it) */
@Mixin(AbstractSoundInstance.class)
public interface SoundLocationAccess {
    @Mutable
    @Accessor("location")
    void giants$setLocation(ResourceLocation location);
}
