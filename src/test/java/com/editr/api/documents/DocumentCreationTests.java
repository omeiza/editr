package com.editr.api.documents;
import com.editr.api.sessions.AnonymousSessionService;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "editr.session.cookie-secure=false")
@AutoConfigureMockMvc
@Testcontainers
class DocumentCreationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnonymousSessionService anonymousSessionService;

    @Autowired
    private DocumentAccessRepository documentAccessRepository;

    @Test
    void creatorCanSaveAndReopenTheirDocument() throws Exception {
        // 1. Create a document without an existing session.
        MvcResult creationResult = mockMvc.perform(post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Untitled Document"))
                .andExpect(jsonPath("$.content").value(""))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(cookie().exists("editr_session"))
                .andExpect(cookie().httpOnly("editr_session", true))
                .andReturn();

        // 2. Keep the document ID and the creator's session cookie.
        String responseBody = creationResult.getResponse().getContentAsString();
        String documentId = JsonPath.read(responseBody, "$.id");
        Cookie sessionCookie = creationResult.getResponse().getCookie("editr_session");

        assertThat(sessionCookie).isNotNull();

        // 3. Save text using the creator's session.
        mockMvc.perform(put("/api/documents/{id}/content", documentId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "My first saved document!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId))
                .andExpect(jsonPath("$.content").value("My first saved document!"));

        // 4. Reopen the document and verify the saved text.
        mockMvc.perform(get("/api/documents/{id}", documentId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId))
                .andExpect(jsonPath("$.title").value("Untitled Document"))
                .andExpect(jsonPath("$.content").value("My first saved document!"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void savingWithoutContentIsRejected() throws Exception {
        // Create a document and keep its session cookie.
        MvcResult creationResult = mockMvc.perform(post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId = JsonPath.read(
                creationResult.getResponse().getContentAsString(),
                "$.id"
        );

        Cookie sessionCookie = creationResult.getResponse().getCookie("editr_session");

        assertThat(sessionCookie).isNotNull();

        // Send JSON that is missing the required content field.
        mockMvc.perform(put("/api/documents/{id}/content", documentId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // Verify that the document still has its original empty content.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(""));
    }

    @Test
    void anotherSessionCannotOverwriteDocument() throws Exception {
        // Create a document belonging to the first session.
        MvcResult ownerResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId = JsonPath.read(
                ownerResult.getResponse().getContentAsString(),
                "$.id"
        );

        Cookie ownerCookie = ownerResult.getResponse().getCookie("editr_session");
        assertThat(ownerCookie).isNotNull();

        // Save the owner's text.
        mockMvc.perform(put("/api/documents/{id}/content", documentId)
                        .cookie(ownerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "content": "The owner's text"
                            }
                            """))
                .andExpect(status().isOk());

        // Start another document without a cookie to create a new session.
        MvcResult otherResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie otherCookie = otherResult.getResponse().getCookie("editr_session");
        assertThat(otherCookie).isNotNull();

        // The second session must not be allowed to change the first document.
        mockMvc.perform(put("/api/documents/{id}/content", documentId)
                        .cookie(otherCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "content": "Someone else's text"
                            }
                            """))
                .andExpect(status().isForbidden());

        // Verify that the owner's text is still intact.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("The owner's text"));
    }

    @Test
    void creatorCanRenameAndReopenTheirDocument() throws Exception {
        // 1. Create a document.
        MvcResult creationResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId = JsonPath.read(
                creationResult.getResponse().getContentAsString(),
                "$.id"
        );

        Cookie sessionCookie = creationResult.getResponse().getCookie("editr_session");
        assertThat(sessionCookie).isNotNull();

        // 2. Rename it using the creator's session.
        mockMvc.perform(put("/api/documents/{id}/title", documentId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "title": "My first draft"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId))
                .andExpect(jsonPath("$.title").value("My first draft"));

        // 3. Reopen it to verify that the new title was saved.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("My first draft"));
    }

    @Test
    void blankTitleIsRejected() throws Exception {
        // 1. Create a document.
        MvcResult creationResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId = JsonPath.read(
                creationResult.getResponse().getContentAsString(),
                "$.id"
        );

        Cookie sessionCookie = creationResult.getResponse().getCookie("editr_session");
        assertThat(sessionCookie).isNotNull();

        // 2. Try to rename it to whitespace.
        mockMvc.perform(put("/api/documents/{id}/title", documentId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "title": "   "
                            }
                            """))
                .andExpect(status().isBadRequest());

        // 3. Verify that the original title remains.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Untitled Document"));
    }

    @Test
    void titleLongerThan255CharactersIsRejected() throws Exception {
        // 1. Create a document.
        MvcResult creationResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId = JsonPath.read(
                creationResult.getResponse().getContentAsString(),
                "$.id"
        );

        Cookie sessionCookie = creationResult.getResponse().getCookie("editr_session");
        assertThat(sessionCookie).isNotNull();

        // 2. Build a title one character over the limit.
        String longTitle = "a".repeat(256);

        String requestBody = """
            {
              "title": "%s"
            }
            """.formatted(longTitle);

        // 3. Try to save the oversized title.
        mockMvc.perform(put("/api/documents/{id}/title", documentId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        // 4. Verify that the original title remains.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Untitled Document"));
    }

    @Test
    void anotherSessionCannotRenameDocument() throws Exception {
        // 1. Create the owner's document.
        MvcResult ownerResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId = JsonPath.read(
                ownerResult.getResponse().getContentAsString(),
                "$.id"
        );

        Cookie ownerCookie = ownerResult.getResponse().getCookie("editr_session");
        assertThat(ownerCookie).isNotNull();

        // 2. Create another session by sending no cookie.
        MvcResult otherResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie otherCookie = otherResult.getResponse().getCookie("editr_session");
        assertThat(otherCookie).isNotNull();

        // 3. Try to rename the owner's document as the other session.
        mockMvc.perform(put("/api/documents/{id}/title", documentId)
                        .cookie(otherCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "title": "Someone else's title"
                            }
                            """))
                .andExpect(status().isForbidden());

        // 4. Verify that the title is unchanged.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Untitled Document"));
    }

    @Test
    void sessionOnlyListsItsAccessibleDocuments() throws Exception {
        // 1. Create the first document and keep its session cookie.
        MvcResult firstResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        String firstDocumentId = JsonPath.read(
                firstResult.getResponse().getContentAsString(),
                "$.id"
        );

        Cookie sessionCookie = firstResult.getResponse().getCookie("editr_session");
        assertThat(sessionCookie).isNotNull();

        // 2. Create another document using the same session.
        MvcResult secondResult = mockMvc.perform(
                        post("/api/documents/start")
                                .cookie(sessionCookie))
                .andExpect(status().isCreated())
                .andReturn();

        String secondDocumentId = JsonPath.read(
                secondResult.getResponse().getContentAsString(),
                "$.id"
        );

        // 3. Create someone else's document by sending no cookie.
        MvcResult otherResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        String otherDocumentId = JsonPath.read(
                otherResult.getResponse().getContentAsString(),
                "$.id"
        );

        // 4. List summaries belonging to the original session.
        MvcResult listResult = mockMvc.perform(
                        get("/api/documents")
                                .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].title").value("Untitled Document"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].updatedAt").exists())
                .andExpect(jsonPath("$[*].content").isEmpty())
                .andReturn();

        java.util.List<String> documentIds = JsonPath.read(
                listResult.getResponse().getContentAsString(),
                "$[*].id"
        );

        // 5. The list must contain exactly our two documents.
        assertThat(documentIds)
                .containsExactlyInAnyOrder(firstDocumentId, secondDocumentId)
                .doesNotContain(otherDocumentId);
    }

    @Test
    void listingDocumentsWithoutSessionIsRejected() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingDocumentsWithUnknownSessionIsRejected() throws Exception {
        Cookie unknownCookie = new Cookie(
                "editr_session",
                "a".repeat(43)
        );

        mockMvc.perform(get("/api/documents")
                        .cookie(unknownCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCanArchiveDocumentWithoutLosingContent() throws Exception {
        // 1. Create a document and keep the owner's session cookie.
        MvcResult creationResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId = JsonPath.read(
                creationResult.getResponse().getContentAsString(),
                "$.id"
        );

        Cookie sessionCookie = creationResult.getResponse().getCookie("editr_session");
        assertThat(sessionCookie).isNotNull();

        // 2. Save some text.
        mockMvc.perform(put("/api/documents/{id}/content", documentId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "content": "Keep this text."
                            }
                            """))
                .andExpect(status().isOk());

        // 3. Archive the document.
        mockMvc.perform(put("/api/documents/{id}/archive", documentId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // 4. It should no longer appear in the active list.
        mockMvc.perform(get("/api/documents")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // 5. It should still be accessible directly, with its text intact.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.content").value("Keep this text."));
    }

    @Test
    void anotherSessionCannotArchiveDocument() throws Exception {
        // 1. Create the owner's document.
        MvcResult ownerResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId = JsonPath.read(
                ownerResult.getResponse().getContentAsString(),
                "$.id"
        );

        Cookie ownerCookie = ownerResult.getResponse().getCookie("editr_session");
        assertThat(ownerCookie).isNotNull();

        // 2. Create a separate session by sending no cookie.
        MvcResult otherResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie otherCookie = otherResult.getResponse().getCookie("editr_session");
        assertThat(otherCookie).isNotNull();

        // 3. Try to archive the owner's document as the other session.
        mockMvc.perform(put("/api/documents/{id}/archive", documentId)
                        .cookie(otherCookie))
                .andExpect(status().isForbidden());

        // 4. Verify that the document is still active.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 5. Verify that it still appears in the owner's active list.
        mockMvc.perform(get("/api/documents")
                        .cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(documentId));
    }

    @Test
    void editorCannotArchiveDocument() throws Exception {
        // 1. Create a document belonging to an owner.
        MvcResult creationResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId = JsonPath.read(
                creationResult.getResponse().getContentAsString(),
                "$.id"
        );

        // 2. Create a separate session and grant it editor access.
        AnonymousSessionService.CreatedSession editor = anonymousSessionService.createSession();

        documentAccessRepository.save(new DocumentAccess(
                UUID.fromString(documentId),
                editor.session().getId(),
                DocumentRole.EDITOR
        ));

        Cookie editorCookie = new Cookie(
                "editr_session",
                editor.token()
        );

        // 3. Confirm that the editor can read the document.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .cookie(editorCookie))
                .andExpect(status().isOk());

        // 4. The editor must not be allowed to archive it.
        mockMvc.perform(put("/api/documents/{id}/archive", documentId)
                        .cookie(editorCookie))
                .andExpect(status().isForbidden());

        // 5. Confirm that the document remains active.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .cookie(editorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void ownerCanFindAndRestoreArchivedDocument() throws Exception {
        // 1. Create a document.
        MvcResult creationResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId = JsonPath.read(
                creationResult.getResponse().getContentAsString(),
                "$.id"
        );

        Cookie sessionCookie = creationResult.getResponse().getCookie("editr_session");
        assertThat(sessionCookie).isNotNull();

        // 2. Save text before archiving.
        mockMvc.perform(put("/api/documents/{id}/content", documentId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "content": "Keep this through restoration."
                            }
                            """))
                .andExpect(status().isOk());

        // 3. Archive the document.
        mockMvc.perform(put("/api/documents/{id}/archive", documentId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk());

        // 4. Find it in the archived list.
        mockMvc.perform(get("/api/documents")
                        .param("status", "ARCHIVED")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(documentId))
                .andExpect(jsonPath("$[0].status").value("ARCHIVED"));

        // 5. Restore it.
        mockMvc.perform(put("/api/documents/{id}/restore", documentId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 6. It should no longer appear in the archived list.
        mockMvc.perform(get("/api/documents")
                        .param("status", "ARCHIVED")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // 7. It should appear in the default active list.
        mockMvc.perform(get("/api/documents")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(documentId))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        // 8. Reopening it should return the original text.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content")
                        .value("Keep this through restoration."));
    }

    @Test
    void editorCannotRestoreDocument() throws Exception {
        // 1. Create the owner's document.
        MvcResult creationResult = mockMvc.perform(
                        post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId = JsonPath.read(
                creationResult.getResponse().getContentAsString(),
                "$.id"
        );

        Cookie ownerCookie = creationResult.getResponse().getCookie("editr_session");
        assertThat(ownerCookie).isNotNull();

        // 2. Archive it as the owner.
        mockMvc.perform(put("/api/documents/{id}/archive", documentId)
                        .cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // 3. Create another session with editor access.
        AnonymousSessionService.CreatedSession editor =
                anonymousSessionService.createSession();

        documentAccessRepository.save(new DocumentAccess(
                UUID.fromString(documentId),
                editor.session().getId(),
                DocumentRole.EDITOR
        ));

        Cookie editorCookie = new Cookie(
                "editr_session",
                editor.token()
        );

        // 4. Confirm the editor can read the archived document.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .cookie(editorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // 5. Try to restore it as the editor.
        mockMvc.perform(put("/api/documents/{id}/restore", documentId)
                        .cookie(editorCookie))
                .andExpect(status().isForbidden());

        // 6. Confirm it remains archived.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }
}
