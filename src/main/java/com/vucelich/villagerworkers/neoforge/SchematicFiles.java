package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.SchematicBuild;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/** Reads a gzipped structure the same way Create's SchematicItem.loadSchematic does. */
public final class SchematicFiles {
    private SchematicFiles() {}

    public static Path root() {
        if (ModList.get().isLoaded("create")) {
            return CreateSchematicPlace.uploadedRoot();
        }
        return FMLPaths.GAMEDIR.get().resolve("schematics");
    }

    public static Path find(String file) {
        if (!safeName(file)) {
            return null;
        }
        Path root = root();
        if (root == null || !Files.isDirectory(root)) {
            return null;
        }
        Path direct = root.resolve(file).normalize();
        if (direct.startsWith(root) && Files.isRegularFile(direct)) {
            return direct;
        }
        try (var stream = Files.list(root)) {
            for (Path dir : stream.toList()) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                Path nested = dir.resolve(file).normalize();
                if (nested.startsWith(dir) && Files.isRegularFile(nested)) {
                    return nested;
                }
            }
        } catch (IOException exception) {
            return null;
        }
        return null;
    }

    public static List<String> names(String primary) {
        Path root = root();
        List<String> found = new ArrayList<>();
        if (root != null && Files.isDirectory(root)) {
            collect(root, found);
            try (var stream = Files.list(root)) {
                for (Path dir : stream.toList()) {
                    if (Files.isDirectory(dir)) {
                        collect(dir, found);
                    }
                }
            } catch (IOException exception) {
                found.clear();
            }
        }
        return SchematicBuild.sameOrigin(primary, found);
    }

    public static CompoundTag read(Path path) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))))) {
            return NbtIo.read(input, NbtAccounter.create(536870912L));
        }
    }

    private static void collect(Path dir, List<String> found) {
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (name.endsWith(".nbt") && safeName(name)) {
                    found.add(name);
                }
            }
        } catch (IOException exception) {
            // A missing folder just means there are no sibling parts.
        }
    }

    private static boolean safeName(String file) {
        return file != null
                && !file.isBlank()
                && file.endsWith(".nbt")
                && !file.contains("..")
                && !file.contains("/")
                && !file.contains("\\");
    }
}
