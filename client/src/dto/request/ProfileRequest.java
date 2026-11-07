package dto.request;

import dto.type.DtoType;
import java.util.UUID;

public class ProfileRequest extends DTO {

    private final String requestId;

    public ProfileRequest(String requestId) {
        super(DtoType.PROFILE_GET);
        this.requestId = UUID.fromString(requestId).toString();
    }

    @Override
    public String toString() {
        return super.toString() + requestId;
    }
}