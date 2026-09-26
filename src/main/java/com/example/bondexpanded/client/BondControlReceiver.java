package com.example.bondexpanded.client;

import com.example.bondexpanded.BondExpanded;
import com.example.bondexpanded.network.BondControlPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.UUID;

public final class BondControlReceiver {

    private static String activeCommand = null;
    private static UUID ownerUuid = null;
    private static int attackCooldown = 0;

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
                    BondExpanded.LOGGER.info("[Bondexpanded] Получена команда: " + activeCommand);
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
                case "attack" -> tickAttack(pet, 20.0D);
                case "hunt" -> tickAttack(pet, 32.0D);
                case "guard" -> stopMovement(pet);
                case "stop" -> {
                    stopMovement(pet);
                    activeCommand = null;
                }
                default -> { }
            }

            if (attackCooldown > 0) attackCooldown--;
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка в BondControlReceiver: " + e.getMessage(), e);
        }
    }

    private static void tickCome(MinecraftClient client, ClientPlayerEntity pet) {
        if (ownerUuid == null) return;

        AbstractClientPlayerEntity owner = null;

        for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
            if (p.getUuid().equals(ownerUuid)) {
                owner = p;
                break;
            }
        }

        if (owner == null) return;

        double distance = pet.distanceTo(owner);

        if (distance < 3.0D) {
            stopMovement(pet);
            return;
        }

        moveToward(pet, owner.getX(), owner.getY(), owner.getZ(), 0.25D);
    }

    private static void tickAttack(ClientPlayerEntity pet, double radius) {
        Box box = pet.getBoundingBox().expand(radius);

        List<HostileEntity> mobs = pet.getWorld().getEntitiesByClass(
                HostileEntity.class,
                box,
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

        double distance = pet.distanceTo(target);

        if (distance > 2.5D) {
            moveToward(pet, target.getX(), target.getY(), target.getZ(), 0.25D);
        } else {
            stopMovement(pet);
            faceTarget(pet, target);

            if (attackCooldown <= 0) {
                pet.attack(target);
                pet.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                attackCooldown = 10;
            }
        }
    }

    private static void moveToward(ClientPlayerEntity pet, double x, double y, double z, double speed) {
        double dx = x - pet.getX();
        double dz = z - pet.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.01D) return;

        dx /= dist;
        dz /= dist;

        pet.setVelocity(dx * speed, pet.getVelocity().y, dz * speed);

        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        pet.setYaw(yaw);
        pet.setHeadYaw(yaw);
        pet.setBodyYaw(yaw);

        BlockPos ahead = pet.getBlockPos().add(
                (int) Math.signum(dx), 0, (int) Math.signum(dz)
        );

        if (!pet.getWorld().getBlockState(ahead).isAir() && pet.isOnGround()) {
            pet.jump();
        }
    }

    private static void faceTarget(ClientPlayerEntity pet, LivingEntity target) {
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
    }

    private static void stopMovement(ClientPlayerEntity pet) {
        pet.setVelocity(0.0D, pet.getVelocity().y, 0.0D);
    }
}
