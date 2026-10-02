package com.editr.api.documents;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name="created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name="updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name="archived_at")
    private OffsetDateTime archivedAt;

    protected Document() {
        // Required by JPA
    }

    public Document(String title) {
        OffsetDateTime now = OffsetDateTime.now();

        this.id = UUID.randomUUID();
        this.title = title;
        this.status = DocumentStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public void updateContent(String content) {
        this.content = content;
        this.updatedAt = OffsetDateTime.now();
    }
}
