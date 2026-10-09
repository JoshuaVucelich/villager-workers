package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.VillagerWorkers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, VillagerWorkers.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<WorkerMenu>> WORKER = MENUS.register(
            "worker",
            () -> new MenuType<>((IContainerFactory<WorkerMenu>) WorkerMenu::fromNetwork, FeatureFlags.VANILLA_SET));

    private ModMenus() {}
}
