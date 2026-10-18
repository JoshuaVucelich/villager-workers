package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.WorkerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class WorkerMenu extends AbstractContainerMenu {
    public static final double RANGE_SQR = 36.0;

    private final Villager villager;
    private WorkerView view;

    public WorkerMenu(int containerId, Inventory inventory, Villager villager, WorkerView view) {
        super(ModMenus.WORKER.get(), containerId);
        this.villager = villager;
        this.view = view;
        WorkerData data = WorkerAttachments.get(villager).orElse(null);
        buildSlots(data == null ? new SimpleContainer(WorkerData.CARRY_SIZE) : new WorkerCarry(data), inventory);
    }

    private WorkerMenu(int containerId, Inventory inventory, WorkerView view) {
        super(ModMenus.WORKER.get(), containerId);
        this.villager = null;
        this.view = view;
        buildSlots(new SimpleContainer(WorkerData.CARRY_SIZE), inventory);
    }

    private void buildSlots(Container carry, Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(carry, column + row * 9, 32 + column * 18, 186 + row * 18));
            }
        }
        addSlot(new Slot(carry, WorkerData.TOOL_SLOT, 8, 204) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES);
            }
        });
        int playerLeft = 62;
        int playerTop = 248;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, playerLeft + column * 18, playerTop + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, playerLeft + column * 18, playerTop + 58));
        }
    }

    public static WorkerMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new WorkerMenu(containerId, inventory, WorkerView.STREAM_CODEC.decode(buf));
    }

    public Villager villager() {
        return villager;
    }

    public WorkerView view() {
        return view;
    }

    public void setView(WorkerView view) {
        this.view = view;
    }

    @Override
    public boolean stillValid(Player player) {
        Villager target = villager;
        if (target == null) {
            Level level = player.level();
            Entity entity = level.getEntity(view.entityId());
            if (entity instanceof Villager found) {
                target = found;
            }
        }
        return target != null
                && target.isAlive()
                && !target.isRemoved()
                && player.level() == target.level()
                && player.distanceToSqr(target) <= RANGE_SQR;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide() || villager == null) {
            return;
        }
        WorkerHold.release(villager.getUUID(), player.getUUID());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return WorkerServer.handleButton(player, this, id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int carryEnd = WorkerData.CARRY_SIZE;
        if (index < carryEnd) {
            if (!moveItemStackTo(stack, carryEnd, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, carryEnd, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
