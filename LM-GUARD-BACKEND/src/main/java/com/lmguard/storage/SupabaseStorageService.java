package com.lmguard.storage;

import com.lmguard.config.properties.StorageProperties;
import com.lmguard.config.properties.SupabaseProperties;
import com.lmguard.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Stores files in Supabase Storage over its REST API.
 *
 * <p>Uploads authenticate with the <strong>service role key</strong>, which bypasses row level
 * security and must never reach the browser. It is read from {@code SUPABASE_SERVICE_ROLE_KEY}
 * and exists only on the server.
 *
 * <p>Endpoints used:
 * <pre>
 *   POST   {SUPABASE_URL}/storage/v1/object/{bucket}/{path}          upload
 *   DELETE {SUPABASE_URL}/storage/v1/object/{bucket}/{path}          delete
 *   GET    {SUPABASE_URL}/storage/v1/object/public/{bucket}/{path}   public read
 * </pre>
 *
 * <p>The returned URL is the public one, so buckets used here must be marked public in the
 * Supabase dashboard. Making them private and issuing signed URLs is the natural next step
 * once inspection images count as restricted evidence; the change is confined to this class.
 */
@Service
@ConditionalOnProperty(name = "lmguard.storage.provider", havingValue = "supabase")
@Slf4j
public class SupabaseStorageService implements FileStorageService {

    private static final String OBJECT_PATH = "/storage/v1/object/";
    private static final String PUBLIC_PATH = "/storage/v1/object/public/";

    private final RestTemplate restTemplate;
    private final SupabaseProperties supabaseProperties;
    private final StorageProperties storageProperties;

    public SupabaseStorageService(@Qualifier("storageRestTemplate") RestTemplate restTemplate,
                                  SupabaseProperties supabaseProperties,
                                  StorageProperties storageProperties) {
        this.restTemplate = restTemplate;
        this.supabaseProperties = supabaseProperties;
        this.storageProperties = storageProperties;

        if (!supabaseProperties.isConfigured()) {
            // Fail at startup rather than on an inspector's first upload in the field.
            throw new IllegalStateException(
                    "STORAGE_PROVIDER=supabase but SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY are not set. "
                            + "Set them, or use STORAGE_PROVIDER=local for local development.");
        }
        log.info("Using Supabase Storage at {}", supabaseProperties.baseUrl());
    }

    @Override
    public StoredFile upload(String bucket, byte[] file, String filename, String contentType) {
        if (file == null || file.length == 0) {
            throw new StorageException("Refusing to store an empty file");
        }
        String bucketName = storageProperties.bucket(bucket);
        String path = StoragePaths.build(filename);
        String uploadUrl = supabaseProperties.baseUrl() + OBJECT_PATH + bucketName + "/" + path;

        HttpHeaders headers = authHeaders();
        headers.setContentType(resolveContentType(contentType));
        headers.setContentLength(file.length);
        headers.set("x-upsert", "false");
        headers.set("cache-control", "max-age=3600");

        try {
            restTemplate.exchange(uploadUrl, HttpMethod.POST, new HttpEntity<>(file, headers), String.class);
        } catch (HttpClientErrorException ex) {
            throw new StorageException(
                    "Supabase rejected the upload to bucket '%s' (%s). Check the bucket exists and that "
                            .formatted(bucketName, ex.getStatusCode())
                            + "SUPABASE_SERVICE_ROLE_KEY is correct. Response: " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new StorageException("Could not reach Supabase Storage: " + ex.getMessage(), ex);
        }

        String publicUrl = supabaseProperties.baseUrl() + PUBLIC_PATH + bucketName + "/" + path;
        log.debug("Uploaded {} bytes to Supabase bucket {} at {}", file.length, bucketName, path);
        return new StoredFile(publicUrl, path);
    }

    @Override
    public void delete(String bucket, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        String bucketName = storageProperties.bucket(bucket);
        String deleteUrl = supabaseProperties.baseUrl() + OBJECT_PATH + bucketName + "/" + path;

        try {
            restTemplate.exchange(deleteUrl, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), String.class);
            log.debug("Deleted {} from Supabase bucket {}", path, bucketName);
        } catch (HttpClientErrorException.NotFound ex) {
            // Already gone is the outcome the caller wanted.
            log.debug("Object {} was already absent from bucket {}", path, bucketName);
        } catch (RestClientException ex) {
            log.warn("Could not delete {} from Supabase bucket {}: {}", path, bucketName, ex.getMessage());
        }
    }

    @Override
    public String providerName() {
        return "SUPABASE";
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(supabaseProperties.serviceRoleKey());
        headers.set("apikey", supabaseProperties.serviceRoleKey());
        return headers;
    }

    private MediaType resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (org.springframework.http.InvalidMediaTypeException ex) {
            log.debug("Unparseable content type '{}'; storing as application/octet-stream", contentType);
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
