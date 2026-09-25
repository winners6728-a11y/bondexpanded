package com.example.bondexpanded.util;

import com.bondofthebeast.component.PlayerBondComponent;
import com.example.bondexpanded.BondExpanded;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PetHelper {
    private PetHelper() {
    }

    public static ServerPlayerEntity getPet(ServerPlayerEntity owner) {
        try {
            if (owner == null || owner.getServer() == null) {
                return null;
            }

            PlayerBondComponent component = com.bondofthebeast.component.ModComponents.PLAYER_BOND.get(owner);
            if (component == null || !component.hasOwner()) {
                return null;
            }

            UUID petUuid = component.getOwnerUUID();
            if (petUuid == null) {
                return null;
            }

            for (ServerPlayerEntity player : owner.getServer().getPlayerManager().getPlayerList()) {
                if (petUuid.equals(player.getUuid())) {
                    return player;
                }
            }
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
        return null;
    }

    public static ServerPlayerEntity getOwner(ServerPlayerEntity pet) {
        try {
            if (pet == null || pet.getServer() == null) {
                return null;
            }

            PlayerBondComponent component = com.bondofthebeast.component.ModComponents.PLAYER_BOND.get(pet);
            if (component == null || !component.hasOwner()) {
                return null;
            }

            UUID ownerUuid = component.getOwnerUUID();
            if (ownerUuid == null) {
                return null;
            }

            return pet.getServer().getPlayerManager().getPlayer(ownerUuid);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
        }
        return null;
    }

    public static boolean isTamed(ServerPlayerEntity pet) {
        try {
            return pet != null && com.bondofthebeast.component.ModComponents.PLAYER_BOND.get(pet).hasOwner();
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return false;
        }
    }

    public static String getStage(ServerPlayerEntity pet) {
        try {
            PlayerBondComponent component = com.bondofthebeast.component.ModComponents.PLAYER_BOND.get(pet);
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
            PlayerBondComponent component = com.bondofthebeast.component.ModComponents.PLAYER_BOND.get(pet);
            if (component == null || component.getPetNickname() == null) {
                return pet == null ? "Неизвестно" : pet.getName().getString();
            }
            return component.getPetNickname();
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return "Неизвестно";
        }
    }

    public static boolean releaseBond(ServerPlayerEntity pet) {
        try {
            PlayerBondComponent component = com.bondofthebeast.component.ModComponents.PLAYER_BOND.get(pet);
            if (component == null) {
                return false;
            }

            Method clearOwner = component.getClass().getMethod("clearOwner");
            clearOwner.invoke(component);
            return true;
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return false;
        }
    }

    public static PlayerBondComponent getComponent(PlayerEntity player) {
        try {
            return com.bondofthebeast.component.ModComponents.PLAYER_BOND.get(player);
        } catch (Exception e) {
            BondExpanded.LOGGER.error("Ошибка: " + e.getMessage(), e);
            return null;
        }
    }
}
