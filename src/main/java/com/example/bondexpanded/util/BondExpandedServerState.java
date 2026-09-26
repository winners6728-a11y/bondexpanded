package com.example.bondexpanded.util;

import com.example.bondexpanded.BondExpanded;
import com.example.bondexpanded.network.BondControlPacket;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.server.MinecraftServer;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BondExpandedServerState {

    private static final Map<UUID, Long> healCooldown = new HashMap<>();
    private static final Map<UUID, Long> speedCooldown = new HashMap<>();
    private static final Map<UUID, Long> howlCooldown = new HashMap<>();
    private static final Map<UUID, String> activeCommands = new HashMap<>();
    private static final Map<UUID, UUID> droppedItems = new HashMap<>();

    // Активные ломания: petUuid -> (blockPos, toolSlot, prevSelectedSlot)
    private static final Map<UUID, BreakTask> breakTasks = new HashMap<>();

    private BondExpandedServerState() {
    }

    private static final class BreakTask {
        final BlockPos pos;
        final int toolSlot;
        final int prevSelectedSlot;
        int waitTicks = 0;

        BreakTask(BlockPos pos, int toolSlot, int prevSelectedSlot) {
            this.pos = pos;
            this.toolSlot = toolSlot;
            this.prevSelectedSlot = prevSelectedSlot;
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                tickProximity(server);
                tickFetchDrops(server);
                tickBreakTasks(server);
            } catch (Exception e) {
                BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            }
        });
    }

    public static void handleCommand(ServerPlayerEntity sender, String command) {
        try {
            if (command.startsWith("fetch_")) {
                handlePetFetchCommand(sender, command);
                return;
            }

            ServerPlayerEntity owner = sender;
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

    private static void handlePetFetchCommand(ServerPlayerEntity pet, String command) {
        switch (command) {
            case "fetch_arrived" -> handleFetchArrived(pet);
            case "fetch_drop" -> handleFetchDrop(pet);
        }
    }

    private static void handleFetchArrived(ServerPlayerEntity pet) {
        try {
            ServerPlayerEntity owner = PetHelper.getOwner(pet);
            if (owner == null) {
                ServerPlayNetworking.send(pet, new BondControlPacket("stop", "", -1, -1, -1));
                activeCommands.remove(pet.getUuid());
                return;
            }

            ServerWorld world = pet.getServerWorld();
            List<ItemEntity> items = world.getEntitiesByClass(
                    ItemEntity.class,
                    pet.getBoundingBox().expand(2.5D),
                    e -> e.isAlive() && !e.isRemoved()
            );

            if (items.isEmpty()) {
                ServerPlayNetworking.send(pet, new BondControlPacket("stop", "", -1, -1, -1));
                activeCommands.remove(pet.getUuid());
                pet.sendMessage(Text.literal("Предмет пропал"), false);
                return;
            }

            ItemEntity item = items.get(0);
            ItemStack stack = item.getStack().copy();
            item.discard();

            pet.getInventory().insertStack(stack);

            for (int i = 0; i < 9; i++) {
                if (!pet.getInventory().getStack(i).isEmpty()) {
                    pet.getInventory().selectedSlot = i;
                    break;
                }
            }

            ServerPlayNetworking.send(
                    pet,
                    new BondControlPacket("fetch_return", owner.getUuidAsString(), -1, -1, -1)
            );

        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка fetch_arrived: " + e.getMessage(), e);
        }
    }

    private static void handleFetchDrop(ServerPlayerEntity pet) {
        try {
            ServerPlayerEntity owner = PetHelper.getOwner(pet);
            if (owner == null) return;

            int slot = -1;
            ItemStack stack = ItemStack.EMPTY;

            for (int i = 0; i < pet.getInventory().size(); i++) {
                ItemStack s = pet.getInventory().getStack(i);
                if (!s.isEmpty()) {
                    stack = s.copy();
                    slot = i;
                    break;
                }
            }

            if (stack.isEmpty()) {
                ServerPlayNetworking.send(pet, new BondControlPacket("stop", "", -1, -1, -1));
                activeCommands.remove(pet.getUuid());
                return;
            }

            pet.getInventory().removeStack(slot);

            ServerWorld world = pet.getServerWorld();
            ItemEntity drop = new ItemEntity(
                    world,
                    pet.getX(),
                    pet.getEyeY() - 0.3D,
                    pet.getZ(),
                    stack
            );

            Vec3d dir = new Vec3d(
                    owner.getX() - pet.getX(),
                    0.0D,
                    owner.getZ() - pet.getZ()
            ).normalize();

            drop.setVelocity(dir.x * 0.25D, 0.15D, dir.z * 0.25D);
            drop.setPickupDelay(10);

            world.spawnEntity(drop);

            droppedItems.put(pet.getUuid(), drop.getUuid());

            ServerPlayNetworking.send(
                    pet,
                    new BondControlPacket("fetch_hold", owner.getUuidAsString(), -1, -1, -1)
            );

        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка fetch_drop: " + e.getMessage(), e);
        }
    }

    private static void tickFetchDrops(MinecraftServer server) {
        Iterator<Map.Entry<UUID, UUID>> it = droppedItems.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            UUID petUuid = entry.getKey();
            UUID dropUuid = entry.getValue();

            Entity dropEntity = null;
            for (ServerWorld w : server.getWorlds()) {
                Entity e = w.getEntity(dropUuid);
                if (e != null) {
                    dropEntity = e;
                    break;
                }
            }

            if (dropEntity == null || !dropEntity.isAlive() || dropEntity.isRemoved()) {
                ServerPlayerEntity pet = server.getPlayerManager().getPlayer(petUuid);

                if (pet != null) {
                    ServerPlayNetworking.send(pet, new BondControlPacket("stop", "", -1, -1, -1));
                    pet.sendMessage(Text.literal("Предмет подобран, команда завершена"), false);
                }

                activeCommands.remove(petUuid);
                it.remove();
            }
        }
    }

    /**
     * Следит за задачами ломания: когда блок сломан — подбирает дроп и возвращает инструмент.
     */
    private static void tickBreakTasks(MinecraftServer server) {
        Iterator<Map.Entry<UUID, BreakTask>> it = breakTasks.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, BreakTask> entry = it.next();
            UUID petUuid = entry.getKey();
            BreakTask task = entry.getValue();

            ServerPlayerEntity pet = server.getPlayerManager().getPlayer(petUuid);
            if (pet == null) {
                it.remove();
                continue;
            }

            task.waitTicks++;

            ServerWorld world = pet.getServerWorld();
            BlockState state = world.getBlockState(task.pos);

            if (!state.isAir()) continue;

            // Блок сломан: подбираем дроп
            List<ItemEntity> drops = world.getEntitiesByClass(
                    ItemEntity.class,
                    new Box(task.pos).expand(2.0D),
                    e -> e.isAlive() && !e.isRemoved()
            );

            for (ItemEntity drop : drops) {
                ItemStack stack = drop.getStack().copy();
                drop.discard();
                pet.getInventory().insertStack(stack);
            }

            // Возвращаем инструмент на место
            if (task.toolSlot >= 0) {
                ItemStack tool = pet.getInventory().getStack(task.toolSlot);
                ItemStack current = pet.getInventory().getStack(task.prevSelectedSlot);

                if (!tool.isEmpty()) {
                    pet.getInventory().setStack(task.toolSlot, current);
                    pet.getInventory().setStack(task.prevSelectedSlot, tool);
                }
            }

            ServerPlayerEntity owner = PetHelper.getOwner(pet);
            if (owner != null) {
                ServerPlayNetworking.send(
                        pet,
                        new BondControlPacket("fetch_return", owner.getUuidAsString(), -1, -1, -1)
                );
            }

            it.remove();
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

        Box box = owner.getBoundingBox().stretch(dir.multiply(20.0D)).expand(2.0D);

        List<ItemEntity> items = owner.getServerWorld().getEntitiesByClass(
                ItemEntity.class, box,
                e -> e.isAlive() && !e.isRemoved()
        );

        ItemEntity best = null;
        double bestScore = -1;

        for (ItemEntity item : items) {
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

        // Ищем подходящий инструмент в инвентаре питомца
        int toolSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = pet.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof MiningToolItem) {
                toolSlot = i;
                break;
            }
        }

        int prevSelected = pet.getInventory().selectedSlot;

        // Переставляем инструмент в руку
        if (toolSlot >= 0 && toolSlot != prevSelected) {
            ItemStack tool = pet.getInventory().getStack(toolSlot).copy();
            ItemStack old = pet.getInventory().getStack(prevSelected).copy();

            pet.getInventory().setStack(toolSlot, old);
            pet.getInventory().setStack(prevSelected, tool);
            pet.getInventory().selectedSlot = prevSelected;
        }

        breakTasks.put(pet.getUuid(), new BreakTask(pos, toolSlot, prevSelected));

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

    private static void tickProximity(MinecraftServer server) {
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
