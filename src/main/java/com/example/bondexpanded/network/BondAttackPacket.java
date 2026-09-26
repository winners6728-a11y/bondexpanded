package com.example.bondexpanded.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record BondAttackPacket(UUID targetUuid) implements FabricPacket {

    public static final Identifier ID = new Identifier("bondexpanded", "attack");

    public static final PacketType<BondAttackPacket> TYPE =
            PacketType.create(ID, BondAttackPacket::read);

    public static BondAttackPacket read(PacketByteBuf buf) {
        return new BondAttackPacket(buf.readUuid());
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeUuid(targetUuid);
    }

    @Override
    public PacketType<BondAttackPacket> getType() {
        return TYPE;
    }
}
