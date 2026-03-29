package com.clonezapper.provider;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Abstraction over a storage backend (local FS, Google Drive, OneDrive, Dropbox).
 * Each provider enumerates files as a Stream of Paths for the pipeline to process.
 */
public interface StorageProvider {

    /** Short name used in the files.provider column (e.g. "local", "gdrive"). */
    String getName();

    /**
     * Enumerate all regular files under the given root path.
     * The caller is responsible for closing the returned stream.
     *
     * @param rootPath provider-specific root identifier (local path, drive ID, etc.)
     * @return stream of paths — lazy where possible
     */
    Stream<Path> enumerate(String rootPath) throws IOException;
}
