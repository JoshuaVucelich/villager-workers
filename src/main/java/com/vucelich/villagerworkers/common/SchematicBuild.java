package com.vucelich.villagerworkers.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Order, materials, and crew slices for a vanilla structure NBT file.
 * Loading the gzip bytes is the loader's job. This class does not place blocks.
 */
public final class SchematicBuild {
    public record Cell(int x, int y, int z, String state, String itemId, CompoundTag blockEntity) {
        public boolean air() {
            return itemId == null || itemId.isBlank() || itemId.endsWith(":air") || itemId.endsWith("structure_void");
        }

        public boolean hasBlockEntity() {
            return blockEntity != null && !blockEntity.isEmpty();
        }
    }

    private SchematicBuild() {}

    public static List<Cell> cells(CompoundTag tag) {
        if (tag == null) {
            return List.of();
        }
        List<String> palette = palette(tag);
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        List<Cell> found = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            int x = pos.size() > 0 ? pos.getInt(0) : 0;
            int y = pos.size() > 1 ? pos.getInt(1) : 0;
            int z = pos.size() > 2 ? pos.getInt(2) : 0;
            int stateIndex = block.getInt("state");
            String state = stateIndex >= 0 && stateIndex < palette.size() ? palette.get(stateIndex) : "minecraft:air";
            String item = itemId(state);
            CompoundTag nbt = block.contains("nbt", Tag.TAG_COMPOUND) ? block.getCompound("nbt").copy() : null;
            Cell cell = new Cell(x, y, z, state, item, nbt);
            if (!cell.air()) {
                found.add(cell);
            }
        }
        return bottomUp(found);
    }

    public static List<Cell> bottomUp(List<Cell> cells) {
        List<Cell> copy = new ArrayList<>(cells == null ? List.of() : cells);
        copy.sort(Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x).thenComparingInt(Cell::z));
        return List.copyOf(copy);
    }

    /** Fallback when Create's ItemRequirement is absent: one item per block, same id, air omitted. */
    public static Map<String, Integer> materials(List<Cell> cells) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (cells == null) {
            return counts;
        }
        for (Cell cell : cells) {
            if (cell == null || cell.air()) {
                continue;
            }
            counts.merge(cell.itemId(), 1, Integer::sum);
        }
        return counts;
    }

    public static List<List<Cell>> split(List<Cell> bottomUp, int workers) {
        List<Cell> cells = bottomUp == null ? List.of() : bottomUp;
        int count = Math.max(1, workers);
        List<List<Cell>> slices = new ArrayList<>();
        int size = cells.size();
        for (int i = 0; i < count; i++) {
            int start = (int) ((long) i * size / count);
            int end = (int) ((long) (i + 1) * size / count);
            slices.add(List.copyOf(cells.subList(start, end)));
        }
        return slices;
    }

    public static List<Cell> slice(List<Cell> bottomUp, int index, int workers) {
        List<List<Cell>> slices = split(bottomUp, workers);
        if (index < 0 || index >= slices.size()) {
            return List.of();
        }
        return slices.get(index);
    }

    public static List<String> orderParts(List<String> names) {
        List<String> copy = new ArrayList<>(names == null ? List.of() : names);
        copy.sort(Comparator.comparingInt(SchematicBuild::partNumber).thenComparing(name -> name == null ? "" : name));
        return List.copyOf(copy);
    }

    public static List<String> sameOrigin(String primary, List<String> directoryNames) {
        String stem = stem(primary);
        List<String> matches = new ArrayList<>();
        if (directoryNames != null) {
            for (String name : directoryNames) {
                if (name != null && stem(name).equals(stem)) {
                    matches.add(name);
                }
            }
        }
        if (matches.isEmpty() && primary != null && !primary.isBlank()) {
            matches.add(primary);
        }
        return orderParts(matches);
    }

    public static List<Cell> sequence(List<List<Cell>> partsInOrder) {
        List<Cell> all = new ArrayList<>();
        if (partsInOrder == null) {
            return all;
        }
        for (List<Cell> part : partsInOrder) {
            all.addAll(bottomUp(part));
        }
        return List.copyOf(all);
    }

    public static boolean buildNowAllowed(boolean creative, boolean canEdit) {
        return creative && canEdit;
    }

    /** Copy the foreman's file and anchor onto the crew and give each a slice. Override workers stay as they are. */
    public static int share(WorkerData foreman, List<WorkerData> members) {
        if (foreman == null || members == null) {
            return 0;
        }
        Optional<String> file = foreman.builder().schematicFile().filter(name -> !name.isBlank());
        if (file.isEmpty()) {
            return 0;
        }
        List<WorkerData> building = new ArrayList<>();
        for (WorkerData member : members) {
            if (member == null) {
                continue;
            }
            if (member != foreman && member.overrideForeman()) {
                continue;
            }
            building.add(member);
        }
        int count = building.size();
        for (int i = 0; i < count; i++) {
            WorkerData member = building.get(i);
            member.setJob(WorkerJob.BUILDER);
            member.setBuilder(member.builder()
                    .withSchematic(foreman.builder().schematicAnchor())
                    .withFile(file)
                    .withSlice(i, count));
        }
        return count;
    }

    public static String stem(String name) {
        String base = stripExtension(name);
        int split = base.lastIndexOf('_');
        if (split > 0 && number(base.substring(split + 1)) > 0 && !base.substring(split + 1).isEmpty()
                && base.substring(split + 1).chars().allMatch(Character::isDigit)) {
            return base.substring(0, split);
        }
        return base;
    }

    public static int partNumber(String name) {
        String base = stripExtension(name);
        int split = base.lastIndexOf('_');
        if (split < 0 || split == base.length() - 1) {
            return 1;
        }
        String tail = base.substring(split + 1);
        if (tail.isEmpty() || !tail.chars().allMatch(Character::isDigit)) {
            return 1;
        }
        return number(tail);
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        String base = name.toLowerCase(Locale.ROOT);
        if (base.endsWith(".nbt")) {
            return base.substring(0, base.length() - 4);
        }
        return base;
    }

    private static String itemId(String state) {
        if (state == null || state.isBlank()) {
            return "minecraft:air";
        }
        int bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    private static List<String> palette(CompoundTag tag) {
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        if (palette.isEmpty()) {
            ListTag palettes = tag.getList("palettes", Tag.TAG_LIST);
            if (!palettes.isEmpty()) {
                palette = palettes.getList(0);
            }
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag state = palette.getCompound(i);
            String name = state.getString("Name");
            if (name.isBlank()) {
                name = "minecraft:air";
            }
            CompoundTag properties = state.getCompound("Properties");
            if (properties.isEmpty()) {
                names.add(name);
            } else {
                names.add(name + properties(properties));
            }
        }
        return names;
    }

    private static String properties(CompoundTag properties) {
        List<String> keys = new ArrayList<>(properties.getAllKeys());
        keys.sort(Comparator.naturalOrder());
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(keys.get(i)).append('=').append(properties.getString(keys.get(i)));
        }
        builder.append(']');
        return builder.toString();
    }
}
