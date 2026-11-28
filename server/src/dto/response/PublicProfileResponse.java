package dto.response;

import domain.PublicProfile;
import dto.type.DtoType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PublicProfileResponse extends DTO {
    private final String requestId;
    private final String status;
    private final PublicProfile profile;
    private final String imageStatus;
    private final String image;

    public PublicProfileResponse(String requestId, String status,
            PublicProfile profile, String imageStatus, String image) {
        super(DtoType.PUBLIC_PROFILE_RESULT);
        this.requestId = requestId;
        this.status = status;
        this.profile = profile;
        this.imageStatus = imageStatus;
        this.image = image;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return super.toString() + String.join(",",
                requestId, status,
                encode(profile == null ? "" : profile.loginId()),
                encode(profile == null ? "" : profile.nickname()),
                encode(profile == null ? "" : profile.statusMessage()),
                encode(profile == null ? "" : profile.joinedAt().toString()),
                imageStatus, image);
    }
}
