package com.example.bondexpanded.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.Screen;

public final class BondExpandedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        try {
            ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                    GrimoireGuiHandler.attach(screen));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
