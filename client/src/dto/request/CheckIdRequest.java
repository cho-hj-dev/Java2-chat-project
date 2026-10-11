package dto.request;

import dto.type.DtoType;

public class CheckIdRequest extends DTO {

    private final String loginId;

    public CheckIdRequest(String loginId) {
        super(DtoType.CHECK_ID);
        this.loginId = loginId;
    }

    @Override
    public String toString() {
        return super.toString() + loginId;
    }
}