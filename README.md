# AI Research — Document-grounded RAG

Spring Boot 4 / Java 21 research assistant with PostgreSQL + pgvector, Google Gemini, and S3-compatible object storage (Backblaze B2).

## Features

- Workspace-scoped PDF upload, validation, storage, extraction, chunking and embeddings.
- Optional page-level OCR for scanned PDFs and mixed scanned/digital documents (Tesseract).
- Durable PostgreSQL ingestion jobs with atomic claims, retry/backoff and stale-job recovery.
- PGVector retrieval restricted to READY documents in the requested workspace.
- Gemini answers grounded in retrieved passages with document/page/chunk citations.
- Persistent conversations and recent message history.
- Soft deletion with resumable object/vector cleanup.
- JWT access tokens, rotating opaque refresh tokens, logout-all revocation and account-status checks.
- Workspace storage quotas, upload/RAG rate limits, validation and consistent API errors.

## Requirements

- Java 21
- PostgreSQL 16 with pgvector
- Google GenAI API access (chat and embeddings)
- Backblaze B2 or another S3-compatible object store

## Local setup

1. Start PostgreSQL:

   ```bash
   docker compose up -d postgres
   ```

2. Copy `src/main/resources/application.example.yaml` to your local `application.yaml` (do not commit secrets) and set:
   - PostgreSQL credentials
   - Google GenAI chat and embedding API keys and project ID
   - S3-compatible endpoint, region, bucket, key ID and application key
   - A Base64 JWT secret generated with `openssl rand -base64 32`

3. Run the application:

   ```bash
   bash ./mvnw spring-boot:run
   ```

Flyway applies the versioned PostgreSQL schema. Hibernate validates the mapped schema. For an existing database created by earlier Hibernate versions, baseline-on-migrate is enabled at version 0; the first migration is additive and uses `IF NOT EXISTS` where appropriate. Back up an existing database before enabling migrations in production.

## API overview

All endpoints are prefixed with `/api/v1`.

### Authentication

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh` — rotate a refresh token
- `POST /auth/logout` — revoke all refresh sessions and invalidate issued access tokens
- `GET /users/me`
- `PATCH /users/me/password` — change password and revoke all existing sessions

Register/login responses contain `accessToken`, `refreshToken`, `expiresInSeconds` and the user profile. Store refresh tokens securely; production browser clients should prefer an HttpOnly, Secure, SameSite cookie over JavaScript-accessible storage.

### Workspaces and documents

- `POST /workspaces`, `GET /workspaces`, `GET /workspaces/{workspaceId}`
- `PATCH /workspaces/{workspaceId}`, `DELETE /workspaces/{workspaceId}`
- `POST /workspaces/{workspaceId}/documents` (multipart field: `file`; legacy singular `/workspace/{workspaceId}/documents` is also supported)
- `GET /workspaces/{workspaceId}/documents`
- `GET /documents/{documentId}`
- `GET /documents/{documentId}/processing` — inspect retry attempts and next scheduled processing time
- `GET /documents/{documentId}/download`
- `GET /documents/{documentId}/content?offset=0&limit=20000`
- `POST /documents/{documentId}/retry`
- `DELETE /documents/{documentId}`

Uploads return the document record immediately. Processing runs asynchronously; poll the document endpoint until status is `READY` or `FAILED`.

### RAG chat

- `POST /workspaces/{workspaceId}/ask`

  ```json
  {
    "question": "What are the key findings?",
    "conversationId": null
  }
  ```

  Pass the returned `conversationId` to continue the conversation. The response includes the answer and retrieved source metadata.

- `GET /workspaces/{workspaceId}/conversations`
- `GET /workspaces/{workspaceId}/conversations/{conversationId}/messages`
- `DELETE /workspaces/{workspaceId}/conversations/{conversationId}`

The Postman collection includes example requests. Configure its variables (`baseUrl`, `email`, `password`, `accessToken`, `refreshToken`, `workspaceId`, `documentId`, and `conversationId`) before running requests.

### Scanned PDF OCR

OCR is opt-in so deployments without the native Tesseract runtime are unaffected. Install Tesseract and the language data files on the application host/container, then configure:

```yaml
app:
  ocr:
    enabled: true
    datapath: /usr/share/tesseract-ocr/5/tessdata
    language: eng
```

The extractor first uses embedded PDF text and invokes OCR only for pages with fewer than 24 extracted characters. It preserves page separators for downstream chunk citations. Install additional traineddata files and set `language` (for example, `eng+hin`) when multilingual OCR is required. OCR increases CPU and memory use; keep the configured PDF page and upload limits appropriate for the deployment.

## Configuration notes

- Upload policy is 25 MB per file; workspace quota is 500 MB by default.
- Multipart limits are explicitly set to match the upload policy.
- Ingestion polling and reconciliation intervals can be configured with `app.ingestion.poll-interval-ms` and `app.ingestion.reconcile-interval-ms`.
- Incomplete document and workspace deletion cleanup is retried in the background.
- Health probes are available at `/actuator/health/liveness` and `/actuator/health/readiness`.
- Refresh tokens are opaque random values; only SHA-256 hashes are stored in PostgreSQL. Access tokens expire after one hour and refresh tokens after 30 days.
- Logout invalidates all active sessions for the account by incrementing its token version.

## Tests and CI

Run locally:

```bash
bash ./mvnw --batch-mode --no-transfer-progress clean verify
```

GitHub Actions runs the Maven verification suite with Java 21 and PostgreSQL/pgvector.

## Production deployment checklist

- Use a managed PostgreSQL instance with pgvector and scheduled backups.
- Provide production Google GenAI and S3 credentials through a secret manager.
- Set exact allowed CORS origins; do not use wildcard origins with credentials.
- Use HTTPS and secure cookie storage for browser refresh tokens.
- Configure trusted reverse-proxy handling before relying on client IP rate limits.
- Monitor ingestion jobs in `PENDING`/ `DEAD` and documents in `FAILED`/ `DELETING`.
- Run an end-to-end smoke test with real Gemini and object storage credentials before deployment.
- Configure OCR for scanned/image-only PDFs if that content must be searchable; current extraction is text-based PDF extraction.
