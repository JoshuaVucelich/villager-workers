package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.VillagerWorkers;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.neoforged.fml.ModList;

/**
 * SHA-256 of the mod file that is actually loaded. A development directory is hashed
 * from its class files so the same log line exists before a jar is built.
 */
public final class JarIdentity {
    private JarIdentity() {}

    public static String sha256() {
        try {
            Path path = ModList.get()
                    .getModContainerById(VillagerWorkers.MOD_ID)
                    .map(container -> container.getModInfo().getOwningFile().getFile().getFilePath())
                    .orElse(null);
            if (path == null || !Files.exists(path)) {
                return "unknown";
            }
            if (Files.isDirectory(path)) {
                return hashTree(path);
            }
            return hashFile(path);
        } catch (RuntimeException | java.io.IOException exception) {
            return "unknown";
        }
    }

    public static String hashFile(Path path) throws java.io.IOException {
        MessageDigest digest = newDigest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private static String hashTree(Path root) throws java.io.IOException {
        MessageDigest digest = newDigest();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        files.sort(Comparator.comparing(path -> root.relativize(path).toString()));
        for (Path file : files) {
            digest.update(root.relativize(file).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return hex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String hex(byte[] hash) {
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
