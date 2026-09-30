package com.editr.api.documents;

public record StartedDocument (
        Document document,
        String newSessionToken
) {}
