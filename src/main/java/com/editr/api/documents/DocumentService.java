package com.editr.api.documents;

import com.editr.api.sessions.AnonymousSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.editr.api.sessions.AnonymousSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final DocumentAccessRepository documentAccessRepository;
    private final AnonymousSessionService anonymousSessionService;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentAccessRepository documentAccessRepository,
            AnonymousSessionService anonymousSessionService
    ) {
        this.documentRepository = documentRepository;
        this.documentAccessRepository = documentAccessRepository;
        this.anonymousSessionService = anonymousSessionService;
    }

    @Transactional
    public StartedDocument startDocument(String token) {
        Optional<AnonymousSession> existingSession =
                anonymousSessionService.findValidSession(token);

        AnonymousSession session;
        String newSessionToken = null;

        if (existingSession.isPresent()) {
            session = existingSession.get();
        } else {
            AnonymousSessionService.CreatedSession createdSession =
                    anonymousSessionService.createSession();

            session = createdSession.session();
            newSessionToken = createdSession.token();
        }

        Document document = new Document("Untitled Document");
        Document savedDocument = documentRepository.save(document);

        DocumentAccess ownerAccess = new DocumentAccess(
                savedDocument.getId(),
                session.getId(),
                DocumentRole.OWNER
        );

        documentAccessRepository.save(ownerAccess);
        return new StartedDocument(savedDocument, newSessionToken);
    }

    @Transactional(readOnly = true)
    public Document getDocument(UUID id, String token) {
        Optional<AnonymousSession> session = anonymousSessionService.findValidSession(token);

        if (session.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "A valid session is required"
            );
        }

        AnonymousSession currentSession = session.get();
        UUID sessionId = currentSession.getId();
        boolean hasAccess = documentAccessRepository.existsByDocumentIdAndSessionId(
                id, sessionId
        );

        if (!hasAccess) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Document not found"
            );
        }

        Optional<Document> document = documentRepository.findById(id);
        if (document.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found"
            );
        }

        return document.get();
    }

    @Transactional
    public Document updateContent(UUID id, String token, String content) {
        Document document = getDocument(id, token);
        document.updateContent(content);
        return document;
    }

    @Transactional
    public Document renameDocument(UUID id, String token, String title) {
        Document document = getDocument(id, token);
        document.rename(title);
        return document;
    }

    @Transactional(readOnly = true)
    public List<DocumentSummaryResponse> listDocuments(String token) {
        AnonymousSession session = anonymousSessionService
                .findValidSession(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "A valid session is required"
                ));

        return documentRepository.findAccessibleDocuments(session.getId());
    }
}
