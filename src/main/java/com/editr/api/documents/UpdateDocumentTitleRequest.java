package com.editr.api.documents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDocumentTitleRequest (
    @NotBlank
    @Size(max = 255)
    String title
) {}
