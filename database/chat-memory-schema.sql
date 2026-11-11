-- MySQL 8.4. Additive migration; no existing account or RAG tables are changed.
CREATE TABLE IF NOT EXISTS chat_memory_conversation (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    owner_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account VARCHAR(64) NOT NULL,
    title VARCHAR(160) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'general',
    primary_index VARCHAR(191) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_chat_memory_owner (owner_key, updated_at, id),
    INDEX idx_chat_memory_category (owner_key, category, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS chat_memory_message (
    sequence_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    conversation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role VARCHAR(16) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'general',
    primary_index VARCHAR(191) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'COMPLETE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_chat_memory_conversation FOREIGN KEY (conversation_id)
        REFERENCES chat_memory_conversation(id) ON DELETE CASCADE,
    CONSTRAINT ck_chat_memory_role CHECK (role IN ('user','assistant')),
    CONSTRAINT ck_chat_memory_status CHECK (status IN ('COMPLETE','INTERRUPTED','FAILED')),
    INDEX idx_chat_memory_messages (conversation_id, sequence_id),
    INDEX idx_chat_memory_question (conversation_id, category, primary_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
