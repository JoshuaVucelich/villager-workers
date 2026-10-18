package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.WorkerData;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Menu view of {@link WorkerData}'s carry slots. The same stacks the job reads. */
public final class WorkerCarry implements Container {
    private final WorkerData data;

    public WorkerCarry(WorkerData data) {
        this.data = data;
    }

    @Override
    public int getContainerSize() {
        return WorkerData.CARRY_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < WorkerData.CARRY_SIZE; slot++) {
            if (!data.carry(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return data.carry(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = data.carry(slot);
        if (stack.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = stack.split(amount);
        data.setCarry(slot, stack);
        setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = data.carry(slot);
        data.setCarry(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        data.setCarry(slot, stack);
        setChanged();
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < WorkerData.CARRY_SIZE; slot++) {
            data.setCarry(slot, ItemStack.EMPTY);
        }
    }
}
