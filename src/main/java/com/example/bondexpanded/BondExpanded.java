package com.example.bondexpanded;

import com.example.bondexpanded.command.BondExpandedCommands;
import com.example.bondexpanded.network.BondExpandedPacket;
import com.example.bondexpanded.passive.ProximityEffectHandler;
import com.example.bondexpanded.util.BondExpandedServerState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BondExpanded implements ModInitializer {

    public static final String MOD_ID = "bondexpanded";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        try {
            ServerPlayNetworking.registerGlobalReceiver(
                    BondExpandedPacket.TYPE,
                    (packet, player, responseSender) -> {
                        if (player.getServer() != null) {
                            player.getServer().execute(() ->
                                    BondExpandedServerState.handleCommand(player, packet.commandId())
                            );
                        }
                    }
            );
        } catch (Exception e) {
            LOGGER.error("Ошибка регистрации пакета: " + e.getMessage(), e);
        }

        try {
            BondExpandedServerState.register();
        } catch (Exception e) {
            LOGGER.error("Ошибка регистрации server state: " + e.getMessage(), e);
        }

        try {
            ProximityEffectHandler.register();
        } catch (Exception e) {
            LOGGER.error("Ошибка регистрации эффектов: " + e.getMessage(), e);
        }

        try {
            CommandRegistrationCallback.EVENT.register(
                    (dispatcher, registryAccess, environment) ->
                            BondExpandedCommands.register(dispatcher)
            );
        } catch (Exception e) {
            LOGGER.error("Ошибка регистрации команд: " + e.getMessage(), e);
        }
    }
}
