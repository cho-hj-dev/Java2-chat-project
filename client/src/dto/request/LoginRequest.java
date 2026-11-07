package dto.request;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import dto.type.DtoType;

public class LoginRequest extends DTO {
    private final String loginId;
    private final String password;

    public LoginRequest(String loginId, String password) {
        super(DtoType.LOGIN);
        this.loginId = loginId;
        this.password = password;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return super.toString() + encode(loginId) + "," + encode(password);
    }
}
