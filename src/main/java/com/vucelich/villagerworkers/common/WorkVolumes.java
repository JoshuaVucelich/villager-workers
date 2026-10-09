package com.vucelich.villagerworkers.common;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Inclusive work cells for a saved job. Dig cells are the only blocks a job may break.
 * Wall cells are the sides and ceiling a tunnel or branch may replace. The floor is never a wall.
 */
public final class WorkVolumes {
    private WorkVolumes() {}

    public static boolean isDig(WorkerData data, ResourceLocation dimension, BlockPos pos) {
        if (data == null || dimension == null || pos == null) {
            return false;
        }
        if (data.job() == WorkerJob.LUMBERJACK) {
            return inArea(data.lumberjack().area(), dimension, pos);
        }
        if (data.job() != WorkerJob.MINER) {
            return false;
        }
        MinerConfig miner = data.miner();
        return switch (miner.mode()) {
            case CLEAR_BOX -> inArea(miner.area(), dimension, pos);
            case TUNNEL, BRANCH -> corridor(miner, dimension, pos, false);
            case SHAFT -> shaft(miner, dimension, pos);
        };
    }

    public static boolean isWall(WorkerData data, ResourceLocation dimension, BlockPos pos) {
        if (data == null || data.job() != WorkerJob.MINER || dimension == null || pos == null) {
            return false;
        }
        MinerConfig miner = data.miner();
        if (miner.mode() != MinerMode.TUNNEL && miner.mode() != MinerMode.BRANCH) {
            return false;
        }
        if (miner.wallBlock() == null || miner.wallBlock().isBlank()) {
            return false;
        }
        if (isDig(data, dimension, pos)) {
            return false;
        }
        return corridor(miner, dimension, pos, true);
    }

    public static int digCount(WorkerData data) {
        if (data == null) {
            return 0;
        }
        if (data.job() == WorkerJob.LUMBERJACK) {
            return boxCount(data.lumberjack().area());
        }
        if (data.job() != WorkerJob.MINER) {
            return 0;
        }
        MinerConfig miner = data.miner();
        return switch (miner.mode()) {
            case CLEAR_BOX -> boxCount(miner.area());
            case TUNNEL -> tunnelCount(miner, false);
            case BRANCH -> tunnelCount(miner, true);
            case SHAFT -> miner.shaftWidth() * miner.shaftWidth() * miner.shaftDepth();
        };
    }

    public static BlockPos digAt(WorkerData data, int index) {
        if (index < 0 || index >= digCount(data)) {
            return null;
        }
        if (data.job() == WorkerJob.LUMBERJACK) {
            return boxAt(data.lumberjack().area().orElseThrow(), index);
        }
        MinerConfig miner = data.miner();
        return switch (miner.mode()) {
            case CLEAR_BOX -> boxAt(miner.area().orElseThrow(), index);
            case TUNNEL, BRANCH -> tunnelAt(miner, index);
            case SHAFT -> shaftAt(miner, index);
        };
    }

    public static int wallCount(WorkerData data) {
        return walls(data).size();
    }

    public static BlockPos wallAt(WorkerData data, int index) {
        List<BlockPos> cells = walls(data);
        if (index < 0 || index >= cells.size()) {
            return null;
        }
        return cells.get(index);
    }

    /**
     * Side and ceiling cells, skipping anything the job also digs and any cell listed twice
     * where a branch meets the main corridor.
     */
    public static List<BlockPos> walls(WorkerData data) {
        if (!placesWalls(data)) {
            return List.of();
        }
        MinerConfig miner = data.miner();
        boolean branches = miner.mode() == MinerMode.BRANCH;
        int raw = wallCount(miner, branches);
        ResourceLocation dimension = miner.origin().orElseThrow().dimension();
        List<BlockPos> cells = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (int i = 0; i < raw; i++) {
            BlockPos pos = rawWallAt(miner, i, branches);
            if (pos == null || isDig(data, dimension, pos) || !seen.add(pos)) {
                continue;
            }
            cells.add(pos);
        }
        return cells;
    }

    private static boolean placesWalls(WorkerData data) {
        if (data == null || data.job() != WorkerJob.MINER) {
            return false;
        }
        MinerConfig miner = data.miner();
        if (miner.wallBlock() == null || miner.wallBlock().isBlank()) {
            return false;
        }
        return miner.mode() == MinerMode.TUNNEL || miner.mode() == MinerMode.BRANCH;
    }

    private static boolean inArea(Optional<WorkArea> area, ResourceLocation dimension, BlockPos pos) {
        if (area.isEmpty() || !area.get().dimension().equals(dimension)) {
            return false;
        }
        WorkArea box = area.get().normalized();
        return pos.getX() >= box.min().getX() && pos.getX() <= box.max().getX()
                && pos.getY() >= box.min().getY() && pos.getY() <= box.max().getY()
                && pos.getZ() >= box.min().getZ() && pos.getZ() <= box.max().getZ();
    }

