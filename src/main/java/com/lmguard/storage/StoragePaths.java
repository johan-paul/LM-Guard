package com.lmguard.storage;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds storage paths.
 *
 * <p>Files are laid out as {@code yyyy/MM/dd/<uuid>.<ext>}. Date prefixes keep any single
 * directory listing small and make manual retention work straightforward; a fresh UUID per
 * upload means a user-supplied filename can never collide with, or overwrite, another
 * inspection's evidence.
 */
public final class StoragePaths {

    private StoragePaths() {
    }

    public static String build(String originalFilename) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String extension = extension(originalFilename);
        return "%04d/%02d/%02d/%s%s".formatted(
                today.getYear(), today.getMonthValue(), today.getDayOfMonth(),
                UUID.randomUUID(), extension);
    }

    /** Lower-cased extension including the dot, or empty when there is nothing safe to use. */
    public static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        String cleaned = filename.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        int dot = cleaned.lastIndexOf('.');
        if (dot < 0 || dot == cleaned.length() - 1) {
            return "";
        }
        String extension = cleaned.substring(dot + 1).toLowerCase(Locale.ROOT);
        // Anything unexpected is dropped rather than trusted into a filesystem path.
        if (!extension.matches("[a-z0-9]{1,8}")) {
            return "";
        }
        return "." + extension;
    }
}
