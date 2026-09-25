package com.example.bondexpanded.util;

import com.example.bondexpanded.BondExpanded;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BondExpandedServerState {
    public static final Map<UUID, Boolean> attackState = new HashMap<>();
    public static final Map<UUID, Boolean> guardState = new HashMap<>();
    public static final Map<UUID, BlockPos> guardPosition = new HashMap<>();
    public static final Map<UUID, Boolean> huntState = new HashMap<>();
    public static final Map<UUID, Long> healCooldown = new HashMap<>();
    public static final Map<UUID, Long> speedCooldown = new HashMap<>();
    public static final Map<UUID, Long> howlCooldown = new HashMap<>();
    public static final Map<UUID, Long> releaseConfirmTime = new HashMap<>();

    private BondExpandedServerState() {
    }

    public static void handleCommand(ServerPlayerEntity owner, String command) {
        try {
            ServerPlayerEntity pet = PetHelper.getPet(owner);
            if (pet == null) {
                owner.sendMessage(net.minecraft.text.Text.literal("У вас нет питомца"), false);
                return;
            }

            switch (command) {
                case "attack" -> toggleAttack(owner, pet);
                case "fetch" -> fetch(owner, pet);
                case "guard" -> toggleGuard(owner, pet);
                case "come" -> come(owner, pet);
                case "heal" -> heal(owner, pet);
                case "speed" -> speed(owner, pet);
                case "info" -> info(owner, pet);
                case "release" -> release(owner, pet);
                case "howl" -> howl(owner, pet);
                case "hunt" -> toggleHunt(owner, pet);
                default -> owner.sendMessage(net.minecraft.text.Text.literal("Неизвестная команда"), false);
            }
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void toggleAttack(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        boolean enabled = !attackState.getOrDefault(pet.getUuid(), false);
        attackState.put(pet.getUuid(), enabled);
        owner.sendMessage(net.minecraft.text.Text.literal(enabled ? "Атака включена" : "Атака выключена"), false);
    }

    private static void toggleGuard(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        boolean enabled = !guardState.getOrDefault(pet.getUuid(), false);
        guardState.put(pet.getUuid(), enabled);
        if (enabled) {
            guardPosition.put(pet.getUuid(), pet.getBlockPos());
        } else {
            guardPosition.remove(pet.getUuid());
        }
        owner.sendMessage(net.minecraft.text.Text.literal(enabled ? "Охрана включена" : "Охрана выключена"), false);
    }

    private static void toggleHunt(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        boolean enabled = !huntState.getOrDefault(pet.getUuid(), false);
        huntState.put(pet.getUuid(), enabled);
        owner.sendMessage(net.minecraft.text.Text.literal(enabled ? "Охота включена" : "Охота выключена"), false);
    }

    private static void heal(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        long now = owner.getServer().getOverworld().getTime();
        long ready = healCooldown.getOrDefault(pet.getUuid(), 0L);
        if (now < ready) {
            owner.sendMessage(net.minecraft.text.Text.literal("Ещё не готово: " + ((ready - now + 19) / 20) + " сек"), false);
            return;
        }
        pet.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
        healCooldown.put(pet.getUuid(), now + 1200);
    }

    private static void speed(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        long now = owner.getServer().getOverworld().getTime();
        long ready = speedCooldown.getOrDefault(pet.getUuid(), 0L);
        if (now < ready) {
            owner.sendMessage(net.minecraft.text.Text.literal("Ещё не готово: " + ((ready - now + 19) / 20) + " сек"), false);
            return;
        }
        pet.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 600, 1));
        speedCooldown.put(pet.getUuid(), now + 1200);
    }

    private static void info(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        String active = "нет";
        UUID id = pet.getUuid();
        if (attackState.getOrDefault(id, false)) active = "attack";
        else if (guardState.getOrDefault(id, false)) active = "guard";
        else if (huntState.getOrDefault(id, false)) active = "hunt";

        owner.sendMessage(net.minecraft.text.Text.literal(
                "Имя: " + PetHelper.getPetName(pet) +
                " | Стадия: " + PetHelper.getStage(pet) +
                " | HP: " + String.format("%.1f", pet.getHealth()) + "/" + String.format("%.1f", pet.getMaxHealth()) +
                " | Связь: " + (PetHelper.isTamed(pet) ? "активна" : "нет") +
                " | Команда: " + active), false);
    }

    private static void release(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        long now = owner.getServer().getOverworld().getTime();
        long confirmedAt = releaseConfirmTime.getOrDefault(pet.getUuid(), -10000L);

        if (now - confirmedAt <= 200) {
            if (PetHelper.releaseBond(pet)) {
                releaseConfirmTime.remove(pet.getUuid());
                owner.sendMessage(net.minecraft.text.Text.literal("Связь разорвана"), false);
            } else {
                owner.sendMessage(net.minecraft.text.Text.literal("Не удалось разорвать связь: метод не найден"), false);
            }
        } else {
            releaseConfirmTime.put(pet.getUuid(), now);
            owner.sendMessage(net.minecraft.text.Text.literal("Нажмите ещё раз в течение 10 секунд для подтверждения"), false);
        }
    }

    private static void howl(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        long now = owner.getServer().getOverworld().getTime();
        long ready = howlCooldown.getOrDefault(pet.getUuid(), 0L);
        if (now < ready) {
            owner.sendMessage(net.minecraft.text.Text.literal("Ещё не готово: " + ((ready - now + 19) / 20) + " сек"), false);
            return;
        }

        pet.getWorld().playSound(null, pet.getBlockPos(), SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.NEUTRAL, 1.0f, 1.0f);

        Box box = pet.getBoundingBox().expand(16.0);
        for (HostileEntity hostile : pet.getWorld().getEntitiesByClass(HostileEntity.class, box, LivingEntity::isAlive)) {
            hostile.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0));
        }

        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 0));
        howlCooldown.put(pet.getUuid(), now + 600);
    }

    private static void come(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        if (pet.squaredDistanceTo(owner) <= 9.0) {
            return;
        }
        moveToward(pet, owner, 1.2);
    }

    private static void fetch(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        HitResult hit = raycast(owner, 10.0);
        if (hit.getType() == HitResult.Type.MISS) {
            return;
        }

        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ItemEntity item) {
            moveToward(pet, item.getX(), item.getY(), item.getZ(), 1.1);
        } else if (hit instanceof BlockHitResult blockHit) {
            Vec3d pos = Vec3d.ofCenter(blockHit.getBlockPos());
            moveToward(pet, pos.x, pos.y, pos.z, 1.1);
        }
    }

    private static HitResult raycast(ServerPlayerEntity player, double distance) {
        Vec3d start = player.getCameraPosVec(1.0f);
        Vec3d rotation = player.getRotationVec(1.0f);
        Vec3d end = start.add(rotation.multiply(distance));
        return player.getWorld().raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player));
    }

    private static void moveToward(ServerPlayerEntity pet, ServerPlayerEntity target, double speed) {
        moveToward(pet, target.getX(), target.getY(), target.getZ(), speed);
    }

    private static void moveToward(ServerPlayerEntity pet, double x, double y, double z, double speed) {
        Vec3d delta = new Vec3d(x - pet.getX(), y - pet.getY(), z - pet.getZ());
        if (delta.lengthSquared() < 0.25) {
            pet.setVelocity(Vec3d.ZERO);
            pet.velocityModified = true;
            return;
        }
        Vec3d velocity = delta.normalize().multiply(Math.min(speed * 0.25, delta.length()));
        pet.setVelocity(velocity.x, velocity.y, velocity.z);
        pet.velocityModified = true;
    }

    public static void tick(ServerWorld world) {
        try {
            for (ServerPlayerEntity pet : world.getPlayers()) {
                UUID id = pet.getUuid();
                if (attackState.getOrDefault(id, false)) {
                    ServerPlayerEntity owner = PetHelper.getOwner(pet);
                    if (owner != null && owner.getWorld() == pet.getWorld()) {
                        HitResult hit = raycast(owner, 20.0);
                        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target && target != pet && target.isAlive()) {
                            moveToward(pet, target.getX(), target.getY(), target.getZ(), 1.2);
                            if (pet.squaredDistanceTo(target) <= 4.0) {
                                target.damage(pet.getDamageSources().mobAttack(pet), 4.0f);
                            }
                        }
                    }
                }

                if (guardState.getOrDefault(id, false)) {
                    BlockPos pos = guardPosition.get(id);
                    if (pos != null && pet.getBlockPos().getSquaredDistance(pos) > 256.0) {
                        moveToward(pet, pos.getX(), pos.getY(), pos.getZ(), 1.0);
                    }
                    if (world.getTime() % 20 == 0) {
                        HostileEntity nearest = world.getEntitiesByClass(
                                HostileEntity.class,
                                new Box(pet.getBlockPos()).expand(16),
                                LivingEntity::isAlive)
                                .stream()
                                .min((a, b) -> Double.compare(pet.squaredDistanceTo(a), pet.squaredDistanceTo(b)))
                                .orElse(null);
                        if (nearest != null && pos != null && nearest.getBlockPos().getSquaredDistance(pos) <= 256.0) {
                            moveToward(pet, nearest.getX(), nearest.getY(), nearest.getZ(), 1.2);
                            if (pet.squaredDistanceTo(nearest) <= 4.0) {
                                nearest.damage(pet.getDamageSources().mobAttack(pet), 4.0f);
                            }
                        }
                    }
                }

                if (huntState.getOrDefault(id, false) && world.getTime() % 10 == 0) {
                    HostileEntity nearest = world.getEntitiesByClass(
                            HostileEntity.class,
                            new Box(pet.getBlockPos()).expand(32),
                            LivingEntity::isAlive)
                            .stream()
                            .min((a, b) -> Double.compare(pet.squaredDistanceTo(a), pet.squaredDistanceTo(b)))
                            .orElse(null);
                    if (nearest != null) {
                        moveToward(pet, nearest.getX(), nearest.getY(), nearest.getZ(), 1.25);
                        if (pet.squaredDistanceTo(nearest) <= 4.0) {
                            nearest.damage(pet.getDamageSources().mobAttack(pet), 4.0f);
                        }
                    }
                }
            }
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }
}
