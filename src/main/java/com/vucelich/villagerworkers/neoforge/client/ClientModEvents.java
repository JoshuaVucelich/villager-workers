package com.vucelich.villagerworkers.neoforge.client;

import com.vucelich.villagerworkers.neoforge.ModMenus;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ClientModEvents {
    private ClientModEvents() {}

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.WORKER.get(), WorkerScreen::new);
    }

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        var renderer = event.getRenderer(EntityType.VILLAGER);
        if (renderer instanceof LivingEntityRenderer<?, ?> living && living.getModel() instanceof VillagerModel<?>) {
            @SuppressWarnings("unchecked")
            LivingEntityRenderer<Villager, VillagerModel<Villager>> villagerRenderer =
                    (LivingEntityRenderer<Villager, VillagerModel<Villager>>) living;
            villagerRenderer.addLayer(new WorkerGearLayer(villagerRenderer));
        }
    }
}
