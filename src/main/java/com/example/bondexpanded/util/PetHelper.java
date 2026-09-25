package com.example.bondexpanded.util;

import com.bondofthebeast.component.ModComponents;
import com.bondofthebeast.component.PlayerBondComponent;
import com.example.bondexpanded.BondExpanded;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public final class PetHelper {

    private PetHelper() {
    }

    public static PlayerBondComponent getComponent(PlayerEntity player) {
        try {
            if (player == null) {
                return null;
            }

            return ModComponents.PLAYER_BOND.get(player);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return null;
        }
    }

    public static ServerPlayerEntity getPet(ServerPlayerEntity owner) {
        try {
            if (owner == null || owner.getServer() == null) {
                return null;
            }

            PlayerBondComponent component = getComponent(owner);

            if (component == null || !component.hasOwner()) {
                return null;
            }

            UUID petUuid = parseUuid(component.getOwnerUUID());

            if (petUuid == null) {
                return null;
            }

            for (ServerPlayerEntity player :
                    owner.getServer().getPlayerManager().getPlayerList()) {
                if (petUuid.equals(player.getUuid())) {
                    return player;
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
            if (pet == null || pet.getServer() == null) {
                return null;
            }

            PlayerBondComponent component = getComponent(pet);

            if (component == null || !component.hasOwner()) {
                return null;
            }

            UUID ownerUuid = parseUuid(component.getOwnerUUID());

            if (ownerUuid == null) {
                return null;
            }

            return pet.getServer().getPlayerManager().getPlayer(ownerUuid);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return null;
        }
    }

    public static boolean isTamed(ServerPlayerEntity pet) {
        try {
            if (pet == null) {
                return false;
            }

            PlayerBondComponent component = getComponent(pet);
            return component != null && component.hasOwner();
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return false;
        }
    }

    public static String getStage(ServerPlayerEntity pet) {
        try {
            if (pet == null) {
                return "Неизвестно";
            }

            PlayerBondComponent component = getComponent(pet);

            if (component == null) {
                return "Неизвестно";
            }

            return String.valueOf(component.getBondLevel());
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return "Неизвестно";
        }
    }

    public static String getPetName(ServerPlayerEntity pet) {
        try {
            if (pet == null) {
                return "Неизвестно";
            }

            PlayerBondComponent component = getComponent(pet);

            if (component == null) {
                return pet.getName().getString();
            }

            String nickname = component.getPetNickname();

            if (nickname == null || nickname.isEmpty()) {
                return pet.getName().getString();
            }

            return nickname;
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return "Неизвестно";
        }
    }

    public static boolean releaseBond(ServerPlayerEntity pet) {
        try {
            if (pet == null) {
                return false;
            }

            PlayerBondComponent component = getComponent(pet);

            if (component == null) {
                return false;
            }

            component.clearOwner();
            return true;
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return false;
        }
    }

    private static UUID parseUuid(String value) {
        try {
            if (value == null || value.isBlank()) {
                return null;
            }

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
