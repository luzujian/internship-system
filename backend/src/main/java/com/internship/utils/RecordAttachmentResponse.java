package com.internship.utils;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Browser-safe byte response for an already authorized record attachment. */
public final class RecordAttachmentResponse {
    private RecordAttachmentResponse() { }

    public static ResponseEntity<byte[]> ok(byte[] bytes) {
        return ResponseEntity.ok()
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }
}
