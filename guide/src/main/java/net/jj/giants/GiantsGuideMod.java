package net.jj.giants;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jj.giants.net.LiveNews;
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

/** JJ's Giants: the Giants Guide. The giants themselves are their own mods, nested in the same jar. */
public class GiantsGuideMod implements ModInitializer {
    public static final String ID = "jj_giants";
    public static final Logger LOG = LoggerFactory.getLogger("JJ's Giants");
    public static final Item GUIDE = new GuideItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    @Override
    public void onInitialize() {
        GuideConfig.load();
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ID, "giants_guide"), GUIDE);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> e.accept(GUIDE));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> FirstJoin.onJoin(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("giantsguide").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            give(p);
            c.getSource().sendSuccess(() -> Component.translatable("message.jj_giants.given"), false);
            return 1;
        })));
        LiveNews.register();
        LOG.info("Giants Guide ready: {} of {} giants installed", Giants.installed().size(), Giants.ALL.size());
    }

    /** one Giants Guide into their pack (or at their feet if it's full) */
    public static void give(ServerPlayer p) {
        ItemStack book = new ItemStack(GUIDE);
        if (!p.getInventory().add(book)) p.drop(book, false);
    }
}
