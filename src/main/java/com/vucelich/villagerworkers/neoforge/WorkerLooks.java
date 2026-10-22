package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.common.WorkerLook;
import net.minecraft.world.entity.npc.Villager;

/** Keeps the synced hat state aligned with saved worker data. Never equips an item. */
public final class WorkerLooks {
    private WorkerLooks() {}

    public static void refresh(Villager villager, WorkerData data) {
        if (villager == null || data == null) {
            return;
        }
        String name = villager.getCustomName() == null ? villager.getName().getString() : villager.getCustomName().getString();
        WorkerLook next = WorkerLook.from(data, name);
        if (villager.hasData(WorkerAttachments.LOOK) && next.equals(villager.getData(WorkerAttachments.LOOK))) {
            return;
        }
        villager.setData(WorkerAttachments.LOOK, next);
    }
}
