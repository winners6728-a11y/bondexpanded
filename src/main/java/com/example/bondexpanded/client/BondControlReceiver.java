package com.example.bondexpanded.client;

import com.example.bondexpanded.BondExpanded;
import com.example.bondexpanded.network.BondAttackPacket;
import com.example.bondexpanded.network.BondControlPacket;
import com.example.bondexpanded.network.BondExpandedPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

public final class BondControlReceiver {

    private static final int TARGET_RESCAN_INTERVAL = 10;
    private static final int PATH_RECALC_INTERVAL = 20;
    private static final int MAX_NODES = 200;

    private static String activeCommand = null;
    private static UUID ownerUuid = null;
    private static BlockPos targetPos = null;
    private static String phase = null;

    private static int attackCooldown = 0;
    private static int rescanCooldown = 0;
    private static LivingEntity cachedTarget = null;

    private static List<BlockPos> cachedPath = null;
    private static BlockPos cachedPathGoal = null;
    private static int pathIndex = 0;
    private static int pathCooldown = 0;

    private BondControlReceiver() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                BondControlPacket.TYPE,
                (packet, player, responseSender) -> {
                    String cmd = packet.command();

                    if ("fetch_return".equals(cmd)) {
                        // И для fetch, и для break — переключить в возврат
                        if ("break".equals(activeCommand)) {
                            phase = "returning";
                        } else {
                            activeCommand = "fetch";
                            phase = "returning";
                            targetPos = null;
                        }
                    } else if ("fetch_hold".equals(cmd)) {
                        activeCommand = "fetch";
                        phase = "waiting_drop";
                    } else if ("stop".equals(cmd)) {
                        activeCommand = "stop";
                        phase = null;
                        targetPos = null;
                    } else {
                        activeCommand = cmd;
                        phase = null;

                        if (packet.x() == -1 && packet.y() == -1 && packet.z() == -1) {
                            targetPos = null;
                        } else {
                            targetPos = new BlockPos(packet.x(), packet.y(), packet.z());
                        }
                    }

                    try {
                        ownerUuid = UUID.fromString(packet.ownerUuid());
                    } catch (Exception ignored) {
                    }

                    rescanCooldown = 0;
                    cachedTarget = null;
                    cachedPath = null;
                    cachedPathGoal = null;
                    pathIndex = 0;
                    pathCooldown = 0;

                    BondExpanded.LOGGER.info("[Bondexpanded] Команда: " + cmd);
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
                    cachedPath = null;
                }
                default -> { }
            }

            if (attackCooldown > 0) attackCooldown--;
            if (rescanCooldown > 0) rescanCooldown--;
            if (pathCooldown > 0) pathCooldown--;
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

        moveTo(pet, owner.getX(), owner.getY(), owner.getZ(), 0.25D);
    }

    private static void tickAttack(MinecraftClient client, ClientPlayerEntity pet, double radius) {
        if (rescanCooldown <= 0 || cachedTarget == null || !cachedTarget.isAlive() || cachedTarget.isRemoved()) {
            cachedTarget = findNearestMob(pet, radius);
            rescanCooldown = TARGET_RESCAN_INTERVAL;
        }

        LivingEntity target = cachedTarget;

        if (target == null) {
            stopMovement(pet);
            return;
        }

        double distance = pet.distanceTo(target);

        if (distance > 2.0D) {
            moveTo(pet, target.getX(), target.getY(), target.getZ(), 0.25D);
        } else {
            stopMovement(pet);

            if (attackCooldown <= 0) {
                try {
                    ClientPlayNetworking.send(new BondAttackPacket(target.getUuid()));
                    pet.swingHand(Hand.MAIN_HAND);
                } catch (Exception e) {
                    BondExpanded.LOGGER.error("Атака сломалась: " + e.getMessage(), e);
                }
                attackCooldown = 12;
            }
        }
    }

    private static LivingEntity findNearestMob(ClientPlayerEntity pet, double radius) {
        Box box = pet.getBoundingBox().expand(radius);

        List<HostileEntity> mobs = pet.getWorld().getEntitiesByClass(
                HostileEntity.class, box,
                entity -> entity.isAlive() && !entity.isRemoved()
        );

        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (HostileEntity mob : mobs) {
            double d = pet.squaredDistanceTo(mob);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = mob;
            }
        }

        return nearest;
    }

    private static void tickFetch(MinecraftClient client, ClientPlayerEntity pet) {
        if (phase == null) {
            phase = (targetPos != null) ? "going" : "returning";
        }

        switch (phase) {
            case "going" -> {
                if (targetPos == null) { activeCommand = null; return; }

                double dx = targetPos.getX() + 0.5D - pet.getX();
                double dz = targetPos.getZ() + 0.5D - pet.getZ();
                double horizontalDist = Math.sqrt(dx * dx + dz * dz);
                double verticalDist = Math.abs(pet.getY() - targetPos.getY());

                if (horizontalDist < 0.8D && verticalDist < 1.5D) {
                    stopMovement(pet);
                    phase = "waiting_pickup";
                    try {
                        ClientPlayNetworking.send(new BondExpandedPacket("fetch_arrived"));
                    } catch (Exception e) {
                        BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
                    }
                    return;
                }

                moveTo(pet, targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, 0.25D);
            }
            case "waiting_pickup" -> {
                pet.setVelocity(0.0D, pet.getVelocity().y, 0.0D);
            }
            case "returning" -> {
                AbstractClientPlayerEntity owner = findOwner(client);
                if (owner == null) { activeCommand = null; return; }

                if (pet.distanceTo(owner) < 2.0D) {
                    stopMovement(pet);
                    phase = "drop_requested";

                    double ddx = owner.getX() - pet.getX();
                    double ddz = owner.getZ() - pet.getZ();
                    float yaw = (float) (Math.atan2(ddz, ddx) * 180.0D / Math.PI) - 90.0F;
                    pet.setHeadYaw(yaw);
                    pet.setBodyYaw(yaw);

                    try {
                        ClientPlayNetworking.send(new BondExpandedPacket("fetch_drop"));
                    } catch (Exception e) {
                        BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
                    }
                    return;
                }

                moveTo(pet, owner.getX(), owner.getY(), owner.getZ(), 0.25D);
            }
            case "drop_requested", "waiting_drop" -> {
                pet.setVelocity(0.0D, pet.getVelocity().y, 0.0D);

                AbstractClientPlayerEntity owner = findOwner(client);
                if (owner != null) {
                    double ddx = owner.getX() - pet.getX();
                    double ddz = owner.getZ() - pet.getZ();
                    if (Math.abs(ddx) > 0.01D || Math.abs(ddz) > 0.01D) {
                        float yaw = (float) (Math.atan2(ddz, ddx) * 180.0D / Math.PI) - 90.0F;
                        pet.setHeadYaw(yaw);
                        pet.setBodyYaw(yaw);
                    }
                }
            }
        }
    }

    private static void tickBreak(MinecraftClient client, ClientPlayerEntity pet) {
        if (targetPos == null && !"returning".equals(phase)
                && !"drop_requested".equals(phase) && !"waiting_drop".equals(phase)) {
            activeCommand = null;
            return;
        }

        if (phase == null) phase = "going";

        switch (phase) {
            case "going" -> {
                double dist = pet.getPos().distanceTo(Vec3d.ofCenter(targetPos));
                if (dist < 3.0D) {
                    stopMovement(pet);
                    phase = "breaking";
                    return;
                }
                moveTo(pet, targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, 0.25D);
            }
            case "breaking" -> {
                stopMovement(pet);

                if (client.world.getBlockState(targetPos).isAir()) {
                    // Блок сломан — ждём, пока сервер подберёт дроп и пришлёт fetch_return
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
            case "returning" -> {
                AbstractClientPlayerEntity owner = findOwner(client);
                if (owner == null) { activeCommand = null; return; }

                if (pet.distanceTo(owner) < 2.0D) {
                    stopMovement(pet);
                    phase = "drop_requested";

                    double ddx = owner.getX() - pet.getX();
                    double ddz = owner.getZ() - pet.getZ();
                    float yaw = (float) (Math.atan2(ddz, ddx) * 180.0D / Math.PI) - 90.0F;
                    pet.setHeadYaw(yaw);
                    pet.setBodyYaw(yaw);

                    try {
                        ClientPlayNetworking.send(new BondExpandedPacket("fetch_drop"));
                    } catch (Exception e) {
                        BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
                    }
                    return;
                }

                moveTo(pet, owner.getX(), owner.getY(), owner.getZ(), 0.25D);
            }
            case "drop_requested", "waiting_drop" -> {
                pet.setVelocity(0.0D, pet.getVelocity().y, 0.0D);

                AbstractClientPlayerEntity owner = findOwner(client);
                if (owner != null) {
                    double ddx = owner.getX() - pet.getX();
                    double ddz = owner.getZ() - pet.getZ();
                    if (Math.abs(ddx) > 0.01D || Math.abs(ddz) > 0.01D) {
                        float yaw = (float) (Math.atan2(ddz, ddx) * 180.0D / Math.PI) - 90.0F;
                        pet.setHeadYaw(yaw);
                        pet.setBodyYaw(yaw);
                    }
                }
            }
        }
    }

    private static void moveTo(ClientPlayerEntity pet, double x, double y, double z, double speed) {
        BlockPos goal = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        BlockPos current = pet.getBlockPos();

        boolean needRecalc = cachedPath == null
                || pathIndex >= cachedPath.size()
                || cachedPathGoal == null
                || cachedPathGoal.getSquaredDistance(goal) > 4.0D
                || pathCooldown <= 0;

        if (needRecalc) {
            cachedPath = findPath(pet, current, goal, MAX_NODES);
            cachedPathGoal = goal;
            pathIndex = 0;
            pathCooldown = PATH_RECALC_INTERVAL;

            if (cachedPath != null && !cachedPath.isEmpty()) {
                BlockPos first = cachedPath.get(0);
                if (first.getSquaredDistance(current) <= 1.5D) {
                    pathIndex = 1;
                }
            }
        }

        if (cachedPath == null || cachedPath.isEmpty() || pathIndex >= cachedPath.size()) {
            directMove(pet, x, y, z, speed);
            return;
        }

        BlockPos next = cachedPath.get(pathIndex);

        double nx = next.getX() + 0.5D;
        double nz = next.getZ() + 0.5D;
        double ny = next.getY();

        double dx = nx - pet.getX();
        double dz = nz - pet.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.7D) {
            pathIndex++;

            if (pathIndex >= cachedPath.size()) {
                directMove(pet, x, y, z, speed);
                return;
            }

            next = cachedPath.get(pathIndex);
            nx = next.getX() + 0.5D;
            nz = next.getZ() + 0.5D;
            ny = next.getY();
            dx = nx - pet.getX();
            dz = nz - pet.getZ();
            dist = Math.sqrt(dx * dx + dz * dz);
        }

        if (dist < 0.01D) return;

        dx /= dist;
        dz /= dist;

        pet.setVelocity(dx * speed, pet.getVelocity().y, dz * speed);

        if (ny > pet.getY() + 0.5D && pet.isOnGround()) {
            pet.jump();
        }
    }

    private static void directMove(ClientPlayerEntity pet, double x, double y, double z, double speed) {
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
            BlockPos ahead = feetPos.add((int) Math.round(dx), 0, (int) Math.round(dz));
            BlockPos above = ahead.up();

            boolean bottomBlocked = !pet.getWorld().getBlockState(ahead).isAir();
            boolean topBlocked = !pet.getWorld().getBlockState(above).isAir();

            if (bottomBlocked && !topBlocked) {
                pet.jump();
            }
        }
    }

    private static List<BlockPos> findPath(ClientPlayerEntity pet, BlockPos start, BlockPos goal, int maxNodes) {
        try {
            if (!isWalkable(pet, start) || !isWalkable(pet, goal)) return null;

            PriorityQueue<PathNode> open = new PriorityQueue<>(Comparator.comparingDouble(PathNode::getF));
            Map<BlockPos, PathNode> all = new HashMap<>();
            Set<BlockPos> closed = new HashSet<>();

            PathNode startNode = new PathNode(start, null, 0.0D, heuristic(start, goal));
            open.add(startNode);
            all.put(start, startNode);

            int processed = 0;

            while (!open.isEmpty() && processed < maxNodes) {
                PathNode current = open.poll();

                if (!closed.add(current.pos)) continue;
                processed++;

                if (current.pos.getSquaredDistance(goal) <= 2.0D) {
                    return reconstruct(current, start);
                }

                for (BlockPos neighbor : getNeighbors(pet, current.pos)) {
                    if (closed.contains(neighbor)) continue;

                    double newCost = current.g + movementCost(current.pos, neighbor);
                    PathNode old = all.get(neighbor);

                    if (old == null || newCost < old.g) {
                        PathNode next = new PathNode(
                                neighbor,
                                current,
                                newCost,
                                newCost + heuristic(neighbor, goal)
                        );
                        all.put(neighbor, next);
                        open.add(next);
                    }
                }
            }

            return null;

        } catch (Exception e) {
            BondExpanded.LOGGER.error("A* сломался: " + e.getMessage(), e);
            return null;
        }
    }

    private static List<BlockPos> getNeighbors(ClientPlayerEntity pet, BlockPos pos) {
        List<BlockPos> list = new ArrayList<>(8);

        BlockPos[] flat = {
                pos.add(1, 0, 0),
                pos.add(-1, 0, 0),
                pos.add(0, 0, 1),
                pos.add(0, 0, -1)
        };

        for (BlockPos p : flat) {
            if (isWalkable(pet, p)) list.add(p);
        }

        BlockPos[] up = {
                pos.add(1, 1, 0),
                pos.add(-1, 1, 0),
                pos.add(0, 1, 1),
                pos.add(0, 1, -1)
        };

        for (BlockPos p : up) {
            if (isWalkable(pet, p)) list.add(p);
        }

        return list;
    }

    private static boolean isWalkable(ClientPlayerEntity pet, BlockPos pos) {
        try {
            int bottom = pet.getWorld().getBottomY();
            int top = pet.getWorld().getTopY() - 2;

            if (pos.getY() < bottom || pos.getY() >= top) return false;

            BlockState feet = pet.getWorld().getBlockState(pos);
            BlockState head = pet.getWorld().getBlockState(pos.up());
            BlockState floor = pet.getWorld().getBlockState(pos.down());

            if (!feet.isAir() || !head.isAir()) return false;
            if (floor.isAir()) return false;

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private static double movementCost(BlockPos from, BlockPos to) {
        if (to.getY() > from.getY()) return 1.4D;
        return 1.0D;
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX())
                + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ());
    }

    private static List<BlockPos> reconstruct(PathNode node, BlockPos start) {
        List<BlockPos> result = new ArrayList<>();
        PathNode current = node;

        while (current != null) {
            result.add(current.pos);
            current = current.parent;
        }

        Collections.reverse(result);

        if (!result.isEmpty() && result.get(0).equals(start)) {
            result.remove(0);
        }

        return result;
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

    private static final class PathNode {
        final BlockPos pos;
        final PathNode parent;
        final double g;
        final double f;

        PathNode(BlockPos pos, PathNode parent, double g, double f) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.f = f;
        }

        double getF() {
            return f;
        }
    }
}
