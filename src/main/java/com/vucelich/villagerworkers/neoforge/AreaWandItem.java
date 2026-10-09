package com.vucelich.villagerworkers.neoforge;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Marks a worker's area or origin. It never breaks or places a block.
 */
public class AreaWandItem extends Item {
    public AreaWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide() || context.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        return SetupSessions.useWand(context);
    }
}
