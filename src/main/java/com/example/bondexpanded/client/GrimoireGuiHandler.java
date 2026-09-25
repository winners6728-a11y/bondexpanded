package com.example.bondexpanded.client;

import com.example.bondexpanded.network.BondExpandedPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.HashMap;
import java.util.Map;

public final class GrimoireGuiHandler {
    private static final String[] COMMANDS = {
            "attack", "fetch", "guard", "come", "heal",
            "speed", "info", "release", "howl", "hunt"
    };

    private static final Map<String, Boolean> LOCAL_TOGGLES = new HashMap<>();

    private static final String[] LABELS = {
            "Атака", "Принеси", "Охрана", "Подойди", "Лечение",
            "Скорость", "Инфо", "Освободить", "Вой", "Охота"
    };

    private GrimoireGuiHandler() {
    }

    public static void attach(Screen screen) {
        try {
            String name = screen.getClass().getName().toLowerCase();
            if (!name.contains("petstatus") && !name.contains("grimoire") && !name.contains("bond")) {
                return;
            }

            int startX = Math.max(4, screen.width - 174);
            int startY = 30;

            for (int i = 0; i < COMMANDS.length; i++) {
                final String command = COMMANDS[i];
                final String label = LABELS[i];
                int column = i / 5;
                int row = i % 5;
                int x = startX + column * 84;
                int y = startY + row * 24;

                boolean toggle = command.equals("attack") || command.equals("guard") || command.equals("hunt");
                Text initial = Text.literal(label).formatted(toggle && LOCAL_TOGGLES.getOrDefault(command, false) ? Formatting.GREEN : Formatting.GRAY);
                ButtonWidget button = ButtonWidget.builder(
                        initial,
                        b -> {
                            if (toggle) {
                                boolean enabled = !LOCAL_TOGGLES.getOrDefault(command, false);
                                LOCAL_TOGGLES.put(command, enabled);
                                b.setMessage(Text.literal(label).formatted(enabled ? Formatting.GREEN : Formatting.GRAY));
                            }
                            send(command);
                        })
                        .dimensions(x, y, 80, 20)
                        .build();

                screen.addDrawableChild(button);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void send(String command) {
        try {
            ClientPlayNetworking.send(new BondExpandedPacket(command));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Text stateText(String label, boolean enabled) {
        return Text.literal(label).formatted(enabled ? Formatting.GREEN : Formatting.GRAY);
    }
}
