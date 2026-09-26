package com.example.bondexpanded.util;

import com.example.bondexpanded.BondExpanded;
import com.example.bondexpanded.movement.PetNavigator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BondExpandedServerState {

    private static final Map<UUID, String> activeCommands = new HashMap<>();
    private static final Map<UUID, PetNavigator> navigators = new HashMap<>();
    private static final Map<UUID, LivingEntity> attackTargets = new HashMap<>();
    private static final Map<UUID, Integer> attackTimers = new HashMap<>();
    private static final Map<UUID, BlockPos> guardPositions = new HashMap<>();

    private BondExpandedServerState() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                tick(server);
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
                case "come" -> activateCome(owner, pet);
                case "guard" -> toggleGuard(owner, pet);
                case "attack" -> toggleAttack(owner, pet);
                case "hunt" -> toggleHunt(owner, pet);
                default -> { }
            }
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void activateCome(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        try {
            stopCommand(pet);
            activeCommands.put(pet.getUuid(), "come");
            getNavigator(pet).reset();
            owner.sendMessage(Text.literal("Питомец идёт к вам"), false);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void toggleGuard(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        try {
            UUID uuid = pet.getUuid();

            if ("guard".equals(activeCommands.get(uuid))) {
                stopCommand(pet);
                owner.sendMessage(Text.literal("Охрана выключена"), false);
                return;
            }

            stopCommand(pet);
            activeCommands.put(uuid, "guard");
            guardPositions.put(uuid, pet.getBlockPos().toImmutable());
            getNavigator(pet).reset();
            owner.sendMessage(Text.literal("Охрана включена"), false);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void toggleAttack(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        try {
            UUID uuid = pet.getUuid();

            if ("attack".equals(activeCommands.get(uuid))) {
                stopCommand(pet);
                owner.sendMessage(Text.literal("Атака выключена"), false);
                return;
            }

            LivingEntity target = findLookedAtTarget(owner);

            if (target == null) {
                owner.sendMessage(Text.literal("Вы не смотрите на подходящую цель"), false);
                return;
            }

            stopCommand(pet);
            activeCommands.put(uuid, "attack");
            attackTargets.put(uuid, target);
            attackTimers.put(uuid, 0);
            getNavigator(pet).reset();
            owner.sendMessage(Text.literal("Атака включена"), false);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void toggleHunt(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        try {
            UUID uuid = pet.getUuid();

            if ("hunt".equals(activeCommands.get(uuid))) {
                stopCommand(pet);
                owner.sendMessage(Text.literal("Охота выключена"), false);
                return;
            }

            stopCommand(pet);
            activeCommands.put(uuid, "hunt");
            attackTimers.put(uuid, 0);
            getNavigator(pet).reset();
            owner.sendMessage(Text.literal("Охота включена"), false);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        try {
            for (ServerPlayerEntity owner : server.getPlayerManager().getPlayerList()) {
                ServerPlayerEntity pet = PetHelper.getPet(owner);
                if (pet == null) continue;

                UUID uuid = pet.getUuid();
                String command = activeCommands.get(uuid);
                if (command == null) continue;

                if (!owner.getWorld().getRegistryKey().equals(pet.getWorld().getRegistryKey())) {
                    stopCommand(pet);
                    continue;
                }

                switch (command) {
                    case "come" -> tickCome(owner, pet);
                    case "guard" -> tickGuard(owner, pet);
                    case "attack" -> tickAttack(owner, pet);
                    case "hunt" -> tickHunt(owner, pet);
                    default -> stopCommand(pet);
                }
            }
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void tickCome(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        try {
            double distance = pet.distanceTo(owner);

            if (distance <= 3.0D) {
                getNavigator(pet).stop();
                return;
            }

            getNavigator(pet).moveTo(owner.getBlockPos());
            checkPathFailure(owner, pet);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void tickGuard(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        try {
            UUID uuid = pet.getUuid();
            BlockPos guardPos = guardPositions.get(uuid);

            if (guardPos == null) {
                guardPos = pet.getBlockPos().toImmutable();
                guardPositions.put(uuid, guardPos);
            }

            LivingEntity target = findNearestHostile(pet, 16.0D);

            if (target != null) {
                attackTarget(owner, pet, target);
                return;
            }

            double distance = pet.getPos().squaredDistanceTo(Vec3d.ofCenter(guardPos));

            if (distance > 4.0D) {
                getNavigator(pet).moveTo(guardPos);
                checkPathFailure(owner, pet);
            } else {
                getNavigator(pet).stop();
            }
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void tickAttack(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        try {
            UUID uuid = pet.getUuid();
            LivingEntity target = attackTargets.get(uuid);

            if (target == null || target.isRemoved() || !target.isAlive()) {
                stopCommand(pet);
                return;
            }

            if (!target.getWorld().getRegistryKey().equals(pet.getWorld().getRegistryKey())) {
                stopCommand(pet);
                return;
            }

            attackTarget(owner, pet, target);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void tickHunt(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        try {
            UUID uuid = pet.getUuid();
            LivingEntity target = attackTargets.get(uuid);

            if (target == null || target.isRemoved() || !target.isAlive()
                    || pet.distanceTo(target) > 32.0D) {
                target = findNearestHostile(pet, 32.0D);
                attackTargets.put(uuid, target);
            }

            if (target == null) {
                getNavigator(pet).stop();
                return;
            }

            attackTarget(owner, pet, target);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void attackTarget(ServerPlayerEntity owner, ServerPlayerEntity pet, LivingEntity target) {
        try {
            double distance = pet.distanceTo(target);
            faceEntity(pet, target);

            if (distance <= 2.75D) {
                getNavigator(pet).stop();

                int timer = attackTimers.getOrDefault(pet.getUuid(), 0);

                if (timer <= 0) {
                    pet.attack(target);
                    pet.swingHand(Hand.MAIN_HAND);
                    attackTimers.put(pet.getUuid(), 10);
                } else {
                    attackTimers.put(pet.getUuid(), timer - 1);
                }
                return;
            }

            getNavigator(pet).moveTo(target.getBlockPos());
            checkPathFailure(owner, pet);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static LivingEntity findNearestHostile(ServerPlayerEntity pet, double radius) {
        try {
            ServerWorld world = pet.getServerWorld();
            Box box = pet.getBoundingBox().expand(radius);

            List<HostileEntity> entities = world.getEntitiesByClass(
                    HostileEntity.class,
                    box,
                    entity -> entity.isAlive() && !entity.isRemoved()
            );

            LivingEntity nearest = null;
            double nearestDistance = Double.MAX_VALUE;

            for (HostileEntity entity : entities) {
                double distance = pet.squaredDistanceTo(entity);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = entity;
                }
            }

            return nearest;
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return null;
        }
    }

    private static LivingEntity findLookedAtTarget(ServerPlayerEntity owner) {
        try {
            ServerWorld world = owner.getServerWorld();
            Vec3d start = owner.getCameraPosVec(1.0F);
            Vec3d direction = owner.getRotationVec(1.0F);
            Vec3d end = start.add(direction.multiply(20.0D));

            Box searchBox = owner.getBoundingBox()
                    .stretch(direction.multiply(20.0D))
                    .expand(1.0D);

            List<LivingEntity> candidates = world.getEntitiesByClass(
                    LivingEntity.class,
                    searchBox,
                    entity -> entity != owner && entity.isAlive() && !entity.isSpectator()
            );

            LivingEntity best = null;
            double bestDistance = 20.0D;

            for (LivingEntity entity : candidates) {
                Box box = entity.getBoundingBox().expand(entity.getTargetingMargin());
                java.util.Optional<Vec3d> hit = box.raycast(start, end);

                if (hit.isPresent()) {
                    double distance = start.distanceTo(hit.get());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = entity;
                    }
                }
            }

            return best;
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return null;
        }
    }

    private static void faceEntity(ServerPlayerEntity pet, LivingEntity target) {
        try {
            double dx = target.getX() - pet.getX();
            double dz = target.getZ() - pet.getZ();
            double dy = target.getEyeY() - pet.getEyeY();
            double horizontal = Math.sqrt(dx * dx + dz * dz);

            if (horizontal < 0.001D) return;

            float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            float pitch = (float) (-Math.atan2(dy, horizontal) * 180.0D / Math.PI);

            pet.setYaw(yaw);
            pet.setPitch(pitch);
            pet.setHeadYaw(yaw);
            pet.setBodyYaw(yaw);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void checkPathFailure(ServerPlayerEntity owner, ServerPlayerEntity pet) {
        try {
            PetNavigator navigator = getNavigator(pet);

            if (navigator.getFailedTicks() > 40) {
                navigator.stop();
                owner.sendMessage(Text.literal("Не могу добраться до цели"), false);
            }
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static PetNavigator getNavigator(ServerPlayerEntity pet) {
        return navigators.computeIfAbsent(pet.getUuid(), ignored -> new PetNavigator(pet));
    }

    private static void stopCommand(ServerPlayerEntity pet) {
        try {
            UUID uuid = pet.getUuid();

            activeCommands.remove(uuid);
            attackTargets.remove(uuid);
            attackTimers.remove(uuid);
            guardPositions.remove(uuid);

            PetNavigator navigator = navigators.get(uuid);
            if (navigator != null) navigator.stop();
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }
}
