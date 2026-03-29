package com.clonezapper.handler;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispatches to the appropriate {@link FileTypeHandler} based on MIME type.
 * Handlers are priority-ordered: specific handlers (image, document) are checked
 * before the generic fallback.
 */
@Component
public class HandlerRegistry {

    private final List<FileTypeHandler> handlers;

    public HandlerRegistry(List<FileTypeHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * Returns the first handler that can handle the given MIME type.
     * Falls back to {@link GenericHandler} if no specific handler matches.
     */
    public FileTypeHandler dispatch(String mimeType) {
        return handlers.stream()
            .filter(h -> h.canHandle(mimeType))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No handler found for MIME type: " + mimeType +
                " (GenericHandler should always match — check bean registration)"));
    }
}
