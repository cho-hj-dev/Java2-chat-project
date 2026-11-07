USE java_chat;

-- 적용 전 중복 닉네임 확인
SELECT nickname, COUNT(*) AS duplicate_count
FROM users
GROUP BY nickname
HAVING COUNT(*) > 1;

-- 중복이 없을 때 기존 DB에 한 번만 적용
ALTER TABLE users
ADD CONSTRAINT uq_users_nickname UNIQUE (nickname);