package net.jj.giants.fun;

import org.jetbrains.annotations.Nullable;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.3) A den for each giant, sized to him (r round, his height h at that size), built column
 * by column like the arena (Builds). Each is its own plan: Pitchgut's scorched crater (Crater), Furrowmaw's burrow pit
 * (Burrow), the Cerberus's three-part yard and throne (Kennel), the Hollowbell's roost (Roost), the Lantern Willow's
 * bog (Mire), Wreckback's cove and wrecks (Cove), the Swarmforge's scrap yard and forge (Yard) and the Gloamwick's
 * dark glade (Glade).
 */
final class Dens {
    private Dens() {}

    /** the smallest and biggest den radius */
    static final int LEAST = 10, MOST = 128;

    static @Nullable Plan shape(String kind, int r, double h) {
        Plan p = switch (kind) {
            case "pitchgut" -> new Crater(r, h);
            case "furrowmaw" -> new Burrow(r, h);
            case "cerberus" -> new Kennel(r, h);
            case "hollowbell" -> new Roost(r, h);
            case "lanternwillow" -> new Mire(r, h);
            case "wreckback" -> new Cove(r, h);
            case "swarmforge" -> new Yard(r, h);
            case "gloamwick" -> new Glade(r, h);
            default -> null;
        };
        return p == null ? null : p.made();
    }
}
