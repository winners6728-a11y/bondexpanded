package com.example.bondexpanded.passive;

import com.example.bondexpanded.BondExpanded;
import com.example.bondexpanded.util.PetHelper;
import com.example.bondexpanded.util.BondExpandedServerState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ProximityEffectHandler {
    private ProximityEffectHandler() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                BondExpandedServerState.tick((net.minecraft.server.world.ServerWorld) player.getWorld());
            }
        });
    }

    private static void tick(MinecraftServer server) {
        try {
            if (server.getTicks() % 100 != 0) {
                return;
            }

            for (ServerPlayerEntity owner : server.getPlayerManager().getPlayerList()) {
                ServerPlayerEntity pet = PetHelper.getPet(owner);
                if (pet == null || pet.getWorld() != owner.getWorld()) {
                    continue;
                }

                if (pet.squaredDistanceTo(owner) < 64.0) {
                    owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));
                    pet.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));
                }
            }
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }
}
