package com.lmguard.storage;

import com.lmguard.config.properties.AppProperties;
import com.lmguard.config.properties.StorageProperties;
import com.lmguard.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Stores files on the local filesystem and serves them from {@code /files/**}.
 *
 * <p>This is what lets the entire inspection pipeline run with no Supabase account and no
 * internet connection - useful on day one of development, and useful again when a venue's
 * network is unreliable during a demonstration.
 *
 * <p>Not for production: files live on one machine's disk and disappear with it.
 */
@Service
@ConditionalOnProperty(name = "lmguard.storage.provider", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalFileStorageService implements FileStorageService {

    private final StorageProperties storageProperties;
    private final AppProperties appProperties;
    private final Path root;

    public LocalFileStorageService(StorageProperties storageProperties, AppProperties appProperties) {
        this.storageProperties = storageProperties;
        this.appProperties = appProperties;
        this.root = Paths.get(storageProperties.localDirectory()).toAbsolutePath().normalize();
        try {
            // Created eagerly so the static resource handler resolves it as a directory
            // on a first run, before anything has been uploaded.
            Files.createDirectories(this.root);
        } catch (IOException ex) {
            throw new StorageException("Could not create the local storage directory: " + this.root, ex);
        }
        log.warn("Using LOCAL file storage at {}. Set STORAGE_PROVIDER=supabase for anything beyond "
                + "local development.", root);
    }

    @Override
    public StoredFile upload(String bucket, byte[] file, String filename, String contentType) {
        if (file == null || file.length == 0) {
            throw new StorageException("Refusing to store an empty file");
        }
        String bucketName = storageProperties.bucket(bucket);
        String path = StoragePaths.build(filename);
        Path target = resolveSafely(bucketName, path);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, file);
        } catch (IOException ex) {
            throw new StorageException("Could not write file to local storage: " + target, ex);
        }

        String url = "%s/files/%s/%s".formatted(appProperties.baseUrl(), bucketName, path);
        log.debug("Stored {} bytes at {} ({})", file.length, target, url);
        return new StoredFile(url, path);
    }

    @Override
    public void delete(String bucket, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        Path target = resolveSafely(storageProperties.bucket(bucket), path);
        try {
            boolean deleted = Files.deleteIfExists(target);
            log.debug("Local storage delete {}: {}", target, deleted ? "removed" : "already absent");
        } catch (IOException ex) {
            log.warn("Could not delete {} from local storage: {}", target, ex.getMessage());
        }
    }

    @Override
    public String providerName() {
        return "LOCAL";
    }

    /** Blocks path traversal: a resolved path must stay inside the storage root. */
    private Path resolveSafely(String bucketName, String path) {
        Path resolved = root.resolve(bucketName).resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new StorageException("Rejected storage path outside the storage root: " + path);
        }
        return resolved;
    }
}
