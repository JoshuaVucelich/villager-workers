package com.vucelich.villagerworkers.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;

/** A rising stair toward a high block, removed from the top when the build is done. */
public final class ScaffoldPlan {
    private ScaffoldPlan() {}

    public static List<BlockPos> stair(BlockPos feet, BlockPos target) {
        if (feet == null || target == null) {
            return List.of();
        }
        int rise = target.getY() - feet.getY();
        if (rise <= 2) {
            return List.of();
        }
        int steps = rise;
        int x = feet.getX();
        int z = feet.getZ();
        int dirX = Integer.compare(target.getX(), x);
        int dirZ = Integer.compare(target.getZ(), z);
        if (dirX == 0 && dirZ == 0) {
            dirX = 1;
        }
        List<BlockPos> posts = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            x += dirX;
            z += dirZ;
            posts.add(new BlockPos(x, feet.getY() + i, z));
        }
        return List.copyOf(posts);
    }

    public static List<BlockPos> topDown(List<BlockPos> posts) {
        List<BlockPos> copy = new ArrayList<>(posts == null ? List.of() : posts);
        copy.sort(Comparator.comparingInt((BlockPos pos) -> -pos.getY()).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return List.copyOf(copy);
    }

    public static BlockPos nextOpen(List<BlockPos> stair, List<BlockPos> already) {
        if (stair == null) {
            return null;
        }
        for (BlockPos pos : stair) {
            if (already == null || !already.contains(pos)) {
                return pos;
            }
        }
        return null;
    }

    /**
     * @return the item id to place, {@code spare} for a plain block, or empty when the worker must stop
     */
    public static String choose(ScaffoldPreference preference, boolean vanilla, boolean andesite, boolean spare) {
        ScaffoldPreference mode = preference == null ? ScaffoldPreference.VANILLA : preference;
        return switch (mode) {
            case PLAIN -> spare ? "spare" : "";
            case ANDESITE -> andesite
                    ? ScaffoldPreference.ANDESITE_ID
                    : vanilla ? ScaffoldPreference.VANILLA_ID : spare ? "spare" : "";
            case VANILLA -> vanilla
                    ? ScaffoldPreference.VANILLA_ID
                    : andesite ? ScaffoldPreference.ANDESITE_ID : spare ? "spare" : "";
        };
    }
}
