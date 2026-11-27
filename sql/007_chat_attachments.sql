USE java_chat;

-- 기존 메시지 종류에 파일 메시지를 추가한다.
-- 기존 CHAT, IMAGE, WHISPER, EMOTICON 값은 그대로 유지한다.
ALTER TABLE chat_messages
    MODIFY COLUMN message_type
    ENUM('CHAT', 'IMAGE', 'WHISPER', 'EMOTICON', 'FILE') NOT NULL;

-- 파일 메시지 한 건에 파일 원본 한 개를 연결한다.
-- 방과 발신자는 chat_messages를 통해 확인한다.
CREATE TABLE chat_attachments (
    attachment_id BIGINT NOT NULL AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes INT NOT NULL,
    sha256_hex CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    file_data MEDIUMBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (attachment_id),

    -- 현재는 메시지 한 건당 파일 한 개만 허용한다.
    UNIQUE KEY uq_chat_attachments_message (message_id),

    CONSTRAINT fk_chat_attachments_message
        FOREIGN KEY (message_id)
        REFERENCES chat_messages (message_id)
        ON DELETE CASCADE,

    -- 빈 파일도 허용하되, 최대 크기는 10MB로 제한한다.
    CONSTRAINT chk_chat_attachments_size
        CHECK (file_size_bytes BETWEEN 0 AND 10485760)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;