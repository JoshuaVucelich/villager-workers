package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One gzipped structure file and where it sits relative to the build origin. */
public record SchematicPiece(String file, int dx, int dy, int dz) {
    public static final Codec<SchematicPiece> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("file").forGetter(SchematicPiece::file),
            Codec.INT.optionalFieldOf("dx", 0).forGetter(SchematicPiece::dx),
            Codec.INT.optionalFieldOf("dy", 0).forGetter(SchematicPiece::dy),
            Codec.INT.optionalFieldOf("dz", 0).forGetter(SchematicPiece::dz)
    ).apply(instance, SchematicPiece::new));

    /** A file name only. No folders, so a book cannot point outside the schematics directory. */
    public static boolean safeName(String file) {
        if (file == null || file.length() < 5 || file.length() > 64 || !file.endsWith(".nbt")) {
            return false;
        }
        if (file.contains("..") || file.indexOf('/') >= 0 || file.indexOf('\\') >= 0) {
            return false;
        }
        for (int i = 0; i < file.length(); i++) {
            char c = file.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '-'
                    || c == '.';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
