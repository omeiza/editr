package com.editr.api.documents;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_access")
public class DocumentAccess {
    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentRole role;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected DocumentAccess() {}

    public DocumentAccess(UUID documentId, UUID sessionId, DocumentRole role) {
        this.id = UUID.randomUUID();
        this.documentId = documentId;
        this.sessionId = sessionId;
        this.role = role;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public DocumentRole getRole() {
        return role;
    }
}
