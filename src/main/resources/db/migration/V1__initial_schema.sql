-- Initial schema for fresh installs and additive baseline for databases created by Hibernate.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS users (
    id uuid PRIMARY KEY,
    created_at timestamptz,
    updated_at timestamptz,
    email varchar(255) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    full_name varchar(255) NOT NULL,
    role varchar(255) NOT NULL,
    status varchar(255) NOT NULL,
    is_email_verified boolean NOT NULL DEFAULT false,
    token_version integer NOT NULL DEFAULT 0
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS token_version integer NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS workspace (
    id uuid PRIMARY KEY,
    created_at timestamptz,
    updated_at timestamptz,
    deleted_at timestamptz,
    owner_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    description varchar(255),
    doc_count integer NOT NULL DEFAULT 0,
    chat_count integer NOT NULL DEFAULT 0,
    byte_count bigint NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_workspace_owner_created
    ON workspace(owner_id, created_at DESC);

CREATE TABLE IF NOT EXISTS document (
    id uuid PRIMARY KEY,
    created_at timestamptz,
    updated_at timestamptz,
    deleted_at timestamptz,
    workspace_id uuid NOT NULL REFERENCES workspace(id),
    uploaded_by uuid NOT NULL,
    display_name varchar(255) NOT NULL,
    original_file_name varchar(255),
    source_type varchar(255) NOT NULL,
    mime_type varchar(255) NOT NULL,
    size_bytes bigint NOT NULL,
    checksum_sha256 varchar(255) NOT NULL,
    storage_bucket varchar(255) NOT NULL,
    storage_key varchar(255) NOT NULL,
    extracted_key varchar(255),
    status varchar(255) NOT NULL,
    page_count integer,
    char_count integer,
    error_code varchar(255),
    error_message varchar(255)
);

CREATE INDEX IF NOT EXISTS idx_document_workspace_status
    ON document(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_document_workspace_checksum
    ON document(workspace_id, checksum_sha256)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS document_chunk (
    id uuid PRIMARY KEY,
    created_at timestamptz,
    document_id uuid NOT NULL,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    token_count integer NOT NULL,
    char_start integer NOT NULL,
    char_end integer NOT NULL,
    page_number integer
);

CREATE INDEX IF NOT EXISTS idx_document_chunk_document_index
    ON document_chunk(document_id, chunk_index);

CREATE TABLE IF NOT EXISTS chat_conversations (
    id uuid PRIMARY KEY,
    created_at timestamptz,
    updated_at timestamptz,
    deleted_at timestamptz,
    workspace_id uuid NOT NULL REFERENCES workspace(id),
    user_id uuid NOT NULL,
    title varchar(120) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_conversation_workspace_user_created
    ON chat_conversations(workspace_id, user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS chat_messages (
    id uuid PRIMARY KEY,
    created_at timestamptz,
    conversation_id uuid NOT NULL REFERENCES chat_conversations(id),
    role varchar(20) NOT NULL,
    content text NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_created
    ON chat_messages(conversation_id, created_at);

CREATE TABLE IF NOT EXISTS document_processing_jobs (
    id uuid PRIMARY KEY,
    created_at timestamptz,
    document_id uuid NOT NULL UNIQUE,
    status varchar(20) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    locked_at timestamptz,
    last_error varchar(500)
);

CREATE INDEX IF NOT EXISTS idx_document_processing_job_due
    ON document_processing_jobs(status, next_attempt_at);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id uuid PRIMARY KEY,
    created_at timestamptz,
    user_id uuid NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user
    ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_expiry
    ON refresh_tokens(expires_at);
