package dto.response;

import dto.type.DtoType;

public class CheckIdResponse extends DTO {

    public enum Status {
        AVAILABLE, DUPLICATE, ERROR
    }

    private final String loginId;
    private final Status status;

    public CheckIdResponse(String loginId, Status status) {
        super(DtoType.CHECK_ID);
        this.loginId = loginId;
        this.status = status;
    }

    @Override
    public String toString() {
        return super.toString() + loginId + "," + status;
    }
}