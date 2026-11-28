USE java_chat;

-- 기존 회원과 새 회원은 모두 일반 사용자로 시작한다.
-- 관리자 지정은 운영자가 별도 UPDATE로 수행한다.
ALTER TABLE users
    ADD COLUMN user_role ENUM('USER', 'ADMIN')
        NOT NULL DEFAULT 'USER';
