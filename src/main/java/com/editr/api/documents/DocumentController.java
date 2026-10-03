package com.editr.api.documents;

import java.util.UUID;
import java.time.Duration;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documentService;

    @Value("${editr.session.cookie-secure}")
    private boolean sessionCookieSecure;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public StartDocumentResponse startDocument(
            @CookieValue(name = "editr_session", required = false)
            String token,
            HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-store");

        StartedDocument result = documentService.startDocument(token);

        if (result.newSessionToken() != null) {
            ResponseCookie cookie = ResponseCookie
                    .from("editr_session", result.newSessionToken())
                    .httpOnly(true)
                    .secure(sessionCookieSecure)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofDays(30))
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        return StartDocumentResponse.from(result.document());
    }

    @GetMapping("/{id}")
    public StartDocumentResponse getDocument(
            @PathVariable("id") UUID id,
            @CookieValue(name = "editr_session", required = false)
            String token,
            HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-store");

        Document document = documentService.getDocument(id, token);
        return StartDocumentResponse.from(document);
    }

    @PutMapping("/{id}/content")
    public StartDocumentResponse updateContent(
            @PathVariable("id") UUID id,
            @CookieValue(name = "editr_session", required = false) String token,
            @Valid @RequestBody UpdateDocumentContentRequest request,
            HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-store");

        Document document = documentService.updateContent(
                id,
                token,
                request.content()
        );

        return StartDocumentResponse.from(document);
    }
    @PutMapping("/{id}/title")
    public StartDocumentResponse renameDocument(
            @PathVariable("id") UUID id,
            @CookieValue(name = "editr_session", required = false) String token,
            @Valid @RequestBody UpdateDocumentTitleRequest request,
            HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-store");

        Document document = documentService.renameDocument(
                id,
                token,
                request.title()
        );

        return StartDocumentResponse.from(document);
    }

    @GetMapping
    public List<DocumentSummaryResponse> listDocuments(
            @CookieValue(name = "editr_session", required = false) String token,
            HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-store");

        return documentService.listDocuments(token);
    }
}
