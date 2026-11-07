package dto.request;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class LoginRequest {
    private final String id;
    private final String password;

    public LoginRequest(String message) {
        String[] values = message.split(",", -1);
        if (values.length != 2) {
            throw new IllegalArgumentException("Invalid login request.");
        }
        id = decode(values[0]);
        password = decode(values[1]);
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public String getId() {
        return id;
    }

    public String getPassword() {
        return password;
    }
}
