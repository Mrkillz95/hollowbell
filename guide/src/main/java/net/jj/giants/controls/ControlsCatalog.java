package net.jj.giants.controls;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import org.jetbrains.annotations.Nullable;

/**
 * The Controls tab's thinking, kept apart from the screen so it can be tested: whose each key mapping is, the keys
 * grouped by giant, and which keys clash.
 *
 * <p>A key is a giant's when its Controls category is {@code key.categories.<his mod id>} (or starts with that and
 * an underscore, like Swarmforge's {@code key.categories.swarmforge_build}), when its own name starts with
 * {@code key.<his mod id>.}, or when his giants_controls.json lists its category. The guide's own are under
 * {@code key.categories.jj_giants}.
 */
public final class ControlsCatalog {
    private ControlsCatalog() {}

    /** the group of the guide's own keys */
    public static final String GUIDE = "guide";

    /** whose this key is: a giant's key, {@link #GUIDE}, or null (Minecraft's or another mod's) */
    public static @Nullable String owner(ControlKey k, List<Giant> giants, Map<String, String> extraCategories) {
        String cat = k.category() == null ? "" : k.category();
        String name = k.name() == null ? "" : k.name();
        String extra = extraCategories.get(cat);
        if (extra != null) return extra;
        if (ours(cat, name, GiantsGuideMod.ID)) return GUIDE;
        // the longest mod id that fits wins (no giant's id starts another's today, but one day it might)
        String best = null;
        int bestLen = -1;
        for (Giant g : giants)
            if (ours(cat, name, g.modId()) && g.modId().length() > bestLen) {
                best = g.key();
                bestLen = g.modId().length();
            }
        return best;
    }

    private static boolean ours(String cat, String name, String modId) {
        String c = "key.categories." + modId;
        return cat.equals(c) || cat.startsWith(c + "_") || cat.startsWith(c + ".") || name.startsWith("key." + modId + ".");
    }

    /**
     * The giants' (and the guide's) keys, grouped: the guide first, then each giant in the guide's order, with only
     * the groups that have keys. Inside a group, keys stay together by category, in the order Minecraft has them.
     */
    public static Map<String, List<ControlKey>> group(
            List<ControlKey> all, List<Giant> giants, Map<String, String> extraCategories) {
        Map<String, List<ControlKey>> found = new LinkedHashMap<>();
        for (ControlKey k : all) {
            String o = owner(k, giants, extraCategories);
            if (o != null) found.computeIfAbsent(o, x -> new ArrayList<>()).add(k);
        }
        Map<String, List<ControlKey>> out = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        order.add(GUIDE);
        for (Giant g : giants) order.add(g.key());
        for (String o : order) {
            List<ControlKey> ks = found.get(o);
            if (ks == null || ks.isEmpty()) continue;
            List<String> cats = new ArrayList<>();
            for (ControlKey k : ks) if (!cats.contains(k.category())) cats.add(k.category());
            List<ControlKey> sorted = new ArrayList<>();
            for (String c : cats) for (ControlKey k : ks) if (k.category().equals(c)) sorted.add(k);
            out.put(o, sorted);
        }
        return out;
    }

    /**
     * What a key does, as a word that's the same for every giant: the last part of its name
     * ({@code key.hollowbell.armour_power} is "armour_power"). Swarmforge's "go_after_look" is everyone else's
     * "attack_look".
     */
    public static String role(String name) {
        String r = name == null ? "" : name.substring(name.lastIndexOf('.') + 1);
        return r.equals("go_after_look") ? "attack_look" : r;
    }

    /**
     * What else is on the same key as {@code k}. {@code clashes} are real clashes (shown red, like Minecraft's own
     * Controls); {@code shared} are the same job on two different giants (every giant's armour power is R, every
     * "go after what I look at" is V): each giant reads its own key only when it's his turn (his armour on, his book
     * in hand), so those are meant to share.
     */
    public record Clash(List<ControlKey> clashes, List<ControlKey> shared) {
        public boolean any() {
            return !clashes.isEmpty();
        }
    }

    public static Clash clash(ControlKey k, List<ControlKey> all, Function<ControlKey, String> ownerOf) {
        List<ControlKey> clashes = new ArrayList<>(), shared = new ArrayList<>();
        if (k.unbound()) return new Clash(clashes, shared);
        String mine = ownerOf.apply(k);
        for (ControlKey o : all) {
            if (o == k || o.name().equals(k.name()) || o.unbound() || !o.key().equals(k.key())) continue;
            String theirs = ownerOf.apply(o);
            boolean bothGiants = mine != null && theirs != null && !mine.equals(GUIDE) && !theirs.equals(GUIDE);
            if (bothGiants && !mine.equals(theirs) && role(k.name()).equals(role(o.name()))) shared.add(o);
            else clashes.add(o);
        }
        return new Clash(clashes, shared);
    }
}
