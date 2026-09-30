package com.editr.api.sessions;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AnonymousSessionService {
    private final AnonymousSessionRepository repository;
    private final SecureRandom random = new SecureRandom();

    public AnonymousSessionService(AnonymousSessionRepository repository) {
        this.repository = repository;
    }

    public record CreatedSession(AnonymousSession session, String token) {}

    @Transactional
    public CreatedSession createSession() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        Base64.Encoder encoder = Base64.getUrlEncoder();
        Base64.Encoder unpaddedEncoder = encoder.withoutPadding();
        String token = unpaddedEncoder.encodeToString(bytes);

        OffsetDateTime now = OffsetDateTime.now();
        AnonymousSession session = new AnonymousSession(hashToken(token), now, now.plusDays(30));

        return new CreatedSession(repository.save(session), token);
    }

    @Transactional(readOnly = true)
    public Optional<AnonymousSession> findValidSession(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            return Optional.empty();
        }

        return repository.findByTokenHash(hashToken(token))
                .filter(session -> !session.isExpired(OffsetDateTime.now()));
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
