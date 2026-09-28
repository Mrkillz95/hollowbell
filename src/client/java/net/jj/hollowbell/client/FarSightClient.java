package net.jj.hollowbell.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.client.render.BellRenderer;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.net.FarSightPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Your side of far sight: the server says once a second where every Hollowbell on your horizon is, and each one
 * is drawn out there with his simple far-away model, hanging calm, gliding between the updates. He fades in and
 * out through the fog over a second. The moment the real one reaches you, the stand-in steps aside for him.
 */
public final class FarSightClient {
    private FarSightClient() {}

    private static final class Ghost {
        final HollowbellEntity e;
        double tx, ty, tz;
        float yaw;
        boolean listed = true;
        float fade;
        Ghost(HollowbellEntity e) { this.e = e; }
    }

    private static final Map<UUID, Ghost> ghosts = new HashMap<>();
    /** real ones this game has, and when each was last seen (so one leaving view is taken over without a gap) */
    private static final Set<UUID> real = new HashSet<>();
    private static final Map<UUID, Long> realSeen = new HashMap<>();

    public static void receive(FarSightPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Set<UUID> now = new HashSet<>();
        for (FarSightPayload.Far f : p.all()) {
            now.add(f.id());
            Ghost g = ghosts.get(f.id());
            if (g == null || g.e.level() != mc.level) {
                HollowbellEntity e = ModEntities.HOLLOWBELL.create(mc.level);
                if (e == null) continue;
                e.makeGhost();
                e.setBellScale(f.scale());
                e.setVariant(f.variant());
                e.moveTo(f.x(), f.y(), f.z(), f.yaw(), 0f);
                g = new Ghost(e);
                // the real one was on the screen a moment ago: the stand-in takes over at once, no fading in
                Long seen = realSeen.get(f.id());
                if (seen != null && mc.level.getGameTime() - seen < 60) g.fade = 1f;
                ghosts.put(f.id(), g);
            }
            g.tx = f.x(); g.ty = f.y(); g.tz = f.z(); g.yaw = f.yaw();
            g.listed = true;
            if (Math.abs(g.e.bellScale() - f.scale()) > 1e-3f) g.e.setBellScale(f.scale());
            if (g.e.variant() != f.variant()) g.e.setVariant(f.variant());
        }
        for (Map.Entry<UUID, Ghost> en : ghosts.entrySet()) if (!now.contains(en.getKey())) en.getValue().listed = false;
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null) { clear(); return; }
        real.clear();
        long t = mc.level.getGameTime();
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof HollowbellEntity h && !h.isGhost()) { real.add(h.getUUID()); realSeen.put(h.getUUID(), t); }
        realSeen.values().removeIf(v -> t - v > 200 || t < v);
        for (Iterator<Map.Entry<UUID, Ghost>> it = ghosts.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Ghost> en = it.next();
            Ghost g = en.getValue();
            if (g.e.level() != mc.level) { it.remove(); continue; }
            boolean handedOver = real.contains(en.getKey());
            boolean show = g.listed && !handedOver && HollowbellConfig.V.farSightBlocks > 0;
            g.fade = handedOver ? 0f : Mth.clamp(g.fade + (show ? 0.05f : -0.05f), 0f, 1f);
            if (!g.listed && g.fade <= 0f) { it.remove(); continue; }
            // glide toward where the server last said he was
            HollowbellEntity e = g.e;
            e.setOldPosAndRot();
            double far = Math.hypot(g.tx - e.getX(), g.tz - e.getZ());
            if (far > 200) e.setPos(g.tx, g.ty, g.tz);
            else e.setPos(e.getX() + (g.tx - e.getX()) * 0.12, e.getY() + (g.ty - e.getY()) * 0.12, e.getZ() + (g.tz - e.getZ()) * 0.12);
            e.setYRot(e.getYRot() + Mth.wrapDegrees(g.yaw - e.getYRot()) * 0.12f);
            e.ghostTick();
        }
    }

    public static void render(WorldRenderContext ctx) {
        if (ghosts.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = ctx.camera().getPosition();
        float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        int limit = Math.min(4096, HollowbellConfig.V.farSightBlocks);
        for (Map.Entry<UUID, Ghost> en : ghosts.entrySet()) {
            Ghost g = en.getValue();
            if (g.fade <= 0f || real.contains(en.getKey())) continue;
            HollowbellEntity e = g.e;
            if (!(mc.getEntityRenderDispatcher().getRenderer(e) instanceof BellRenderer r)) continue;
            double x = Mth.lerp(partial, e.xo, e.getX()), y = Mth.lerp(partial, e.yo, e.getY()), z = Mth.lerp(partial, e.zo, e.getZ());
            double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (Math.hypot(dx, dz) > limit * 1.05 + 64) continue;
            double k = BellRenderer.shrink(dist);
            AABB box = e.bodyBox();
            if (ctx.frustum() != null && !ctx.frustum().isVisible(k < 1 ? BellRenderer.shrunk(box, cam.x, cam.y, cam.z, k) : box)) continue;
            PoseStack ps = new PoseStack();
            ps.translate(dx, dy, dz);
            r.renderGhost(e, ps, partial, g.fade, ctx.consumers());
        }
    }

    /** how many are being drawn far off right now (for the tests and the curious) */
    public static int count() { return ghosts.size(); }

    public static void clear() { ghosts.clear(); real.clear(); realSeen.clear(); }
}
