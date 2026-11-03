package net.jj.giants.fun;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.3 "Biome brush") Point and paint any giant's biome (his own paint, round the spot), or
 * erase: the giants' biomes in the circle go back to what the world would make there. The Biome Brush item does it on
 * a click; the book and /giants admin paint|erase do it without one. Erasing goes a few chunks a tick and never loads
 * land on the server thread.
 */
public final class Brush {
    private Brush() {}

    public static final int MIN_R = 16, MAX_R = 256;

    /** each admin's brush: which giant ("erase" to erase), how wide, biome only or his full ground */
    public static final class Setting {
        public String giant = "erase", mode = "biome";
        public int radius = 32;
    }

    static final Map<UUID, Setting> settings = new HashMap<>();

    public static Setting of(ServerPlayer p) {
        return settings.computeIfAbsent(p.getUUID(), k -> new Setting());
    }

    public static AdminServer.Result set(ServerPlayer p, String giant, int radius, String mode) {
        Setting s = of(p);
        if (!giant.equals("erase")) {
            Giant g = AdminServer.giant(giant);
            if (g == null) return AdminServer.Result.fail("No giant called " + giant + ". Or erase.");
            giant = g.key();
        }
        s.giant = giant;
        s.radius = Mth.clamp(radius, MIN_R, MAX_R);
        s.mode = mode.equals("full") ? "full" : "biome";
        Fun.changed();
        return AdminServer.Result.said(List.of("Brush: " + words(s) + "."));
    }

    static String words(Setting s) {
        if (s.giant.equals("erase")) return "erase giants' biomes, " + s.radius + " blocks round";
        Giant g = AdminServer.giant(s.giant);
        return "the " + (g == null ? s.giant : AdminServer.nameOf(g)) + "'s " + (s.mode.equals("full") ? "ground"
                : "biome") + ", " + s.radius + " blocks round";
    }

    // the item

    public static final class Item extends net.minecraft.world.item.Item {
        public Item(Properties p) {
            super(p);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (!level.isClientSide && player instanceof ServerPlayer p) used(p, p.isShiftKeyDown());
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }

