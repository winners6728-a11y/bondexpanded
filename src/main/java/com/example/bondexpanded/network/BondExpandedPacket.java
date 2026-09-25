package com.example.bondexpanded.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record BondExpandedPacket(String commandId) implements FabricPacket {
    public static final Identifier ID = new Identifier("bondexpanded", "command");

    public BondExpandedPacket {
        if (commandId == null || commandId.length() > 32) {
            throw new IllegalArgumentException("Недопустимая команда");
        }
    }

    public static BondExpandedPacket read(PacketByteBuf buf) {
        return new BondExpandedPacket(buf.readString(32));
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeString(commandId);
    }

    @Override
    public Identifier getPacketId() {
        return ID;
    }
}
