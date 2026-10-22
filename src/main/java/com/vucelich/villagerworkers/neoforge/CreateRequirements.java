package com.vucelich.villagerworkers.neoforge;

import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's item requirement for one schematic cell.
 * Only called when Create is loaded. Consumed stacks are building materials. Damaged tools are not.
 */
public final class CreateRequirements {
    public record Need(List<ItemStack> consume, boolean invalid) {}

    private CreateRequirements() {}

    public static Need of(BlockState state) {
        try {
            ItemRequirement requirement = ItemRequirement.of(state, null);
            if (requirement == null || requirement.isInvalid()) {
                return new Need(List.of(), true);
            }
            if (requirement.isEmpty()) {
                return new Need(List.of(), false);
            }
            List<ItemStack> consume = new ArrayList<>();
            for (ItemRequirement.StackRequirement required : requirement.getRequiredItems()) {
                if (required.usage == ItemRequirement.ItemUseType.CONSUME && required.stack != null && !required.stack.isEmpty()) {
                    consume.add(required.stack.copy());
                }
            }
            return new Need(List.copyOf(consume), false);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
