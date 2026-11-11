-- MySQL 8.4 / eth20. Additive migration; never drops existing tables.
-- Run with --default-character-set=utf8mb4 and select the application database.
CREATE TABLE IF NOT EXISTS rag_knowledge_base (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NULL,
    access_scope VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_rag_kb_code UNIQUE (code),
    CONSTRAINT ck_rag_kb_access CHECK (access_scope IN ('PRIVATE', 'PUBLIC')),
    CONSTRAINT ck_rag_kb_enabled CHECK (enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS rag_document (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT UNSIGNED NOT NULL,
    source_key VARCHAR(191) NOT NULL COMMENT 'Stable application-assigned source ID, not the full URL',
    version INT UNSIGNED NOT NULL DEFAULT 1,
    title VARCHAR(500) NOT NULL,
    source_type VARCHAR(16) NOT NULL COMMENT 'FILE, URL, TEXT, DATABASE',
    source_uri TEXT NULL COMMENT 'Source locator only; never store signed URLs or credentials',
    mime_type VARCHAR(127) NULL,
    language VARCHAR(35) NOT NULL DEFAULT 'zh-TW',
    content LONGTEXT NOT NULL COMMENT 'Extracted, normalized text used for chunking',
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    chunker_version VARCHAR(100) NULL COMMENT 'Splitter implementation/configuration identity',
    metadata JSON NULL COMMENT 'Non-secret additional source metadata',
    last_error TEXT NULL,
    indexed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_rag_doc_kb FOREIGN KEY (knowledge_base_id) REFERENCES rag_knowledge_base(id) ON DELETE RESTRICT,
    CONSTRAINT uk_rag_doc_version UNIQUE (knowledge_base_id, source_key, version),
    CONSTRAINT ck_rag_doc_version CHECK (version > 0),
    CONSTRAINT ck_rag_doc_source CHECK (source_type IN ('FILE', 'URL', 'TEXT', 'DATABASE')),
    CONSTRAINT ck_rag_doc_status CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'ARCHIVED')),
    CONSTRAINT ck_rag_doc_hash CHECK (content_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_rag_doc_content CHECK (CHAR_LENGTH(TRIM(content)) > 0),
    CONSTRAINT ck_rag_doc_meta CHECK (metadata IS NULL OR JSON_TYPE(metadata) = 'OBJECT'),
    INDEX idx_rag_doc_search (knowledge_base_id, status),
    INDEX idx_rag_doc_hash (knowledge_base_id, content_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS rag_chunk (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT UNSIGNED NOT NULL,
    chunk_index INT UNSIGNED NOT NULL COMMENT 'Zero-based order within one document version',
    content TEXT NOT NULL,
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_count INT UNSIGNED NULL COMMENT 'Use the embedding model tokenizer, not character count',
    page_number INT UNSIGNED NULL COMMENT 'One-based PDF page, if applicable',
    section_title VARCHAR(500) NULL,
    char_start INT UNSIGNED NULL COMMENT 'Zero-based Unicode code point offset into normalized document content',
    char_end INT UNSIGNED NULL COMMENT 'Exclusive end, same unit as char_start',
    metadata JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_rag_chunk_doc FOREIGN KEY (document_id) REFERENCES rag_document(id) ON DELETE CASCADE,
    CONSTRAINT uk_rag_chunk_order UNIQUE (document_id, chunk_index),
    CONSTRAINT ck_rag_chunk_content CHECK (CHAR_LENGTH(TRIM(content)) > 0),
    CONSTRAINT ck_rag_chunk_hash CHECK (content_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_rag_chunk_page CHECK (page_number IS NULL OR page_number > 0),
    CONSTRAINT ck_rag_chunk_offsets CHECK ((char_start IS NULL AND char_end IS NULL) OR
        (char_start IS NOT NULL AND char_end IS NOT NULL AND char_end > char_start)),
    CONSTRAINT ck_rag_chunk_meta CHECK (metadata IS NULL OR JSON_TYPE(metadata) = 'OBJECT'),
    FULLTEXT INDEX ft_rag_chunk_content (content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS rag_embedding (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    chunk_id BIGINT UNSIGNED NOT NULL,
    provider VARCHAR(32) NOT NULL DEFAULT 'ollama',
    model_name VARCHAR(128) NOT NULL COMMENT 'Embedding model, independent of chat model',
    model_digest VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Pinned model digest; tags alone may change',
    preprocessing_version VARCHAR(64) NOT NULL DEFAULT 'v1' COMMENT 'Includes query/document prefixes and normalization policy',
    dimensions INT UNSIGNED NOT NULL,
    embedding JSON NOT NULL COMMENT 'Numeric vector; no native ANN index in this migration',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_rag_embedding_chunk FOREIGN KEY (chunk_id) REFERENCES rag_chunk(id) ON DELETE CASCADE,
    CONSTRAINT uk_rag_embedding_model UNIQUE (chunk_id, provider, model_digest, preprocessing_version, dimensions),
    CONSTRAINT ck_rag_embedding_dimensions CHECK (dimensions > 0 AND dimensions <= 65536),
    CONSTRAINT ck_rag_embedding_model CHECK (CHAR_LENGTH(TRIM(model_name)) > 0 AND CHAR_LENGTH(TRIM(model_digest)) > 0),
    CONSTRAINT ck_rag_embedding_array CHECK (JSON_TYPE(embedding) = 'ARRAY' AND JSON_LENGTH(embedding) = dimensions),
    CONSTRAINT ck_rag_embedding_numbers CHECK (JSON_SCHEMA_VALID(
        '{"type":"array","minItems":1,"items":{"type":"number"}}', embedding)),
    INDEX idx_rag_embedding_space (provider, model_digest, preprocessing_version, dimensions)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
