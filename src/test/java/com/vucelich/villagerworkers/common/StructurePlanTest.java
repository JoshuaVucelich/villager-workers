package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class StructurePlanTest {
    @Test
    void bottomUpPutsTheFloorFirstAndOffsetsPieces() {
        List<StructurePlan.PlanCell> cells = StructurePlan.offset(
                List.of(new StructurePlan.PlanCell(0, 2, 0), new StructurePlan.PlanCell(1, 0, 0)),
                16,
                0,
                3);
        List<StructurePlan.PlanCell> ordered = StructurePlan.bottomUp(cells);
        assertEquals(new StructurePlan.PlanCell(17, 0, 3), ordered.get(0));
        assertEquals(new StructurePlan.PlanCell(16, 2, 3), ordered.get(1));
    }

    @Test
    void tenWorkersShareFifteenLayers() {
        int[][] bands = StructurePlan.bands(0, 14, 10);
        assertEquals(10, bands.length);
        assertEquals(0, bands[0][0]);
        assertEquals(1, bands[0][1]);
        assertEquals(9, bands[4][1]);
        assertEquals(10, bands[5][0]);
        assertEquals(10, bands[5][1]);
        assertEquals(14, bands[9][1]);
        int covered = 0;
        for (int[] band : bands) {
            covered += band[1] - band[0] + 1;
        }
        assertEquals(15, covered);
        assertTrue(StructurePlan.inLayer(1, 0, 1, true));
        assertFalse(StructurePlan.inLayer(2, 0, 1, true));
        assertTrue(StructurePlan.inLayer(99, 0, 1, false));
    }

    @Test
    void instantBuildIsCreativeOnly() {
        assertTrue(StructurePlan.instantAllowed(true, true));
        assertFalse(StructurePlan.instantAllowed(false, true));
        assertFalse(StructurePlan.instantAllowed(true, false));
        assertFalse(StructurePlan.instantAllowed(false, false));
    }

    @Test
    void pieceBookKeepsSafeNamesAndDropsPaths() {
        List<SchematicPiece> pieces = PieceList.parse("""
                hall.nbt 0 0 0
                wing.nbt 16 0 0
                ../secret.nbt 0 0 0
                plain.nbt
                # comment
                """);
        assertEquals(3, pieces.size());
        assertEquals("hall.nbt", pieces.get(0).file());
        assertEquals(16, pieces.get(1).dx());
        assertEquals("plain.nbt", pieces.get(2).file());
        assertFalse(SchematicPiece.safeName("../secret.nbt"));
    }

    @Test
    void containerDataIsNotCopied() {
        CompoundTag sign = new CompoundTag();
        sign.putString("id", "minecraft:sign");
        assertTrue(BlockEntitySafety.safe(sign));
        CompoundTag chest = new CompoundTag();
        chest.put("Items", new ListTag());
        assertFalse(BlockEntitySafety.safe(chest));
        assertNull(BlockEntitySafety.copyIfSafe(chest));
        CompoundTag loot = new CompoundTag();
        loot.putString("LootTable", "minecraft:chests/simple_dungeon");
        assertFalse(BlockEntitySafety.safe(loot));
    }

    @Test
    void oldBuilderSaveHasNoStructureAndPiecesRoundTrip() {
        UUID owner = UUID.fromString("6f1e0c2a-3b4d-4e5f-8a9b-0c1d2e3f4a5b");
        String old = "{\"owner\":\"" + owner + "\",\"builder\":{\"material_chests\":[]}}";
        WorkerData data = WorkerData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(old)).getOrThrow();
        assertFalse(data.builder().hasStructure());
        assertTrue(data.builder().pieces().isEmpty());
        data.setBuilder(data.builder()
                .withStructureOrigin(java.util.Optional.of(new net.minecraft.core.BlockPos(3, 4, 5)))
                .withPieces(List.of(new SchematicPiece("hall.nbt", 0, 0, 0), new SchematicPiece("wing.nbt", 16, 0, 0)))
                .withLayers(0, 4));
        WorkerData again = WorkerData.CODEC.parse(JsonOps.INSTANCE, WorkerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
        assertTrue(again.builder().hasStructure());
        assertEquals(2, again.builder().pieces().size());
        assertEquals(16, again.builder().pieces().get(1).dx());
        assertEquals(0, again.builder().layerMin());
        assertEquals(4, again.builder().layerMax());
        assertEquals(3, again.builder().structureOrigin().orElseThrow().getX());
    }
}
