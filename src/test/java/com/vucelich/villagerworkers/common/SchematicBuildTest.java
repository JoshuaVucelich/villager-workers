package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class SchematicBuildTest {
    @Test
    void fallbackMaterialsSkipAir() {
        List<SchematicBuild.Cell> cells = List.of(
                cell(0, 0, 0, "minecraft:stone"),
                cell(1, 0, 0, "minecraft:stone"),
                cell(2, 0, 0, "minecraft:stone"),
                cell(3, 0, 0, "minecraft:air"));
        assertEquals(3, SchematicBuild.materials(cells).get("minecraft:stone"));
        assertFalse(SchematicBuild.materials(cells).containsKey("minecraft:air"));
    }

    @Test
    void bottomLayerComesFirst() {
        List<SchematicBuild.Cell> sorted = SchematicBuild.bottomUp(List.of(
                cell(1, 1, 0, "minecraft:oak_planks"),
                cell(0, 0, 2, "minecraft:stone"),
                cell(0, 0, 1, "minecraft:dirt")));
        assertEquals(0, sorted.get(0).y());
        assertEquals(1, sorted.get(0).z());
        assertEquals(0, sorted.get(1).y());
        assertEquals(2, sorted.get(1).z());
        assertEquals(1, sorted.get(2).y());
    }

    @Test
    void twoWorkersSplitLayers() {
        List<SchematicBuild.Cell> cells = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cells.add(cell(i, 0, 0, "minecraft:stone"));
        }
        for (int i = 0; i < 4; i++) {
            cells.add(cell(i, 1, 0, "minecraft:oak_planks"));
        }
        List<List<SchematicBuild.Cell>> slices = SchematicBuild.split(SchematicBuild.bottomUp(cells), 2);
        assertEquals(4, slices.get(0).size());
        assertEquals(4, slices.get(1).size());
        assertEquals(0, slices.get(0).get(0).y());
        assertEquals(1, slices.get(1).get(0).y());
    }

    @Test
    void threeWorkersSplitOneLayer() {
        List<SchematicBuild.Cell> cells = new java.util.ArrayList<>();
        for (int x = 0; x < 6; x++) {
            cells.add(cell(x, 0, 0, "minecraft:stone"));
        }
        List<List<SchematicBuild.Cell>> slices = SchematicBuild.split(cells, 3);
        assertEquals(2, slices.get(0).size());
        assertEquals(2, slices.get(1).size());
        assertEquals(2, slices.get(2).size());
        assertEquals(0, slices.get(0).get(0).x());
        assertEquals(2, slices.get(1).get(0).x());
        assertEquals(4, slices.get(2).get(0).x());
    }

    @Test
    void numberedPartsPlaceInOrder() {
        List<String> ordered = SchematicBuild.orderParts(List.of("dock_10.nbt", "dock_2.nbt", "dock.nbt"));
        assertEquals(List.of("dock.nbt", "dock_2.nbt", "dock_10.nbt"), ordered);
        List<SchematicBuild.Cell> sequence = SchematicBuild.sequence(List.of(
                List.of(cell(0, 5, 0, "minecraft:stone")),
                List.of(cell(0, 0, 0, "minecraft:dirt"))));
        assertEquals("minecraft:stone", sequence.get(0).itemId());
        assertEquals("minecraft:dirt", sequence.get(1).itemId());
    }

    @Test
    void siblingsShareOneOrigin() {
        List<String> parts = SchematicBuild.sameOrigin("dock_2.nbt", List.of("dock.nbt", "dock_2.nbt", "other.nbt", "dock_3.nbt"));
        assertEquals(List.of("dock.nbt", "dock_2.nbt", "dock_3.nbt"), parts);
    }

    @Test
    void buildNowIsCreativeAndPermitted() {
        assertTrue(SchematicBuild.buildNowAllowed(true, true));
        assertFalse(SchematicBuild.buildNowAllowed(false, true));
        assertFalse(SchematicBuild.buildNowAllowed(true, false));
        assertEquals(1, List.of(SchematicBuild.buildNowAllowed(true, true)).size());
        assertEquals(0, List.of(false, false).stream().filter(Boolean::booleanValue).count());
    }

    @Test
    void structureTagKeepsChestNbtAndDropsAir() {
        CompoundTag tag = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(state("minecraft:air"));
        palette.add(state("minecraft:stone"));
        CompoundTag chest = state("minecraft:chest");
        CompoundTag properties = new CompoundTag();
        properties.putString("facing", "east");
        chest.put("Properties", properties);
        palette.add(chest);
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        blocks.add(block(0, 0, 0, 0, null));
        blocks.add(block(1, 0, 0, 1, null));
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:chest");
        blocks.add(block(2, 1, 0, 2, nbt));
        tag.put("blocks", blocks);
        List<SchematicBuild.Cell> cells = SchematicBuild.cells(tag);
        assertEquals(2, cells.size());
        assertEquals(1, cells.stream().filter(SchematicBuild.Cell::hasBlockEntity).count());
        assertEquals(0, cells.stream().filter(SchematicBuild.Cell::air).count());
        assertEquals("minecraft:chest[facing=east]", cells.get(1).state());
    }

    @Test
    void shareAssignsSlicesAndLeavesOverrides() {
        UUID owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        WorkerData foreman = WorkerData.hire(owner);
        foreman.setForeman(true);
        foreman.setBuilder(BuilderConfig.defaults()
                .withFile(Optional.of("dock.nbt"))
                .withSchematic(Optional.of(new BlockPos(1, 2, 3))));
        WorkerData crew = WorkerData.hire(owner);
        WorkerData own = WorkerData.hire(owner);
        own.setOverrideForeman(true);
        int assigned = SchematicBuild.share(foreman, List.of(foreman, crew, own));
        assertEquals(2, assigned);
        assertEquals(0, foreman.builder().slice());
        assertEquals(2, foreman.builder().slices());
        assertEquals(1, crew.builder().slice());
        assertEquals("dock.nbt", crew.builder().schematicFile().orElse(""));
        assertEquals(new BlockPos(1, 2, 3), crew.builder().schematicAnchor().orElse(null));
        assertTrue(own.builder().schematicFile().isEmpty());
    }

    @Test
    void ruleOverlayKeepsTheWorkersSlice() {
        UUID owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        WorkerData foreman = WorkerData.hire(owner);
        foreman.setForeman(true);
        foreman.setJob(WorkerJob.BUILDER);
        foreman.setBuilder(BuilderConfig.defaults().withFile(Optional.of("dock.nbt")).withSlice(0, 2));
        WorkerData worker = WorkerData.hire(owner);
        worker.setJob(WorkerJob.MINER);
        worker.setBuilder(BuilderConfig.defaults().withFile(Optional.of("dock.nbt")).withSlice(1, 2));
        worker.pushRules(CrewRules.effective(worker, foreman));
        assertEquals(1, worker.builder().slice());
        assertEquals(WorkerJob.BUILDER, worker.job());
        worker.popRules();
        assertEquals(1, worker.builder().slice());
        assertEquals(WorkerJob.MINER, worker.job());
    }

    private static SchematicBuild.Cell cell(int x, int y, int z, String item) {
        return new SchematicBuild.Cell(x, y, z, item, item, null);
    }

    private static CompoundTag state(String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        return tag;
    }

    private static CompoundTag block(int x, int y, int z, int state, CompoundTag nbt) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", new ListTag());
        tag.getList("pos", net.minecraft.nbt.Tag.TAG_INT);
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(x));
        pos.add(IntTag.valueOf(y));
        pos.add(IntTag.valueOf(z));
        tag.put("pos", pos);
        tag.putInt("state", state);
        if (nbt != null) {
            tag.put("nbt", nbt);
        }
        return tag;
    }
}
