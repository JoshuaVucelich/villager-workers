package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.SchematicPiece;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * Loads gzipped vanilla structure files once per file version.
 * Positions are relative to the build origin, with each piece's offset applied, lowest y first.
 */
public final class StructureLibrary {
    public record Loaded(String key, List<StructureBlockInfo> blocks, int minY, int maxY, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private static final ConcurrentHashMap<String, Loaded> CACHE = new ConcurrentHashMap<>();

    private StructureLibrary() {}

    public static void clear() {
        CACHE.clear();
    }

    public static Loaded load(ServerLevel level, List<SchematicPiece> pieces, int kilobytes) {
        if (level == null || pieces == null || pieces.isEmpty()) {
            return new Loaded("", List.of(), 0, 0, "No schematic pieces.");
        }
        long maxBytes = Math.max(256, kilobytes) * 1024L;
        StringBuilder keyBuilder = new StringBuilder();
        for (SchematicPiece piece : pieces) {
            Path path = StructureFiles.resolve(level.getServer(), piece.file());
            long size = -1L;
            long modified = -1L;
            if (path != null && Files.isRegularFile(path)) {
                try {
                    size = Files.size(path);
                    modified = Files.getLastModifiedTime(path).toMillis();
                } catch (IOException ignored) {
                    size = -1L;
                }
            }
            keyBuilder.append(piece.file())
                    .append('@')
                    .append(piece.dx()).append(',').append(piece.dy()).append(',').append(piece.dz())
                    .append('#')
                    .append(size).append(':').append(modified)
                    .append('|');
        }
        String key = keyBuilder.toString();
        Loaded cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Loaded loaded = read(level, pieces, maxBytes, key);
        if (CACHE.size() > 16) {
            CACHE.clear();
        }
        CACHE.put(key, loaded);
        return loaded;
    }

    private static Loaded read(ServerLevel level, List<SchematicPiece> pieces, long maxBytes, String key) {
        List<StructureBlockInfo> blocks = new ArrayList<>();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        try {
            for (SchematicPiece piece : pieces) {
                if (!SchematicPiece.safeName(piece.file())) {
                    return new Loaded(key, List.of(), 0, 0, "Bad schematic file name.");
                }
                Path path = StructureFiles.resolve(level.getServer(), piece.file());
                if (path == null || !Files.isRegularFile(path)) {
                    return new Loaded(key, List.of(), 0, 0, "Missing schematic " + piece.file() + ".");
                }
                if (Files.size(path) > maxBytes) {
                    return new Loaded(key, List.of(), 0, 0, piece.file() + " is larger than the schematic limit.");
                }
                CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.create(maxBytes));
                StructureTemplate template = new StructureTemplate();
                template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
                for (StructureBlockInfo info : allBlocks(template)) {
                    BlockPos pos = info.pos().offset(piece.dx(), piece.dy(), piece.dz());
                    blocks.add(new StructureBlockInfo(pos, info.state(), info.nbt()));
                    minY = Math.min(minY, pos.getY());
                    maxY = Math.max(maxY, pos.getY());
                }
            }
        } catch (Exception ex) {
            return new Loaded(key, List.of(), 0, 0, "Could not read that schematic.");
        }
        if (blocks.isEmpty()) {
            return new Loaded(key, List.of(), 0, -1, null);
        }
        blocks.sort(Comparator
                .comparingInt((StructureBlockInfo info) -> info.pos().getY())
                .thenComparingInt(info -> info.pos().getZ())
                .thenComparingInt(info -> info.pos().getX()));
        return new Loaded(key, List.copyOf(blocks), minY, maxY, null);
    }

    /**
     * {@code filterBlocks} keeps only one block id. The palette list is every block, including air.
     * Structure void is dropped so it never becomes a cell.
     */
    @SuppressWarnings("unchecked")
    private static List<StructureBlockInfo> allBlocks(StructureTemplate template) throws ReflectiveOperationException {
        java.lang.reflect.Field field = StructureTemplate.class.getDeclaredField("palettes");
        field.setAccessible(true);
        List<?> palettes = (List<?>) field.get(template);
        if (palettes == null || palettes.isEmpty()) {
            return List.of();
        }
        Object palette = palettes.get(0);
        List<StructureBlockInfo> blocks = (List<StructureBlockInfo>) palette.getClass().getMethod("blocks").invoke(palette);
        List<StructureBlockInfo> kept = new ArrayList<>();
        for (StructureBlockInfo info : blocks) {
            if (info != null && !info.state().is(Blocks.STRUCTURE_VOID)) {
                kept.add(info);
            }
        }
        return kept;
    }
}
