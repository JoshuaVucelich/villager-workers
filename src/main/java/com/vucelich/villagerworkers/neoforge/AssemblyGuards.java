package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.ActionPace;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Blocks that belong to a moving assembly are not ordinary world blocks.
 * Sable and Create are asked by name so this class still loads when either mod is absent.
 * A failed lookup while the mod is present is occupied, so a worker does not dig through a ship it cannot see.
 */
final class AssemblyGuards {
    private static boolean shipLookupFailed;
    private static boolean shipFailureLogged;
    private static long shipRetryAt = Long.MIN_VALUE;
    private static boolean shipBound;
    private static MethodHandle shipContainer;
    private static MethodHandle shipInBounds;
    private static MethodHandle shipPlot;
    private static MethodHandle shipSubLevel;
    private static boolean contraptionLookupFailed;
    private static boolean contraptionBound;
    private static Class<? extends Entity> contraptionType;
    private static WeakReference<ServerLevel> cacheLevel = new WeakReference<>(null);
    private static long cacheTick = Long.MIN_VALUE;
    private static final Map<BlockPos, Boolean> cache = new HashMap<>();

    private AssemblyGuards() {}

    /** Sable is present and the ship lookup failed, so every block is treated as occupied. */
    static boolean shipGuardFailed() {
        return shipLookupFailed;
    }

    /** After the cooldown, try the ship lookup once even if this tick does not scan a block. */
    static void poll(ServerLevel level) {
        if (level == null || !shipLookupFailed || level.getGameTime() < shipRetryAt) {
            return;
        }
        onShip(level, BlockPos.ZERO);
    }

    static void onServerStopped(ServerStoppedEvent event) {
        cacheLevel = new WeakReference<>(null);
        cacheTick = Long.MIN_VALUE;
        cache.clear();
        shipRetryAt = Long.MIN_VALUE;
    }

    static boolean occupied(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        BlockPos key = pos.immutable();
        long tick = level.getGameTime();
        if (cacheLevel.get() != level || cacheTick != tick) {
            cacheLevel = new WeakReference<>(level);
            cacheTick = tick;
            cache.clear();
        }
        Boolean cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean value = onShip(level, key) || onContraption(level, key);
        cache.put(key, value);
        return value;
    }

    private static boolean onShip(ServerLevel level, BlockPos pos) {
        if (!ModList.get().isLoaded("sable")) {
            return false;
        }
        long now = level.getGameTime();
        if (shipLookupFailed && now < shipRetryAt) {
            return true;
        }
        shipLookupFailed = false;
        try {
            if (!shipBound) {
                bindShip();
            }
            if (shipContainer == null || shipInBounds == null || shipPlot == null || shipSubLevel == null) {
                failShip(null, now);
                return true;
            }
            Object container = shipContainer.invoke(level);
            if (container == null) {
                shipFailureLogged = false;
                return false;
            }
            if (!((boolean) shipInBounds.invoke(container, pos))) {
                shipFailureLogged = false;
                return false;
            }
            Object plot = shipPlot.invoke(container, new ChunkPos(pos));
            shipFailureLogged = false;
            return plot != null && shipSubLevel.invoke(plot) != null;
        } catch (Throwable exception) {
            failShip(exception, now);
            return true;
        }
    }

    /**
     * {@code Class.getMethod} walks every overload, including Sable's client one, and the dedicated server
     * then tries to load {@code ClientLevel}. A direct method handle names only the server overload.
     * {@code shipBound} is set only after every handle exists.
     */
    private static void bindShip() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> containers = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Class<?> serverContainers = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        Class<?> plot = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot");
        Class<?> subLevel = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
        MethodHandle container = lookup.findStatic(containers, "getContainer", MethodType.methodType(serverContainers, ServerLevel.class));
        MethodHandle inBounds = lookup.findVirtual(containers, "inBounds", MethodType.methodType(boolean.class, BlockPos.class));
        MethodHandle plotAt = lookup.findVirtual(containers, "getPlot", MethodType.methodType(plot, ChunkPos.class));
        MethodHandle sub = lookup.findVirtual(plot, "getSubLevel", MethodType.methodType(subLevel));
        shipContainer = container;
        shipInBounds = inBounds;
        shipPlot = plotAt;
        shipSubLevel = sub;
        shipBound = true;
    }

    private static void failShip(Throwable exception, long now) {
        shipLookupFailed = true;
        shipRetryAt = now + ActionPace.SHIP_RETRY_TICKS;
        if (shipFailureLogged) {
            return;
        }
        shipFailureLogged = true;
        Throwable stack = exception != null ? exception : new IllegalStateException("Sable ship lookup returned no handles");
        VillagerWorkersMod.LOGGER.warn("Could not ask Sable whether a block is on a ship. Treating those blocks as occupied.", stack);
    }

    private static boolean onContraption(ServerLevel level, BlockPos pos) {
        if (!ModList.get().isLoaded("create")) {
            return false;
        }
        if (contraptionLookupFailed) {
            return true;
        }
        try {
            if (!contraptionBound) {
                bindContraption();
            }
            if (contraptionType == null) {
                failContraption(null);
                return true;
            }
            AABB box = new AABB(pos);
            return !level.getEntitiesOfClass(
                    contraptionType,
                    box.inflate(0.5D),
                    entity -> entity.isAlive() && entity.getBoundingBox().intersects(box)).isEmpty();
        } catch (Throwable exception) {
            failContraption(exception);
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private static void bindContraption() throws ClassNotFoundException {
        Class<?> found = Class.forName("com.simibubi.create.content.contraptions.AbstractContraptionEntity");
        if (!Entity.class.isAssignableFrom(found)) {
            throw new ClassNotFoundException(found.getName());
        }
        contraptionType = (Class<? extends Entity>) found;
        contraptionBound = true;
    }

    private static void failContraption(Throwable exception) {
        contraptionLookupFailed = true;
        if (exception == null) {
            VillagerWorkersMod.LOGGER.warn("Could not ask Create whether a block is on a contraption. Treating those blocks as occupied.");
            return;
        }
        VillagerWorkersMod.LOGGER.warn(
                "Could not ask Create whether a block is on a contraption. Treating those blocks as occupied.", exception);
    }
}
