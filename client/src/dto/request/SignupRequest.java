package dto.request;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import dto.type.DtoType;

public class SignupRequest extends DTO {

    private final String loginId;
    private final String password;
    private final String nickname;
    private final String postalCode;
    private final String address;
    private final String addressDetail;
    private final String profileImage;
    private final String email;
    private final long verificationId;
    private final String emailCode;

    public SignupRequest(
            String loginId, String password, String nickname,
            String postalCode, String address, String addressDetail,
            String profileImage, String email,
            long verificationId, String emailCode) {

        super(DtoType.SIGNUP);
        this.loginId = loginId;
        this.password = password;
        this.nickname = nickname;
        this.postalCode = postalCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.profileImage = profileImage;
        this.email = email;
        this.verificationId = verificationId;
        this.emailCode = emailCode;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return super.toString()
                + encode(loginId) + ","
                + encode(password) + ","
                + encode(nickname) + ","
                + encode(postalCode) + ","
                + encode(address) + ","
                + encode(addressDetail) + ","
                + profileImage + ","
                + encode(email) + ","
                + verificationId + ","
                + emailCode;
    }
}