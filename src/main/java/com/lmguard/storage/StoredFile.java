package com.lmguard.storage;

/**
 * Where a stored file ended up.
 *
 * @param url  publicly resolvable URL, for the frontend to render
 * @param path bucket-relative path, for deletion and re-signing
 */
public record StoredFile(String url, String path) {
}
