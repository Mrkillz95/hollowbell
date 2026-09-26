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
        PayloadTypeRegistry.playS2C().register(SafeListPayload.TYPE, SafeListPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MoodPayload.TYPE, MoodPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.jj.hollowbell.net.DetailPayload.TYPE, net.jj.hollowbell.net.DetailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BeingHimPayload.TYPE, BeingHimPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ThumpPayload.TYPE, ThumpPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CodexPayload.TYPE, (pay, ctx) -> ctx.server().execute(() -> CodexOrders.handle(ctx.player(), pay)));
        ServerPlayNetworking.registerGlobalReceiver(SafeDropPayload.TYPE, (pay, ctx) -> ctx.server().execute(() -> CodexOrders.dropFromSafeList(ctx.player(), pay.id())));
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
                case DrivePayload.LEAVE -> him.dropRider();
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
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> e.accept(ModItems.CODEX));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(e -> e.accept(ModItems.CROWN));

        CommandRegistrationCallback.EVENT.register((d, access, env) -> HollowbellCommand.register(d));
        ServerTickEvents.END_SERVER_TICK.register(CodexOrders::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(net.jj.hollowbell.world.Away::tick);
        ServerTickEvents.END_WORLD_TICK.register(net.jj.hollowbell.world.KeepAwake::tick);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> { if (entity instanceof HollowbellEntity h) h.clearBars(); });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> { if (entity instanceof HollowbellEntity h) HollowbellCommand.keepToTheLimit(h, world); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> CodexOrders.forgetEverything());

        BellRig rig = BellRig.get();
        BellModel.preload();
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
