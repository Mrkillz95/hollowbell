package net.jj.hollowbell.client;

import java.util.Map;
import java.util.WeakHashMap;
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
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.Detail;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModBlocks;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.client.render.BellArmorRenderer;
import net.jj.hollowbell.client.render.BellMeshes;
import net.jj.hollowbell.client.render.BellRenderer;
import net.jj.hollowbell.client.render.BellingRenderer;
import net.jj.hollowbell.client.render.LootCacheRenderer;
import net.jj.hollowbell.client.render.ShotRenderer;
import net.jj.hollowbell.client.render.StingerHookRenderer;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Shot;
import net.jj.hollowbell.fx.client.BigFxClient;
import net.jj.hollowbell.fx.client.MoveFx;
import net.jj.hollowbell.item.CodexItem;
import net.jj.hollowbell.net.BeingHimPayload;
import net.jj.hollowbell.net.DetailPayload;
import net.jj.hollowbell.net.FarSightPayload;
import net.jj.hollowbell.net.GlassBellPayload;
import net.jj.hollowbell.net.LookMarkPayload;
import net.jj.hollowbell.net.LootBeamsPayload;
import net.jj.hollowbell.net.MoodPayload;
import net.jj.hollowbell.net.SafeListPayload;
import net.jj.hollowbell.net.ThumpPayload;
import net.jj.hollowbell.solid.Solid;
import net.jj.hollowbell.solid.client.SolidClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class HollowbellClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.HOLLOWBELL, BellRenderer::new);
        EntityRendererRegistry.register(ModEntities.BELLING, BellingRenderer::new);
        BellArmorRenderer.register(); // bell glass armor drawn see-through
        EntityRendererRegistry.register(ModEntities.SEAT, NoopRenderer::new);
        EntityRendererRegistry.register(ModEntities.STINGER_HOOK, StingerHookRenderer::new);
        EntityRendererRegistry.register(ModEntities.SHOT, ShotRenderer::new);
        Shot.eggFx = ShotRenderer::fx;
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CROWN, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(), ModBlocks.TENDRIL_GLASS, ModBlocks.BELL_SHARD);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.LOOT_CACHE, RenderType.cutout());
        BlockEntityRenderers.register(ModBlocks.LOOT_CACHE_ENTITY, LootCacheRenderer::new);
        CodexItem.openPages = () -> {
            var mc = Minecraft.getInstance();
            if (mc.screen == null) mc.setScreen(new CodexScreen());
        };

        ClientPlayNetworking.registerGlobalReceiver(
                SafeListPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> CodexScreen.safeList(p)));
        ClientPlayNetworking.registerGlobalReceiver(
                LookMarkPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> LookAim.mark(p.entityId())));
        ClientPlayNetworking.registerGlobalReceiver(
                MoodPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> CodexScreen.mood(p)));
        ClientPlayNetworking.registerGlobalReceiver(
                BeingHimPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> BeingHim.set(p.bellId(), p.on())));
        ClientPlayNetworking.registerGlobalReceiver(
                ThumpPayload.TYPE,
                (p, ctx) -> ctx.client().execute(() -> {
                    var pl = ctx.client().player;
                    if (pl != null) Shake.crash(Math.sqrt(pl.distanceToSqr(p.x(), p.y(), p.z())), p.power());
                }));
        // the big moments: dust and chunks of ground, splashes and waves, flashes, clouds, shake and late sound
        BigFxClient.init(
                () -> BellConfig.V.screenShake,
                () -> BellConfig.V.soundVolume,
                () -> BellConfig.V.simpleFarAway,
                () -> BellConfig.V.bigEffects);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            CodexScreen.forgetEverything();
            BeingHim.set(-1, false);
            BellSounds.clear();
            FightMusic.clear();
            Solid.forgetAll(true);
            SolidClient.clear();
            MoveFx.clear();
        });
        ArmourPowerKey.init();
        LookKey.init();
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            Shake.tick();
            BeingHim.tick(c);
            BellSounds.tick(c);
            ArmourPowerKey.tick(c);
            beats(c);
            LookKey.tick(c);
            FightMusic.tick(c);
            SolidClient.tick(c);
            MoveFx.tick(c);
        });
        // what his moves look like beyond his body: rings of sound, trails, goo, beams
        WorldRenderEvents.AFTER_TRANSLUCENT.register(MoveFx::render);
        // he's ticked even when the game would skip him for having his middle too far off (see tickIfSkipped)
        ClientTickEvents.END_WORLD_TICK.register(level -> {
            for (var e : level.entitiesForRendering()) if (e instanceof HollowbellEntity h) h.tickIfSkipped();
        });
        HudRenderCallback.EVENT.register((g, t) -> {
            BeingHim.hud(g);
            ArmourPowerKey.hud(g);
        });
        // the glass goes on after every other creature, so whatever he has caught shows through it
        // far-off stand-ins first, then all the glass (theirs too), back to front
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            FarSightClient.render(ctx);
            BellRenderer.drawGlass();
            LootBeamsClient.render(ctx);
            GlassBellClient.render(ctx);
        });
        ClientPlayNetworking.registerGlobalReceiver(
                GlassBellPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> GlassBellClient.receive(p)));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(GlassBellClient::clear));
        ClientPlayNetworking.registerGlobalReceiver(
                LootBeamsPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> LootBeamsClient.receive(p)));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(LootBeamsClient::clear));
        ClientPlayNetworking.registerGlobalReceiver(
                FarSightPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> FarSightClient.receive(p)));
        ClientTickEvents.END_CLIENT_TICK.register(FarSightClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(FarSightClient::clear));
        WorldRenderEvents.START.register(ctx -> {
            BellRenderer.forgetFrame();
            LootBeamsClient.startFrame();
        });
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "model_reload");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager rm) {
                        BellMeshes.INSTANCE.invalidate();
                    }
                });
        // /hollowbell detail [on|off]: the server passes it on, and the setting is kept on this computer only (a
        // client command of the same name would take every /hollowbell command and never send the rest on)
        ClientPlayNetworking.registerGlobalReceiver(
                DetailPayload.TYPE,
                (p, ctx) -> ctx.client().execute(() -> {
                    if (p.what() == DetailPayload.FAR) {
                        // how far off he can be seen: kept in this game's own settings too (on a server the server's
                        // decides who is sent)
                        BellConfig.V.farSightBlocks = Math.max(0, Math.min(4096, p.arg()));
                        BellConfig.save();
                        return;
                    }
                    if (p.what() == DetailPayload.AT) {
                        BellConfig.V.simpleFarAwayAt = Math.max(20, Math.min(4000, p.arg()));
                        BellConfig.save();
                        if (ctx.client().player != null)
                            ctx.client()
                                    .player
                                    .displayClientMessage(
                                            Component.translatable(
                                                    "command.hollowbell.detail_at", BellConfig.V.simpleFarAwayAt),
                                            false);
                        return;
                    }
                    if (p.what() == DetailPayload.ON) Detail.set(true);
                    else if (p.what() == DetailPayload.OFF) Detail.set(false);
                    String key = p.what() == DetailPayload.ON
                            ? "command.hollowbell.detail_on"
                            : p.what() == DetailPayload.OFF
                                    ? "command.hollowbell.detail_off"
                                    : Detail.on()
                                            ? "command.hollowbell.detail_is_on"
                                            : "command.hollowbell.detail_is_off";
                    if (ctx.client().player != null)
                        ctx.client()
                                .player
                                .displayClientMessage(Component.translatable(key, BellConfig.V.simpleFarAwayAt), false);
                }));
    }

    private static final Map<HollowbellEntity, Long> LAST_BEAT = new WeakHashMap<>();

    /** each new swim beat of one near you gives the camera a slight nudge */
    private static void beats(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof HollowbellEntity h)) continue;
            long at = h.pulseStartedAt();
            Long seen = LAST_BEAT.put(h, at);
            if (seen != null && seen != at && mc.level.getGameTime() - at < 10)
                Shake.beat(Math.sqrt(h.distanceToSqr(mc.player)), h.bellScale());
        }
    }
}
