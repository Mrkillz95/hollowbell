package net.jj.giants.client.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.editor.SoundCatalog;
import net.jj.giants.mixin.client.SoundLocationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 12, ADMIN_BRIDGE.md §29) The giant editor and the sound picker on this player's game: the looks the server
 * sends are handed to each installed giant ("lookclient"; he builds his look's meshes once and keeps them), and the
 * sound settings are applied as his sounds play (SoundEngineMixin). Leaving the world puts everything back.
 */
public final class EditorClient {
    private EditorClient() {}

    private static JsonObject sound = new JsonObject();
    /** the book's own previews: played as they are, the picker leaves them alone */
    private static final Set<SoundInstance> PREVIEWS = Collections.newSetFromMap(new WeakHashMap<>());
    private static @Nullable SoundInstance playing;
    /** the last looks the server sent (the pictures read them) */
    private static JsonObject looks = new JsonObject();

    /** the server's looks: each installed giant gets his kind's ({} = none) */
    public static void looks(JsonObject o) {
        looks = o;
        java.util.Map<java.util.UUID, Integer> t = new java.util.HashMap<>();
        if (o.get("followers") instanceof JsonObject f)
            for (var e : f.entrySet())
                try {
                    t.put(java.util.UUID.fromString(e.getKey()), e.getValue().getAsInt());
                } catch (RuntimeException bad) {
                    // left out
                }
        TINTS = java.util.Map.copyOf(t);
        JsonObject kinds = o.get("kinds") instanceof JsonObject k ? k : new JsonObject();
        for (Giant g : Giants.installed()) {
            JsonElement mine = kinds.get(g.key());
            AdminServer.call(null, g, "lookclient", mine instanceof JsonObject m ? m.toString() : "{}");
        }
    }

    /** (1.16) followers wearing their giant's look: each one's colour (LivingEntityTintMixin) */
    private static volatile java.util.Map<java.util.UUID, Integer> TINTS = java.util.Map.of();

    /** the colour (ARGB) to draw this creature in: his own times his giant's look's, as it was when none */
    public static int tint(net.minecraft.world.entity.Entity e, int argb) {
        var t = TINTS;
        if (t.isEmpty() || e == null) return argb;
        Integer c = t.get(e.getUUID());
        if (c == null) return argb;
        int a = argb >>> 24, r = (argb >> 16 & 255) * (c >> 16 & 255) / 255, g = (argb >> 8 & 255) * (c >> 8 & 255)
                / 255, b = (argb & 255) * (c & 255) / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static JsonObject lastLooks() {
        return looks;
    }

    public static void sound(JsonObject o) {
        sound = o;
    }

    public static void forget() {
        sound = new JsonObject();
        looks(new JsonObject());
        stopPreview();
    }

    // the picker (SoundEngineMixin)

    private static @Nullable JsonObject of(String kind) {
        return sound.get("giants") instanceof JsonObject g && g.get(kind) instanceof JsonObject k ? k : null;
    }

    /** whose (a giant's kind) a sound is, when the server changed anything of his; else null */
    private static @Nullable String changedKind(SoundInstance s) {
        if (!(sound.get("giants") instanceof JsonObject g) || g.isEmpty() || PREVIEWS.contains(s)) return null;
        ResourceLocation rl = s.getLocation();
        String kind = SoundCatalog.kindOf(rl.getNamespace());
        return kind != null && g.has(kind) ? kind : null;
    }

    /** off (not played): his music turned off, or his ground's sounds; another giant's music: pointed at it */
    public static boolean silenced(SoundInstance s) {
        String kind = changedKind(s);
        if (kind == null) return false;
        JsonObject k = of(kind);
        String path = s.getLocation().getPath();
        if (SoundCatalog.fightMusic(path) && k.has("music")) {
            String to = k.get("music").getAsString();
            if (to.equals("off")) return true;
            String ns = SoundCatalog.NAMESPACE.get(to);
            if (ns != null && !to.equals(kind) && s instanceof AbstractSoundInstance a) {
                ResourceLocation track = ResourceLocation.fromNamespaceAndPath(ns, SoundCatalog.trackFor(kind, path,
                        to));
                // (only a track this game has: that giant's mod is here)
                if (Minecraft.getInstance().getSoundManager().getSoundEvent(track) != null)
                    ((SoundLocationAccess) a).giants$setLocation(track);
            }
            return false;
        }
        return SoundCatalog.ambient(kind, path) && k.has("ambient") && !k.get("ambient").getAsBoolean();
    }

    public static float pitch(SoundInstance s) {
        String kind = changedKind(s);
        if (kind == null) return 1f;
        JsonObject k = of(kind);
        if (!k.has("voicePitch") || !SoundCatalog.voice(kind, s.getLocation().getPath())) return 1f;
        return k.get("voicePitch").getAsFloat();
    }

    public static float volume(SoundInstance s) {
        String kind = changedKind(s);
        if (kind == null) return 1f;
        JsonObject k = of(kind);
        if (!k.has("ambientVolume") || !SoundCatalog.ambient(kind, s.getLocation().getPath())) return 1f;
        return k.get("ambientVolume").getAsFloat();
    }

    // the book's previews

    /** plays a giant's fight track (his "music.fight"), or one of his voice's sounds at a pitch; stops the last */
    public static boolean preview(String kind, boolean music, float pitch) {
        stopPreview();
        String ns = SoundCatalog.NAMESPACE.get(kind);
        if (ns == null) return false;
        String path = music ? "music.fight" : SoundCatalog.VOICE.getOrDefault(kind, java.util.List.of("hurt")).get(0);
        // (his voice: the first of his voice sounds he has a roar or growl for)
        if (!music)
            for (String v : SoundCatalog.VOICE.getOrDefault(kind, java.util.List.of()))
                if (v.contains("roar") || v.contains("bellow") || v.contains("growl") || v.equals("toll")) {
                    path = v;
                    break;
                }
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(ns, path);
        var sm = Minecraft.getInstance().getSoundManager();
        if (sm.getSoundEvent(rl) == null) return false;
        SoundInstance s = new SimpleSoundInstance(rl, SoundSource.MASTER, music ? 0.8f : 1f,
                Math.max(0.5f, Math.min(2f, pitch)), RandomSource.create(), false, 0,
                SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        PREVIEWS.add(s);
        playing = s;
        sm.play(s);
        return true;
    }

    public static void stopPreview() {
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
            playing = null;
        }
    }

    public static boolean previewing() {
        return playing != null && Minecraft.getInstance().getSoundManager().isActive(playing);
    }
}
