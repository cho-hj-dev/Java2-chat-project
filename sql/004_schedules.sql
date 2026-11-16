USE java_chat;

-- 일정은 회원별로 저장한다.
CREATE TABLE schedules (
    schedule_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    schedule_date DATE NOT NULL,
    title VARCHAR(100) NOT NULL,
    details TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (schedule_id),

    -- 회원의 날짜별 일정을 조회할 때 사용한다.
    INDEX idx_schedules_user_date (user_id, schedule_date),

    CONSTRAINT fk_schedules_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;