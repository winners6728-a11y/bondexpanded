package com.example.bondexpanded.util;

import com.example.bondexpanded.BondExpanded;
import com.example.bondexpanded.network.BondControlPacket;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BondExpandedServerState {

    private static final Map<UUID, Long> healCooldown = new HashMap<>();
    private static final Map<UUID, Long> speedCooldown = new HashMap<>();
    private static final Map<UUID, Long> howlCooldown = new HashMap<>();
    private static final Map<UUID, Long> releaseConfirm = new HashMap<>();
    private static final Map<UUID, String> activeCommands = new HashMap<>();

    private BondExpandedServerState() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                tickProximity(server);
            } catch (Exception e) {
                BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            }
        });
    }

    public static void handleCommand(ServerPlayerEntity owner, String command) {
        try {
            ServerPlayerEntity pet = PetHelper.getPet(owner);

            if (pet == null) {
                owner.sendMessage(Text.literal("У вас нет питомца"), false);
                return;
            }

            if (!pet.getWorld().getRegistryKey().equals(owner.getWorld().getRegistryKey())) {
                owner.sendMessage(Text.literal("Питомец находится в другом мире"), false);
                return;
            }

            switch (command) {
                case "come", "attack", "hunt", "guard" -> sendControlToPet(owner, pet, command);
                case "stop" -> sendControlToPet(owner, pet, "stop");
                case "heal" -> applyHeal(owner, pet);
                case "speed" -> applySpeed(owner, pet);
                case "info" -> showInfo(owner, pet);
                case "release" -> releaseBond(owner, pet);
                case "howl" -> doHowl(owner, pet);
                default -> owner.sendMessage(Text.literal("Неизвестная команда"), false);
            }
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void sendControlToPet(ServerPlayerEntity owner, ServerPlayerEntity pet, String command) {
        try {
            activeCommands.put(pet.getUuid(), command);

            ServerPlayNetworking.send(
                    pet,
                    new BondControlPacket(command, owner.getUuidAsString())
            );

            String msg = switch (command) {
                case "come" -> "Питомец идёт к вам";
                case "attack" -> "Питомец атакует";
                case "hunt" -> "Питомец охотится";
                case "guard" -> "Питомец охраняет";
                case "stop" -> "Питомец остановлен";
                default -> "Команда отправлена";
            };

            owner.sendMessage(Text.literal(msg), false);

        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка отправки: " + e.getMessage(), e);
        }
    }

    private static void applyHeal(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        UUID uuid = pet.getUuid();
        long now = System.currentTimeMillis();
        long last = healCooldown.getOrDefault(uuid, 0L);
        long remaining = 60000L - (now - last);

        if (remaining > 0) {
            owner.sendMessage(Text.literal("Ещё не готово: " + (remaining / 1000) + " сек"), false);
            return;
        }

        pet.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1, true, true, true));
        healCooldown.put(uuid, now);
        owner.sendMessage(Text.literal("Питомец вылечен"), false);
    }

    private static void applySpeed(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        UUID uuid = pet.getUuid();
        long now = System.currentTimeMillis();
        long last = speedCooldown.getOrDefault(uuid, 0L);
        long remaining = 60000L - (now - last);

        if (remaining > 0) {
            owner.sendMessage(Text.literal("Ещё не готово: " + (remaining / 1000) + " сек"), false);
            return;
        }

        pet.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 600, 1, true, true, true));
        speedCooldown.put(uuid, now);
        owner.sendMessage(Text.literal("Питомец ускорен"), false);
    }

    private static void showInfo(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        try {
            String name = PetHelper.getPetName(pet);
            String stage = PetHelper.getStage(pet);
            float hp = pet.getHealth();
            String active = activeCommands.getOrDefault(pet.getUuid(), "нет");

            owner.sendMessage(Text.literal("=== Питомец ==="), false);
            owner.sendMessage(Text.literal("Имя: " + name), false);
            owner.sendMessage(Text.literal("Стадия: " + stage), false);
            owner.sendMessage(Text.literal("HP: " + hp), false);
            owner.sendMessage(Text.literal("Команда: " + active), false);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void releaseBond(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        UUID uuid = pet.getUuid();
        long now = System.currentTimeMillis();
        long last = releaseConfirm.getOrDefault(uuid, 0L);

        if (now - last > 10000L) {
            releaseConfirm.put(uuid, now);
            owner.sendMessage(Text.literal("Нажмите ещё раз в течение 10 секунд для подтверждения"), false);
            return;
        }

        if (PetHelper.releaseBond(pet)) {
            owner.sendMessage(Text.literal("Связь разорвана"), false);
        } else {
            owner.sendMessage(Text.literal("Не удалось разорвать связь"), false);
        }
        releaseConfirm.remove(uuid);
    }

    private static void doHowl(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        UUID uuid = pet.getUuid();
        long now = System.currentTimeMillis();
        long last = howlCooldown.getOrDefault(uuid, 0L);
        long remaining = 30000L - (now - last);

        if (remaining > 0) {
            owner.sendMessage(Text.literal("Ещё не готово: " + (remaining / 1000) + " сек"), false);
            return;
        }

        ServerWorld world = pet.getServerWorld();
        world.playSound(null, pet.getBlockPos(), SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.PLAYERS, 1.0F, 1.0F);

        Box box = pet.getBoundingBox().expand(16.0D);
        List<HostileEntity> hostiles = world.getEntitiesByClass(HostileEntity.class, box, e -> e.isAlive());

        for (HostileEntity hostile : hostiles) {
            hostile.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, true, true, true));
        }

        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 0, true, true, true));
        howlCooldown.put(uuid, now);
        owner.sendMessage(Text.literal("Питомец воет!"), false);
    }

    private static void tickProximity(net.minecraft.server.MinecraftServer server) {
        // пассивный эффект рядом — обновляем раз в 100 тиков
        if (server.getTicks() % 100 != 0) return;

        for (ServerPlayerEntity owner : server.getPlayerManager().getPlayerList()) {
            ServerPlayerEntity pet = PetHelper.getPet(owner);
            if (pet == null) continue;

            if (!owner.getWorld().getRegistryKey().equals(pet.getWorld().getRegistryKey())) continue;
            if (owner.distanceTo(pet) > 8.0D) continue;

            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, false, true));
            pet.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, false, true));
        }
    }
}
