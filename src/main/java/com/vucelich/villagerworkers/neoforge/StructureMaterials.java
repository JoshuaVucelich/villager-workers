package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.BlockEntitySafety;
import com.vucelich.villagerworkers.common.BuilderConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.fml.ModList;

/** Materials for one structure cell, and a short list for the worker screen. */
public final class StructureMaterials {
    public record Need(List<ItemStack> consume, boolean invalid) {}

    private static final ConcurrentHashMap<String, String> SUMMARIES = new ConcurrentHashMap<>();
    private static final int SUMMARY_CELLS = 20000;

    private StructureMaterials() {}

    public static void clear() {
        SUMMARIES.clear();
    }

    public static Need required(BlockState state) {
        if (ModList.get().isLoaded("create")) {
            CreateRequirements.Need fromCreate = CreateRequirements.of(state);
            if (fromCreate != null) {
                return new Need(fromCreate.consume(), fromCreate.invalid());
            }
        }
        return vanilla(state);
    }

    public static String summarize(net.minecraft.server.level.ServerLevel level, BuilderConfig builder) {
        if (builder == null || !builder.hasStructure()) {
            return "no structure file";
        }
        StructureLibrary.Loaded loaded = StructureLibrary.load(level, builder.pieces(), WorkerSettings.createSchematicUploadKb());
        if (!loaded.ok()) {
            return loaded.error() == null ? "Could not read that schematic." : loaded.error();
        }
        String cached = SUMMARIES.get(loaded.key());
        if (cached != null) {
            return cached;
        }
        Map<Item, Integer> counts = new HashMap<>();
        int seen = 0;
        for (var info : loaded.blocks()) {
            if (seen++ >= SUMMARY_CELLS) {
                break;
            }
            if (!StructurePlanKeeps.counts(info.pos().getY(), builder)) {
                continue;
            }
            Need need = required(info.state());
            if (need.invalid()) {
                continue;
            }
            for (ItemStack stack : need.consume()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        String text = format(counts);
        if (SUMMARIES.size() > 16) {
            SUMMARIES.clear();
        }
        SUMMARIES.put(loaded.key(), text);
        return text;
    }

    public static String format(Map<Item, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "no materials";
        }
        List<Map.Entry<Item, Integer>> rows = new ArrayList<>(counts.entrySet());
        rows.sort(Comparator.<Map.Entry<Item, Integer>>comparingInt(Map.Entry::getValue).reversed());
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (Map.Entry<Item, Integer> row : rows) {
            if (shown >= 8) {
                builder.append(", ...");
                break;
            }
            if (shown > 0) {
                builder.append(", ");
            }
            builder.append(row.getValue()).append(' ').append(BuiltInRegistries.ITEM.getKey(row.getKey()).getPath());
            shown++;
        }
        return builder.toString();
    }

    private static Need vanilla(BlockState state) {
        if (state == null || state.isAir()) {
            return new Need(List.of(), false);
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return new Need(List.of(), false);
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return new Need(List.of(), false);
        }
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return new Need(List.of(), false);
        }
        return new Need(List.of(new ItemStack(item)), false);
    }

    /** Layer filter used while counting. Kept here so the summary matches the worker's band. */
    static final class StructurePlanKeeps {
        private StructurePlanKeeps() {}

        static boolean counts(int y, BuilderConfig builder) {
            return com.vucelich.villagerworkers.common.StructurePlan.inLayer(
                    y, builder.layerMin(), builder.layerMax(), builder.limitsLayers());
        }
    }

    static boolean safeToCopy(net.minecraft.nbt.CompoundTag tag) {
        return BlockEntitySafety.copyIfSafe(tag) != null || tag == null || tag.isEmpty();
    }
}
