USE java_chat;

-- 메시지 하나에서 여러 회원을 멘션할 수 있으므로 연결 테이블로 저장한다.
-- 실제 멘션 대상과 방 참여 여부는 서버가 검증한 뒤 이 테이블에 기록한다.
CREATE TABLE chat_message_mentions (
    message_id BIGINT NOT NULL,
    mentioned_user_id INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (message_id, mentioned_user_id),
    INDEX idx_chat_message_mentions_user (mentioned_user_id, message_id),

    -- 메시지가 삭제되면 해당 메시지의 멘션도 함께 삭제한다.
    CONSTRAINT fk_chat_message_mentions_message
        FOREIGN KEY (message_id) REFERENCES chat_messages (message_id)
        ON DELETE CASCADE,

    -- 회원이 삭제되면 그 회원을 가리키던 멘션도 함께 삭제한다.
    CONSTRAINT fk_chat_message_mentions_user
        FOREIGN KEY (mentioned_user_id) REFERENCES users (user_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
