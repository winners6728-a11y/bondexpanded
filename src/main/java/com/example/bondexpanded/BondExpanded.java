package com.example.bondexpanded;

import com.example.bondexpanded.command.BondExpandedCommands;
import com.example.bondexpanded.network.BondAttackPacket;
import com.example.bondexpanded.network.BondExpandedPacket;
import com.example.bondexpanded.network.BondGiveConfirmPacket;
import com.example.bondexpanded.util.BondExpandedServerState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
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
            ServerPlayNetworking.registerGlobalReceiver(
                    BondAttackPacket.TYPE,
                    (packet, player, responseSender) -> {
                        if (player.getServer() != null) {
                            player.getServer().execute(() -> handleAttack(player, packet));
                        }
                    }
            );
        } catch (Exception e) {
            LOGGER.error("Ошибка регистрации пакета атаки: " + e.getMessage(), e);
        }

        try {
            ServerPlayNetworking.registerGlobalReceiver(
                    BondGiveConfirmPacket.TYPE,
                    (packet, player, responseSender) -> {
                        if (player.getServer() != null) {
                            player.getServer().execute(() ->
                                    BondExpandedServerState.handleGiveConfirm(player, packet.slots())
                            );
                        }
                    }
            );
        } catch (Exception e) {
            LOGGER.error("Ошибка регистрации пакета give: " + e.getMessage(), e);
        }

        try {
            BondExpandedServerState.register();
        } catch (Exception e) {
            LOGGER.error("Ошибка регистрации server state: " + e.getMessage(), e);
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

    private static void handleAttack(ServerPlayerEntity player, BondAttackPacket packet) {
        try {
            if (!(player.getWorld() instanceof ServerWorld world)) return;

            Entity entity = world.getEntity(packet.targetUuid());
            if (!(entity instanceof LivingEntity target)) return;
            if (!target.isAlive() || target.isRemoved()) return;

            double distance = player.distanceTo(target);
            if (distance > 5.0D) return;

            target.damage(player.getDamageSources().playerAttack(player), 5.0F);
            player.swingHand(Hand.MAIN_HAND);

        } catch (Exception e) {
            LOGGER.error("Ошибка атаки на сервере: " + e.getMessage(), e);
        }
    }
}
