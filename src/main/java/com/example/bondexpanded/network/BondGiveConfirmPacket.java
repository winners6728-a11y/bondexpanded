package com.example.bondexpanded.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record BondGiveConfirmPacket(int[] slots) implements FabricPacket {

    public static final Identifier ID = new Identifier("bondexpanded", "give_confirm");

    public static final PacketType<BondGiveConfirmPacket> TYPE =
            PacketType.create(ID, BondGiveConfirmPacket::read);

    public static BondGiveConfirmPacket read(PacketByteBuf buf) {
        int count = buf.readInt();
        int[] arr = new int[count];
        for (int i = 0; i < count; i++) arr[i] = buf.readInt();
        return new BondGiveConfirmPacket(arr);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeInt(slots.length);
        for (int s : slots) buf.writeInt(s);
    }

    @Override
    public PacketType<BondGiveConfirmPacket> getType() {
        return TYPE;
    }
}
