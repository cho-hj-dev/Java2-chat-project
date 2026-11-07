package dto.response;

import domain.MemberProfile;
import dto.type.DtoType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ProfileResponse extends DTO {

    private final String requestId;
    private final String status;
    private final MemberProfile profile;
    private final String imageStatus;
    private final String image;

    public ProfileResponse(
            String requestId,
            String status,
            MemberProfile profile,
            String imageStatus,
            String image) {

        super(DtoType.PROFILE_RESULT);

        this.requestId = requestId;
        this.status = status;
        this.profile = profile;
        this.imageStatus = imageStatus;
        this.image = image;
    }

    private static String encode(String value) {
        String text = value == null ? "" : value;

        return Base64.getEncoder().encodeToString(
                text.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public String toString() {
        MemberProfile data = profile == null
                ? new MemberProfile("", "", "", "", "", "")
                : profile;

        return super.toString() + String.join(",",
                requestId,
                status,
                encode(data.loginId()),
                encode(data.nickname()),
                encode(data.postalCode()),
                encode(data.address()),
                encode(data.addressDetail()),
                imageStatus,
                image
        );
    }
}