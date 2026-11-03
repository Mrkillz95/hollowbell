package net.jj.giants;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminBookItem;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.net.LiveNews;
import net.jj.giants.perf.PerfServer;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Giants Guide. The giants themselves are their own mods, nested in the same jar. */
public class GiantsGuideMod implements ModInitializer {
    public static final String ID = "jj_giants";
    public static final Logger LOG = LoggerFactory.getLogger("Giants Guide");
    public static final Item GUIDE =
            new GuideItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    @Override
    public void onInitialize() {
        GuideConfig.load();
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ID, "giants_guide"), GUIDE);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> e.accept(GUIDE));
        // (Part 5) joining: the starting kit, My Giant's pick, fight music, the challenges (Players)
        net.jj.giants.players.Players.register();
        // (Part 7) the admin fun and world tools
        net.jj.giants.fun.Fun.register();
        CommandRegistrationCallback.EVENT.register((d, reg, env) ->
                d.register(Commands.literal("giantsguide").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    give(p);
                    c.getSource().sendSuccess(() -> Component.translatable("message.jj_giants.given"), false);
                    return 1;
                })));
        LiveNews.register();
        PerfServer.register();
        // the admin settings: /giants admin, the world's values for every giant, the admin book (ADMIN_BRIDGE.md)
        AdminBookItem.register();
        AdminServer.register();
        AdminBook.register();
        // (Part 6) the server tools: pause, events, schedules, areas, map, stats, alerts, respawns, quiet, profiles,
        // pack mode (ADMIN_BRIDGE.md §22)
        net.jj.giants.tools.Tools.register();
        // (Part 8) the new systems
        net.jj.giants.systems.Systems.register();
        LOG.info(
                "Giants Guide loaded: {} of {} giants installed",
                Giants.installed().size(),
                Giants.ALL.size());
    }

    /** one Giants Guide into their pack (or at their feet if it's full) */
    public static void give(ServerPlayer p) {
        ItemStack book = new ItemStack(GUIDE);
        if (!p.getInventory().add(book)) p.drop(book, false);
    }
}
