package com.example.bondexpanded.command;

import com.example.bondexpanded.util.BondExpandedServerState;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public final class BondExpandedCommands {
    private BondExpandedCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        try {
            dispatcher.register(CommandManager.literal("bond")
                    .then(CommandManager.literal("attack").executes(context -> execute(context, "attack")))
                    .then(CommandManager.literal("fetch").executes(context -> execute(context, "fetch")))
                    .then(CommandManager.literal("guard").executes(context -> execute(context, "guard")))
                    .then(CommandManager.literal("come").executes(context -> execute(context, "come")))
                    .then(CommandManager.literal("heal").executes(context -> execute(context, "heal")))
                    .then(CommandManager.literal("speed").executes(context -> execute(context, "speed")))
                    .then(CommandManager.literal("info").executes(context -> execute(context, "info")))
                    .then(CommandManager.literal("release").executes(context -> execute(context, "release")))
                    .then(CommandManager.literal("howl").executes(context -> execute(context, "howl")))
                    .then(CommandManager.literal("hunt").executes(context -> execute(context, "hunt"))));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int execute(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context, String command) {
        try {
            BondExpandedServerState.handleCommand(context.getSource().getPlayer(), command);
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}
