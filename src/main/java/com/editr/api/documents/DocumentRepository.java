package com.editr.api.documents;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

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
}
