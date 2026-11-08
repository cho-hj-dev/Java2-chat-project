package dto.request;

import dto.type.DtoType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AccountRecoveryRequest extends DTO {

    private final String payload;

    private AccountRecoveryRequest(String payload) {
        super(DtoType.ACCOUNT_RECOVERY);
        this.payload = payload;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    public static AccountRecoveryRequest sendFindId(String email) {
        return new AccountRecoveryRequest(
                "FIND_ID_SEND," + encode(email));
    }

    public static AccountRecoveryRequest verifyFindId(
            long verificationId, String email, String code) {
        return new AccountRecoveryRequest(
                "FIND_ID_VERIFY," + verificationId
                + "," + encode(email) + "," + code);
    }

    public static AccountRecoveryRequest sendReset(
            String loginId, String email) {
        return new AccountRecoveryRequest(
                "RESET_SEND," + encode(loginId)
                + "," + encode(email));
    }

    public static AccountRecoveryRequest resetPassword(
            long verificationId, String loginId, String email,
            String code, String newPassword) {
        return new AccountRecoveryRequest(
                "RESET_COMMIT," + verificationId
                + "," + encode(loginId)
                + "," + encode(email)
                + "," + code
                + "," + encode(newPassword));
    }

    @Override
    public String toString() {
        return super.toString() + payload;
    }
}
