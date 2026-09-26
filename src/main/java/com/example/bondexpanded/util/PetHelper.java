package com.example.bondexpanded.util;

import com.bondofthebeast.component.ModComponents;
import com.bondofthebeast.component.PlayerBondComponent;
import com.example.bondexpanded.BondExpanded;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public final class PetHelper {

    public static final int FINAL_STAGE_LEVEL = 4;

    private PetHelper() {
    }

    public static PlayerBondComponent getComponent(PlayerEntity player) {
        try {
            if (player == null) return null;
            return ModComponents.PLAYER_BOND.get(player);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return null;
        }
    }

    public static ServerPlayerEntity getPet(ServerPlayerEntity owner) {
        try {
            if (owner == null || owner.getServer() == null) return null;

            UUID ownerUuid = owner.getUuid();

            for (ServerPlayerEntity candidate : owner.getServer().getPlayerManager().getPlayerList()) {
                try {
                    PlayerBondComponent component = getComponent(candidate);
                    if (component == null) continue;
                    if (!component.hasOwner()) continue;

                    String ownerUuidString = component.getOwnerUUID();
                    UUID storedOwnerUuid = parseUuid(ownerUuidString);

                    if (storedOwnerUuid == null) continue;
                    if (storedOwnerUuid.equals(ownerUuid)) return candidate;

                } catch (Exception e) {
                    BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
                }
            }

            return null;

        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return null;
        }
    }

    public static ServerPlayerEntity getOwner(ServerPlayerEntity pet) {
        try {
            if (pet == null || pet.getServer() == null) return null;

            PlayerBondComponent component = getComponent(pet);
            if (component == null || !component.hasOwner()) return null;

            String ownerUuidString = component.getOwnerUUID();
            UUID ownerUuid = parseUuid(ownerUuidString);

            if (ownerUuid == null) return null;

            return pet.getServer().getPlayerManager().getPlayer(ownerUuid);

        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return null;
        }
    }

    public static boolean isTamed(ServerPlayerEntity pet) {
        try {
            if (pet == null) return false;
            PlayerBondComponent component = getComponent(pet);
            return component != null && component.hasOwner();
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return false;
        }
    }

    public static String getStage(ServerPlayerEntity pet) {
        try {
            if (pet == null) return "Неизвестно";
            PlayerBondComponent component = getComponent(pet);
            if (component == null) return "Неизвестно";
            return String.valueOf(component.getBondLevel());
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return "Неизвестно";
        }
    }

    public static int getBondLevel(ServerPlayerEntity pet) {
        try {
            if (pet == null) return -1;
            PlayerBondComponent component = getComponent(pet);
            if (component == null) return -1;
            return component.getBondLevel();
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return -1;
        }
    }

    public static boolean isFinalStage(ServerPlayerEntity pet) {
        try {
            int level = getBondLevel(pet);
            return level >= FINAL_STAGE_LEVEL;
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return false;
        }
    }

    public static String getPetName(ServerPlayerEntity pet) {
        try {
            if (pet == null) return "Неизвестно";

            PlayerBondComponent component = getComponent(pet);
            if (component == null) return pet.getName().getString();

            String nickname = component.getPetNickname();
            if (nickname == null || nickname.isEmpty()) return pet.getName().getString();

            return nickname;

        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return "Неизвестно";
        }
    }

    public static boolean releaseBond(ServerPlayerEntity pet) {
        try {
            if (pet == null) return false;
            PlayerBondComponent component = getComponent(pet);
            if (component == null) return false;
            if (!component.hasOwner()) return false;
            component.clearOwner();
            return true;
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return false;
        }
    }

    public static boolean hasCollar(ServerPlayerEntity pet) {
        try {
            if (pet == null) return false;
            PlayerBondComponent component = getComponent(pet);
            if (component == null) return false;

            String[] candidates = {
                    "hasCollar",
                    "isCollarEquipped",
                    "hasCollarEquipped",
                    "isCollarOn",
                    "getCollar"
            };

            for (String name : candidates) {
                try {
                    java.lang.reflect.Method m = component.getClass().getMethod(name);
                    Object result = m.invoke(component);
                    if (result instanceof Boolean) return (Boolean) result;
                    if (result != null) return true;
                } catch (NoSuchMethodException ignored) {
                } catch (Exception e) {
                    BondExpanded.LOGGER.error("Ошибка вызова " + name + ": " + e.getMessage(), e);
                }
            }

            return true;

        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка hasCollar: " + e.getMessage(), e);
            return true;
        }
    }

    private static UUID parseUuid(String value) {
        try {
            if (value == null || value.isBlank()) return null;
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return null;
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return null;
        }
    }
}
