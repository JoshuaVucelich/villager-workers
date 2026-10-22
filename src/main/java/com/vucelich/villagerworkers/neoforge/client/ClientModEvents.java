package com.vucelich.villagerworkers.neoforge.client;

import com.vucelich.villagerworkers.neoforge.ModMenus;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ClientModEvents {
    private ClientModEvents() {}

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.WORKER.get(), WorkerScreen::new);
    }

    public static void registerLayers(EntityRenderersEvent.AddLayers event) {
        EntityRenderer<?> renderer = event.getRenderer(EntityType.VILLAGER);
        if (renderer instanceof VillagerRenderer villagers) {
            villagers.addLayer(new WorkerGearLayer(villagers));
        }
    }
}
