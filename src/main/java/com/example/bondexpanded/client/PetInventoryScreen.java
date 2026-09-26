package com.example.bondexpanded.client;

import com.example.bondexpanded.network.BondGiveConfirmPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PetInventoryScreen extends Screen {

    private final int[] slots;
    private final List<ItemStack> stacks;
    private final Set<Integer> selected = new HashSet<>();
    private int guiLeft;
    private int guiTop;

    public PetInventoryScreen(int[] slots, List<ItemStack> stacks) {
        super(Text.literal("Инвентарь питомца"));
        this.slots = slots;
        this.stacks = stacks;
    }

    @Override
    protected void init() {
        super.init();

        int cols = 9;
        int rows = Math.max(1, (stacks.size() + cols - 1) / cols);

        guiLeft = (width - cols * 20) / 2 - 20;
        guiTop = (height - rows * 20) / 2;

        int checkX = guiLeft + cols * 20 + 10;
        int checkY = guiTop + rows * 20 / 2 - 10;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("V").styled(s -> s.withColor(0x55FF55)),
                b -> confirm()
        ).dimensions(checkX, checkY, 30, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("X"),
                b -> close()
        ).dimensions(checkX, checkY + 25, 30, 20).build());
    }

    private void confirm() {
        List<Integer> list = new ArrayList<>();
        for (int idx : selected) {
            if (idx >= 0 && idx < slots.length) list.add(slots[idx]);
        }
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);

        try {
            ClientPlayNetworking.send(new BondGiveConfirmPacket(arr));
        } catch (Exception e) {
            e.printStackTrace();
        }

        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, guiTop - 25, 0xFFFFFF);

        int cols = 9;

        for (int i = 0; i < stacks.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = guiLeft + col * 20;
            int y = guiTop + row * 20;

            int bgColor = selected.contains(i) ? 0xFF00AA00 : 0xFF333333;

            context.fill(x, y, x + 18, y + 18, bgColor);
            context.drawBorder(x, y, 18, 18, 0xFFFFFFFF);

            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                context.drawItem(stack, x + 1, y + 1);
                context.drawItemInSlot(textRenderer, stack, x + 1, y + 1);
            }
        }

        for (int i = 0; i < stacks.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = guiLeft + col * 20;
            int y = guiTop + row * 20;

            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                ItemStack stack = stacks.get(i);
                if (!stack.isEmpty()) {
                    context.drawItemTooltip(textRenderer, stack, mouseX, mouseY);
                }
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int cols = 9;

        for (int i = 0; i < stacks.size(); i++) {
            if (stacks.get(i).isEmpty()) continue;

            int col = i % cols;
            int row = i / cols;
            int x = guiLeft + col * 20;
            int y = guiTop + row * 20;

            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                if (selected.contains(i)) selected.remove(i);
                else selected.add(i);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
