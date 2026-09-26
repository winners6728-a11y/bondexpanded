package com.example.bondexpanded.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BondExpandedClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("Bondexpanded-Client");

    @Override
    public void onInitializeClient() {
        try {
            BondControlReceiver.register();

            ClientTickEvents.END_CLIENT_TICK.register(BondControlReceiver::tick);

            ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
                try {
                    String className = screen.getClass().getName();
                    LOGGER.info("[Bondexpanded] Открыт экран: " + className);
                    GrimoireGuiHandler.attach(screen);
                } catch (Exception e) {
                    LOGGER.error("Ошибка: " + e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Ошибка инициализации клиента: " + e.getMessage(), e);
        }
    }
}
