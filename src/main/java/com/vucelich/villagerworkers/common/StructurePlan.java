package com.vucelich.villagerworkers.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Order, offsets, and layer splits for a structure build.
 * This does not load files or place blocks.
 */
public final class StructurePlan {
    public record PlanCell(int x, int y, int z) {}

    private StructurePlan() {}

    public static List<PlanCell> offset(List<PlanCell> cells, int dx, int dy, int dz) {
        List<PlanCell> moved = new ArrayList<>();
        if (cells == null) {
            return moved;
        }
        for (PlanCell cell : cells) {
            if (cell != null) {
                moved.add(new PlanCell(cell.x + dx, cell.y + dy, cell.z + dz));
            }
        }
        return moved;
    }

    /** Lowest y first, then z, then x, so supports exist before the blocks above them. */
    public static List<PlanCell> bottomUp(List<PlanCell> cells) {
        List<PlanCell> copy = new ArrayList<>();
        if (cells != null) {
            copy.addAll(cells);
        }
        copy.sort(Comparator.comparingInt(PlanCell::y).thenComparingInt(PlanCell::z).thenComparingInt(PlanCell::x));
        return copy;
    }

    public static boolean inLayer(int y, int minY, int maxY, boolean limited) {
        if (!limited) {
            return true;
        }
        return y >= minY && y <= maxY;
    }

    /**
     * Splits an inclusive y range across workers. Earlier workers take the extra layers.
     * The result is never taller than the structure, and never longer than the crew.
     */
    public static int[][] bands(int minY, int maxY, int workers) {
        if (workers <= 0 || maxY < minY) {
            return new int[0][];
        }
        int span = maxY - minY + 1;
        int count = Math.min(workers, span);
        int[][] out = new int[count][2];
        int base = span / count;
        int extra = span % count;
        int cursor = minY;
        for (int i = 0; i < count; i++) {
            int height = base + (i < extra ? 1 : 0);
            out[i][0] = cursor;
            out[i][1] = cursor + height - 1;
            cursor += height;
        }
        return out;
    }

    /** Creative instant build. Survival, adventure, and spectator stay false. */
    public static boolean instantAllowed(boolean creative, boolean instabuild) {
        return creative && instabuild;
    }
}
