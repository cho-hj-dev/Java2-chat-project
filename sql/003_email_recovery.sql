USE java_chat;

-- 기존 회원은 이메일이 없으므로 NULL을 허용한다.
-- MySQL UNIQUE 인덱스에서는 여러 행의 NULL을 허용한다.
ALTER TABLE users
    ADD COLUMN email VARCHAR(254) NULL,
    ADD COLUMN email_verified_at DATETIME NULL,
    ADD CONSTRAINT uq_users_email UNIQUE (email);

CREATE TABLE email_verifications (
    verification_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id INT NULL,
    email VARCHAR(254) NOT NULL,

    -- SIGNUP: 신규 가입
    -- BIND_EMAIL: 기존 회원의 이메일 등록
    -- FIND_ID: 아이디 찾기
    -- RESET_PASSWORD: 비밀번호 재설정
    purpose ENUM(
        'SIGNUP',
        'BIND_EMAIL',
        'FIND_ID',
        'RESET_PASSWORD'
    ) NOT NULL,

    code_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    consumed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (verification_id),

    INDEX idx_email_verifications_lookup
        (email, purpose, created_at),

    INDEX idx_email_verifications_user
        (user_id),

    CONSTRAINT fk_email_verifications_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;