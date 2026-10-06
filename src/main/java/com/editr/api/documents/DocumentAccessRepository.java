package com.editr.api.documents;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DocumentAccessRepository extends JpaRepository<DocumentAccess, UUID> {
    boolean existsByDocumentIdAndSessionId(
            UUID documentId,
            UUID sessionId
    );

    boolean existsByDocumentIdAndSessionIdAndRole(
            UUID documentId,
            UUID sessionId,
            DocumentRole role
    );
}
