package com.example.bondexpanded.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record BondControlPacket(String command, String ownerUuid) implements FabricPacket {

    public static final Identifier ID = new Identifier("bondexpanded", "control");

    public static final PacketType<BondControlPacket> TYPE =
            PacketType.create(ID, BondControlPacket::read);

    public static BondControlPacket read(PacketByteBuf buf) {
        return new BondControlPacket(
                buf.readString(32),
                buf.readString(36)
        );
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeString(command);
        buf.writeString(ownerUuid);
    }

    @Override
    public PacketType<BondControlPacket> getType() {
        return TYPE;
    }
}
