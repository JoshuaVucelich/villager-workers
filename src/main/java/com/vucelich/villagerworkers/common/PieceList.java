package com.vucelich.villagerworkers.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads schematic pieces from a book. One piece per line: {@code file.nbt dx dy dz}.
 * A line with only the file name sits at the origin.
 */
public final class PieceList {
    public static final int MAX_PIECES = 32;

    private PieceList() {}

    public static List<SchematicPiece> parse(String text) {
        List<SchematicPiece> pieces = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String body = text.length() > 4000 ? text.substring(0, 4000) : text;
        for (String raw : body.split("\\R")) {
            if (pieces.size() >= MAX_PIECES) {
                break;
            }
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length != 1 && parts.length != 4) {
                continue;
            }
            if (!SchematicPiece.safeName(parts[0])) {
                continue;
            }
            int dx = 0;
            int dy = 0;
            int dz = 0;
            if (parts.length == 4) {
                try {
                    dx = clamp(Integer.parseInt(parts[1]));
                    dy = clamp(Integer.parseInt(parts[2]));
                    dz = clamp(Integer.parseInt(parts[3]));
                } catch (NumberFormatException ex) {
                    continue;
                }
            }
            pieces.add(new SchematicPiece(parts[0], dx, dy, dz));
        }
        return List.copyOf(pieces);
    }

    private static int clamp(int value) {
        return Math.max(-4096, Math.min(4096, value));
    }
}
