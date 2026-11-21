USE java_chat;

-- 방 자체는 서버를 재시작해도 유지한다.
CREATE TABLE chat_rooms (
    room_id BIGINT NOT NULL AUTO_INCREMENT,
    room_name VARCHAR(40) NOT NULL,
    room_type ENUM('GROUP', 'DIRECT') NOT NULL,
    created_by INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (room_id),
    CONSTRAINT uq_chat_rooms_name UNIQUE (room_name),
    CONSTRAINT fk_chat_rooms_creator
        FOREIGN KEY (created_by) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 현재 화면에 입장해 있는지와 관계없이 방의 소속 회원을 기록한다.
CREATE TABLE chat_room_members (
    room_id BIGINT NOT NULL,
    user_id INT NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (room_id, user_id),
    INDEX idx_chat_room_members_user (user_id, room_id),
    CONSTRAINT fk_chat_room_members_room
        FOREIGN KEY (room_id) REFERENCES chat_rooms (room_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_chat_room_members_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 내용은 기존 그림의 Base64 문자열도 담을 수 있게 MEDIUMTEXT로 둔다.
-- 귓속말은 recipient_user_id로 수신자를 구별한다.
CREATE TABLE chat_messages (
    message_id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    sender_user_id INT NOT NULL,
    recipient_user_id INT NULL,
    message_type ENUM('CHAT', 'IMAGE', 'WHISPER', 'EMOTICON') NOT NULL,
    content MEDIUMTEXT NOT NULL,
    sent_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (message_id),
    INDEX idx_chat_messages_room_time (room_id, sent_at, message_id),
    CONSTRAINT fk_chat_messages_room
        FOREIGN KEY (room_id) REFERENCES chat_rooms (room_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_chat_messages_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 로비도 메시지를 저장하려면 고정된 방 ID가 필요하다.
-- 일반 방 목록에는 로비를 포함하지 않도록 서버에서 구분한다.
INSERT INTO chat_rooms (room_name, room_type, created_by)
VALUES ('Lobby', 'GROUP', NULL);