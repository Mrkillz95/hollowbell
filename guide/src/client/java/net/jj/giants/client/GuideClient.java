package net.jj.giants.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.giants.GuideItem;
import net.jj.giants.net.ToldPayload;
import net.minecraft.client.Minecraft;

public class GuideClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GuideItem.opener = () -> Minecraft.getInstance().setScreen(new GuideScreen());
        ClientPlayNetworking.registerGlobalReceiver(ToldPayload.TYPE, (p, ctx) -> Live.told(p));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> Live.forget());
        // development only: -Djj_giants.autotest=<script> takes pictures, then quits. Never in a normal game.
        if (System.getProperty("jj_giants.autotest") != null) net.jj.giants.client.dev.AutoTest.init();
    }
}
