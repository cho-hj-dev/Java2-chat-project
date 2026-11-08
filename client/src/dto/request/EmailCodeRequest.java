package dto.request;

import dto.type.DtoType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EmailCodeRequest extends DTO {

    private final String payload;

    private EmailCodeRequest(String payload) {
        super(DtoType.EMAIL_CODE);
        this.payload = payload;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static EmailCodeRequest send(String email) {
        return new EmailCodeRequest("SEND," + encode(email));
    }

    public static EmailCodeRequest verify(
            long verificationId, String email, String code) {

        return new EmailCodeRequest(
                "VERIFY," + verificationId
                + "," + encode(email)
                + "," + code
        );
    }

    @Override
    public String toString() {
        return super.toString() + payload;
    }
}