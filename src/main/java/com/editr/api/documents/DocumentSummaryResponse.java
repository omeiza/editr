package com.editr.api.documents;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentSummaryResponse(
        UUID id,
        String title,
        DocumentStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
