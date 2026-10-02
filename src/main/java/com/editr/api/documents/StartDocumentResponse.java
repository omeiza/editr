package com.editr.api.documents;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StartDocumentResponse (
    UUID id,
    String title,
    String content,
    DocumentStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static StartDocumentResponse from(Document document) {
        return new StartDocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getContent(),
                document.getStatus(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
