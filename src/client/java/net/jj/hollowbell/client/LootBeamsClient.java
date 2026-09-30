package net.jj.hollowbell.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.jj.hollowbell.ModBlocks;
import net.jj.hollowbell.block.LootCacheBlock;
import net.jj.hollowbell.client.render.BellRenderer;
import net.jj.hollowbell.net.LootBeamsPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The beams of his loot caches, seen from far off. The cache draws its own beam while its ground is loaded for you;
 * past that, the server says where the lit ones are (up to 1024 blocks) and they're drawn here, the same beam, never
 * both at once. Near or far, the beam is drawn with the fog pushed back, so it doesn't vanish into the fog at the
 * edge of your render distance and then pop back.
 */
public final class LootBeamsClient {
    private LootBeamsClient() {}

    /** the beam's look (the same as a beacon's, pale green) */
    public static final int COLOUR = 0xFFA8F0B4;
    public static final float WIDTH = 0.25f, GLOW = 0.32f;
    /** from this far off the beam widens so it stays as wide on the screen */
    public static final double WIDE_FROM = 96;
    /** where the fog on a beam starts and ends (never nearer than the game's own fog) */
    public static final float FOG_START = 640f, FOG_END = 1600f;

    private static List<BlockPos> beams = new ArrayList<>();
    private static ClientLevel beamsFor;
    /** the frame each cache last drew its own beam in */
    private static final Map<Long, Long> drawnByCache = new HashMap<>();
    private static long frame;
    private static MultiBufferSource.BufferSource own;

    public static void receive(LootBeamsPayload p) {
        Minecraft mc = Minecraft.getInstance();
        beams = new ArrayList<>(p.all());
        beamsFor = mc.level;
    }

    public static void clear() { beams = new ArrayList<>(); beamsFor = null; drawnByCache.clear(); }

    /** how many beams the server has told us about (for the tests and the curious) */
    public static int count() { return beams.size(); }

    public static void startFrame() {
        frame++;
        if (drawnByCache.size() > 64) drawnByCache.values().removeIf(f -> frame - f > 20);
    }

    /** the cache drew its own beam this frame */
    public static void cacheDrew(BlockPos pos) { drawnByCache.put(pos.asLong(), frame); }

    /** the far ones: every beam the server told us about that its cache isn't drawing itself */
    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || beams.isEmpty() || beamsFor != mc.level) return;
        Vec3 cam = ctx.camera().getPosition();
        float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        long t = mc.level.getGameTime();
        for (BlockPos b : beams) {
            Long f = drawnByCache.get(b.asLong());
            if (f != null && frame - f <= 1) continue;
            // loaded here and not a lit cache any more: the server will say so in a moment
            var ch = mc.level.getChunkSource().getChunk(b.getX() >> 4, b.getZ() >> 4, false);
            if (ch != null && !(ch instanceof net.minecraft.world.level.chunk.EmptyLevelChunk)) {
                BlockState st = mc.level.getBlockState(b);
                if (!st.is(ModBlocks.LOOT_CACHE) || !st.getValue(LootCacheBlock.LIT)) continue;
            }
            double dx = b.getX() - cam.x, dz = b.getZ() - cam.z;
            if (dx * dx + dz * dz > 1100.0 * 1100.0) continue;
            draw(new Matrix4f().translation((float) dx, (float) (b.getY() - cam.y), (float) dz), b, partial, t, mc.level.getMaxBuildHeight(), ctx.camera());
        }
    }

    /**
     * One beam, drawn at once. {@code at} is the cache's corner, relative to the camera. Far off it's drawn smaller
     * and nearer by the same amount (the game draws nothing past four times your render distance), which looks the
     * same from where you stand.
     */
    public static void draw(Matrix4f at, BlockPos pos, float partial, long t, int buildTop, Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        int top = buildTop - pos.getY();
        if (top <= 1) return;
        Vec3 base = new Vec3(at.m30(), at.m31(), at.m32());
        double far = Math.max(base.length(), base.add(0, top, 0).length());
        float k = (float) BellRenderer.shrink(far);
        // past 96 blocks it widens with the distance, so it never gets thinner on the screen than it is there
        double across = Math.hypot(base.x, base.z);
        float wide = (float) Math.max(1.0, across / WIDE_FROM);
        PoseStack ps = new PoseStack();
        ps.scale(k, k, k);
        ps.mulPose(at);
        if (own == null) own = MultiBufferSource.immediate(new ByteBufferBuilder(4096));
        float fs0 = RenderSystem.getShaderFogStart(), fe0 = RenderSystem.getShaderFogEnd();
        float fs = fs0, fe = fe0;
        // pushed-back fog, unless you're under water, in lava or snow, or blinded
        boolean clear = camera.getFluidInCamera() == FogType.NONE && (mc.player == null
                || (!mc.player.hasEffect(MobEffects.BLINDNESS) && !mc.player.hasEffect(MobEffects.DARKNESS)));
        if (clear) { fs = Math.max(fs0, FOG_START); fe = Math.max(fe0, FOG_END); }
        try {
            // (set first: the beam's two layers are each drawn the moment the next one starts)
            RenderSystem.setShaderFogStart(fs * k);
            RenderSystem.setShaderFogEnd(fe * k);
            BeaconRenderer.renderBeaconBeam(ps, own, BeaconRenderer.BEAM_LOCATION, partial, 1f, t, 1, top, COLOUR, WIDTH * wide, GLOW * wide);
            own.endBatch();
        } finally {
            RenderSystem.setShaderFogStart(fs0);
            RenderSystem.setShaderFogEnd(fe0);
        }
    }
}
