package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.WorkerData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** Owners who are on this server right now. The primary owner is preferred when one of them must act. */
public final class OnlineOwners {
    private OnlineOwners() {}

    public static List<ServerPlayer> all(Villager villager, WorkerData data) {
        if (villager == null || data == null || villager.getServer() == null) {
            return List.of();
        }
        List<ServerPlayer> found = new ArrayList<>();
        for (UUID id : data.ownerIds()) {
            ServerPlayer player = villager.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                found.add(player);
            }
        }
        return found;
    }

    public static ServerPlayer first(Villager villager, WorkerData data) {
        List<ServerPlayer> online = all(villager, data);
        if (online.isEmpty()) {
            return null;
        }
        UUID primary = data.owner();
        for (ServerPlayer player : online) {
            if (player.getUUID().equals(primary)) {
                return player;
            }
        }
        return online.get(0);
    }
}
