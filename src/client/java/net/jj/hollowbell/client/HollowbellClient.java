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
        net.jj.hollowbell.client.render.BellArmorRenderer.register();    // bell glass armor drawn see-through
        EntityRendererRegistry.register(ModEntities.SEAT, NoopRenderer::new);
        EntityRendererRegistry.register(ModEntities.STINGER_HOOK, net.jj.hollowbell.client.render.StingerHookRenderer::new);
        EntityRendererRegistry.register(ModEntities.SHOT, net.jj.hollowbell.client.render.ShotRenderer::new);
        net.jj.hollowbell.entity.Shot.eggFx = net.jj.hollowbell.client.render.ShotRenderer::fx;
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CROWN, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(), ModBlocks.TENDRIL_GLASS, ModBlocks.BELL_SHARD);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.LOOT_CACHE, RenderType.cutout());
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(ModBlocks.LOOT_CACHE_ENTITY, net.jj.hollowbell.client.render.LootCacheRenderer::new);
        CodexItem.openPages = () -> { var mc = Minecraft.getInstance(); if (mc.screen == null) mc.setScreen(new CodexScreen()); };

        ClientPlayNetworking.registerGlobalReceiver(SafeListPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> CodexScreen.safeList(p)));
        ClientPlayNetworking.registerGlobalReceiver(net.jj.hollowbell.net.LookMarkPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> LookAim.mark(p.entityId())));
        ClientPlayNetworking.registerGlobalReceiver(MoodPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> CodexScreen.mood(p)));
        ClientPlayNetworking.registerGlobalReceiver(BeingHimPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> BeingHim.set(p.bellId(), p.on())));
        ClientPlayNetworking.registerGlobalReceiver(ThumpPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            var pl = ctx.client().player;
            if (pl != null) Shake.crash(Math.sqrt(pl.distanceToSqr(p.x(), p.y(), p.z())), p.power());
        }));
        // the big moments: dust and chunks of ground, splashes and waves, flashes, clouds, shake and late sound
        net.jj.hollowbell.fx.client.BigFxClient.init(() -> net.jj.hollowbell.HollowbellConfig.V.screenShake, () -> net.jj.hollowbell.HollowbellConfig.V.soundVolume,
                () -> net.jj.hollowbell.HollowbellConfig.V.simpleFarAway, () -> net.jj.hollowbell.HollowbellConfig.V.bigEffects);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> { CodexScreen.forgetEverything(); BeingHim.set(-1, false); BellSounds.clear(); FightMusic.clear(); net.jj.hollowbell.solid.Solid.forgetAll(true); net.jj.hollowbell.solid.client.SolidClient.clear(); });
        ArmourPowerKey.init();
        ClientTickEvents.END_CLIENT_TICK.register(c -> { Shake.tick(); BeingHim.tick(c); BellSounds.tick(c); ArmourPowerKey.tick(c); FightMusic.tick(c); net.jj.hollowbell.solid.client.SolidClient.tick(c); });
        // he's ticked even when the game would skip him for having his middle too far off (see tickIfSkipped)
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_WORLD_TICK.register(level -> {
            for (var e : level.entitiesForRendering()) if (e instanceof net.jj.hollowbell.entity.HollowbellEntity h) h.tickIfSkipped();
        });
        HudRenderCallback.EVENT.register((g, t) -> { BeingHim.hud(g); ArmourPowerKey.hud(g); });
        // the glass goes on after every other creature, so whatever he has caught shows through it
        // far-off stand-ins first, then all the glass (theirs too), back to front
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> { FarSightClient.render(ctx); BellRenderer.drawGlass(); LootBeamsClient.render(ctx); });
        ClientPlayNetworking.registerGlobalReceiver(net.jj.hollowbell.net.LootBeamsPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> LootBeamsClient.receive(p)));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(LootBeamsClient::clear));
        ClientPlayNetworking.registerGlobalReceiver(net.jj.hollowbell.net.FarSightPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> FarSightClient.receive(p)));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(FarSightClient::tick);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(FarSightClient::clear));
        WorldRenderEvents.START.register(ctx -> { BellRenderer.forgetFrame(); LootBeamsClient.startFrame(); });
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "model_reload"); }
            @Override public void onResourceManagerReload(ResourceManager rm) { BellMeshes.INSTANCE.invalidate(); }
        });
        net.jj.hollowbell.client.dev.AutoTest.init();
        // /hollowbell detail [on|off]: the server passes it on, and the setting is kept on this computer only.
        // (It used to be a command of the client's own, but then the client took every /hollowbell command for its
        // own and never sent the rest to the server.)
        ClientPlayNetworking.registerGlobalReceiver(net.jj.hollowbell.net.DetailPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            if (p.what() == net.jj.hollowbell.net.DetailPayload.FAR) {
                // how far off he can be seen: kept in this game's own settings too (on a server the server's decides who is sent)
                net.jj.hollowbell.HollowbellConfig.V.farSightBlocks = Math.max(0, Math.min(4096, p.arg()));
                net.jj.hollowbell.HollowbellConfig.save();
                return;
            }
            if (p.what() == net.jj.hollowbell.net.DetailPayload.ON) net.jj.hollowbell.Detail.set(true);
            else if (p.what() == net.jj.hollowbell.net.DetailPayload.OFF) net.jj.hollowbell.Detail.set(false);
            String key = p.what() == net.jj.hollowbell.net.DetailPayload.ON ? "command.hollowbell.detail_on"
                    : p.what() == net.jj.hollowbell.net.DetailPayload.OFF ? "command.hollowbell.detail_off"
                    : net.jj.hollowbell.Detail.on() ? "command.hollowbell.detail_is_on" : "command.hollowbell.detail_is_off";
            if (ctx.client().player != null) ctx.client().player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), false);
        }));
    }
}
