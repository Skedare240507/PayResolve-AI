package com.payresolve.cases;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaseAttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CaseService caseService;

    @Autowired
    private CaseAttachmentRepository attachmentRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        caseService.seedDemoCases();
        jdbcTemplate.update("DELETE FROM case_attachments");
    }

    @Test
    @DisplayName("Upload rejects file with fake PNG content-type but invalid magic bytes")
    void rejectsSpoofedContentTypeWithoutMagicBytes() throws Exception {
        byte[] textContent = "This is plain text pretending to be a PNG".getBytes();
        MockMultipartFile fakePng = new MockMultipartFile(
            "file",
            "fake.png",
            "image/png",
            textContent
        );

        mockMvc.perform(multipart("/api/cases/PR-0142/attachments")
                .file(fakePng)
                .with(user("operator").roles("OPERATOR"))
                .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid file type. Allowed formats: PNG, JPEG, PDF"));
    }

    @Test
    @DisplayName("Upload accepts valid PNG with magic bytes and randomizes stored filename")
    void acceptsValidPngAndRandomizesFilename() throws Exception {
        // PNG magic bytes: 0x89, 0x50, 0x4E, 0x47
        byte[] validPngBytes = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00 };
        MockMultipartFile validPng = new MockMultipartFile(
            "file",
            "malicious-original-name.exe.png",
            "image/png",
            validPngBytes
        );

        mockMvc.perform(multipart("/api/cases/PR-0142/attachments")
                .file(validPng)
                .with(user("operator").roles("OPERATOR"))
                .with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.filename").isNotEmpty())
            .andExpect(jsonPath("$.filename").value(org.hamcrest.Matchers.not("malicious-original-name.exe.png")))
            .andExpect(jsonPath("$.filename").value(org.hamcrest.Matchers.endsWith(".png")))
            .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    @DisplayName("Upload rejects when attachments reach cap of 5 per case with 422")
    void capsAttachmentsAtFive() throws Exception {
        byte[] validPdfBytes = "%PDF-1.4 test content".getBytes();

        boolean hitCap = false;
        for (int i = 0; i < 7; i++) {
            MockMultipartFile pdf = new MockMultipartFile(
                "file",
                "test-" + i + ".pdf",
                "application/pdf",
                validPdfBytes
            );

            var result = mockMvc.perform(multipart("/api/cases/PR-0141/attachments")
                    .file(pdf)
                    .with(user("operator").roles("OPERATOR"))
                    .with(csrf()))
                .andReturn();

            if (result.getResponse().getStatus() == 422) {
                assertThat(result.getResponse().getContentAsString()).contains("Maximum 5 attachments allowed per case");
                hitCap = true;
                break;
            }
        }
        assertThat(hitCap).isTrue();
    }

    @Test
    @DisplayName("VIEWER role cannot upload attachments (returns 403)")
    void viewerCannotUpload() throws Exception {
        byte[] validPdfBytes = "%PDF-1.4 test content".getBytes();
        MockMultipartFile pdf = new MockMultipartFile(
            "file",
            "receipt.pdf",
            "application/pdf",
            validPdfBytes
        );

        mockMvc.perform(multipart("/api/cases/PR-0142/attachments")
                .file(pdf)
                .with(user("viewer").roles("VIEWER"))
                .with(csrf()))
            .andExpect(status().isForbidden());
    }
}
