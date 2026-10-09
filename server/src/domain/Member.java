package domain;

import java.time.LocalDateTime;

public class Member {

    private final int userId;
    private final String loginId;
    private final String nickname;
    private final LocalDateTime createdAt;

    public Member(int userId, String loginId, String nickname,
                  LocalDateTime createdAt) {
        this.userId = userId;
        this.loginId = loginId;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }

    public int getUserId() {
        return userId;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getNickname() {
        return nickname;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}