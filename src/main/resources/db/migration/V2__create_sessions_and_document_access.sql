CREATE TABLE anonymous_sessions
(
    id         UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (expires_at > created_at)
);

CREATE TABLE document_access
(
    id          UUID PRIMARY KEY,
    document_id UUID                     NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    session_id  UUID                     NOT NULL REFERENCES anonymous_sessions (id) ON DELETE CASCADE,
    role        VARCHAR(20)              NOT NULL CHECK (role IN ('OWNER', 'EDITOR')),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_document_access UNIQUE (document_id, session_id)
);

CREATE UNIQUE INDEX uq_document_owner ON document_access (document_id) WHERE role = 'OWNER';