package dto.request;

import dto.type.DtoType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class PublicProfileRequest extends DTO {
    private final String requestId;
    private final String loginId;

    public PublicProfileRequest(String requestId, String loginId) {
        super(DtoType.PUBLIC_PROFILE_GET);
        this.requestId = UUID.fromString(requestId).toString();
        this.loginId = loginId;
    }

    @Override
    public String toString() {
        return super.toString() + requestId + "," +
                Base64.getEncoder().encodeToString(
                        loginId.getBytes(StandardCharsets.UTF_8));
    }
}
