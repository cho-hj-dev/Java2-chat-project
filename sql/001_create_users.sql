USE java_chat;

CREATE TABLE users (
    user_id INT NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    profile_image_path VARCHAR(255) NULL,
    status_message VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    postal_code VARCHAR(10) NULL,
    address VARCHAR(255) NULL,
    address_detail VARCHAR(255) NULL,

    PRIMARY KEY (user_id),
    CONSTRAINT uq_users_login_id UNIQUE (login_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;