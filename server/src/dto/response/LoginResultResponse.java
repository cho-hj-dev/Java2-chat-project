package dto.response;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import dto.type.DtoType;

public class LoginResultResponse extends DTO {

    public enum Status {
        SUCCESS,
        INVALID_CREDENTIALS,
        ALREADY_LOGGED_IN,
        ERROR
    }

    private final Status status;
    private final String loginId;
    private final String nickname;
    private final String userRole;

    public LoginResultResponse(Status status,
                               String loginId, String nickname) {
        this(status, loginId, nickname, "USER");
    }

    public LoginResultResponse(Status status,
                               String loginId, String nickname, String userRole) {
        super(DtoType.LOGIN_RESULT);
        this.status = status;
        this.loginId = loginId;
        this.nickname = nickname;
        this.userRole = userRole;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return super.toString() + status.name() + ","
                + encode(loginId) + "," + encode(nickname)
                + "," + userRole;
    }
}
