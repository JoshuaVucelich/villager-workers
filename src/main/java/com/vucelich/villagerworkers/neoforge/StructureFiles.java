package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.SchematicPiece;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Structure files live in the world folder, under villager_workers/schematics. */
public final class StructureFiles {
    private StructureFiles() {}

    public static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("villager_workers").resolve("schematics");
    }

    public static Path resolve(MinecraftServer server, String file) {
        if (server == null || !SchematicPiece.safeName(file)) {
            return null;
        }
        Path dir = directory(server).toAbsolutePath().normalize();
        Path path = dir.resolve(file).normalize();
        if (!path.startsWith(dir)) {
            return null;
        }
        return path;
    }
}
