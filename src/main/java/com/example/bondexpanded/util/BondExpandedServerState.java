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

import java.util.ArrayList;
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
    private static final Map<UUID, BreakTask> breakTasks = new HashMap<>();

    /** Слоты, в которые питомец положил поднятый дроп (для fetch и break). */
    private static final Map<UUID, List<Integer>> droppedSlots = new HashMap<>();

    /** Инструмент для break: слот и предыдущий selectedSlot. */
    private static final Map<UUID, int[]> toolState = new HashMap<>();

    private BondExpandedServerState() {
    }

    private static final class BreakTask {
        final BlockPos pos;
        BreakTask(BlockPos pos) {
            this.pos = pos;
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

            // UTILITY — всегда
            switch (command) {
                case "heal" -> { applyHeal(owner, pet); return; }
                case "speed" -> { applySpeed(owner, pet); return; }
                case "info" -> { showInfo(owner, pet); return; }
                case "howl" -> { doHowl(owner, pet); return; }
                case "stop" -> { sendControl(owner, pet, "stop", null); return; }
            }

            // AI — только финальная стадия + ошейник
            if (!PetHelper.isFinalStage(pet)) {
                owner.sendMessage(Text.literal("Питомец должен быть в финальной форме"), false);
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

    /**
     * Питомец подошёл к предмету. Подбираем его в СВОБОДНЫЙ слот хотбара и запоминаем слот.
     */
    private static void handleFetchArrived(ServerPlayerEntity pet) {
        try {
            ServerPlayerEntity owner = PetHelper.getOwner(pet);
            if (owner == null) {
                stopPet(pet);
                return;
            }

            ServerWorld world = pet.getServerWorld();
            List<ItemEntity> items = world.getEntitiesByClass(
                    ItemEntity.class,
                    pet.getBoundingBox().expand(2.5D),
                    e -> e.isAlive() && !e.isRemoved()
            );

            if (items.isEmpty()) {
                owner.sendMessage(Text.literal("Предмет пропал"), false);
                stopPet(pet);
                return;
            }

            ItemEntity item = items.get(0);
            ItemStack stack = item.getStack().copy();

            int freeSlot = findFreeHotbarSlot(pet);
            if (freeSlot == -1) {
                owner.sendMessage(Text.literal("У питомца нет свободного слота"), false);
                stopPet(pet);
                return;
            }

            item.discard();
            pet.getInventory().setStack(freeSlot, stack);
            pet.getInventory().selectedSlot = freeSlot;

            droppedSlots.computeIfAbsent(pet.getUuid(), k -> new ArrayList<>()).add(freeSlot);

            ServerPlayNetworking.send(
                    pet,
                    new BondControlPacket("fetch_return", owner.getUuidAsString(), -1, -1, -1)
            );

        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка fetch_arrived: " + e.getMessage(), e);
        }
    }

    /**
     * Питомец дошёл до хозяина. Выкидываем ТОЛЬКО те предметы, что подобрали (из droppedSlots).
     */
    private static void handleFetchDrop(ServerPlayerEntity pet) {
        try {
            ServerPlayerEntity owner = PetHelper.getOwner(pet);
            if (owner == null) return;

            List<Integer> slots = droppedSlots.remove(pet.getUuid());

            if (slots == null || slots.isEmpty()) {
                // Нечего выкидывать - просто завершаем
                stopPet(pet);
                return;
            }

            ServerWorld world = pet.getServerWorld();
            int thrown = 0;

            for (int slot : slots) {
                ItemStack stack = pet.getInventory().getStack(slot);
                if (stack.isEmpty()) continue;

                ItemStack copy = stack.copy();
                pet.getInventory().setStack(slot, ItemStack.EMPTY);

                ItemEntity drop = new ItemEntity(
                        world,
                        pet.getX(),
                        pet.getEyeY() - 0.3D,
                        pet.getZ(),
                        copy
                );

                Vec3d dir = new Vec3d(
                        owner.getX() - pet.getX(),
                        0.0D,
                        owner.getZ() - pet.getZ()
                ).normalize();

                drop.setVelocity(dir.x * 0.25D, 0.15D, dir.z * 0.25D);
                drop.setPickupDelay(10);

                world.spawnEntity(drop);

                // Следим за последним выкинутым (если несколько - подберут все, но этого достаточно)
                droppedItems.put(pet.getUuid(), drop.getUuid());
                thrown++;
            }

            if (thrown == 0) {
                stopPet(pet);
                return;
            }

            // Возвращаем инструмент в исходный слот, если был break
            int[] tool = toolState.remove(pet.getUuid());
            if (tool != null && tool[1] >= 0 && tool[1] < 9) {
                pet.getInventory().selectedSlot = tool[1];
            }

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
                droppedSlots.remove(petUuid);
                it.remove();
            }
        }
    }

    /**
     * Следим за блоками, которые ломает питомец. Как только блок сломан - собираем ВСЕ дропы
     * в свободные слоты, запоминаем их, отправляем fetch_return.
     */
    private static void tickBreakTasks(MinecraftServer server) {
        Iterator<Map.Entry<UUID, BreakTask>> it = breakTasks.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, BreakTask> entry = it.next();
            UUID petUuid = entry.getKey();
            BreakTask task = entry.getValue();

            ServerPlayerEntity pet = server.getPlayerManager().getPlayer(petUuid);
            if (pet == null) { it.remove(); continue; }

            ServerWorld world = pet.getServerWorld();
            BlockState state = world.getBlockState(task.pos);

            if (!state.isAir()) continue;

            // Блок сломан - собираем ВСЕ дропы в радиусе
            List<ItemEntity> drops = world.getEntitiesByClass(
                    ItemEntity.class,
                    new Box(task.pos).expand(2.5D),
                    e -> e.isAlive() && !e.isRemoved()
            );

            List<Integer> slots = droppedSlots.computeIfAbsent(petUuid, k -> new ArrayList<>());

            for (ItemEntity drop : drops) {
                int freeSlot = findFreeHotbarSlot(pet);
                if (freeSlot == -1) break;

                ItemStack stack = drop.getStack().copy();
                drop.discard();

                pet.getInventory().setStack(freeSlot, stack);
                slots.add(freeSlot);
            }

            // Вернуть селектед слот к инструменту или предыдущему
            int[] tool = toolState.get(petUuid);
            if (tool != null && tool[1] >= 0 && tool[1] < 9) {
                // ничего пока не делаем, вернём после дропа
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
                droppedSlots.remove(pet.getUuid());
                breakTasks.remove(pet.getUuid());
                toolState.remove(pet.getUuid());
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

        // Ищем подходящий инструмент и переключаемся на него
        int prevSelected = pet.getInventory().selectedSlot;
        int toolSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = pet.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof MiningToolItem) {
                toolSlot = i;
                break;
            }
        }

        if (toolSlot >= 0) {
            pet.getInventory().selectedSlot = toolSlot;
        }

        toolState.put(pet.getUuid(), new int[]{toolSlot, prevSelected});

        breakTasks.put(pet.getUuid(), new BreakTask(pos));
        sendControl(owner, pet, "break", pos);
    }

    private static int findFreeHotbarSlot(ServerPlayerEntity pet) {
        for (int i = 0; i < 9; i++) {
            if (pet.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private static void stopPet(ServerPlayerEntity pet) {
        ServerPlayNetworking.send(pet, new BondControlPacket("stop", "", -1, -1, -1));
        activeCommands.remove(pet.getUuid());
        droppedSlots.remove(pet.getUuid());
        breakTasks.remove(pet.getUuid());
        toolState.remove(pet.getUuid());
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
            if (!PetHelper.isFinalStage(pet)) continue;
            if (!owner.getWorld().getRegistryKey().equals(pet.getWorld().getRegistryKey())) continue;
            if (owner.distanceTo(pet) > 8.0D) continue;

            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, false, true));
            pet.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, false, true));
        }
    }
}
