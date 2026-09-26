package com.example.bondexpanded.passive;

import com.example.bondexpanded.BondExpanded;
import com.example.bondexpanded.util.PetHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ProximityEffectHandler {

    private static final int CHECK_INTERVAL = 100;
    private static final double MAX_DISTANCE = 8.0D;
    private static int tickCounter = 0;

    private ProximityEffectHandler() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                tickCounter++;
                if (tickCounter < CHECK_INTERVAL) return;
                tickCounter = 0;

                applyProximityEffects(server);
            } catch (Exception e) {
                BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            }
        });
    }

    private static void applyProximityEffects(MinecraftServer server) {
        try {
            for (ServerPlayerEntity owner : server.getPlayerManager().getPlayerList()) {
                ServerPlayerEntity pet = PetHelper.getPet(owner);
                if (pet == null) continue;

                if (!owner.getWorld().getRegistryKey().equals(pet.getWorld().getRegistryKey())) {
                    continue;
                }

                double distance = owner.distanceTo(pet);
                if (distance > MAX_DISTANCE) continue;

                owner.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.REGENERATION,
                        100,
                        0,
                        true,
                        false,
                        true
                ));

                pet.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.REGENERATION,
                        100,
                        0,
                        true,
                        false,
                        true
                ));
            }
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }
}
