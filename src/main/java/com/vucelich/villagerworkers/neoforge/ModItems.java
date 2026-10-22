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
    /** Yellow hard hat worn by every worker. Rendered on the villager, not equipped as armor. */
    public static final DeferredItem<Item> HARD_HAT = ITEMS.registerSimpleItem("hard_hat");
    /** Orange hard hat worn by a foreman. */
    public static final DeferredItem<Item> FOREMAN_HAT = ITEMS.registerSimpleItem("foreman_hat");
    /** Clipboard held in a foreman's hand. */
    public static final DeferredItem<Item> CLIPBOARD = ITEMS.registerSimpleItem("clipboard");
    /** Rolled blueprint carried under a foreman's arm. */
    public static final DeferredItem<Item> BLUEPRINT_ROLL = ITEMS.registerSimpleItem("blueprint_roll");

    private ModItems() {}
}
