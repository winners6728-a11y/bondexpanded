package com.example.bondexpanded.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record BondGiveRequestPacket(int[] slots, List<ItemStack> stacks) implements FabricPacket {

    public static final Identifier ID = new Identifier("bondexpanded", "give_request");

    public static final PacketType<BondGiveRequestPacket> TYPE =
            PacketType.create(ID, BondGiveRequestPacket::read);

    public static BondGiveRequestPacket read(PacketByteBuf buf) {
        int count = buf.readInt();
        int[] slots = new int[count];
        List<ItemStack> stacks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            slots[i] = buf.readInt();
            stacks.add(buf.readItemStack());
        }
        return new BondGiveRequestPacket(slots, stacks);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeInt(slots.length);
        for (int i = 0; i < slots.length; i++) {
            buf.writeInt(slots[i]);
            buf.writeItemStack(stacks.get(i));
        }
    }

    @Override
    public PacketType<BondGiveRequestPacket> getType() {
        return TYPE;
    }
}
