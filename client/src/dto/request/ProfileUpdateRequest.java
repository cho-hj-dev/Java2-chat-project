package dto.request;

import dto.type.DtoType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class ProfileUpdateRequest extends DTO {

    private final String payload;

    public ProfileUpdateRequest(
            String requestId,
            String nickname,
            String postalCode,
            String address,
            String addressDetail,
            String imageAction,
            String imageData,
            String currentPassword,
            String newPassword) {

        super(DtoType.PROFILE_UPDATE);

        payload = String.join(",",
                UUID.fromString(requestId).toString(),
                encode(nickname),
                encode(postalCode),
                encode(address),
                encode(addressDetail),
                imageAction,
                imageData,
                encode(currentPassword),
                encode(newPassword)
        );
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public String toString() {
        return super.toString() + payload;
    }
}