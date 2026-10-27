package net.jj.giants.client.perf;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.controls.ControlKey;
import net.jj.giants.controls.ControlsBridge;
import net.jj.giants.controls.ControlsJson;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * The Controls tab's half on the player's game: Minecraft's own key mappings (the same ones its Controls screen
 * shows, so a change here is a change there), each giant's giants_controls.json, and his gestures' on/off.
 */
public final class ControlsClient {
    private ControlsClient() {}

    private static @Nullable Map<String, ControlsJson.Controls> files;
    private static final Map<String, Boolean> gestures = new HashMap<>();

    /** every giant's (and the guide's) giants_controls.json, read once */
    public static Map<String, ControlsJson.Controls> files() {
        if (files == null) files = ControlsJson.all(Giants.installed());
        return files;
    }

    /** adds (or replaces) one owner's file as if his jar had it (the autotest's pictures use this) */
    public static void addFile(ControlsJson.Controls c) {
        files();
        Map<String, ControlsJson.Controls> m = new java.util.LinkedHashMap<>(files);
        m.put(c.owner(), c);
        files = m;
    }

    public static Map<String, String> extraCategories() {
        return ControlsJson.extraCategories(files());
    }

    /** all of Minecraft's key mappings, as the catalog sees them */
    public static List<ControlKey> keys() {
        List<ControlKey> out = new ArrayList<>();
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings) out.add(of(km));
        return out;
    }

    public static ControlKey of(KeyMapping km) {
        return new ControlKey(km.getName(), km.getCategory(), km.saveString(), km.getDefaultKey().getName());
    }

    public static @Nullable KeyMapping mapping(String name) {
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings) if (km.getName().equals(name)) return km;
        return null;
    }

    /** sets a key the way Minecraft's Controls screen does: the key, the lookup table again, then options.txt */
    public static void bind(KeyMapping km, InputConstants.Key key) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.setKey(km, key);
        KeyMapping.resetMapping();
        mc.options.save();
    }

    /** puts these keys back to their defaults (one save at the end) */
    public static void resetAll(Collection<KeyMapping> kms) {
        Minecraft mc = Minecraft.getInstance();
        for (KeyMapping km : kms) km.setKey(km.getDefaultKey());
        KeyMapping.resetMapping();
        mc.options.save();
    }

    /** asks each giant whether his gestures are on (null: his mod doesn't say) */
    public static void readGestures() {
        gestures.clear();
        for (Map.Entry<String, ControlsJson.Controls> e : files().entrySet()) {
            Giant g = Giants.byKey(e.getKey());
            if (g == null) continue;
            for (ControlsJson.Gesture x : e.getValue().gestures()) {
                Boolean v = ControlsBridge.get(g, x);
                if (v != null) gestures.put(e.getKey() + ":" + x.id(), v);
            }
        }
    }

    public static @Nullable Boolean gesture(String owner, ControlsJson.Gesture x) {
        return gestures.get(owner + ":" + x.id());
    }

    /** turns a gesture on or off in his mod; false if it didn't take it */
    public static boolean setGesture(String owner, ControlsJson.Gesture x, boolean on) {
        Giant g = Giants.byKey(owner);
        if (g == null || !ControlsBridge.set(g, x, on)) return false;
        Boolean now = ControlsBridge.get(g, x);
        gestures.put(owner + ":" + x.id(), now != null ? now : on);
        return true;
    }
}