        @Override
        public InteractionResult useOn(UseOnContext c) {
            if (!c.getLevel().isClientSide && c.getPlayer() instanceof ServerPlayer p) used(p, p.isShiftKeyDown());
            return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable("item.jj_giants.biome_brush.tip").withStyle(ChatFormatting.GRAY));
        }
    }

    public static final Item ITEM = new Item(new net.minecraft.world.item.Item.Properties().stacksTo(1)
            .rarity(Rarity.EPIC));

    static void register() {
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID,
                "biome_brush"), ITEM);
    }

    public static void give(ServerPlayer p) {
        if (p.getInventory().contains(new ItemStack(ITEM))) return;
        ItemStack s = new ItemStack(ITEM);
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    private static void used(ServerPlayer p, boolean erase) {
        if (!Fun.admin(p)) {
            p.displayClientMessage(Component.literal("This brush is for admins."), true);
            return;
        }
        BlockPos at = Spots.looked(p, Spots.LOOK);
        if (at == null) {
            p.displayClientMessage(Component.literal("Point at the ground (256 blocks at most)."), true);
            return;
        }
        Setting s = of(p);
        AdminServer.Result r = erase || s.giant.equals("erase")
                ? erase(p.getServer(), p.getGameProfile().getName(), p.serverLevel(), at.getX(), at.getZ(), s.radius, p)
                : paint(p.getServer(), p.getGameProfile().getName(), p, s.giant, s.radius, s.mode, at.getX(), at.getZ());
        p.displayClientMessage(Component.literal(String.join(" ", r.lines())), false);
        if (!r.announce().isEmpty()) Fun.tell(p.getServer(), p, r.announce().get(0));
    }

    // painting: his own paint, round the spot

    public static AdminServer.Result paint(MinecraftServer server, String who, ServerPlayer p, String giant, int radius,
            String mode, int x, int z) {
        Giant g = AdminServer.giant(giant);
        if (g == null) return AdminServer.Result.fail("No giant called " + giant + ".");
        int r = Mth.clamp(radius, MIN_R, MAX_R);
        String m = mode.equals("full") ? "full" : "biome";
        List<String> said = AdminServer.call(server, g, "paint", r + " " + m + " " + p.getStringUUID() + " depth=5 at="
                + x + "," + z);
        if (said == null || said.isEmpty())
            return AdminServer.Result.fail(AdminServer.nameOf(g) + ": his mod didn't paint.");
        Fun.log(server, who, g.key(), "paint", "biome brush", AdminServer.nameOf(g) + " " + m + " at " + x + " " + z
                + ", " + r + " round");
        return new AdminServer.Result(true, said, List.of(who + " painted the " + AdminServer.nameOf(g) + "'s "
                + (m.equals("full") ? "ground" : "biome") + " at " + x + " " + z + "."));
    }

    // erasing: the giants' biomes back to the world's own

    /** the biome mods of every giant the Guide knows */
    static Set<String> giantSpaces() {
        return Giants.ALL.stream().map(Giant::modId).collect(Collectors.toSet());
    }

    /** (the tests) a biome that counts as a giant's too */
    public static @Nullable String testGiantBiome;

    static boolean giants(Holder<Biome> b) {
        return b.unwrapKey().map(k -> giantSpaces().contains(k.location().getNamespace())
                || k.location().toString().equals(testGiantBiome)).orElse(false);
    }

    private static final class Job {
        ServerLevel level;
        int cx, cz, radius;
        final ArrayDeque<ChunkPos> left = new ArrayDeque<>();
        int total, done, waited;
        @Nullable UUID who;
        @Nullable Runnable then;
    }

    private static final List<Job> jobs = new ArrayList<>();

    public static AdminServer.Result erase(MinecraftServer server, String who, ServerLevel l, int x, int z,
            int radius, @Nullable ServerPlayer p) {
        Job j = eraseJob(l, x, z, radius, p == null ? null : p.getUUID(), null);
        Fun.log(server, who, "all", "erase", "biome brush", "erased at " + x + " " + z + ", " + j.radius + " round");
        return new AdminServer.Result(true, List.of("Erasing the giants' biomes " + j.radius + " blocks round " + x
                + " " + z + " (" + j.total + " chunks)."), List.of(who + " erased giants' biomes at " + x + " " + z
                + "."));
    }

    static Job eraseJob(ServerLevel l, int x, int z, int radius, @Nullable UUID who, @Nullable Runnable then) {
        Job j = new Job();
        j.level = l;
        j.cx = x;
        j.cz = z;
        j.radius = Mth.clamp(radius, 1, MAX_R + 64);
        j.who = who;
        j.then = then;
        for (int a = (x - j.radius) >> 4; a <= (x + j.radius) >> 4; a++)
            for (int b = (z - j.radius) >> 4; b <= (z + j.radius) >> 4; b++) {
                double dx = Math.max((a << 4) - x, Math.max(0, x - ((a << 4) + 15)));
                double dz = Math.max((b << 4) - z, Math.max(0, z - ((b << 4) + 15)));
                if (dx * dx + dz * dz <= (double) j.radius * j.radius) j.left.add(new ChunkPos(a, b));
            }
        j.total = j.left.size();
        jobs.add(j);
        return j;
    }

    public static boolean erasing() {
        return !jobs.isEmpty();
    }

    public static boolean stopErasing() {
        boolean any = !jobs.isEmpty();
        jobs.clear();
        return any;
    }

    /** a few chunks a tick, about 10 ms at most; land asked for ahead with a ticket, never loaded here */
    static void tick(MinecraftServer server) {
        if (jobs.isEmpty()) return;
        Job j = jobs.get(0);
        long until = System.nanoTime() + 10_000_000L;
        int k = 0;
        for (ChunkPos ahead : j.left) {
            if (k++ >= 16) break;
            j.level.getChunkSource().addRegionTicket(Spots.FUN, ahead, 1, ahead);
        }
        while (!j.left.isEmpty() && System.nanoTime() < until) {
            ChunkPos cp = j.left.peek();
            LevelChunk c = j.level.getChunkSource().getChunkNow(cp.x, cp.z);
            if (c == null) {
                if (++j.waited < 1200) break;
                j.left.poll();
                j.waited = 0;
                continue;
            }
            j.waited = 0;
            j.left.poll();
            eraseChunk(j, c);
            j.done++;
        }
        if (j.left.isEmpty()) {
            jobs.remove(0);
            ServerPlayer p = j.who == null ? null : server.getPlayerList().getPlayer(j.who);
            if (p != null) p.displayClientMessage(Component.literal("Erased: " + j.done + " chunks."), false);
            if (j.then != null) j.then.run();
        }
    }

    /** every column of the chunk in the circle whose biome is a giant's: what the world would make there */
    private static void eraseChunk(Job j, LevelChunk chunk) {
        ServerLevel l = j.level;
        var src = l.getChunkSource().getGenerator().getBiomeSource();
        var sampler = l.getChunkSource().randomState().sampler();
        double r2 = (double) j.radius * j.radius;
        Holder<Biome>[] fallback = new Holder[1];
        boolean[] any = {false};
        chunk.fillBiomesFromNoise((qx, qy, qz, s) -> {
            Holder<Biome> was = chunk.getNoiseBiome(qx, qy, qz);
            int x = QuartPos.toBlock(qx) + 2, z = QuartPos.toBlock(qz) + 2;
            if ((x - j.cx) * (double) (x - j.cx) + (z - j.cz) * (double) (z - j.cz) > r2 || !giants(was)) return was;
            any[0] = true;
            Holder<Biome> w = src.getNoiseBiome(qx, qy, qz, sampler);
            if (!giants(w)) return w;
            // (inside a giant's own ground the world itself makes his biome: the nearest of the world's own instead)
            if (fallback[0] == null) fallback[0] = nearestPlain(l, qx, qy, qz);
            return fallback[0];
        }, sampler);
        if (!any[0]) return;
        chunk.setUnsaved(true);
        l.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
    }

    /** the world's own biome nearest a spot that isn't any giant's (asked of its generator, never loading land) */
    private static Holder<Biome> nearestPlain(ServerLevel l, int qx, int qy, int qz) {
        var src = l.getChunkSource().getGenerator().getBiomeSource();
        var sampler = l.getChunkSource().randomState().sampler();
        for (int r = 8; r <= 1024; r *= 2)
            for (int a = 0; a < 8; a++) {
                double ang = a * Math.PI / 4;
                Holder<Biome> b = src.getNoiseBiome(qx + (int) (Math.cos(ang) * r), qy, qz + (int) (Math.sin(ang) * r),
                        sampler);
                if (!giants(b)) return b;
            }
        var reg = l.registryAccess().registryOrThrow(Registries.BIOME);
        return reg.getHolderOrThrow(l.dimension() == Level.NETHER ? Biomes.NETHER_WASTES
                : l.dimension() == Level.END ? Biomes.THE_END : Biomes.PLAINS);
    }

    /** twice a second: the circle shown to each admin holding the brush, round what they point at */
    static void showCircles(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.getMainHandItem().is(ITEM) || !Fun.admin(p)) continue;
            BlockPos at = Spots.looked(p, Spots.LOOK);
            if (at == null) continue;
            Setting s = of(p);
            int n = Mth.clamp(s.radius / 2, 24, 96);
            for (int i = 0; i < n; i++) {
                double ang = i * Math.PI * 2 / n;
                int x = at.getX() + (int) Math.round(Math.cos(ang) * s.radius),
                        z = at.getZ() + (int) Math.round(Math.sin(ang) * s.radius);
                double y = Spots.ground(p.serverLevel(), x, z);
                if (Double.isNaN(y)) y = at.getY() + 1;
                p.serverLevel().sendParticles(p, s.giant.equals("erase") || p.isShiftKeyDown()
                        ? ParticleTypes.SMOKE : ParticleTypes.END_ROD, true, x + 0.5, y + 0.3, z + 0.5, 1, 0, 0, 0, 0);
            }
            p.displayClientMessage(Component.literal((p.isShiftKeyDown() ? "Erase: " : "Brush: ") + (p.isShiftKeyDown()
                    ? "giants' biomes, " + s.radius + " blocks round" : words(s))
                    + ". Right-click to " + (s.giant.equals("erase") || p.isShiftKeyDown() ? "erase" : "paint")
                    + ", sneak + right-click to erase."), true);
        }
    }

    static void forget() {
        jobs.clear();
        settings.clear();
    }
}
