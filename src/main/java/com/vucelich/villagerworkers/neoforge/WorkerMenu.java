package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.WorkerAction;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.common.WorkerPermissions;
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
    /**
     * Auto GUI scale on 1280x720 and 2560x1440 is 426 wide and 240 tall.
     * 416 by 232 stays inside that window. Slots sit on the right.
     */
    public static final int PANEL_WIDTH = 416;
    public static final int PANEL_HEIGHT = 232;
    public static final int CARRY_X = 252;
    public static final int CARRY_Y = 56;
    public static final int TOOL_X = 252;
    public static final int TOOL_Y = 114;
    public static final int PLAYER_X = 252;
    public static final int PLAYER_Y = 138;

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
                addSlot(new Slot(carry, column + row * 9, CARRY_X + column * 18, CARRY_Y + row * 18));
            }
        }
        addSlot(new Slot(carry, WorkerData.TOOL_SLOT, TOOL_X, TOOL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES);
            }
        });
        int playerLeft = PLAYER_X;
        int playerTop = PLAYER_Y;
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
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (player.level() != target.level() || player.distanceToSqr(target) > RANGE_SQR) {
            return false;
        }
        if (player.level().isClientSide()) {
            return true;
        }
        WorkerData data = WorkerAttachments.get(target).orElse(null);
        return data != null
                && WorkerPermissions.allows(data.owner(), data.trustedIds(), player.getUUID(), WorkerAction.OPEN_SCREEN);
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
