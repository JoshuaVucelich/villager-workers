package com.vucelich.villagerworkers.neoforge;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.LockCode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Chest and barrel access for worker hauling. Create blocks are never opened. */
public final class ContainerAccess {
    public enum Failure {
        NONE,
        MISSING,
        NO_ACCESS
    }

    private ContainerAccess() {}

    /**
     * Opens a chest or barrel for the owner. Spawn protection, the world border, and both halves of a double chest
     * are checked again on every call. Lock checks do not call {@code canOpen}, which would play the locked sound.
     * An offline owner cannot open anything.
     */
    public static Container open(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        if (failure(level, pos, owner) != Failure.NONE) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof Container container ? container : null;
    }

    public static Failure failure(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return Failure.MISSING;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) && !(state.getBlock() instanceof BarrelBlock)) {
            return Failure.MISSING;
        }
        if (owner == null || !mayUse(level, pos, owner) || !locksAllow(level, pos, owner)) {
            return Failure.NO_ACCESS;
        }
        return Failure.NONE;
    }

    /** Spawn protection and the world border, including the other half of a double chest. */
    public static boolean mayUse(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level == null || pos == null || player == null || !level.isLoaded(pos)) {
            return false;
        }
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        BlockPos other = partner(level, pos);
        return other == null || level.mayInteract(player, other);
    }

    /**
     * One locked-container warning for the player who clicked. Hauling must not call this: it pauses with
     * {@code NO_ACCESS} instead of repeating the sound.
     */
    public static boolean denyIfLocked(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BaseContainerBlockEntity locked = firstLocked(level, pos, player);
        if (locked == null) {
            return false;
        }
        locked.canOpen(player);
        return true;
    }

    private static boolean locksAllow(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!halfUnlocks(level.getBlockEntity(pos), player)) {
            return false;
        }
        BlockPos other = partner(level, pos);
        return other == null || halfUnlocks(level.getBlockEntity(other), player);
    }

    private static BaseContainerBlockEntity firstLocked(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level == null || pos == null || player == null) {
            return null;
        }
        BaseContainerBlockEntity here = lockedHalf(level.getBlockEntity(pos), player);
        if (here != null) {
            return here;
        }
        BlockPos other = partner(level, pos);
        return other == null ? null : lockedHalf(level.getBlockEntity(other), player);
    }

    private static BaseContainerBlockEntity lockedHalf(BlockEntity entity, ServerPlayer player) {
        return entity instanceof BaseContainerBlockEntity base && !halfUnlocks(base, player) ? base : null;
    }

    /** Same rule as {@code canOpen}, without the locked message or sound. */
    private static boolean halfUnlocks(BlockEntity entity, ServerPlayer player) {
        if (!(entity instanceof BaseContainerBlockEntity base)) {
            return true;
        }
        LockCode lock = base.collectComponents().getOrDefault(DataComponents.LOCK, LockCode.NO_LOCK);
        return player.isSpectator() || lock.unlocksWith(player.getMainHandItem());
    }

    private static BlockPos partner(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return null;
        }
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        BlockState otherState = level.getBlockState(other);
        return otherState.getBlock() instanceof ChestBlock ? other : null;
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
