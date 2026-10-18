package com.vucelich.villagerworkers.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Runtime-only record of who has a worker's menu open.
 * Nothing here is written into the worker attachment.
 */
final class WorkerHold {
    private static final Map<UUID, Set<UUID>> VIEWERS = new ConcurrentHashMap<>();

    private WorkerHold() {}

    static void watch(Villager villager, ServerPlayer player) {
        if (villager == null || player == null) {
            return;
        }
        VIEWERS.computeIfAbsent(villager.getUUID(), id -> ConcurrentHashMap.newKeySet()).add(player.getUUID());
    }

    static void release(UUID villagerId, UUID playerId) {
        if (villagerId == null || playerId == null) {
            return;
        }
        Set<UUID> viewers = VIEWERS.get(villagerId);
        if (viewers == null) {
            return;
        }
        viewers.remove(playerId);
        if (viewers.isEmpty()) {
            VIEWERS.remove(villagerId, viewers);
        }
    }

    static void releasePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        VIEWERS.entrySet().removeIf(entry -> {
            entry.getValue().remove(playerId);
            if (entry.getValue().isEmpty()) {
                return true;
            }
            return false;
        });
    }

    static boolean watching(UUID villagerId) {
        Set<UUID> viewers = villagerId == null ? null : VIEWERS.get(villagerId);
        return viewers != null && !viewers.isEmpty();
    }

    static void forget(UUID villagerId) {
        if (villagerId == null) {
            return;
        }
        VIEWERS.remove(villagerId);
    }

    static void onServerStopped(ServerStoppedEvent event) {
        VIEWERS.clear();
    }

    /** Stop pathing before the villager tick so vanilla walking does not start. */
    static boolean preTick(Villager villager) {
        ServerPlayer look = refresh(villager);
        if (look == null) {
            return false;
        }
        holdStill(villager, look);
        return true;
    }

    /** Stop movement again after the tick, and freeze job timers. Position is left alone so a ship can carry the worker. */
    static boolean postTick(Villager villager) {
        ServerPlayer look = refresh(villager);
        if (look == null) {
            return false;
        }
        holdStill(villager, look);
        JobRunner.freezeWhileHeld(villager);
        return true;
    }

    private static ServerPlayer refresh(Villager villager) {
        if (!WorkerSettings.holdWhileMenuOpen()) {
            return null;
        }
        Set<UUID> viewers = VIEWERS.get(villager.getUUID());
        if (viewers == null || viewers.isEmpty()) {
            return null;
        }
        if (!(villager.level() instanceof ServerLevel level) || level.getServer() == null) {
            return null;
        }
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        List<UUID> stale = null;
        for (UUID id : viewers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (!stillWatching(player, villager)) {
                if (stale == null) {
                    stale = new ArrayList<>();
                }
                stale.add(id);
                continue;
            }
            double distance = player.distanceToSqr(villager);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (stale != null) {
            viewers.removeAll(stale);
            if (viewers.isEmpty()) {
                VIEWERS.remove(villager.getUUID(), viewers);
            }
        }
        return nearest;
    }

    private static boolean stillWatching(ServerPlayer player, Villager villager) {
        if (player == null || !player.isAlive() || player.isRemoved() || player.hasDisconnected()) {
            return false;
        }
        if (player.level() != villager.level() || player.distanceToSqr(villager) > WorkerMenu.RANGE_SQR) {
            return false;
        }
        if (!(player.containerMenu instanceof WorkerMenu menu) || menu.view() == null) {
            return false;
        }
        if (!menu.view().villagerId().equals(villager.getUUID())) {
            return false;
        }
        Villager bound = menu.villager();
        return bound == null || bound == villager || bound.getUUID().equals(villager.getUUID());
    }

    private static void holdStill(Villager villager, ServerPlayer look) {
        Vec3 motion = villager.getDeltaMovement();
        villager.setDeltaMovement(0.0, motion.y, 0.0);
        villager.setXxa(0.0F);
        villager.setZza(0.0F);
        villager.getNavigation().stop();
        var brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(look, true));
        villager.getLookControl().setLookAt(look, 30.0F, 30.0F);
        double dx = look.getX() - villager.getX();
        double dz = look.getZ() - villager.getZ();
        if (dx * dx + dz * dz > 1.0E-4) {
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0F / Math.PI)) - 90.0F;
            villager.setYRot(yaw);
            villager.setYHeadRot(yaw);
        }
    }
}
