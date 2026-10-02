package com.editr.api.documents;

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
}
