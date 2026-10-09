package com.vucelich.villagerworkers.neoforge;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Chest and barrel access for worker hauling. Create blocks are never opened. */
public final class ContainerAccess {
    private ContainerAccess() {}

    /**
     * Opens a chest or barrel for the owner. Spawn protection, the world border, and a container lock are checked
     * again on every call. An offline owner cannot open anything.
     */
    public static Container open(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        if (level == null || pos == null || owner == null || !level.isLoaded(pos)) {
            return null;
        }
        if (!level.mayInteract(owner, pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BaseContainerBlockEntity container && !container.canOpen(owner)) {
            return null;
        }
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        if (!(state.getBlock() instanceof BarrelBlock)) {
            return null;
        }
        return entity instanceof Container container ? container : null;
    }

    public static ItemStack insert(Container container, ItemStack stack) {
        if (container == null || stack == null || stack.isEmpty()) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        stack = stack.copy();
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) {
                continue;
            }
            int room = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (room <= 0) {
                continue;
            }
            existing.grow(room);
            stack.shrink(room);
            container.setItem(slot, existing);
        }
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, stack)) {
                continue;
            }
            int move = Math.min(stack.getMaxStackSize(), stack.getCount());
            container.setItem(slot, stack.split(move));
        }
        container.setChanged();
        return stack;
    }

    /** Removes one item, preferring the stack with the most durability left. */
    public static ItemStack takeBest(Container container, Predicate<ItemStack> predicate) {
        if (container == null || predicate == null) {
            return ItemStack.EMPTY;
        }
        int bestSlot = -1;
        int bestRemaining = Integer.MIN_VALUE;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !predicate.test(stack)) {
                continue;
            }
            int remaining = stack.isDamageableItem()
                    ? stack.getMaxDamage() - stack.getDamageValue()
                    : Integer.MAX_VALUE;
            if (remaining > bestRemaining) {
                bestRemaining = remaining;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack slot = container.getItem(bestSlot);
        ItemStack taken = slot.split(1);
        container.setItem(bestSlot, slot);
        container.setChanged();
        return taken;
    }
}
