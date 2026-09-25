package com.example.bondexpanded.client;

import com.example.bondexpanded.network.BondExpandedPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class GrimoireGuiHandler {

    private static final String[] COMMANDS = {
            "attack",
            "fetch",
            "guard",
            "come",
            "heal",
            "speed",
            "info",
            "release",
            "howl",
            "hunt"
    };

    private static final String[] LABELS = {
            "Атака",
            "Принеси",
            "Охрана",
            "Подойди",
            "Лечение",
            "Скорость",
            "Инфо",
            "Освободить",
            "Вой",
            "Охота"
    };

    private GrimoireGuiHandler() {
    }

    public static void attach(Screen screen) {
        try {
            String className = screen.getClass().getName();

            boolean isBondScreen =
                    className.contains("PetStatusScreen")
                            || className.contains("Grimoire")
                            || className.contains("Bond");

            if (!isBondScreen) {
                return;
            }

            List<net.minecraft.client.gui.widget.ClickableWidget> buttons =
                    Screens.getButtons(screen);

            int startX = Math.max(4, screen.width - 174);
            int startY = 30;

            for (int i = 0; i < COMMANDS.length; i++) {
                final String commandId = COMMANDS[i];

                int column = i / 5;
                int row = i % 5;

                int x = startX + column * 84;
                int y = startY + row * 24;

                ButtonWidget button = ButtonWidget.builder(
                                Text.literal(LABELS[i]),
                                ignored -> sendCommand(commandId)
                        )
                        .dimensions(x, y, 80, 20)
                        .build();

                buttons.add(button);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendCommand(String commandId) {
        try {
            ClientPlayNetworking.send(
                    new BondExpandedPacket(commandId)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
