package com.editr.api.documents;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void creatorCanRetrieveTheirNewDocument() throws Exception {
        // Create a document without an existing session.
        MvcResult creationResult = mockMvc.perform(post("/api/documents/start"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Untitled Document"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(cookie().exists("editr_session"))
                .andExpect(cookie().httpOnly("editr_session", true))
                .andReturn();

        // Read the document ID and session cookie from the response.
        String responseBody = creationResult.getResponse().getContentAsString();
        String documentId = JsonPath.read(responseBody, "$.id");
        Cookie sessionCookie = creationResult.getResponse().getCookie("editr_session");

        assertThat(sessionCookie).isNotNull();

        // Reopen the document using the creator's session.
        mockMvc.perform(get("/api/documents/{id}", documentId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId))
                .andExpect(jsonPath("$.title").value("Untitled Document"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
