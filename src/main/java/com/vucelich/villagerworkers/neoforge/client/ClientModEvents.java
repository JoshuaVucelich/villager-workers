package com.vucelich.villagerworkers.neoforge.client;

import com.vucelich.villagerworkers.neoforge.ModMenus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ClientModEvents {
    private ClientModEvents() {}

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.WORKER.get(), WorkerScreen::new);
    }
}
