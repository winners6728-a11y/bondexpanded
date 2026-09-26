package com.example.bondexpanded.client;

import com.example.bondexpanded.BondExpanded;
import com.example.bondexpanded.network.BondAttackPacket;
import com.example.bondexpanded.network.BondControlPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;

public final class BondControlReceiver {

    private static final float YAW_STEP = 2.5F;
    private static final float PITCH_STEP = 2.0F;

    private static String activeCommand = null;
    private static UUID ownerUuid = null;
    private static BlockPos targetPos = null;
    private static String phase = null;
    private static int attackCooldown = 0;
    private static int waitTicks = 0;

    private BondControlReceiver() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                BondControlPacket.TYPE,
                (packet, player, responseSender) -> {
                    activeCommand = packet.command();
                    try {
                        ownerUuid = UUID.fromString(packet.ownerUuid());
                    } catch (Exception e) {
                        ownerUuid = null;
                    }

                    if (packet.x() == -1 && packet.y() == -1 && packet.z() == -1) {
                        targetPos = null;
                    } else {
                        targetPos = new BlockPos(packet.x(), packet.y(), packet.z());
                    }

                    phase = null;
                    waitTicks = 0;

                    BondExpanded.LOGGER.info("[Bondexpanded] Команда: " + activeCommand
                            + (targetPos != null ? " @ " + targetPos.toShortString() : ""));
                }
        );
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (activeCommand == null) return;

        ClientPlayerEntity pet = client.player;

        try {
            switch (activeCommand) {
                case "come" -> tickCome(client, pet);
                case "attack" -> tickAttack(client, pet, 20.0D);
                case "hunt" -> tickAttack(client, pet, 32.0D);
                case "guard" -> stopMovement(pet);
                case "fetch" -> tickFetch(client, pet);
                case "break" -> tickBreak(client, pet);
                case "stop" -> {
                    stopMovement(pet);
                    activeCommand = null;
                    phase = null;
                    targetPos = null;
                }
                default -> { }
            }

            if (attackCooldown > 0) attackCooldown--;
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private static void tickCome(MinecraftClient client, ClientPlayerEntity pet) {
        AbstractClientPlayerEntity owner = findOwner(client);
        if (owner == null) return;

        if (pet.distanceTo(owner) < 3.0D) {
            stopMovement(pet);
            return;
        }

        smoothLookAt(pet, owner.getX(), owner.getY() + owner.getHeight() / 2.0, owner.getZ());
        moveToward(pet, owner.getX(), owner.getY(), owner.getZ(), 0.25D);
    }

    private static void tickAttack(MinecraftClient client, ClientPlayerEntity pet, double radius) {
        Box box = pet.getBoundingBox().expand(radius);

        List<HostileEntity> mobs = pet.getWorld().getEntitiesByClass(
                HostileEntity.class, box,
                entity -> entity.isAlive() && !entity.isRemoved()
        );

        LivingEntity target = null;
        double nearest = Double.MAX_VALUE;

        for (HostileEntity mob : mobs) {
            double d = pet.squaredDistanceTo(mob);
            if (d < nearest) {
                nearest = d;
                target = mob;
            }
        }

        if (target == null) {
            stopMovement(pet);
            return;
        }

        double torsoY = target.getY() + target.getHeight() / 2.0;
        smoothLookAt(pet, target.getX(), torsoY, target.getZ());

        double distance = pet.distanceTo(target);

        if (distance > 2.0D) {
            moveToward(pet, target.getX(), target.getY(), target.getZ(), 0.25D);
        } else {
            stopMovement(pet);

            if (attackCooldown <= 0) {
                try {
                    ClientPlayNetworking.send(new BondAttackPacket(target.getUuid()));
                    pet.swingHand(Hand.MAIN_HAND);

                    BondExpanded.LOGGER.info("[Bondexpanded] Запрос атаки на " + target.getName().getString());
                } catch (Exception e) {
                    BondExpanded.LOGGER.error("Атака сломалась: " + e.getMessage(), e);
                }
                attackCooldown = 12;
            }
        }
    }

    private static void tickFetch(MinecraftClient client, ClientPlayerEntity pet) {
        if (targetPos == null) { activeCommand = null; return; }
        if (phase == null) phase = "going";

        switch (phase) {
            case "going" -> {
                double dist = pet.getPos().distanceTo(Vec3d.ofCenter(targetPos));
                if (dist < 1.8D) {
                    stopMovement(pet);
                    phase = "pickup";
                    waitTicks = 15;
                    return;
                }
                smoothLookAt(pet, targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);
                moveToward(pet, targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, 0.25D);
            }
            case "pickup" -> {
                stopMovement(pet);
                if (--waitTicks <= 0) phase = "returning";
            }
            case "returning" -> {
                AbstractClientPlayerEntity owner = findOwner(client);
                if (owner == null) { activeCommand = null; return; }
                if (pet.distanceTo(owner) < 3.0D) {
                    dropHeldItem(pet);
                    stopMovement(pet);
                    activeCommand = null;
                    phase = null;
                    return;
                }
                smoothLookAt(pet, owner.getX(), owner.getY() + owner.getHeight() / 2.0, owner.getZ());
                moveToward(pet, owner.getX(), owner.getY(), owner.getZ(), 0.25D);
            }
        }
    }

    private static void tickBreak(MinecraftClient client, ClientPlayerEntity pet) {
        if (targetPos == null) { activeCommand = null; return; }
        if (phase == null) phase = "going";

        switch (phase) {
            case "going" -> {
                double dist = pet.getPos().distanceTo(Vec3d.ofCenter(targetPos));
                if (dist < 3.0D) {
                    stopMovement(pet);
                    phase = "breaking";
                    return;
                }
                smoothLookAt(pet, targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);
                moveToward(pet, targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, 0.25D);
            }
            case "breaking" -> {
                stopMovement(pet);
                smoothLookAt(pet, targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);

                if (client.world.getBlockState(targetPos).isAir()) {
                    phase = "pickup";
                    waitTicks = 15;
                    return;
                }

                if (client.interactionManager != null) {
                    Direction dir = getFacingDirection(pet, targetPos);
                    try {
                        client.interactionManager.updateBlockBreakingProgress(targetPos, dir);
                        pet.swingHand(Hand.MAIN_HAND);
                    } catch (Exception e) {
                        BondExpanded.LOGGER.error("Поломка сломалась: " + e.getMessage(), e);
                    }
                }
            }
            case "pickup" -> {
                stopMovement(pet);
                if (--waitTicks <= 0) phase = "returning";
            }
            case "returning" -> {
                AbstractClientPlayerEntity owner = findOwner(client);
                if (owner == null) { activeCommand = null; return; }
                if (pet.distanceTo(owner) < 3.0D) {
                    dropHeldItem(pet);
                    stopMovement(pet);
                    activeCommand = null;
                    phase = null;
                    return;
                }
                smoothLookAt(pet, owner.getX(), owner.getY() + owner.getHeight() / 2.0, owner.getZ());
                moveToward(pet, owner.getX(), owner.getY(), owner.getZ(), 0.25D);
            }
        }
    }

    private static void smoothLookAt(ClientPlayerEntity pet, double targetX, double targetY, double targetZ) {
        try {
            double dx = targetX - pet.getX();
            double dy = targetY - pet.getEyeY();
            double dz = targetZ - pet.getZ();
            double horiz = Math.sqrt(dx * dx + dz * dz);

            if (horiz < 0.01D) return;

            float targetYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            float targetPitch = (float) (-Math.atan2(dy, horiz) * 180.0D / Math.PI);

            float currentYaw = pet.getYaw();
            float currentPitch = pet.getPitch();

            float deltaYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
            float deltaPitch = targetPitch - currentPitch;

            float stepYaw = MathHelper.clamp(deltaYaw, -YAW_STEP, YAW_STEP);
            float stepPitch = MathHelper.clamp(deltaPitch, -PITCH_STEP, PITCH_STEP);

            float newYaw = currentYaw + stepYaw;
            float newPitch = MathHelper.clamp(currentPitch + stepPitch, -90.0F, 90.0F);

            pet.setYaw(newYaw);
            pet.setPitch(newPitch);
            pet.setHeadYaw(newYaw);
            pet.setBodyYaw(newYaw);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка поворота: " + e.getMessage(), e);
        }
    }

    private static void moveToward(ClientPlayerEntity pet, double x, double y, double z, double speed) {
        double dx = x - pet.getX();
        double dz = z - pet.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.5D) {
            pet.setVelocity(pet.getVelocity().x * 0.3D, pet.getVelocity().y, pet.getVelocity().z * 0.3D);
            return;
        }

        dx /= dist;
        dz /= dist;

        double adjustedSpeed = dist < 2.0D ? speed * 0.6D : speed;
        pet.setVelocity(dx * adjustedSpeed, pet.getVelocity().y, dz * adjustedSpeed);

        if (pet.isOnGround()) {
            BlockPos feetPos = pet.getBlockPos();
            BlockPos ahead = feetPos.add((int) Math.signum(dx), 0, (int) Math.signum(dz));
            BlockPos above = ahead.up();

            boolean bottomBlocked = !pet.getWorld().getBlockState(ahead).isAir();
            boolean topBlocked = !pet.getWorld().getBlockState(above).isAir();

            if (bottomBlocked && !topBlocked) {
                pet.jump();
            }
        }
    }

    private static Direction getFacingDirection(ClientPlayerEntity pet, BlockPos pos) {
        double dx = pos.getX() + 0.5D - pet.getX();
        double dy = pos.getY() + 0.5D - pet.getY();
        double dz = pos.getZ() + 0.5D - pet.getZ();

        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        double absZ = Math.abs(dz);

        if (absY >= absX && absY >= absZ) return dy > 0 ? Direction.UP : Direction.DOWN;
        if (absX >= absZ) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void dropHeldItem(ClientPlayerEntity pet) {
        try {
            for (int i = 0; i < 9; i++) {
                if (!pet.getInventory().getStack(i).isEmpty()) {
                    pet.getInventory().selectedSlot = i;
                    pet.dropSelectedItem(false);
                    return;
                }
            }
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Дроп не удался: " + e.getMessage(), e);
        }
    }

    private static AbstractClientPlayerEntity findOwner(MinecraftClient client) {
        if (ownerUuid == null) return null;
        for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
            if (p.getUuid().equals(ownerUuid)) return p;
        }
        return null;
    }

    private static void stopMovement(ClientPlayerEntity pet) {
        pet.setVelocity(0.0D, pet.getVelocity().y, 0.0D);
    }
}
