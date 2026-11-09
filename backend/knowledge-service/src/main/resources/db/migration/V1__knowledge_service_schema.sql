-- ===========================================================================
-- V1__knowledge_service_schema.sql
-- Knowledge Service Schema — Document Ingestion, Versioning, Permissions & FTS
-- ===========================================================================

-- 1. documents table
CREATE TABLE documents (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       TEXT        NOT NULL,
    title           TEXT        NOT NULL,
    content_type    TEXT        NOT NULL DEFAULT 'text/plain',
    source_type     TEXT        NOT NULL DEFAULT 'UPLOAD',
    s3_key          TEXT        NOT NULL,
    file_size_bytes BIGINT      NOT NULL DEFAULT 0,
    content_hash    TEXT        NOT NULL,
    version         INTEGER     NOT NULL DEFAULT 1,
    permissions     TEXT[]      NOT NULL DEFAULT '{"PUBLIC"}',
    status          TEXT        NOT NULL DEFAULT 'INDEXED',
    metadata        JSONB       NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT uq_documents_tenant_hash UNIQUE (tenant_id, content_hash)
);

ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents FORCE ROW LEVEL SECURITY;

CREATE POLICY documents_tenant_isolation ON documents
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

CREATE INDEX idx_documents_tenant ON documents (tenant_id);
CREATE INDEX idx_documents_status ON documents (tenant_id, status);

-- 2. document_chunks table (stores extracted text chunks and PostgreSQL FTS tsvector)
CREATE TABLE document_chunks (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       TEXT        NOT NULL,
    document_id     UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version         INTEGER     NOT NULL DEFAULT 1,
    chunk_index     INTEGER     NOT NULL,
    content         TEXT        NOT NULL,
    token_count     INTEGER     NOT NULL DEFAULT 0,
    embedding_id    TEXT,
    metadata        JSONB       NOT NULL DEFAULT '{}',
    fts_vector      tsvector    GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE document_chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_chunks FORCE ROW LEVEL SECURITY;

CREATE POLICY document_chunks_tenant_isolation ON document_chunks
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

CREATE INDEX idx_chunks_tenant_doc ON document_chunks (tenant_id, document_id);
CREATE INDEX idx_chunks_fts ON document_chunks USING gin (fts_vector);

-- 3. Grants for app_user
GRANT SELECT, INSERT, UPDATE, DELETE ON documents TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON document_chunks TO app_user;
