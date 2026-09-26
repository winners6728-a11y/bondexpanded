package com.example.bondexpanded.util;

import com.example.bondexpanded.BondExpanded;
import com.example.bondexpanded.network.BondControlPacket;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BondExpandedServerState {

    private static final Map<UUID, Long> healCooldown = new HashMap<>();
    private static final Map<UUID, Long> speedCooldown = new HashMap<>();
    private static final Map<UUID, Long> howlCooldown = new HashMap<>();
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

            if (!PetHelper.hasCollar(pet)) {
                owner.sendMessage(Text.literal("На питомце нет ошейника подчинения"), false);
                return;
            }

            switch (command) {
                case "come" -> sendControl(owner, pet, "come", null);
                case "attack" -> toggle(owner, pet, "attack", null);
                case "hunt" -> toggle(owner, pet, "hunt", null);
                case "guard" -> toggle(owner, pet, "guard", null);
                case "stop" -> sendControl(owner, pet, "stop", null);
                case "heal" -> applyHeal(owner, pet);
                case "speed" -> applySpeed(owner, pet);
                case "info" -> showInfo(owner, pet);
                case "howl" -> doHowl(owner, pet);
                case "fetch" -> doFetch(owner, pet);
                case "break" -> doBreak(owner, pet);
                default -> owner.sendMessage(Text.literal("Неизвестная команда"), false);
            }
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void toggle(ServerPlayerEntity owner, ServerPlayerEntity pet, String command, BlockPos pos) {
        UUID uuid = pet.getUuid();
        if (command.equals(activeCommands.get(uuid))) {
            sendControl(owner, pet, "stop", null);
        } else {
            sendControl(owner, pet, command, pos);
        }
    }

    private static void sendControl(ServerPlayerEntity owner, ServerPlayerEntity pet, String command, BlockPos pos) {
        try {
            if ("stop".equals(command)) {
                activeCommands.remove(pet.getUuid());
            } else {
                activeCommands.put(pet.getUuid(), command);
            }

            int x = pos == null ? -1 : pos.getX();
            int y = pos == null ? -1 : pos.getY();
            int z = pos == null ? -1 : pos.getZ();

            ServerPlayNetworking.send(
                    pet,
                    new BondControlPacket(command, owner.getUuidAsString(), x, y, z)
            );

            String msg = switch (command) {
                case "come" -> "Питомец идёт к вам";
                case "attack" -> "Атака включена";
                case "hunt" -> "Охота включена";
                case "guard" -> "Охрана включена";
                case "stop" -> "Команда выключена";
                case "fetch" -> "Питомец идёт за предметом";
                case "break" -> "Питомец идёт ломать блок";
                default -> "Команда отправлена";
            };

            owner.sendMessage(Text.literal(msg), false);

        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка отправки: " + e.getMessage(), e);
        }
    }

    private static void doFetch(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        Vec3d start = owner.getCameraPosVec(1.0F);
        Vec3d dir = owner.getRotationVec(1.0F);
        Vec3d end = start.add(dir.multiply(20.0D));

        Box box = owner.getBoundingBox().stretch(dir.multiply(20.0D)).expand(2.0D);

        List<net.minecraft.entity.ItemEntity> items = owner.getServerWorld().getEntitiesByClass(
                net.minecraft.entity.ItemEntity.class, box,
                e -> e.isAlive() && !e.isRemoved()
        );

        net.minecraft.entity.ItemEntity best = null;
        double bestScore = -1;

        for (net.minecraft.entity.ItemEntity item : items) {
            Vec3d itemPos = item.getPos();
            Vec3d toItem = itemPos.subtract(start).normalize();
            double dot = toItem.dotProduct(dir);
            if (dot > 0.5) {
                double dist = start.distanceTo(itemPos);
                if (dist < 20.0 && dot > bestScore) {
                    bestScore = dot;
                    best = item;
                }
            }
        }

        if (best == null) {
            owner.sendMessage(Text.literal("Не вижу предмет впереди"), false);
            return;
        }

        BlockPos pos = best.getBlockPos();
        sendControl(owner, pet, "fetch", pos);
    }

    private static void doBreak(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        Vec3d start = owner.getCameraPosVec(1.0F);
        Vec3d dir = owner.getRotationVec(1.0F);
        Vec3d end = start.add(dir.multiply(20.0D));

        BlockHitResult hit = owner.getServerWorld().raycast(
                new net.minecraft.world.RaycastContext(
                        start, end,
                        net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                        net.minecraft.world.RaycastContext.FluidHandling.NONE,
                        owner
                )
        );

        if (hit == null || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) {
            owner.sendMessage(Text.literal("Не вижу блок впереди"), false);
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = owner.getServerWorld().getBlockState(pos);

        if (state.isAir()) {
            owner.sendMessage(Text.literal("Там нет блока"), false);
            return;
        }

        sendControl(owner, pet, "break", pos);
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
        String name = PetHelper.getPetName(pet);
        String stage = PetHelper.getStage(pet);
        float hp = pet.getHealth();
        String active = activeCommands.getOrDefault(pet.getUuid(), "нет");

        owner.sendMessage(Text.literal("=== Питомец ==="), false);
        owner.sendMessage(Text.literal("Имя: " + name), false);
        owner.sendMessage(Text.literal("Стадия: " + stage), false);
        owner.sendMessage(Text.literal("HP: " + hp), false);
        owner.sendMessage(Text.literal("Команда: " + active), false);
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
