package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.ActionPace;
import com.vucelich.villagerworkers.common.ProblemCode;
import com.vucelich.villagerworkers.common.SchematicBuild;
import com.vucelich.villagerworkers.common.SchematicBuild.Cell;
import com.vucelich.villagerworkers.common.WorkerActivity;
import com.vucelich.villagerworkers.common.WorkerData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Places a saved schematic from the bottom of each part upward.
 * One worker places its slice. The work runs only while that worker is building.
 */
public final class SchematicBuilder {
    private static final Map<String, List<Cell>> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Cursor> CURSORS = new ConcurrentHashMap<>();

    private static final class Cursor {
        String key = "";
        int index;
        long cooldownUntil;
        BlockPos walkTo;
    }

    private SchematicBuilder() {}

    public static void tick(Villager villager, WorkerData data) {
        if (!(villager.level() instanceof ServerLevel level) || data.activity() != WorkerActivity.WORKING) {
            return;
        }
        BlockPos anchor = data.builder().schematicAnchor().orElse(null);
        String file = data.builder().schematicFile().orElse("");
        if (anchor == null || file.isBlank()) {
            pause(villager, data, ProblemCode.MISSING_CHEST, "a schematic");
            return;
        }
        List<Cell> mine = slice(level, data, file);
        if (mine == null) {
            pause(villager, data, ProblemCode.MISSING_CHEST, file);
            return;
        }
        Cursor cursor = CURSORS.computeIfAbsent(villager.getUUID(), id -> new Cursor());
        String key = file + "#" + data.builder().slice() + "/" + data.builder().slices();
        if (!key.equals(cursor.key)) {
            cursor.key = key;
            cursor.index = 0;
        }
        if (level.getGameTime() < cursor.cooldownUntil) {
            if (cursor.walkTo != null) {
                villager.getNavigation().moveTo(cursor.walkTo.getX() + 0.5, cursor.walkTo.getY(), cursor.walkTo.getZ() + 0.5, 0.6);
            }
            return;
        }
        int budget = Math.max(1, WorkerConfig.schematicBlocksPerAction());
        int placed = 0;
        while (cursor.index < mine.size() && placed < budget) {
            Cell cell = mine.get(cursor.index);
            BlockPos pos = anchor.offset(cell.x(), cell.y(), cell.z());
            if (!level.isLoaded(pos)) {
                cursor.index++;
                continue;
            }
            if (villager.distanceToSqr(Vec3.atCenterOf(pos)) > ActionPace.REACH_SQR) {
                cursor.walkTo = pos.immutable();
                villager.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.6);
                return;
            }
            cursor.walkTo = null;
            BlockState wanted = parse(level, cell.state());
            if (wanted == null) {
                cursor.index++;
                continue;
            }
            BlockState world = level.getBlockState(pos);
            if (world.equals(wanted)) {
                cursor.index++;
                continue;
            }
            if (!world.isAir() && !world.canBeReplaced()) {
                OwnerNotices.report(villager, data, ProblemCode.BLOCKED, pos.getX() + " " + pos.getY() + " " + pos.getZ());
                cursor.index++;
                continue;
            }
            List<String> needed = needed(wanted, cell);
            if (!take(villager, data, needed)) {
                pause(villager, data, ProblemCode.OUT_OF_MATERIALS, needed.isEmpty() ? cell.itemId() : needed.get(0));
                return;
            }
            place(level, pos, wanted, cell.blockEntity());
            cursor.index++;
            placed++;
        }
        if (cursor.index >= mine.size()) {
            data.setActivity(WorkerActivity.STOPPED);
            villager.getNavigation().stop();
            return;
        }
        cursor.cooldownUntil = level.getGameTime() + ActionPace.ticksBetweenActions(data.rested());
    }

    public static int buildNow(ServerLevel level, WorkerData data) {
        String file = data.builder().schematicFile().orElse("");
        BlockPos anchor = data.builder().schematicAnchor().orElse(null);
        if (level == null || anchor == null || file.isBlank()) {
            return 0;
        }
        List<Cell> cells = loadAll(level, file);
        if (cells == null) {
            return 0;
        }
        int placed = 0;
        for (Cell cell : cells) {
            BlockState wanted = parse(level, cell.state());
            if (wanted == null) {
                continue;
            }
            place(level, anchor.offset(cell.x(), cell.y(), cell.z()), wanted, cell.blockEntity());
            placed++;
        }
        data.setActivity(WorkerActivity.STOPPED);
        return placed;
    }

    private static List<Cell> slice(ServerLevel level, WorkerData data, String file) {
        List<Cell> all = loadAll(level, file);
        if (all == null) {
            return null;
        }
        return SchematicBuild.slice(all, data.builder().slice(), data.builder().slices());
    }

    private static List<Cell> loadAll(ServerLevel level, String file) {
        List<Cell> cached = CACHE.get(file);
        if (cached != null) {
            return cached;
        }
        List<String> parts = SchematicFiles.names(file);
        if (parts.isEmpty()) {
            parts = List.of(file);
        }
        List<List<Cell>> loaded = new ArrayList<>();
        for (String part : parts) {
            Path path = SchematicFiles.find(part);
            if (path == null) {
                if (part.equals(file)) {
                    return null;
                }
                continue;
            }
            try {
                CompoundTag tag = SchematicFiles.read(path);
                StructureTemplate template = new StructureTemplate();
                template.load(level.holderLookup(Registries.BLOCK), tag);
                loaded.add(SchematicBuild.cells(tag));
            } catch (IOException | RuntimeException exception) {
                if (part.equals(file)) {
                    return null;
                }
            }
        }
        if (loaded.isEmpty()) {
            return null;
        }
        List<Cell> cells = SchematicBuild.sequence(loaded);
        CACHE.put(file, cells);
        return cells;
    }

    private static List<String> needed(BlockState state, Cell cell) {
        if (ModList.get().isLoaded("create")) {
            List<String> fromCreate = CreateSchematicPlace.items(state);
            if (!fromCreate.isEmpty()) {
                return fromCreate;
            }
        }
        return List.of(cell.itemId());
    }

    private static boolean take(Villager villager, WorkerData data, List<String> items) {
        List<ItemStack> taken = new ArrayList<>();
        List<Container> chests = chests(villager, data);
        for (String id : items) {
            Item item = item(id);
            if (item == null) {
                refund(villager, chests, taken);
                return false;
            }
            ItemStack got = takeOne(villager.getInventory(), item);
            if (got.isEmpty()) {
                for (Container chest : chests) {
                    got = takeOne(chest, item);
                    if (!got.isEmpty()) {
                        break;
                    }
                }
            }
            if (got.isEmpty()) {
                refund(villager, chests, taken);
                return false;
            }
            taken.add(got);
        }
        return true;
    }

    private static ItemStack takeOne(Container container, Item item) {
        if (container == null) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            ItemStack taken = stack.split(1);
            container.setItem(slot, stack);
            container.setChanged();
            return taken;
        }
        return ItemStack.EMPTY;
    }

    private static void refund(Villager villager, List<Container> chests, List<ItemStack> taken) {
        for (ItemStack stack : taken) {
            ItemStack left = ContainerAccess.insert(villager.getInventory(), stack.copy());
            for (Container chest : chests) {
                if (left.isEmpty()) {
                    break;
                }
                left = ContainerAccess.insert(chest, left);
            }
            if (!left.isEmpty()) {
                villager.spawnAtLocation(left);
            }
        }
    }

    private static List<Container> chests(Villager villager, WorkerData data) {
        List<Container> open = new ArrayList<>();
        if (!(villager.level() instanceof ServerLevel level)) {
            return open;
        }
        for (BlockPos pos : data.builder().materialChests()) {
            Container chest = ContainerAccess.open(level, pos);
            if (chest != null) {
                open.add(chest);
            }
        }
        return open;
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state, CompoundTag nbt) {
        if (ModList.get().isLoaded("create")) {
            CreateSchematicPlace.place(level, state, pos, nbt);
            return;
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (nbt == null || nbt.isEmpty()) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            return;
        }
        CompoundTag copy = nbt.copy();
        copy.putInt("x", pos.getX());
        copy.putInt("y", pos.getY());
        copy.putInt("z", pos.getZ());
        entity.loadWithComponents(copy, level.registryAccess());
    }

    private static BlockState parse(ServerLevel level, String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        String id = state;
        String properties = "";
        int bracket = state.indexOf('[');
        if (bracket >= 0) {
            id = state.substring(0, bracket);
            int end = state.endsWith("]") ? state.length() - 1 : state.length();
            properties = state.substring(bracket + 1, end);
        }
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(parsed);
        if (block == null || (block == Blocks.AIR && !"minecraft:air".equals(id))) {
            return null;
        }
        BlockState result = block.defaultBlockState();
        if (properties.isBlank()) {
            return result;
        }
        for (String part : properties.split(",")) {
            int equals = part.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            Property<?> property = block.getStateDefinition().getProperty(part.substring(0, equals));
            if (property != null) {
                result = apply(result, property, part.substring(equals + 1));
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.map(parsedValue -> state.setValue(property, parsedValue)).orElse(state);
    }

    private static Item item(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null || !BuiltInRegistries.ITEM.containsKey(parsed)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(parsed);
    }

    private static void pause(Villager villager, WorkerData data, ProblemCode code, String subject) {
        data.setActivity(WorkerActivity.STOPPED);
        OwnerNotices.report(villager, data, code, subject == null ? "" : subject);
        villager.getNavigation().stop();
    }
}
