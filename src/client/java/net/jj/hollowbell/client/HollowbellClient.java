package net.jj.hollowbell.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModBlocks;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.client.render.BellMeshes;
import net.jj.hollowbell.client.render.BellRenderer;
import net.jj.hollowbell.client.render.BellingRenderer;
import net.jj.hollowbell.item.CodexItem;
import net.jj.hollowbell.net.BeingHimPayload;
import net.jj.hollowbell.net.MoodPayload;
import net.jj.hollowbell.net.SafeListPayload;
import net.jj.hollowbell.net.ThumpPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class HollowbellClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.HOLLOWBELL, BellRenderer::new);
        EntityRendererRegistry.register(ModEntities.BELLING, BellingRenderer::new);
        EntityRendererRegistry.register(ModEntities.SEAT, NoopRenderer::new);
        EntityRendererRegistry.register(ModEntities.SHOT, ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx, 2.2f, false));
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CROWN, RenderType.cutout());
        CodexItem.openPages = () -> { var mc = Minecraft.getInstance(); if (mc.screen == null) mc.setScreen(new CodexScreen()); };

        ClientPlayNetworking.registerGlobalReceiver(SafeListPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> CodexScreen.safeList(p)));
        ClientPlayNetworking.registerGlobalReceiver(MoodPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> CodexScreen.mood(p)));
        ClientPlayNetworking.registerGlobalReceiver(BeingHimPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> BeingHim.set(p.bellId(), p.on())));
        ClientPlayNetworking.registerGlobalReceiver(ThumpPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            var pl = ctx.client().player;
            if (pl != null) Shake.crash(Math.sqrt(pl.distanceToSqr(p.x(), p.y(), p.z())), p.power());
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> { CodexScreen.forgetEverything(); BeingHim.set(-1, false); BellSounds.clear(); });
        ClientTickEvents.END_CLIENT_TICK.register(c -> { Shake.tick(); BeingHim.tick(c); BellSounds.tick(c); });
        // he's ticked even when the game would skip him for having his middle too far off (see tickIfSkipped)
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_WORLD_TICK.register(level -> {
            for (var e : level.entitiesForRendering()) if (e instanceof net.jj.hollowbell.entity.HollowbellEntity h) h.tickIfSkipped();
        });
        HudRenderCallback.EVENT.register((g, t) -> BeingHim.hud(g));
        // the glass goes on after every other creature, so whatever he has caught shows through it
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> BellRenderer.drawGlass());
        WorldRenderEvents.START.register(ctx -> BellRenderer.forgetFrame());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "model_reload"); }
            @Override public void onResourceManagerReload(ResourceManager rm) { BellMeshes.INSTANCE.invalidate(); }
        });
        net.jj.hollowbell.client.dev.AutoTest.init();
        // /hollowbell detail [on|off]: the server passes it on, and the setting is kept on this computer only.
        // (It used to be a command of the client's own, but then the client took every /hollowbell command for its
        // own and never sent the rest to the server.)
        ClientPlayNetworking.registerGlobalReceiver(net.jj.hollowbell.net.DetailPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            if (p.what() == net.jj.hollowbell.net.DetailPayload.ON) net.jj.hollowbell.Detail.set(true);
            else if (p.what() == net.jj.hollowbell.net.DetailPayload.OFF) net.jj.hollowbell.Detail.set(false);
            String key = p.what() == net.jj.hollowbell.net.DetailPayload.ON ? "command.hollowbell.detail_on"
                    : p.what() == net.jj.hollowbell.net.DetailPayload.OFF ? "command.hollowbell.detail_off"
                    : net.jj.hollowbell.Detail.on() ? "command.hollowbell.detail_is_on" : "command.hollowbell.detail_is_off";
            if (ctx.client().player != null) ctx.client().player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), false);
        }));
    }
}
