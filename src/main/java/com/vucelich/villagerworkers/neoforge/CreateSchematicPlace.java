package com.vucelich.villagerworkers.neoforge;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.foundation.utility.CreatePaths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Create calls. This class loads only when Create is installed. */
public final class CreateSchematicPlace {
    private CreateSchematicPlace() {}

    public static String fileName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String file = stack.get(AllDataComponents.SCHEMATIC_FILE);
        return file == null ? "" : file;
    }

    public static Path uploadedRoot() {
        return CreatePaths.UPLOADED_SCHEMATICS_DIR;
    }

    public static void place(Level level, BlockState state, BlockPos pos, CompoundTag nbt) {
        BlockHelper.placeSchematicBlock(level, state, pos, ItemStack.EMPTY, nbt == null ? new CompoundTag() : nbt.copy());
    }

    public static List<String> items(BlockState state) {
        ItemRequirement requirement;
        try {
            requirement = ItemRequirement.of(state, null);
        } catch (RuntimeException exception) {
            return List.of();
        }
        if (requirement == null || requirement.isEmpty() || requirement.isInvalid()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (ItemRequirement.StackRequirement required : requirement.getRequiredItems()) {
            String id = BuiltInRegistries.ITEM.getKey(required.stack.getItem()).toString();
            int count = Math.max(1, required.stack.getCount());
            for (int i = 0; i < count; i++) {
                ids.add(id);
            }
        }
        return ids;
    }
}
