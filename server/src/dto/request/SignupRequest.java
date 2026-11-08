package dto.request;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SignupRequest {

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

    public SignupRequest(String message) {
        String[] values = message.split(",", -1);

        if (values.length != 10) {
            throw new IllegalArgumentException("잘못된 회원가입 요청입니다.");
        }

        loginId = decode(values[0]);
        password = decode(values[1]);
        nickname = decode(values[2]);
        postalCode = decode(values[3]);
        address = decode(values[4]);
        addressDetail = decode(values[5]);
        profileImage = values[6];
        email = decode(values[7]);
        verificationId = Long.parseLong(values[8]);
        emailCode = values[9];
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8);
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPassword() {
        return password;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getAddress() {
        return address;
    }

    public String getAddressDetail() {
        return addressDetail;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public String getEmail() {
        return email;
    }

    public long getVerificationId() {
        return verificationId;
    }

    public String getEmailCode() {
        return emailCode;
    }
}