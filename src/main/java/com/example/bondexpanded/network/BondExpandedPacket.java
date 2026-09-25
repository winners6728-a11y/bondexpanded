package com.example.bondexpanded.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record BondExpandedPacket(String commandId) implements FabricPacket {

    public static final PacketType<BondExpandedPacket> TYPE = PacketType.create(
            new Identifier("bondexpanded", "command"),
            BondExpandedPacket::new
    );

    public BondExpandedPacket(PacketByteBuf buf) {
        this(buf.readString());
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeString(commandId);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
