package net.jj.hollowbell;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.jj.hollowbell.command.BellCommand;
import net.jj.hollowbell.command.GiantsCommand;
import net.jj.hollowbell.entity.BellMoves;
import net.jj.hollowbell.entity.BellSolid;
import net.jj.hollowbell.entity.Belling;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.fx.BigFx;
import net.jj.hollowbell.item.BellArmorItem;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.item.GlassBell;
import net.jj.hollowbell.item.Powers;
import net.jj.hollowbell.net.ArmourPowerPayload;
import net.jj.hollowbell.net.BeingHimPayload;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.jj.hollowbell.net.DetailPayload;
import net.jj.hollowbell.net.DrivePayload;
import net.jj.hollowbell.net.FarSightPayload;
import net.jj.hollowbell.net.GlassBellPayload;
import net.jj.hollowbell.net.HitPayload;
import net.jj.hollowbell.net.LookMarkPayload;
import net.jj.hollowbell.net.LootBeamsPayload;
import net.jj.hollowbell.net.MoodPayload;
import net.jj.hollowbell.net.SafeDropPayload;
import net.jj.hollowbell.net.SafeListPayload;
import net.jj.hollowbell.net.ThumpPayload;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.solid.Solid;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.BellGen;
import net.jj.hollowbell.world.FarOrders;
import net.jj.hollowbell.world.FarSight;
import net.jj.hollowbell.world.GroundCheck;
import net.jj.hollowbell.world.KeepAwake;
import net.jj.hollowbell.world.LootBeams;
import net.jj.hollowbell.world.NoWait;
import net.jj.hollowbell.world.Painter;
import net.jj.hollowbell.world.RespawnPaint;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HollowbellMod implements ModInitializer {
    public static final String MOD_ID = "hollowbell";
    public static final Logger LOG = LoggerFactory.getLogger("Hollowbell");
    public static final boolean IN_TESTS = System.getProperty("fabric-api.gametest") != null;

    @Override
    public void onInitialize() {
        BellConfig.load();
        ModBlocks.init();
        ModSounds.init();
        ModEntities.init();
        ModItems.init();
        FabricDefaultAttributeRegistry.register(ModEntities.HOLLOWBELL, HollowbellEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.BELLING, Belling.createAttributes());

        PayloadTypeRegistry.playC2S().register(CodexPayload.TYPE, CodexPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SafeDropPayload.TYPE, SafeDropPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DrivePayload.TYPE, DrivePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HitPayload.TYPE, HitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ArmourPowerPayload.TYPE, ArmourPowerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SafeListPayload.TYPE, SafeListPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MoodPayload.TYPE, MoodPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LookMarkPayload.TYPE, LookMarkPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DetailPayload.TYPE, DetailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BeingHimPayload.TYPE, BeingHimPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ThumpPayload.TYPE, ThumpPayload.CODEC);
        BigFx.register();
        PayloadTypeRegistry.playS2C().register(FarSightPayload.TYPE, FarSightPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LootBeamsPayload.TYPE, LootBeamsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GlassBellPayload.TYPE, GlassBellPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(
                CodexPayload.TYPE, (pay, ctx) -> ctx.server().execute(() -> CodexOrders.handle(ctx.player(), pay)));
        ServerPlayNetworking.registerGlobalReceiver(
                SafeDropPayload.TYPE,
                (pay, ctx) -> ctx.server().execute(() -> CodexOrders.dropFromSafeList(ctx.player(), pay.id())));
        ServerPlayNetworking.registerGlobalReceiver(
                ArmourPowerPayload.TYPE,
                (pay, ctx) -> ctx.server().execute(() -> {
                    if (pay.bell()) GlassBell.ring(ctx.player());
                    else Powers.use(ctx.player());
                }));
        ServerPlayNetworking.registerGlobalReceiver(
                HitPayload.TYPE,
                (pay, ctx) -> ctx.server().execute(() -> {
                    ServerPlayer p = ctx.player();
                    if (p.serverLevel().getEntity(pay.bellId()) instanceof HollowbellEntity h && !p.isSpectator())
                        h.hitBy(p, pay.bone());
                }));
        ServerPlayNetworking.registerGlobalReceiver(
                DrivePayload.TYPE,
                (pay, ctx) -> ctx.server().execute(() -> {
                    ServerPlayer p = ctx.player();
                    HollowbellEntity him = null;
                    for (HollowbellEntity m :
                            p.serverLevel().getEntities(ModEntities.HOLLOWBELL, m -> m.rider() == p)) {
                        him = m;
                        break;
                    }
                    if (him == null) return;
                    switch (pay.what()) {
                        case DrivePayload.LEAVE -> him.setMeDown(p);
                        case DrivePayload.ATTACK -> CodexOrders.moveFromCrown(p, him, pay.arg());
                        default -> him.drive(p, pay.forward(), pay.strafe(), pay.yaw(), pay.arg());
                    }
                }));

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(e -> {
            e.accept(ModItems.CALM_EGG);
            e.accept(ModItems.HUNTING_EGG);
            e.accept(ModItems.GUARDIAN_EGG);
            e.accept(ModItems.SMALL_EGG);
            e.accept(ModItems.BELLING_EGG);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(e -> {
            e.accept(ModItems.STINGER);
            e.accept(ModItems.BELL_HELMET);
            e.accept(ModItems.BELL_CHESTPLATE);
            e.accept(ModItems.BELL_LEGGINGS);
            e.accept(ModItems.BELL_BOOTS);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(e -> {
            e.accept(ModItems.POD);
            e.accept(ModItems.BELL_GLASS);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> {
            e.accept(ModItems.CODEX);
            e.accept(ModItems.FINDER);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(e -> e.accept(ModItems.CROWN));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.NATURAL_BLOCKS).register(e -> {
            e.accept(ModItems.BELL_CALCITE);
            e.accept(ModItems.TENDRIL_GLASS);
            e.accept(ModItems.BELL_SHARD);
            e.accept(ModItems.SPORE_MOSS);
        });

        BellArmorItem.init();

        CommandRegistrationCallback.EVENT.register((d, access, env) -> {
            BellCommand.register(d);
            // one of the giant mods registers /giants for everyone (see GiantsCommand)
            if (GiantsCommand.elected()) GiantsCommand.register(d);
        });
        var late = ResourceLocation.fromNamespaceAndPath(MOD_ID, "after_giants");
        CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, late);
        CommandRegistrationCallback.EVENT.register(late, (d, access, env) -> {
            if (GiantsCommand.electedIsOld()) {
                LOG.info("The /giants command here comes from a mod that doesn't know the Hollowbell yet: this newer"
                        + " copy answers it.");
                GiantsCommand.register(d);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(CodexOrders::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(BookRule::serverTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BookRule.leaving(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(Away::tick);
        ServerTickEvents.END_SERVER_TICK.register(WorldOne::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(FarSight::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(LootBeams::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(FarOrders::tick);
        ServerTickEvents.END_SERVER_TICK.register(NoWait::tick);
        ServerTickEvents.END_SERVER_TICK.register(Painter::tick);
        ServerTickEvents.END_SERVER_TICK.register(RespawnPaint::tick);
        ServerTickEvents.END_SERVER_TICK.register(GroundCheck::tick);
        ServerTickEvents.END_WORLD_TICK.register(KeepAwake::tick);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (!(entity instanceof HollowbellEntity h)) return;
            h.clearBars();
            if (h.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION) h.leftThisWorld();
            // the world's own one going to sleep with his chunk: note where, for the finder
            if (h.isWorldOne()) WorldOne.get(world.getServer()).seen(h);
            // put away with his chunk (not out of the world as a sum, not dead): noted, so an order can still reach him
            var why = h.getRemovalReason();
            var away = Away.get(world.getServer());
            // (the game stops tracking him before it puts him away, with no reason given yet)
            if (!h.steppedOut()
                    && !h.isDeadOrDying()
                    && (why == null
                            || why == Entity.RemovalReason.UNLOADED_TO_CHUNK
                            || why == Entity.RemovalReason.UNLOADED_WITH_PLAYER)) away.noteParked(h);
            else away.unpark(h.getUUID());
        });
        // the game saves him with his chunk, which nothing may load again after a restart: note where each one is
        ServerLifecycleEvents.SERVER_STOPPING.register(Away::noteAllLoaded);
        // the cap: only for freshly made ones. A saved one loading with its chunk is nobody arriving.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            BellMoves.loadedTree(entity, world);
            if (entity instanceof HollowbellEntity h) {
                Away.get(world.getServer()).unpark(h.getUUID());
                WorldOne.joined(h, world);
            }
        });
        // his ground: chosen as the overworld is made, before any of its land, so the world makes that land as his
        BellGen.init();
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.dimension() == Level.OVERWORLD) WorldOne.worldLoaded(world);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            CodexOrders.forgetEverything();
            NoWait.forget();
            Painter.forget();
            BellGen.forget();
            FarOrders.forget();
            GroundCheck.forget();
            Solid.forgetAll(false);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CodexOrders.forgetPlayer(handler.getPlayer().getUUID()));

        BellRig rig = BellRig.get();
        BellModel.preload();
        BellSolid.preload();
        LOG.info(
                "Hollowbell loaded: {} bones, {} strands, {} pods, {} eggs",
                rig.boneCount(),
                rig.strands.length,
                rig.pods.length,
                rig.eggs.length);
    }
}
