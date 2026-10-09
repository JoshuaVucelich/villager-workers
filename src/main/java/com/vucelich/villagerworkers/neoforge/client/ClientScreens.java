package com.vucelich.villagerworkers.neoforge.client;

import com.vucelich.villagerworkers.neoforge.ModPayloads;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public final class ClientScreens {
    private ClientScreens() {}

    public static void openHire(UUID villagerId, String name) {
        Minecraft.getInstance().setScreen(new HireScreen(villagerId, name));
    }

    public static void applySnapshot(com.vucelich.villagerworkers.neoforge.WorkerView view) {
        if (Minecraft.getInstance().screen instanceof WorkerScreen screen) {
            screen.applySnapshot(view);
        }
    }

    public static void applyPermissions(ModPayloads.PermissionsView view) {
        if (Minecraft.getInstance().screen instanceof WorkerScreen screen) {
            screen.applyPermissions(view);
        }
    }
}
