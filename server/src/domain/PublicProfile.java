package domain;

import java.time.LocalDateTime;

public record PublicProfile(String loginId, String nickname,
        String statusMessage, String imagePath, LocalDateTime joinedAt) {
}
