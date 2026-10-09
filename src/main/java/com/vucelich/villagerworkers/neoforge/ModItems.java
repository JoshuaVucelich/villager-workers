package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.VillagerWorkers;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VillagerWorkers.MOD_ID);
    public static final DeferredItem<AreaWandItem> AREA_WAND = ITEMS.registerItem(
            "area_wand",
            AreaWandItem::new,
            new Item.Properties().stacksTo(1));

    private ModItems() {}
}
