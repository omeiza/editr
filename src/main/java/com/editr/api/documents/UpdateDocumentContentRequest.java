package com.editr.api.documents;

import jakarta.validation.constraints.NotNull;

public record UpdateDocumentContentRequest(@NotNull String content) {}
