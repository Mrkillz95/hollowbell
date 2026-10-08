package net.jj.hollowbell;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.jj.hollowbell.command.HollowbellCommand;
import net.jj.hollowbell.entity.Belling;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.net.BeingHimPayload;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.jj.hollowbell.net.DrivePayload;
import net.jj.hollowbell.net.HitPayload;
import net.jj.hollowbell.net.MoodPayload;
import net.jj.hollowbell.net.SafeDropPayload;
import net.jj.hollowbell.net.SafeListPayload;
import net.jj.hollowbell.net.ThumpPayload;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HollowbellMod implements ModInitializer {
    public static final String MOD_ID = "hollowbell";
    public static final Logger LOG = LoggerFactory.getLogger("Hollowbell");
    public static final boolean IN_TESTS = System.getProperty("fabric-api.gametest") != null;

    @Override
    public void onInitialize() {
        HollowbellConfig.load();
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
        PayloadTypeRegistry.playC2S().register(net.jj.hollowbell.net.ArmourPowerPayload.TYPE, net.jj.hollowbell.net.ArmourPowerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SafeListPayload.TYPE, SafeListPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MoodPayload.TYPE, MoodPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.jj.hollowbell.net.LookMarkPayload.TYPE, net.jj.hollowbell.net.LookMarkPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.jj.hollowbell.net.DetailPayload.TYPE, net.jj.hollowbell.net.DetailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BeingHimPayload.TYPE, BeingHimPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ThumpPayload.TYPE, ThumpPayload.CODEC);
        net.jj.hollowbell.fx.BigFx.register();
        PayloadTypeRegistry.playS2C().register(net.jj.hollowbell.net.FarSightPayload.TYPE, net.jj.hollowbell.net.FarSightPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.jj.hollowbell.net.LootBeamsPayload.TYPE, net.jj.hollowbell.net.LootBeamsPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CodexPayload.TYPE, (pay, ctx) -> ctx.server().execute(() -> CodexOrders.handle(ctx.player(), pay)));
        ServerPlayNetworking.registerGlobalReceiver(SafeDropPayload.TYPE, (pay, ctx) -> ctx.server().execute(() -> CodexOrders.dropFromSafeList(ctx.player(), pay.id())));
        ServerPlayNetworking.registerGlobalReceiver(net.jj.hollowbell.net.ArmourPowerPayload.TYPE,
                (pay, ctx) -> ctx.server().execute(() -> net.jj.hollowbell.item.BellPower.use(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(HitPayload.TYPE, (pay, ctx) -> ctx.server().execute(() -> {
            ServerPlayer p = ctx.player();
            if (p.serverLevel().getEntity(pay.bellId()) instanceof HollowbellEntity h && !p.isSpectator()) h.hitBy(p, pay.bone());
        }));
        ServerPlayNetworking.registerGlobalReceiver(DrivePayload.TYPE, (pay, ctx) -> ctx.server().execute(() -> {
            ServerPlayer p = ctx.player();
            HollowbellEntity him = null;
            for (HollowbellEntity m : p.serverLevel().getEntities(ModEntities.HOLLOWBELL, m -> m.rider() == p)) { him = m; break; }
            if (him == null) return;
            switch (pay.what()) {
                case DrivePayload.LEAVE -> him.setMeDown(p);
                case DrivePayload.ATTACK -> CodexOrders.moveFromCrown(p, him, pay.arg());
                default -> him.drive(p, pay.forward(), pay.strafe(), pay.yaw(), pay.arg());
            }
        }));

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(e -> {
            e.accept(ModItems.CALM_EGG); e.accept(ModItems.HUNTING_EGG); e.accept(ModItems.GUARDIAN_EGG); e.accept(ModItems.SMALL_EGG); e.accept(ModItems.BELLING_EGG);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(e -> {
            e.accept(ModItems.STINGER); e.accept(ModItems.BELL_HELMET); e.accept(ModItems.BELL_CHESTPLATE); e.accept(ModItems.BELL_LEGGINGS); e.accept(ModItems.BELL_BOOTS);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(e -> { e.accept(ModItems.POD); e.accept(ModItems.BELL_GLASS); });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> { e.accept(ModItems.CODEX); e.accept(ModItems.FINDER); });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(e -> e.accept(ModItems.CROWN));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.NATURAL_BLOCKS).register(e -> {
            e.accept(ModItems.BELL_CALCITE); e.accept(ModItems.TENDRIL_GLASS); e.accept(ModItems.BELL_SHARD); e.accept(ModItems.SPORE_MOSS);
        });

        net.jj.hollowbell.item.BellArmorItem.init();

        CommandRegistrationCallback.EVENT.register((d, access, env) -> {
            HollowbellCommand.register(d);
            // one mod of the five registers /giants for everyone (see GiantsCommand)
            if (net.jj.hollowbell.command.GiantsCommand.elected()) net.jj.hollowbell.command.GiantsCommand.register(d);
        });
        ServerTickEvents.END_SERVER_TICK.register(CodexOrders::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(net.jj.hollowbell.world.Away::tick);
        ServerTickEvents.END_SERVER_TICK.register(net.jj.hollowbell.world.WorldOne::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(net.jj.hollowbell.world.FarSight::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(net.jj.hollowbell.world.LootBeams::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(net.jj.hollowbell.world.FarOrders::tick);
        ServerTickEvents.END_SERVER_TICK.register(net.jj.hollowbell.world.NoWait::tick);
        ServerTickEvents.END_SERVER_TICK.register(net.jj.hollowbell.world.Painter::tick);
        ServerTickEvents.END_SERVER_TICK.register(net.jj.hollowbell.world.GroundCheck::tick);
        ServerTickEvents.END_WORLD_TICK.register(net.jj.hollowbell.world.KeepAwake::tick);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (!(entity instanceof HollowbellEntity h)) return;
            h.clearBars();
            // the world's own one going to sleep with his chunk: note where, for the finder
            if (h.isWorldOne()) net.jj.hollowbell.world.WorldOne.get(world.getServer()).seen(h);
            // put away with his chunk (not out of the world as a sum, not dead): noted, so an order can still reach him
            var why = h.getRemovalReason();
            var away = net.jj.hollowbell.world.Away.get(world.getServer());
            // (the game stops tracking him before it puts him away, with no reason given yet)
            if (!h.steppedOut() && !h.isDeadOrDying() && (why == null || why == Entity.RemovalReason.UNLOADED_TO_CHUNK || why == Entity.RemovalReason.UNLOADED_WITH_PLAYER))
                away.noteParked(h);
            else away.unpark(h.getUUID());
        });
        // every one still loaded is written down where he is as the server stops: the game saves him with his chunk
        // without putting him away, and after a restart nothing may load that chunk again
        ServerLifecycleEvents.SERVER_STOPPING.register(net.jj.hollowbell.world.Away::noteAllLoaded);
        // the cap: only for freshly made ones. A saved one loading with its chunk is nobody arriving.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof HollowbellEntity h) {
                net.jj.hollowbell.world.Away.get(world.getServer()).unpark(h.getUUID());
                net.jj.hollowbell.world.WorldOne.joined(h, world);
            }
        });
        // his ground: chosen as the overworld is made, before any of its land, so the world makes that land as his
        net.jj.hollowbell.world.BellGen.init();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.dimension() == net.minecraft.world.level.Level.OVERWORLD) net.jj.hollowbell.world.WorldOne.worldLoaded(world);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { CodexOrders.forgetEverything(); net.jj.hollowbell.world.BellGen.forget(); net.jj.hollowbell.world.FarOrders.forget(); net.jj.hollowbell.world.GroundCheck.forget(); net.jj.hollowbell.solid.Solid.forgetAll(false); });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CodexOrders.forgetPlayer(handler.getPlayer().getUUID()));

        BellRig rig = BellRig.get();
        BellModel.preload();
        net.jj.hollowbell.entity.HollowSolid.preload();
        LOG.info("Hollowbell is ready: {} bones, {} strands, {} pods, {} egg clumps", rig.boneCount(), rig.strands.length,
                rig.pods.length, rig.eggs.length);
    }

    /** gives one of the mod's advancements */
    public static void award(ServerPlayer p, String name) {
        var adv = p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(MOD_ID, name));
        if (adv == null) return;
        var progress = p.getAdvancements().getOrStartProgress(adv);
        for (String c : progress.getRemainingCriteria()) p.getAdvancements().award(adv, c);
    }
}
