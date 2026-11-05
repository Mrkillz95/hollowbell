package net.jj.giants.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.giants.GuideItem;
import net.jj.giants.client.admin.AdminBookClient;
import net.jj.giants.client.perf.PerfClient;
import net.jj.giants.net.ToldPayload;
import net.minecraft.client.Minecraft;

public class GuideClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GuideItem.opener = () -> Minecraft.getInstance().setScreen(new GuideScreen());
        ClientPlayNetworking.registerGlobalReceiver(ToldPayload.TYPE, (p, ctx) -> Live.told(p));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> Live.forget());
        // (lagfix) too little memory for the giants: said once a game, on joining a world
        ClientPlayConnectionEvents.JOIN.register((h, sender, mc) -> mc.execute(() -> MemoryNote.joined(mc)));
        PerfClient.init();
        AdminBookClient.init();
        net.jj.giants.client.players.PlayersClient.init();
        // (Part 9) Weather Gods: a giant's weather round the player
        WeatherClient.init();
    }
}
