package net.jj.giants.adventure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.Spots;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * (Part 11, ADMIN_BRIDGE.md §28.2) The Adventure Wand: points and boxes placed by clicking blocks, like the Biome
 * Brush. Its job is picked on the book's Adventure page or with /giants admin wand: a path (each click adds the next
 * point; crouch-click takes the last one off), an area (first click one corner, next click the other; crouch-click
 * clears it) or the box of an adventure map to save (the same two corners). While it is held, the paths and areas of
 * this world show as coloured dust.
 */
public final class Wand {
    private Wand() {}

    /** each admin's wand: what it places, and the name */
    public static final class Setting {
        public String mode = "path", name = "path1";
        /** (areas and map boxes) the first corner, waiting for the second */
        public BlockPos first;
    }

    static final Map<UUID, Setting> settings = new HashMap<>();

    public static Setting of(ServerPlayer p) {
        return settings.computeIfAbsent(p.getUUID(), k -> new Setting());
    }

    static void forget() {
        settings.clear();
    }

    public static AdminServer.Result set(ServerPlayer p, String mode, String name) {
        String n = AdvData.name(name);
        if (n == null) return AdminServer.Result.fail("A name is letters, digits, _ and - (24 at most).");
        if (!List.of("path", "area", "map").contains(mode))
            return AdminServer.Result.fail("The wand places a path, an area or a map's box.");
        Setting s = of(p);
        s.mode = mode;
        s.name = n;
        s.first = null;
        give(p);
        Adventure.changed();
        return AdminServer.Result.said(List.of("Wand: " + words(s) + ". Right-click blocks with it."));
    }

    static String words(Setting s) {
        return switch (s.mode) {
            case "area" -> "the area " + s.name + " (click one corner, then the other)";
            case "map" -> "the box of the map " + s.name + " (click one corner, then the other)";
            default -> "the path " + s.name + " (each click adds a point; crouch-click takes the last off)";
        };
    }

    public static final class Item extends net.minecraft.world.item.Item {
        public Item(Properties p) {
            super(p);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (!level.isClientSide && player instanceof ServerPlayer p) used(p, null);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }

