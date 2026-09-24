package net.jj.mountain.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.jj.mountain.ModBlocks;
import net.jj.mountain.ModEntities;
import net.jj.mountain.MountainMod;
import net.jj.mountain.client.dev.AutoTest;
import net.jj.mountain.client.render.GooGlobRenderer;
import net.jj.mountain.client.render.HeartRenderer;
import net.jj.mountain.client.render.MountainRenderer;
import net.jj.mountain.client.render.VoxelModel;
import net.jj.mountain.entity.MountainEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class MountainClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.MOUNTAIN, MountainRenderer::new);
        EntityRendererRegistry.register(ModEntities.MOUNTAIN_PART, NoopRenderer::new);
        EntityRendererRegistry.register(ModEntities.GRIP, NoopRenderer::new);
        EntityRendererRegistry.register(ModEntities.HEART, HeartRenderer::new);
        EntityRendererRegistry.register(ModEntities.GOO_GLOB, GooGlobRenderer::new);
        EntityRendererRegistry.register(ModEntities.GUT_TENTACLE, net.jj.mountain.client.render.GutTentacleRenderer::new);
        EntityRendererRegistry.register(ModEntities.GUT_LEECH, net.jj.mountain.client.render.GutLeechRenderer::new);
        EntityRendererRegistry.register(ModEntities.WATCHER, net.jj.mountain.client.render.WatcherEyeRenderer::new);
        EntityRendererRegistry.register(ModEntities.SHADOW, net.jj.mountain.client.render.ShadowRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.GOO, RenderType.cutout());
        MountainEntity.clientTickHook = ClientEffects::tick;
        net.jj.mountain.item.MountainCodexItem.openPages = () -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen == null) mc.setScreen(new CodexScreen());
        };
        MountainEntity.clientFootHook = (leg, x, y, z) -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            Shake.thump(Math.sqrt(mc.player.distanceToSqr(x, y, z)), nearestScale(x, y, z));
        };
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> {
            Shake.tick(); InsideHim.tick(c); Climbing.tick(c);
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                net.jj.mountain.net.InsideHimPayload.TYPE,
                (payload, context) -> context.client().execute(() -> InsideHim.set(payload.mountainId(), payload.on())));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                net.jj.mountain.net.SafeListPayload.TYPE,
                (payload, context) -> context.client().execute(() -> CodexScreen.safeList(payload)));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                net.jj.mountain.net.MoodPayload.TYPE,
                (payload, context) -> context.client().execute(() -> CodexScreen.mood(payload)));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                net.jj.mountain.net.StormPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> net.jj.mountain.client.render.MountainFX.storm(payload.mountain(), payload.targets())));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CodexScreen.forgetEverything();
            net.jj.mountain.client.render.MountainFX.forgetStorm();
            net.jj.mountain.entity.MountainCollision.forgetAll(true);
        });
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((g, t) -> InsideHim.hud(g));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(RiderCarry::tick);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(ctx -> RiderCarry.frame(ctx.tickCounter().getGameTimeDeltaPartialTick(false)));
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "model_reload"); }
            @Override public void onResourceManagerReload(ResourceManager rm) { for (VoxelModel v : VoxelModel.ALL) v.invalidate(); }
        });
        AutoTest.init();
    }

    /** how big the mountain whose foot this was is (his size sets how hard it lands) */
    private static float nearestScale(double x, double y, double z) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return 1f;
        float best = 1f; double bd = Double.MAX_VALUE;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof MountainEntity m)) continue;
            double d = m.distanceToSqr(x, y, z);
            if (d < bd) { bd = d; best = m.mountainScale(); }
        }
        return best;
    }
}
