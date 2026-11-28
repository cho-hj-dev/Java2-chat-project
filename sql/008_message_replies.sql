USE java_chat;

-- 답장이 참조하는 원본 메시지 ID.
-- 기존 메시지는 답장이 아니므로 NULL로 남는다.
ALTER TABLE chat_messages
    ADD COLUMN reply_to_message_id BIGINT NULL
        AFTER recipient_user_id,

    -- 원본 메시지에 연결된 답장을 찾을 때 사용한다.
    ADD INDEX idx_chat_messages_reply_to
        (reply_to_message_id),

    -- 존재하지 않는 메시지 ID를 답장 대상으로 저장하지 못하게 한다.
    ADD CONSTRAINT fk_chat_messages_reply_to
        FOREIGN KEY (reply_to_message_id)
        REFERENCES chat_messages (message_id)
        ON DELETE SET NULL;