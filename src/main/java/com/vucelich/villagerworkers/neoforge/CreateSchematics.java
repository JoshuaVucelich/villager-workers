package com.vucelich.villagerworkers.neoforge;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Reads a schematicannon that already holds a deployed schematic.
 * This does not start the cannon, change its state, or spend its gunpowder.
 * Block-entity NBT from the schematic is not applied: Create 6.0.10 keeps arbitrary item components
 * inside that data, so copying it would place unsafe contents.
 */
public final class CreateSchematics {
    private CreateSchematics() {}

    public static boolean containsSchematic(ServerLevel level, BlockPos pos) {
        return blueprint(level, pos) != null;
    }

    public static ItemStack blueprint(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof SchematicannonBlockEntity cannon)) {
            return null;
        }
        ItemStack stack = cannon.inventory.getStackInSlot(0).copy();
        if (!stack.has(AllDataComponents.SCHEMATIC_FILE)
                || !stack.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false)
                || !stack.has(AllDataComponents.SCHEMATIC_ANCHOR)) {
            return null;
        }
        return stack;
    }

    public static SchematicPrinter load(ServerLevel level, ItemStack blueprint) {
        SchematicPrinter printer = new SchematicPrinter();
        printer.loadSchematic(blueprint.copy(), level, false);
        return printer;
    }

    public static String key(ItemStack blueprint) {
        String file = blueprint.get(AllDataComponents.SCHEMATIC_FILE);
        BlockPos anchor = blueprint.get(AllDataComponents.SCHEMATIC_ANCHOR);
        return file + "@" + (anchor == null ? "" : anchor.toShortString());
    }
}
