USE java_chat;

-- 이모티콘의 PNG/GIF 원본을 저장한다.
-- 기존 채팅 메시지의 content에는 SMILE 같은 emoticon_id가 저장된다.
CREATE TABLE emoticon_assets (
    emoticon_id VARCHAR(32) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    mime_type ENUM('image/png', 'image/gif') NOT NULL,
    image_data MEDIUMBLOB NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (emoticon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;