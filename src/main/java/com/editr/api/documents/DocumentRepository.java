package com.editr.api.documents;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /* This inherits the following methods from JPA:
     *
     * 1. save()
     * 2. findById()
     * 3. findAll()
     * 4. existsById(id)
     * 5. deleteById(id)
     * 6. count()
     *
     * And many more...
     *
     * We can also add more methods here
     */

    @Query("""
        SELECT d
        FROM Document d
        WHERE d.id IN (
            SELECT access.documentId
            FROM DocumentAccess access
            WHERE access.sessionId = :sessionId
        )
        ORDER BY d.updatedAt DESC, d.id ASC
        """)
    List<Document> findAccessibleDocuments(
            @Param("sessionId") UUID sessionId
    );
}
