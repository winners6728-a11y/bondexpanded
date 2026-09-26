package com.example.bondexpanded.movement;

import com.example.bondexpanded.BondExpanded;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
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

public final class PetNavigator {

    private static final int MAX_NODES = 512;
    private static final int MAX_FAILURE_TICKS = 40;
    private static final double WALK_SPEED = 0.20D;
    private static final double JUMP_VELOCITY = 0.42D;

    private final ServerPlayerEntity pet;

    private List<BlockPos> path = Collections.emptyList();
    private int pathIndex;
    private int failedTicks;
    private BlockPos target;

    public PetNavigator(ServerPlayerEntity pet) {
        this.pet = pet;
    }

    public void reset() {
        try {
            path = Collections.emptyList();
            pathIndex = 0;
            failedTicks = 0;
            target = null;
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    public int getFailedTicks() {
        return failedTicks;
    }

    public void moveTo(BlockPos targetPos) {
        try {
            if (pet == null || pet.isRemoved()) return;
            if (targetPos == null) { stop(); return; }

            if (target == null || !target.equals(targetPos) || pathIndex >= path.size()) {
                target = targetPos.toImmutable();

                List<BlockPos> newPath = findPath(pet.getBlockPos(), target, MAX_NODES);

                if (newPath == null || newPath.isEmpty()) {
                    failedTicks++;
                    fallbackMove(targetPos);
                    return;
                }

                path = newPath;
                pathIndex = 0;
                failedTicks = 0;
            }

            followPath();
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    public void stop() {
        try {
            Vec3d velocity = pet.getVelocity();
            pet.setVelocity(0.0D, velocity.y, 0.0D);
            pet.setSprinting(false);

            path = Collections.emptyList();
            pathIndex = 0;
            failedTicks = 0;
            target = null;
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private void followPath() {
        try {
            if (pathIndex >= path.size()) { stop(); return; }

            BlockPos node = path.get(pathIndex);

            double nodeX = node.getX() + 0.5D;
            double nodeY = node.getY();
            double nodeZ = node.getZ() + 0.5D;

            double dx = nodeX - pet.getX();
            double dy = nodeY - pet.getY();
            double dz = nodeZ - pet.getZ();

            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

            if (horizontalDistance < 0.65D && Math.abs(dy) < 1.25D) {
                pathIndex++;
                if (pathIndex >= path.size()) { stop(); return; }

                node = path.get(pathIndex);
                nodeX = node.getX() + 0.5D;
                nodeY = node.getY();
                nodeZ = node.getZ() + 0.5D;

                dx = nodeX - pet.getX();
                dy = nodeY - pet.getY();
                dz = nodeZ - pet.getZ();
                horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            }

            if (horizontalDistance < 0.01D) return;

            double vx = dx / horizontalDistance * WALK_SPEED;
            double vz = dz / horizontalDistance * WALK_SPEED;

            Vec3d currentVelocity = pet.getVelocity();
            pet.setVelocity(vx, currentVelocity.y, vz);
            pet.setSprinting(false);

            updateRotation(nodeX, nodeY, nodeZ);

            if (node.getY() > pet.getBlockPos().getY() && pet.isOnGround()) {
                pet.jump();
            }

            if (isBlockedAhead(vx, vz) && pet.isOnGround()) {
                pet.setVelocity(vx, JUMP_VELOCITY, vz);
            }
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private void fallbackMove(BlockPos targetPos) {
        try {
            if (failedTicks > MAX_FAILURE_TICKS) { stop(); return; }

            double dx = targetPos.getX() + 0.5D - pet.getX();
            double dz = targetPos.getZ() + 0.5D - pet.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance < 0.05D) { stop(); return; }

            dx /= distance;
            dz /= distance;

            if (isLavaAhead(dx, dz)) { stop(); return; }

            if (isBlockedAhead(dx, dz)) {
                if (pet.isOnGround()) {
                    pet.setVelocity(dx * WALK_SPEED, JUMP_VELOCITY, dz * WALK_SPEED);
                } else {
                    stop();
                }
                return;
            }

            pet.setVelocity(dx * WALK_SPEED, pet.getVelocity().y, dz * WALK_SPEED);

            updateRotation(
                    targetPos.getX() + 0.5D,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5D
            );
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private void updateRotation(double targetX, double targetY, double targetZ) {
        try {
            double dx = targetX - pet.getX();
            double dz = targetZ - pet.getZ();
            double dy = targetY + pet.getStandingEyeHeight() - pet.getEyeY();
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

    private boolean isBlockedAhead(double vx, double vz) {
        try {
            BlockPos feet = pet.getBlockPos();
            int x = (int) Math.signum(vx);
            int z = (int) Math.signum(vz);

            if (x == 0 && z == 0) return false;

            BlockPos feetBlock = feet.add(x, 0, z);
            BlockPos headBlock = feet.add(x, 1, z);

            BlockState feetState = pet.getServerWorld().getBlockState(feetBlock);
            BlockState headState = pet.getServerWorld().getBlockState(headBlock);

            return !feetState.isAir() && !headState.isAir();
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return true;
        }
    }

    private boolean isLavaAhead(double vx, double vz) {
        try {
            BlockPos feet = pet.getBlockPos();
            int x = (int) Math.signum(vx);
            int z = (int) Math.signum(vz);

            if (x == 0 && z == 0) return false;

            BlockPos next = feet.add(x, 0, z);
            return pet.getServerWorld().getFluidState(next).isOf(Fluids.LAVA);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return true;
        }
    }

    private List<BlockPos> findPath(BlockPos start, BlockPos goal, int maxNodes) {
        try {
            ServerWorld world = pet.getServerWorld();

            if (!isWalkable(world, start) || !isWalkable(world, goal)) return null;

            PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::getF));
            Map<BlockPos, Node> nodes = new HashMap<>();
            Set<BlockPos> closed = new HashSet<>();

            Node startNode = new Node(start, null, 0.0D, heuristic(start, goal));
            open.add(startNode);
            nodes.put(start, startNode);

            int processed = 0;

            while (!open.isEmpty() && processed < maxNodes) {
                Node current = open.poll();

                if (!closed.add(current.pos)) continue;
                processed++;

                if (current.pos.equals(goal) || current.pos.getSquaredDistance(goal) <= 2.0D) {
                    return reconstruct(current);
                }

                for (BlockPos neighbor : getNeighbors(world, current.pos)) {
                    if (closed.contains(neighbor)) continue;

                    double newCost = current.g + movementCost(current.pos, neighbor);
                    Node old = nodes.get(neighbor);

                    if (old == null || newCost < old.g) {
                        Node next = new Node(neighbor, current, newCost, newCost + heuristic(neighbor, goal));
                        nodes.put(neighbor, next);
                        open.add(next);
                    }
                }
            }

            return null;
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return null;
        }
    }

    private List<BlockPos> getNeighbors(ServerWorld world, BlockPos pos) {
        List<BlockPos> result = new ArrayList<>();
        addNeighbor(world, result, pos.add(1, 0, 0));
        addNeighbor(world, result, pos.add(-1, 0, 0));
        addNeighbor(world, result, pos.add(0, 0, 1));
        addNeighbor(world, result, pos.add(0, 0, -1));
        addNeighbor(world, result, pos.add(1, 1, 0));
        addNeighbor(world, result, pos.add(-1, 1, 0));
        addNeighbor(world, result, pos.add(0, 1, 1));
        addNeighbor(world, result, pos.add(0, 1, -1));
        return result;
    }

    private void addNeighbor(ServerWorld world, List<BlockPos> result, BlockPos pos) {
        try {
            if (isWalkable(world, pos)) result.add(pos);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
    }

    private boolean isWalkable(ServerWorld world, BlockPos pos) {
        try {
            if (pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY() - 2) {
                return false;
            }

            BlockState feet = world.getBlockState(pos);
            BlockState head = world.getBlockState(pos.up());
            BlockState floor = world.getBlockState(pos.down());

            if (!feet.isAir() || !head.isAir()) return false;

            if (world.getFluidState(pos).isOf(Fluids.LAVA)
                    || world.getFluidState(pos.up()).isOf(Fluids.LAVA)) {
                return false;
            }

            return !floor.isAir();
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return false;
        }
    }

    private double movementCost(BlockPos from, BlockPos to) {
        if (to.getY() > from.getY()) return 1.4D;
        return 1.0D;
    }

    private double heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX())
                + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ());
    }

    private List<BlockPos> reconstruct(Node node) {
        List<BlockPos> result = new ArrayList<>();
        Node current = node;

        while (current != null) {
            result.add(current.pos);
            current = current.parent;
        }

        Collections.reverse(result);

        if (!result.isEmpty() && result.get(0).equals(pet.getBlockPos())) {
            result.remove(0);
        }

        return result;
    }

    private static final class Node {
        private final BlockPos pos;
        private final Node parent;
        private final double g;
        private final double f;

        private Node(BlockPos pos, Node parent, double g, double f) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.f = f;
        }

        private double getF() {
            return f;
        }
    }
}
