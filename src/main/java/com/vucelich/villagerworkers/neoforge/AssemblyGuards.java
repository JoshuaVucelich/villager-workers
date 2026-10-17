package com.vucelich.villagerworkers.neoforge;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

/**
 * Blocks that belong to a moving assembly are not ordinary world blocks.
 * Sable is loaded by reflection so this mod still starts when Sable is absent.
 * The Create type is only touched after Create is confirmed loaded.
 */
final class AssemblyGuards {
    private static boolean shipLookupFailed;
    private static boolean shipBound;
    private static MethodHandle shipContainer;
    private static MethodHandle shipInBounds;
    private static MethodHandle shipPlot;
    private static MethodHandle shipSubLevel;

    private AssemblyGuards() {}

    static boolean occupied(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        return onShip(level, pos) || onContraption(level, pos);
    }

    private static boolean onShip(ServerLevel level, BlockPos pos) {
        if (!ModList.get().isLoaded("sable")) {
            return false;
        }
        try {
            if (!shipBound) {
                bindShip();
            }
            if (shipContainer == null) {
                return false;
            }
            Object container = shipContainer.invoke(level);
            if (container == null || !((boolean) shipInBounds.invoke(container, pos))) {
                return false;
            }
            Object plot = shipPlot.invoke(container, new ChunkPos(pos));
            return plot != null && shipSubLevel.invoke(plot) != null;
        } catch (Throwable exception) {
            if (!shipLookupFailed) {
                shipLookupFailed = true;
                VillagerWorkersMod.LOGGER.warn("Could not ask Sable whether a block is on a ship", exception);
            }
            return false;
        }
    }

    /**
     * {@code Class.getMethod} walks every overload, including Sable's client one, and the dedicated server
     * then tries to load {@code ClientLevel}. A direct method handle names only the server overload.
     */
    private static void bindShip() throws ReflectiveOperationException {
        shipBound = true;
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> containers = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Class<?> serverContainers = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        Class<?> plot = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot");
        Class<?> subLevel = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
        shipContainer = lookup.findStatic(containers, "getContainer", MethodType.methodType(serverContainers, ServerLevel.class));
        shipInBounds = lookup.findVirtual(containers, "inBounds", MethodType.methodType(boolean.class, BlockPos.class));
        shipPlot = lookup.findVirtual(containers, "getPlot", MethodType.methodType(plot, ChunkPos.class));
        shipSubLevel = lookup.findVirtual(plot, "getSubLevel", MethodType.methodType(subLevel));
    }

    private static boolean onContraption(ServerLevel level, BlockPos pos) {
        if (!ModList.get().isLoaded("create")) {
            return false;
        }
        AABB box = new AABB(pos);
        return !level.getEntitiesOfClass(
                AbstractContraptionEntity.class,
                box.inflate(0.5D),
                entity -> entity.isAlive() && entity.getBoundingBox().intersects(box)).isEmpty();
    }
}
