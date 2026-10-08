package net.jj.hollowbell.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.NoWait;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 1.9.8: one summoned far from every player is always found. He used to be put down in land that was loaded but
 * not running, so he never took a tick and was never written down anywhere: /giants where said none, /giants tp went
 * somewhere else. Now the summon holds his land running for a few seconds, and when his chunk is put away he's
 * written down where he lies. And a tp to land that isn't made yet never makes the server wait for it.
 */
public class FarFindTests implements FabricGameTest {

    // ------------------------------------------------------------------ the longest the server went without a tick

    private static long lastEnd, longestGap;
    private static boolean hooked;

    /** from now on, the longest time between two server ticks ending (everything the server did in between) */
    static void startTiming() {
        if (!hooked) {
            hooked = true;
            ServerTickEvents.END_SERVER_TICK.register(s -> {
                long now = System.nanoTime();
                if (lastEnd != 0 && now - lastEnd > longestGap) longestGap = now - lastEnd;
                lastEnd = now;
            });
        }
        lastEnd = 0;
        longestGap = 0;
    }

    static long longestMs() { return longestGap / 1_000_000; }

    // ------------------------------------------------------------------ helpers

    static ServerPlayer player(GameTestHelper h, Vec3 at) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "far-find"), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return true; }
        };
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0f, 0f);
        p.setGameMode(GameType.CREATIVE);
        return p;
    }

    static void drop(ServerPlayer p) {
        if (p != null && p.getServer().getPlayerList().getPlayer(p.getUUID()) == p) p.getServer().getPlayerList().remove(p);
    }

    private static final Pattern AT = Pattern.compile("(?:near|at) (-?\\d+) (?:-?\\d+ )?(-?\\d+)");

    /** does one of these lines put him within `r` blocks of x, z? */
    static boolean saysNear(List<String> lines, double x, double z, double r) {
        for (String s : lines) {
            Matcher m = AT.matcher(s);
            while (m.find()) {
                if (Math.hypot(Integer.parseInt(m.group(1)) - x, Integer.parseInt(m.group(2)) - z) < r) return true;
            }
        }
        return false;
    }

    /** does this message (or any message inside it) carry numbers within `r` blocks of x, z? */
    static boolean mentions(Component c, double x, double z, double r) {
        if (!(c.getContents() instanceof TranslatableContents t)) return false;
        Object[] a = t.getArgs();
        for (int i = 0; i + 1 < a.length; i++) {
            if (a[i] instanceof Number nx && a[i + 1] instanceof Number nz
                    && Math.hypot(nx.doubleValue() - x, nz.doubleValue() - z) < r) return true;
        }
        return false;
    }

    static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    // ------------------------------------------------------------------ this mod's own part

    private static List<HollowbellEntity> loadedNear(ServerLevel l, double x, double z, double r) {
        return new java.util.ArrayList<>(l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && Math.hypot(e.getX() - x, e.getZ() - z) < r));
    }

    private static void summon(GameTestHelper h, double x, double z) {
        var src = h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPermission(4);
        h.getLevel().getServer().getCommands().performPrefixedCommand(src,
            String.format(java.util.Locale.ROOT, "execute positioned %.1f 100 %.1f rotated 0 0 run hollowbell summon calm 0.1", x, z));
    }

    /** written down as lying somewhere, or out of the world: either way, known */
    private static boolean known(MinecraftServer server, UUID id) {
        Away a = Away.get(server);
        return a.get(id) != null || a.lying(server).containsKey(id);
    }

    private static void reload(MinecraftServer server) {
        Away.reloadForTests(server);
    }

    private static void clearAll(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        for (ServerLevel l : server.getAllLevels())
            for (HollowbellEntity f : List.copyOf(l.getEntities(ModEntities.HOLLOWBELL, f -> !f.isRemoved()))) f.discard();
        Away a = Away.get(server);
        a.forgetAll();
        a.forgetParked();
    }

    private static Component finder(ServerLevel l, Vec3 from) {
        return net.jj.hollowbell.item.FinderItem.tell(l, from);
    }

    private static Component tpNow(ServerPlayer p) {
        return net.jj.hollowbell.world.TakeMe.tp(p, 0);
    }

    private static void noteFake(MinecraftServer server, UUID id, String dim, double x, double z) {
        Away.get(server).noteParked(id, dim, x, z, 0.1f);
    }

    private static void forgetFake(MinecraftServer server, UUID id) {
        Away.get(server).unpark(id);
    }

    /** anything this mod's tests need set for a far-off one (and put back after) */
    private static void before(GameTestHelper h) {}
    private static void after(GameTestHelper h) {}

    // ------------------------------------------------------------------ the tests

    /** summoned 3000 blocks from the only player, in land nobody has loaded: where, the finder and tp all find him */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400000, batch = "far_find_summon")
    public void summonedFarAwayIsFound(GameTestHelper h) {
        farAway(h, false);
    }

    /** the same, with the world's notes written out and read back in between, as a restart does */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400000, batch = "far_find_reload")
    public void summonedFarAwayIsFoundAfterASaveAndReload(GameTestHelper h) {
        farAway(h, true);
    }

    /**
     * (The test server runs its ticks as fast as it can, while land is made in real time on other threads: so the
     * waits here go by the clock, and the land is asked for again while it's waited on, as a player would.)
     */
    private void farAway(GameTestHelper h, boolean restart) {
        clearAll(h);
        before(h);
        ServerLevel l = h.getLevel();
        MinecraftServer server = l.getServer();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        ServerPlayer p = player(h, Vec3.atBottomCenterOf(o.above(2)));
        double x = o.getX() + 3000.5, z = o.getZ() + (restart ? 900.5 : 300.5);
        if (NoWait.loaded(l, (int) x, (int) z)) { after(h); drop(p); h.fail("the land 3000 blocks out was loaded already"); return; }
        startTiming();
        summon(h, x, z);
        UUID[] id = {null};
        int[] stage = {0};
        long[] since = {System.currentTimeMillis()};
        long start = since[0];
        h.onEachTick(() -> {
            if (stage[0] == 99) return;
            long now = System.currentTimeMillis();
            String bad = null;
            if (now - start > 150_000) {
                StringBuilder all = new StringBuilder();
                for (ServerLevel lv : server.getAllLevels())
                    for (var e : loadedNear(lv, x, z, 1e9)) all.append(" ").append(e.getBlockX()).append(",").append(e.getBlockY()).append(",").append(e.getBlockZ());
                bad = (stage[0] == 0 ? "he never turned up 3000 blocks out" : stage[0] == 1 ? "his land was never put away" : "the tp never got there, or he wasn't there")
                    + " (land there loaded: " + NoWait.loaded(l, (int) x, (int) z) + ", loaded ones:" + all + ", player at " + p.getBlockX() + " " + p.getBlockZ() + ")";
            }
            else if (stage[0] == 0) {
                // he turns up as his land is loaded, and takes his first ticks there
                List<HollowbellEntity> near = loadedNear(l, x, z, 120);
                if (near.isEmpty()) return;
                id[0] = near.get(0).getUUID();
                stage[0] = 1;
                return;
            } else if (stage[0] == 1) {
                // nobody is near: his land is put away (or he steps out of the world)
                if (l.getEntity(id[0]) != null) return;
                if (!known(server, id[0])) bad = "he went from the world without being written down anywhere";
                if (restart) reload(server);
                List<String> where = GiantsBridge.giants(server, "where", "");
                if (bad == null && !saysNear(where, x, z, 160)) bad = "/giants where doesn't find him near " + (int) x + " " + (int) z + ": " + where;
                List<String> list = GiantsBridge.giants(server, "list", "");
                if (bad == null && !saysNear(list, x, z, 160)) bad = "/giants list doesn't find him: " + list;
                Component finder = finder(l, p.position());
                if (bad == null && !mentions(finder, x, z, 160)) bad = "the finder doesn't point at him: " + key(finder);
                List<String> tp = bad != null ? List.of() : GiantsBridge.giants(server, "tp", p.getUUID().toString());
                if (bad == null && (tp.isEmpty() || tp.get(0).contains("none"))) bad = "/giants tp: " + tp;
                if (bad == null) { stage[0] = 2; return; }
            } else if (stage[0] == 2) {
                if (NoWait.onTheWay(p) || Math.hypot(p.getX() - x, p.getZ() - z) > 200) return;
                if (p.level() != l) bad = "taken to another world";
                else { stage[0] = 3; since[0] = now; return; }
            } else {
                // he loads round you (or comes back into the world as you get there)
                if (!loadedNear(l, p.getX(), p.getZ(), 260).isEmpty()) {
                    long ms = longestMs();
                    stage[0] = 99;
                    clearAll(h);
                    after(h);
                    drop(p);
                    if (ms >= 3000) h.fail("the server stopped for " + ms + " ms on the way");
                    else h.succeed();
                    return;
                }
                if (now - since[0] > 60_000) bad = "tp took the player to " + (int) p.getX() + " " + (int) p.getZ() + " but he isn't there";
                else return;
            }
            stage[0] = 99;
            clearAll(h);
            after(h);
            drop(p);
            h.fail(bad);
        });
    }

    /** a tp to land that has never been made: you're told it's being got ready, the server never waits for it */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400000, batch = "far_find_unmade")
    public void tpToUnmadeLandNeverStopsTheServer(GameTestHelper h) {
        clearAll(h);
        ServerLevel l = h.getLevel();
        MinecraftServer server = l.getServer();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        ServerPlayer p = player(h, Vec3.atBottomCenterOf(o.above(2)));
        int x = o.getX() - 1_100_000, z = o.getZ() + 800_000;
        if (NoWait.loaded(l, x, z)) { drop(p); h.fail("that land was loaded already"); return; }
        UUID id = UUID.randomUUID();
        noteFake(server, id, l.dimension().location().toString(), x + 0.5, z + 0.5);
        startTiming();
        long t0 = System.nanoTime();
        Component said = tpNow(p);
        long callMs = (System.nanoTime() - t0) / 1_000_000;
        String first = callMs >= 200 ? "the tp itself took " + callMs + " ms"
            : said == null || !key(said).endsWith("tp_soon") ? "not told the land is being got ready: " + (said == null ? null : key(said))
            : Math.hypot(p.getX() - x, p.getZ() - z) < 1000 ? "taken there before the land was made" : null;
        if (first != null) { forgetFake(server, id); drop(p); h.fail(first); return; }
        boolean[] done = {false};
        long start = System.currentTimeMillis();
        h.onEachTick(() -> {
            if (done[0]) return;
            if (NoWait.onTheWay(p) && System.currentTimeMillis() - start < 120_000) return;
            done[0] = true;
            long ms = longestMs();
            double d = Math.hypot(p.getX() - x, p.getZ() - z);
            BlockPos feet = p.blockPosition();
            boolean ground = !l.getBlockState(feet.below()).isAir() || !l.getBlockState(feet.below(2)).isAir();
            boolean free = l.getBlockState(feet).getCollisionShape(l, feet).isEmpty();
            boolean loaded = NoWait.loadedAround(l, p.getBlockX(), p.getBlockZ());
            forgetFake(server, id);
            drop(p);
            if (d >= 120) h.fail("landed " + (int) d + " blocks from the spot");
            else if (!loaded) h.fail("landed on land that isn't loaded");
            else if (!(ground && free)) h.fail("not set down on safe ground");
            else if (ms >= 1000) h.fail("the server stopped for " + ms + " ms while the land was made");
            else h.succeed();
        });
    }
}