    private static int boxCount(Optional<WorkArea> area) {
        if (area.isEmpty()) {
            return 0;
        }
        WorkArea box = area.get().normalized();
        int sx = box.max().getX() - box.min().getX() + 1;
        int sy = box.max().getY() - box.min().getY() + 1;
        int sz = box.max().getZ() - box.min().getZ() + 1;
        long count = (long) sx * sy * sz;
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    /** Top-down, so the first index is the highest y. */
    private static BlockPos boxAt(WorkArea area, int index) {
        WorkArea box = area.normalized();
        int sx = box.max().getX() - box.min().getX() + 1;
        int sz = box.max().getZ() - box.min().getZ() + 1;
        int slice = sx * sz;
        int y = box.max().getY() - index / slice;
        int rem = index % slice;
        int x = box.min().getX() + rem % sx;
        int z = box.min().getZ() + rem / sx;
        return new BlockPos(x, y, z);
    }

    private static boolean shaft(MinerConfig miner, ResourceLocation dimension, BlockPos pos) {
        WorkOrigin origin = miner.origin().orElse(null);
        if (origin == null || !origin.dimension().equals(dimension)) {
            return false;
        }
        int dx = pos.getX() - origin.pos().getX();
        int dz = pos.getZ() - origin.pos().getZ();
        int dy = pos.getY() - origin.pos().getY();
        if (dx < 0 || dz < 0 || dx >= miner.shaftWidth() || dz >= miner.shaftWidth()) {
            return false;
        }
        if (miner.shaftUp()) {
            return dy >= 0 && dy < miner.shaftDepth();
        }
        return dy <= 0 && -dy < miner.shaftDepth();
    }

    private static BlockPos shaftAt(MinerConfig miner, int index) {
        WorkOrigin origin = miner.origin().orElseThrow();
        int width = miner.shaftWidth();
        int layer = width * width;
        int dy = index / layer;
        int rem = index % layer;
        int y = miner.shaftUp() ? origin.pos().getY() + dy : origin.pos().getY() - dy;
        return new BlockPos(origin.pos().getX() + rem % width, y, origin.pos().getZ() + rem / width);
    }

    private static boolean corridor(MinerConfig miner, ResourceLocation dimension, BlockPos pos, boolean wall) {
        WorkOrigin origin = miner.origin().orElse(null);
        if (origin == null || !origin.dimension().equals(dimension)) {
            return false;
        }
        Direction facing = horizontal(origin.facing());
        Direction right = facing.getClockWise();
        int forward = along(origin.pos(), pos, facing);
        int lateral = along(origin.pos(), pos, right);
        int up = pos.getY() - origin.pos().getY();
        int half = miner.width() / 2;
        if (wall) {
            if (shell(miner, forward, lateral, up, half)) {
                return true;
            }
            return miner.mode() == MinerMode.BRANCH && branchShell(miner, forward, lateral, up, half);
        }
        if (inside(miner, forward, lateral, up, half)) {
            return true;
        }
        return miner.mode() == MinerMode.BRANCH && branchInside(miner, forward, lateral, up, half);
    }

    private static boolean inside(MinerConfig miner, int forward, int lateral, int up, int half) {
        return forward >= 0 && forward < miner.length()
                && lateral >= -half && lateral <= half
                && up >= 0 && up < miner.height();
    }

    private static boolean shell(MinerConfig miner, int forward, int lateral, int up, int half) {
        if (forward < 0 || forward >= miner.length() || up < 0 || up > miner.height()) {
            return false;
        }
        boolean side = (lateral == half + 1 || lateral == -half - 1) && up < miner.height();
        boolean ceiling = up == miner.height() && lateral >= -half && lateral <= half;
        return side || ceiling;
    }

    private static boolean branchInside(MinerConfig miner, int forward, int lateral, int up, int half) {
        if (up < 0 || up >= miner.height()) {
            return false;
        }
        int outside = Math.abs(lateral) - half;
        if (outside < 1 || outside > miner.branchLength()) {
            return false;
        }
        for (int station = miner.branchSpacing(); station < miner.length(); station += miner.branchSpacing()) {
            if (Math.abs(forward - station) <= half) {
                return true;
            }
        }
        return false;
    }

    private static boolean branchShell(MinerConfig miner, int forward, int lateral, int up, int half) {
        if (up < 0 || up > miner.height()) {
            return false;
        }
        int outside = Math.abs(lateral) - half;
        if (outside < 1 || outside > miner.branchLength()) {
            return false;
        }
        for (int station = miner.branchSpacing(); station < miner.length(); station += miner.branchSpacing()) {
            boolean side = Math.abs(forward - station) == half + 1 && up < miner.height();
            boolean ceiling = up == miner.height() && Math.abs(forward - station) <= half;
            if (side || ceiling) {
                return true;
            }
        }
        return false;
    }

    private static int tunnelCount(MinerConfig miner, boolean branches) {
        int main = miner.length() * miner.width() * miner.height();
        if (!branches || miner.origin().isEmpty()) {
            return main;
        }
        int stations = 0;
        for (int station = miner.branchSpacing(); station < miner.length(); station += miner.branchSpacing()) {
            stations++;
        }
        return main + stations * 2 * miner.branchLength() * miner.width() * miner.height();
    }

    private static BlockPos tunnelAt(MinerConfig miner, int index) {
        WorkOrigin origin = miner.origin().orElseThrow();
        Direction facing = horizontal(origin.facing());
        Direction right = facing.getClockWise();
        int cross = miner.width() * miner.height();
        int main = miner.length() * cross;
        int half = miner.width() / 2;
        if (index < main) {
            int forward = index / cross;
            int rem = index % cross;
            int lateral = rem % miner.width() - half;
            int up = rem / miner.width();
            return shift(origin.pos(), facing, forward, right, lateral, up);
        }
        int inner = index - main;
        int perSide = miner.branchLength() * cross;
        int perStation = perSide * 2;
        int stationSlot = inner / perStation;
        int station = miner.branchSpacing() * (stationSlot + 1);
        int side = (inner % perStation) / perSide;
        int cell = inner % perSide;
        int distance = cell / cross + 1;
        int rem = cell % cross;
        int alongFacing = rem % miner.width() - half;
        int up = rem / miner.width();
        int sign = side == 0 ? -1 : 1;
        return shift(origin.pos(), facing, station + alongFacing, right, sign * (half + distance), up);
    }

    private static int wallCount(MinerConfig miner, boolean branches) {
        int length = miner.length();
        int height = miner.height();
        int width = miner.width();
        int main = length * height * 2 + length * width;
        if (!branches) {
            return main;
        }
        int stations = 0;
        for (int station = miner.branchSpacing(); station < length; station += miner.branchSpacing()) {
            stations++;
        }
        int perBranch = miner.branchLength() * height * 2 + miner.branchLength() * width;
        return main + stations * 2 * perBranch;
    }

    private static BlockPos rawWallAt(MinerConfig miner, int index, boolean branches) {
        WorkOrigin origin = miner.origin().orElseThrow();
        Direction facing = horizontal(origin.facing());
        Direction right = facing.getClockWise();
        int length = miner.length();
        int height = miner.height();
        int width = miner.width();
        int half = width / 2;
        int sideCount = length * height * 2;
        int main = sideCount + length * width;
        if (index < sideCount) {
            int forward = index % length;
            int rest = index / length;
            int up = rest % height;
            int sign = rest / height == 0 ? -1 : 1;
            return shift(origin.pos(), facing, forward, right, sign * (half + 1), up);
        }
        if (index < main) {
            int local = index - sideCount;
            int forward = local % length;
            int lateral = local / length - half;
            return shift(origin.pos(), facing, forward, right, lateral, height);
        }
        int inner = index - main;
        int perBranch = miner.branchLength() * height * 2 + miner.branchLength() * width;
        int perStation = perBranch * 2;
        int stationSlot = inner / perStation;
        int station = miner.branchSpacing() * (stationSlot + 1);
        int side = (inner % perStation) / perBranch;
        int cell = inner % perBranch;
        int sign = side == 0 ? -1 : 1;
        int branchSides = miner.branchLength() * height * 2;
        if (cell < branchSides) {
            int distance = cell % miner.branchLength() + 1;
            int rest = cell / miner.branchLength();
            int up = rest % height;
            int wallSign = rest / height == 0 ? -1 : 1;
            return shift(origin.pos(), facing, station + wallSign * (half + 1), right, sign * (half + distance), up);
        }
        int local = cell - branchSides;
        int distance = local % miner.branchLength() + 1;
        int along = local / miner.branchLength() - half;
        return shift(origin.pos(), facing, station + along, right, sign * (half + distance), height);
    }

    private static BlockPos shift(BlockPos origin, Direction facing, int forward, Direction right, int lateral, int up) {
        return origin.relative(facing, forward).relative(right, lateral).above(up);
    }

    private static int along(BlockPos origin, BlockPos pos, Direction axis) {
        return (pos.getX() - origin.getX()) * axis.getStepX()
                + (pos.getY() - origin.getY()) * axis.getStepY()
                + (pos.getZ() - origin.getZ()) * axis.getStepZ();
    }

    private static Direction horizontal(String facing) {
        Direction direction = Direction.byName(facing == null ? "" : facing);
        if (direction == null || direction.getAxis().isVertical()) {
            return Direction.NORTH;
        }
        return direction;
    }
}
