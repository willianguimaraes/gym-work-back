package com.gymwork.api.assessment.dto;

import com.gymwork.api.assessment.PhotoPosition;
import java.time.Instant;
import java.util.UUID;

/** Foto com URL temporária ({@code url} expira em {@code urlExpiresAt}; peça outra em /photos/{id}/url). */
public record PhotoResponse(
        UUID id,
        UUID assessmentId,
        PhotoPosition position,
        String contentType,
        long sizeBytes,
        Integer width,
        Integer height,
        String url,
        Instant urlExpiresAt,
        Instant createdAt) {
}
