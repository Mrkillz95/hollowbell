package net.jj.mountain;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.jj.mountain.command.MountainCommand;
import net.jj.mountain.entity.HeartEntity;
import net.jj.mountain.entity.MountainEntity;
import net.jj.mountain.entity.MountainPart;
import net.jj.mountain.innards.Innards;
import net.jj.mountain.rig.MountainRig;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MountainMod implements ModInitializer {
    public static final String MODID = "mountain_breathes";
    public static final Logger LOG = LoggerFactory.getLogger("The Mountain That Breathes");

    @Override
    public void onInitialize() {
        MountainConfig.load();
        ModBlocks.init();
        ModEntities.init();
        ModItems.init();
        ModSounds.init();
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(net.jj.mountain.net.StrugglePayload.TYPE, net.jj.mountain.net.StrugglePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(net.jj.mountain.net.CodexPayload.TYPE, net.jj.mountain.net.CodexPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(net.jj.mountain.net.DrivePayload.TYPE, net.jj.mountain.net.DrivePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
                .register(net.jj.mountain.net.InsideHimPayload.TYPE, net.jj.mountain.net.InsideHimPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
                .register(net.jj.mountain.net.SafeListPayload.TYPE, net.jj.mountain.net.SafeListPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
                .register(net.jj.mountain.net.MoodPayload.TYPE, net.jj.mountain.net.MoodPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
                .register(net.jj.mountain.net.StormPayload.TYPE, net.jj.mountain.net.StormPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(net.jj.mountain.net.SafeDropPayload.TYPE, net.jj.mountain.net.SafeDropPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(net.jj.mountain.net.SafeDropPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    if (context.player() != null) net.jj.mountain.net.CodexOrders.dropFromSafeList(context.player(), payload.id());
                }));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(net.jj.mountain.net.DrivePayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    var pl = context.player();
                    if (pl == null) return;
                    MountainEntity him = null;
                    for (MountainEntity m : pl.serverLevel().getEntities(ModEntities.MOUNTAIN, m -> m.rider() == pl)) { him = m; break; }
                    if (him == null) return;
                    switch (payload.what()) {
                        case net.jj.mountain.net.DrivePayload.LEAVE -> { if (!him.setMeDown()) him.dropRider(); }
                        case net.jj.mountain.net.DrivePayload.ATTACK -> net.jj.mountain.net.CodexOrders.attackFromInside(pl, him, payload.arg());
                        default -> him.drive(pl, payload.forward(), payload.strafe(), payload.yaw());
                    }
                }));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(net.jj.mountain.net.CodexPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    if (context.player() != null) net.jj.mountain.net.CodexOrders.handle(context.player(), payload);
                }));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(net.jj.mountain.net.StrugglePayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    var p = context.player();
                    if (p == null || payload.presses() <= 0) return;
                    double r = 400;
                    for (MountainEntity m : p.serverLevel().getEntitiesOfClass(MountainEntity.class, p.getBoundingBox().inflate(r)))
                        if (m.held() == p) { m.struggleOut(p, payload.presses()); return; }
                }));
        FabricDefaultAttributeRegistry.register(ModEntities.MOUNTAIN, MountainEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.HEART, HeartEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.GUT_TENTACLE, net.jj.mountain.entity.inside.GutTentacle.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.GUT_LEECH, net.jj.mountain.entity.inside.GutLeech.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.WATCHER, net.jj.mountain.entity.inside.WatcherEye.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.SHADOW, net.jj.mountain.entity.ShadowOfYou.createAttributes());
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(e -> { e.accept(ModItems.CALM_EGG); e.accept(ModItems.HUNTER_EGG); e.accept(ModItems.GUARDIAN_EGG); });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.NATURAL_BLOCKS).register(e -> { e.accept(ModItems.GOO); e.accept(ModItems.HEART); });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(e -> { e.accept(ModItems.FLESH); e.accept(ModItems.GOO_BUCKET); });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> {
            e.accept(ModItems.EYE); e.accept(ModItems.HORN); e.accept(ModItems.COMPASS); e.accept(ModItems.CODEX);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(e -> {
            e.accept(ModItems.HIDE_HELMET); e.accept(ModItems.HIDE_CHESTPLATE); e.accept(ModItems.HIDE_LEGGINGS); e.accept(ModItems.HIDE_BOOTS);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> MountainCommand.register(dispatcher));
        // a melee hit on him: work out which eye (if any) the swing actually landed on
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!level.isClientSide && entity instanceof MountainPart part) part.noteAttacker(player);
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(Innards::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(net.jj.mountain.world.MountainWorld::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(net.jj.mountain.world.Crater::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(net.jj.mountain.world.Shockwave::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(net.jj.mountain.net.CodexOrders::serverTick);
        // he is put down far from anybody when he is big, so hold his chunk open until he has taken his first tick
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof MountainEntity m) {
                m.bornAt(world);            // a new one is stamped now; one coming back out of a chunk keeps its own
                if (net.jj.mountain.world.MountainWorld.keepToTheLimit(m, world)) return;
                m.forceChunks();
            }
        });
        // when his chunk unloads the game skips remove(): take his bars down so they don't get stuck on screen
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof MountainEntity m) {
                // the game skips remove() here, so this is also where his journey gets written down
                var why = m.getRemovalReason();
                if (why == null || why == net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK
                        || why == net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_WITH_PLAYER) m.parkForNow();
                m.clearBars();
                net.jj.mountain.entity.MountainCollision.forget(m);
            }
        });
        // nothing from one world carries over into the next one opened in the same game
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            net.jj.mountain.net.CodexOrders.forgetEverything();
            net.jj.mountain.world.Crater.forgetEverything();
            net.jj.mountain.world.Shockwave.forgetEverything();
            net.jj.mountain.entity.MountainCollision.forgetAll(false);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Innards.onJoin(handler.getPlayer()));
        MountainRig rig = MountainRig.get();
        LOG.info("The Mountain That Breathes is ready: {} bones, {} eyes, {} legs, {} arms", rig.boneCount(), rig.eyes.length, rig.legs.length, rig.arms.length);
    }
}