        @Override
        public InteractionResult useOn(UseOnContext c) {
            if (!c.getLevel().isClientSide && c.getPlayer() instanceof ServerPlayer p) used(p, c.getClickedPos());
            return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatableWithFallback("item.jj_giants.adventure_wand.tip",
                    "Admins: places paths, areas and adventure map boxes. Pick its job on the admin book's Adventure"
                            + " page.").withStyle(ChatFormatting.GRAY));
        }
    }

    public static final Item ITEM = new Item(new net.minecraft.world.item.Item.Properties().stacksTo(1)
            .rarity(Rarity.EPIC));

    static void register() {
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID,
                "adventure_wand"), ITEM);
    }

    public static void give(ServerPlayer p) {
        if (p.getInventory().contains(new ItemStack(ITEM))) return;
        ItemStack s = new ItemStack(ITEM);
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    /** a click: on a block (at), or in the air (the block looked at, 64 blocks at most) */
    static void used(ServerPlayer p, BlockPos at) {
        if (!p.hasPermissions(net.jj.giants.admin.AdminCommand.OPS)) {
            p.displayClientMessage(Component.literal("This wand is for admins."), true);
            return;
        }
        if (at == null) at = Spots.looked(p, 64);
        if (at == null) {
            p.displayClientMessage(Component.literal("Point at a block (64 blocks at most)."), true);
            return;
        }
        AdminServer.Result r = click(p, at, p.isShiftKeyDown());
        p.displayClientMessage(Component.literal(String.join(" ", r.lines()))
                .withStyle(r.ok() ? ChatFormatting.GOLD : ChatFormatting.RED), true);
    }

    /** what a click does (also the tests) */
    public static AdminServer.Result click(ServerPlayer p, BlockPos at, boolean crouch) {
        Setting s = of(p);
        MinecraftServer server = p.getServer();
        String who = p.getGameProfile().getName();
        String dim = p.level().dimension().location().toString();
        if (s.mode.equals("path")) {
            if (crouch) return Adventure.pathUndo(server, who, s.name);
            return Adventure.pathAdd(server, who, s.name, dim, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
        }
        if (crouch) {
            s.first = null;
            if (s.mode.equals("area")) return Adventure.areaRemove(server, who, s.name);
            return AdminServer.Result.said(List.of("Map box cleared: click its first corner."));
        }
        if (s.first == null) {
            s.first = at;
            return AdminServer.Result.said(List.of("First corner " + at.getX() + " " + at.getY() + " " + at.getZ()
                    + ": now click the other one."));
        }
        BlockPos a = s.first;
        s.first = null;
        if (s.mode.equals("area")) return Adventure.areaSet(server, who, s.name, dim, a, at);
        return AdvMaps.save(server, p, s.name, a, at);
    }

    // showing them

    private static final DustParticleOptions PATH = new DustParticleOptions(new Vector3f(1f, 0.75f, 0.1f), 1.5f),
            AREA = new DustParticleOptions(new Vector3f(0.2f, 0.8f, 1f), 1.5f),
            FIRST = new DustParticleOptions(new Vector3f(1f, 0.2f, 0.2f), 2f);

    /** to each admin holding the wand: the paths (gold) and areas (blue) of their world, every half second */
    static void show(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        AdvData d = AdvData.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.getMainHandItem().is(ITEM) || p.getOffhandItem().is(ITEM))) continue;
            ServerLevel l = p.serverLevel();
            String dim = l.dimension().location().toString();
            Adventure.Round r = Adventure.round;
            AdvData.State st = r != null && l == r.level ? r.state : d.s;
            for (AdvData.Path path : st.paths.values()) {
                if (!path.dim.equals(dim)) continue;
                for (int i = 0; i < path.pts.size(); i++) {
                    double[] a = path.pts.get(i);
                    dust(p, l, ParticleTypes.END_ROD, a[0], a[1] + 0.5, a[2]);
                    if (i + 1 < path.pts.size()) line(p, l, PATH, a, path.pts.get(i + 1));
                }
            }
            for (AdvData.Area a : st.areas.values()) if (a.dim.equals(dim)) box(p, l, AREA, a);
            Setting s = of(p);
            if (s.first != null) dust(p, l, FIRST, s.first.getX() + 0.5, s.first.getY() + 1.2, s.first.getZ() + 0.5);
        }
    }

    private static void dust(ServerPlayer p, ServerLevel l, net.minecraft.core.particles.ParticleOptions o, double x,
            double y, double z) {
        if (p.distanceToSqr(x, y, z) > 160 * 160) return;
        l.sendParticles(p, o, true, x, y, z, 1, 0, 0, 0, 0);
    }

    private static void line(ServerPlayer p, ServerLevel l, DustParticleOptions o, double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = (int) Math.min(80, Math.max(1, len / 1.5));
        for (int i = 1; i < n; i++) dust(p, l, o, a[0] + dx * i / n, a[1] + 0.3 + dy * i / n, a[2] + dz * i / n);
    }

    private static void box(ServerPlayer p, ServerLevel l, DustParticleOptions o, AdvData.Area a) {
        double x1 = Math.min(a.x1, a.x2), x2 = Math.max(a.x1, a.x2) + 1, z1 = Math.min(a.z1, a.z2),
                z2 = Math.max(a.z1, a.z2) + 1, y1 = Math.min(a.y1, a.y2), y2 = Math.max(a.y1, a.y2) + 1;
        double[][] c = {{x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2}};
        for (int i = 0; i < 4; i++) {
            double[] u = c[i], v = c[(i + 1) % 4];
            line(p, l, o, u, v);
            line(p, l, o, new double[] {u[0], y2, u[2]}, new double[] {v[0], y2, v[2]});
            line(p, l, o, u, new double[] {u[0], y2, u[2]});
        }
    }
}
