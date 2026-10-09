package com.vucelich.villagerworkers.neoforge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
    }

    private WorkerMenu(int containerId, WorkerView view) {
        super(ModMenus.WORKER.get(), containerId);
        this.villager = null;
        this.view = view;
    }

    public static WorkerMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new WorkerMenu(containerId, WorkerView.STREAM_CODEC.decode(buf));
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
    public boolean clickMenuButton(Player player, int id) {
        return WorkerServer.handleButton(player, this, id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
