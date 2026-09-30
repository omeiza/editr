package com.editr.api.sessions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AnonymousSessionRepository extends JpaRepository<AnonymousSession, UUID> {
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

    Optional<AnonymousSession> findByTokenHash(String tokenHash);
}