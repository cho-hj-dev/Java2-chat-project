package dto.response;

import dto.type.DtoType;

public class SignupResponse extends DTO {

    public enum Status {
        SUCCESS,
        DUPLICATE_ID,
        INVALID_INPUT,
        ERROR,
        DUPLICATE_NICKNAME,
    }

    private final Status status;

    public SignupResponse(Status status) {
        super(DtoType.SIGNUP);
        this.status = status;
    }

    @Override
    public String toString() {
        return super.toString() + status.name();
    }
}
