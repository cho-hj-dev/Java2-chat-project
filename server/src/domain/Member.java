package domain;

import java.time.LocalDateTime;

public class Member {

    private final int userId;
    private final String loginId;
    private final String nickname;
    private final String userRole;
    private final LocalDateTime createdAt;

    public Member(int userId, String loginId, String nickname,
                  LocalDateTime createdAt) {
        this(userId, loginId, nickname, createdAt, "USER");
    }

    public Member(int userId, String loginId, String nickname,
                  LocalDateTime createdAt, String userRole) {
        this.userId = userId;
        this.loginId = loginId;
        this.nickname = nickname;
        this.createdAt = createdAt;
        this.userRole = userRole;
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

    public String getUserRole() {
        return userRole;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
