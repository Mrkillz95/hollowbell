package net.jj.hollowbell.world;

import java.util.ArrayList;
import java.util.List;
import net.jj.hollowbell.command.GiantsCommand;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The other giants' grounds, as their /giants bridges give them ("groundspot id x z radius"), so that a new ground
 * of his is chosen clear of theirs.
 */
public final class OtherGrounds {
    private OtherGrounds() {}

    private static final Logger LOG = LoggerFactory.getLogger("giants");

    /** how far apart two grounds are kept, edge to edge */
    public static final int GAP = 200;

    /** x, z and radius of every other giant's ground whose mod says where it is (me: his own id, left out) */
    public static List<int[]> of(MinecraftServer server, String me) {
        List<int[]> out = new ArrayList<>();
        for (String line : GiantsCommand.ask(server, "groundspot", "")) {
            String[] w = line.trim().split("\\s+");
            if (w.length != 5 || !w[0].equals("groundspot") || w[1].equals(me)) continue;
            try {
                out.add(new int[] {Integer.parseInt(w[2]), Integer.parseInt(w[3]), Integer.parseInt(w[4])});
            } catch (NumberFormatException e) {
                LOG.debug("A giant's ground came back as {}", line);
            }
        }
        return out;
    }

    /**
     * x, z moved straight out from any other giant's ground until a ground of the given radius there keeps GAP
     * blocks off it. A few rounds, as moving off one can land on another.
     */
    public static int[] keepOff(List<int[]> others, int x, int z, int radius) {
        for (int round = 0; round < 4; round++) {
            boolean moved = false;
            for (int[] o : others) {
                double dx = x - o[0], dz = z - o[1], len = Math.hypot(dx, dz), keep = radius + o[2] + GAP;
                if (len >= keep) continue;
                if (len < 1) {
                    dx = 1;
                    dz = 0;
                    len = 1;
                }
                x = Mth.floor(o[0] + dx / len * keep);
                z = Mth.floor(o[1] + dz / len * keep);
                moved = true;
            }
            if (!moved) break;
        }
        return new int[] {x, z};
    }

    /** true if a ground of this radius at x, z would come within GAP blocks of another giant's */
    public static boolean near(List<int[]> others, int x, int z, int radius) {
        for (int[] o : others) {
            if (Math.hypot(x - o[0], z - o[1]) < radius + o[2] + GAP) return true;
        }
        return false;
    }
}
