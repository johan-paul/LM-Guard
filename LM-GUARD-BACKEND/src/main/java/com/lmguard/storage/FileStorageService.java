package com.lmguard.storage;

/**
 * Where package images, evidence crops and reports are kept.
 *
 * <p>Business logic never learns which implementation is in play. That is what allows the
 * project to run against Supabase Storage in production and the local filesystem during a
 * hackathon with no internet, without a single conditional in the inspection workflow.
 *
 * <p>Large binaries never go into PostgreSQL - the database holds the URL and the path only.
 */
public interface FileStorageService {

    /**
     * @param bucket      logical bucket: {@code package-images}, {@code evidence} or {@code reports}
     * @param file        file bytes
     * @param filename    original filename, used to derive the stored name and extension
     * @param contentType MIME type
     * @return the stored URL and path
     * @throws com.lmguard.exception.StorageException when the file could not be stored
     */
    StoredFile upload(String bucket, byte[] file, String filename, String contentType);

    /** Convenience overload for the default package-images bucket. */
    default StoredFile upload(byte[] file, String filename, String contentType) {
        return upload(com.lmguard.config.properties.StorageProperties.PACKAGE_IMAGES, file, filename, contentType);
    }

    /**
     * Deletes a stored file. Implementations log and swallow "already gone", because a delete
     * that finds nothing has achieved what the caller wanted.
     */
    void delete(String bucket, String path);

    default void delete(String path) {
        delete(com.lmguard.config.properties.StorageProperties.PACKAGE_IMAGES, path);
    }

    /** Which implementation this is: {@code SUPABASE} or {@code LOCAL}. */
    String providerName();
}
